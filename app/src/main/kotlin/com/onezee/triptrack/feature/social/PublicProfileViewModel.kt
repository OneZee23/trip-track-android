// iOS: PublicProfileView.swift (refresh, loadProfile, loadAllBadges, loadMoreTripsIfNeeded,
// hydrateLegacyTrips, toggleBlock, toggleFollow, resumeFollowAfterSignIn, toggleReaction,
// shareTrip, recomputeProfileRows, loadGaragePreview, loadJourneysPreview)
package com.onezee.triptrack.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.logic.build
import com.onezee.triptrack.model.*
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The journal of someone's public trips, prepared off the main thread (iOS `recomputeProfileRows`). */
data class PublicJournal(
    val history: PreparedProfileHistory,
    val originals: Map<UUID, SocialFeedTrip>,
    val gridCards: List<SocialFeedTrip>,
)

data class PublicProfileState(
    val profile: SocialProfile? = null,
    /** Set when the first load failed: what the screen draws instead of a profile. */
    val unavailable: ProfileUnavailableState? = null,
    /** A failed refresh of an already loaded profile: the trips section says so. */
    val loadFailed: Boolean = false,
    val isLoading: Boolean = false,
    val isTogglingFollow: Boolean = false,
    val isBlocked: Boolean = false,
    val isTogglingBlock: Boolean = false,
    val garage: List<PublicVehicle> = emptyList(),
    val journeys: List<PublicJourneyDto> = emptyList(),
    /** Every badge of the account (`/users/:id/badges`); null until it came, and the card stays a preview. */
    val allBadgeIds: List<String>? = null,
    val tripCards: List<SocialFeedTrip> = emptyList(),
    val pinnedCards: List<SocialFeedTrip> = emptyList(),
    val pager: ProfileTripsPager = ProfileTripsPager(),
    val journal: PublicJournal? = null,
)

/**
 * iOS: PublicProfileView's state and networking. One screen per account; only the latest load may
 * commit, so a pull-to-refresh racing an error recovery never leaves the older answer on screen.
 */
class PublicProfileViewModel(
    val accountId: UUID,
    val preloaded: SocialAuthor?,
    private val social: SocialFeature,
    private val locale: () -> Locale = { Locale.getDefault() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : ViewModel() {
    private val api = social.api
    private val worker = social.worker
    private val mutable = MutableStateFlow(PublicProfileState())
    val state: StateFlow<PublicProfileState> = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var journalJob: Job? = null
    private var garageGeneration = 0
    private var generation = 0

    val isOwnProfile: Boolean get() = social.accountId == accountId

    /** Server profile, then the identity that came with the tap, then «Водитель». */
    fun displayName(fallback: String): String = state.value.profile?.displayName?.takeIf { it.isNotEmpty() }
        ?: preloaded?.displayName?.takeIf { it.isNotEmpty() } ?: fallback

    /** Cancels the previous load: only the latest request commits. */
    fun refresh(): Job {
        loadJob?.cancel()
        generation++
        return viewModelScope.launch {
            if (isOwnProfile) runCatching { social.syncOwnProfile() }
            loadProfile()
            // A pull-to-refresh replaces the header car too: a photo hidden since must not stay.
            loadGarage(force = true)
        }.also { loadJob = it }
    }

    private suspend fun loadProfile() {
        val current = generation
        mutable.update { it.copy(isLoading = true, loadFailed = false) }
        try {
            val profile = withContext(worker) { api.profile(accountId) }
            // The author fallback only matters for a server that still sends six-field trips.
            if (current != generation) return
            val fallback = SocialAuthor(accountId, profile.displayName, profile.avatarEmoji, profile.profileLevel)
            val pinned = profile.pinnedTrips.orEmpty().map { it.feedTrip(fallback) }
            val cards = profile.recentTrips.map { it.feedTrip(fallback) }
            mutable.update { it.copy(profile = profile, unavailable = null, pinnedCards = pinned, tripCards = cards,
                pager = ProfileTripsPager.reset(cards, profile.stats.publicTripCount), allBadgeIds = null) }
            prepareJournal()
            val legacy = profile.recentTrips.filter { it.author == null }
            if (legacy.isNotEmpty()) viewModelScope.launch { hydrateLegacy(legacy) }
            if (profile.openBlocks.achievements) viewModelScope.launch { loadBadges() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.update { it.copy(loadFailed = true, unavailable = if (it.profile == null) ProfileUnavailableState.from(error, preloaded) else null) }
        } finally { mutable.update { it.copy(isLoading = false) } }
    }

    /** A retry from the unavailable screen: the profile only, as on iOS. */
    fun retry() { loadJob?.cancel(); loadJob = viewModelScope.launch { loadProfile() } }

    /** Every badge (0.9.1). A failure, an older server included, leaves the preview as it was. */
    private suspend fun loadBadges() {
        val current = generation
        try { val ids = withContext(worker) { api.badges(accountId) }; if (current != generation) return; mutable.update { it.copy(allBadgeIds = ids) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {}
    }

    /**
     * Fills in what a pre-feed profile left out from `/social/trip`, per trip and silently: a card
     * that cannot be re-read keeps what it has. Replaced BY ID: a refresh may rebuild the list.
     */
    private suspend fun hydrateLegacy(legacy: List<SocialProfileRecentTrip>) {
        val current = generation
        for (trip in legacy) {
            try {
                val item = withContext(worker) { api.trip(trip.id, includeTrack = false).item }
                if (current != generation) return
                mutable.update { it.copy(tripCards = it.tripCards.map { card -> if (card.id == item.id) item else card }) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {}
        }
        prepareJournal()
    }

    /** The garage for the header car and the sections row; a failed refresh keeps the snapshot. */
    fun loadGarage(force: Boolean = false): Job? {
        if (!force && state.value.garage.isNotEmpty()) return null
        val generation = ++garageGeneration
        return viewModelScope.launch {
            val vehicles = try { withContext(worker) { api.garage(accountId) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (generation != garageGeneration || (force && vehicles == null)) return@launch
            mutable.update { it.copy(garage = vehicles.orEmpty()) }
        }
    }

    /**
     * One page of 50 journeys, not three: the hub card shows three, its caption counts all of them,
     * and the journal folds legs by every journey.
     */
    fun loadJourneys(): Job? {
        val current = generation
        if (state.value.journeys.isNotEmpty()) return null
        return viewModelScope.launch {
            val journeys = try { withContext(worker) { api.journeys(accountId, null, 50).journeys } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            if (current != generation) return@launch
            mutable.update { it.copy(journeys = journeys) }
            prepareJournal()
        }
    }

    /** The next page once a card near the end shows; [count] counts the list the card is in. */
    fun loadMoreIfNeeded(appearedIndex: Int, count: Int): Job? {
        val current = generation
        val pager = state.value.pager
        val cursor = pager.cursor ?: return null
        if (!pager.shouldLoad(appearedIndex, count)) return null
        mutable.update { it.copy(pager = it.pager.begin()) }
        return viewModelScope.launch {
            try {
                val page = withContext(worker) { api.profileTrips(accountId, cursor, ProfileTripsPager.PAGE_SIZE) }
                // The profile was refreshed while the page travelled: its cursor belongs to the old list.
                if (current != generation || state.value.pager.cursor != cursor) return@launch
                mutable.update { old -> old.pager.apply(page, old.tripCards).let { (pager, cards) -> old.copy(pager = pager, tripCards = cards) } }
                prepareJournal()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (state.value.pager.cursor == cursor) mutable.update { it.copy(pager = it.pager.fail()) } }
        }
    }

    /** The public DTO has no checkpoints: absence is unknown, so these trips never fold as routine. */
    private fun prepareJournal() {
        journalJob?.cancel()
        val snapshot = state.value
        val language = locale()
        val zone = zone()
        journalJob = viewModelScope.launch {
            val journal = withContext(worker) {
                val pins = snapshot.pinnedCards
                val all = pins + snapshot.tripCards.filter { card -> pins.none { it.id == card.id } }
                val history = PreparedProfileHistory.build(ProfileHistoryLibrary(all.map { it.asTrip() }),
                    snapshot.journeys.map { it.asJourney(accountId) }, "", null, null, language, zone,
                    pinnedTripIds = pins.map { it.id }, foldsRoutine = false, keepsEmptyJourneys = false, foldsJourneysInGrid = false,
                    now = social.clock())
                val originals = all.associateBy { it.id }
                PublicJournal(history, originals, history.gridRuns.flatten().mapNotNull { row -> (row as? HistoryRow.Trip)?.let { originals[it.value.id] } })
            }
            mutable.update { it.copy(journal = journal) }
        }
    }

    /** Optimistic; on a refusal the server's truth is read again. */
    fun toggleFollow(): Job? {
        val current = state.value.profile ?: return null
        if (!social.signedIn || isOwnProfile || state.value.isTogglingFollow) return null
        val was = current.isFollowing ?: false
        mutable.update { it.copy(isTogglingFollow = true, profile = current.copy(isFollowing = !was, followerCount = maxOf(0, current.followerCount + if (was) -1 else 1))) }
        return viewModelScope.launch {
            try { withContext(worker) { api.follow(accountId, follow = !was) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.update { it.copy(profile = current) }
                social.say(SocialMessage.actionFailed)
                loadJob?.cancel(); loadProfile()
            }
            finally { mutable.update { it.copy(isTogglingFollow = false) } }
        }
    }

    /**
     * The follow a guest tapped before signing in. Posted directly (the session flip reloads the
     * profile, so the optimistic path has nothing to flip); the refresh after it shows the truth.
     */
    fun resumeFollowAfterSignIn(): Job = viewModelScope.launch {
        mutable.update { it.copy(isTogglingFollow = true) }
        try { withContext(worker) { api.follow(accountId, follow = true) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {}
        finally { mutable.update { it.copy(isTogglingFollow = false) } }
        refresh().join()
    }

    /** Optimistic. Blocking also ends the follow both ways, which the server already did. */
    fun toggleBlock(): Job? {
        if (!social.signedIn || isOwnProfile || state.value.isTogglingBlock) return null
        val was = state.value.isBlocked
        mutable.update { it.copy(isBlocked = !was, isTogglingBlock = true) }
        return viewModelScope.launch {
            try {
                withContext(worker) { api.block(accountId, block = !was) }
                if (!was) mutable.update { s -> s.copy(profile = s.profile?.copy(isFollowing = false)) }
            } catch (cancelled: CancellationException) { mutable.update { it.copy(isBlocked = was) }; throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(isBlocked = was) }; social.say(SocialMessage.actionFailed) }
            finally { mutable.update { it.copy(isTogglingBlock = false) } }
        }
    }

    /** Optimistic like the feed's; the card comes back if the server refuses. Both lists, by id. */
    fun toggleReaction(tripId: UUID, emoji: String): Job? {
        val before = state.value.tripCards.firstOrNull { it.id == tripId } ?: state.value.pinnedCards.firstOrNull { it.id == tripId } ?: return null
        val after = before.togglingReaction(emoji)
        replaceCard(tripId) { after }
        return viewModelScope.launch {
            try { withContext(worker) { social.trips.social.react(tripId, before.myReaction, emoji) } }
            catch (cancelled: CancellationException) { replaceCard(tripId) { before }; throw cancelled }
            catch (_: Exception) { replaceCard(tripId) { before }; social.say(SocialMessage.actionFailed) }
        }
    }

    /** A comment written on a trip pushed off this page: its card shows the new count on the way back. */
    fun commentCountChanged(tripId: UUID, delta: Int) = replaceCard(tripId) { it.withCommentCount(maxOf(0, it.commentCount + delta)) }

    private fun replaceCard(tripId: UUID, change: (SocialFeedTrip) -> SocialFeedTrip) {
        mutable.update { s ->
            s.copy(tripCards = s.tripCards.map { if (it.id == tripId) change(it) else it },
                pinnedCards = s.pinnedCards.map { if (it.id == tripId) change(it) else it },
                journal = s.journal?.let { j -> j.copy(originals = j.originals.mapValues { (id, card) -> if (id == tripId) change(card) else card },
                    gridCards = j.gridCards.map { if (it.id == tripId) change(it) else it }) })
        }
    }

    /** A link for a trip card's share: a refused or unreachable `/social/share` still opens the sheet, link-less. */
    suspend fun shareLink(tripId: UUID): String? = try { withContext(worker) { api.shareTrip(tripId).shareUrl } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    /** The session changed under the screen: the previous account's answer must not stay. */
    fun sessionChanged() {
        journalJob?.cancel()
        garageGeneration++
        mutable.value = PublicProfileState()
        refresh()
    }
}

/** iOS PublicProfileView.isPrivacyMode: an active account whose roads are all hidden reads «•••», not zeros. */
fun SocialProfile.isPrivacyMode(isOwn: Boolean): Boolean =
    !isOwn && stats.tripCount == 0 && (profileLevel > 1 || currentStreak > 0 || bestStreak > 0)

/** iOS PublicProfileView.hasPublicRoutes: the server's count (a public trip may have no preview). */
val SocialProfile.hasPublicRoutes: Boolean get() = stats.publicTripCount > 0
