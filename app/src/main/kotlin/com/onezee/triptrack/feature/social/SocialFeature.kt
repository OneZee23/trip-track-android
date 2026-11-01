// iOS: the services the social screens reach through `.shared` (AuthService, TokenStore,
// NotificationsInboxStore, CompanionsStore) and the `.openUserProfile` / `.openTripDetail` /
// `.openJourneyDetail` hand-offs of TripTrackApp and FeedView.
package com.onezee.triptrack.feature.social

import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.account.AuthService
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Transient feedback of the social screens and the link router, resolved to words by the UI. */
enum class SocialMessage { loadFailed, actionFailed, linkCopied, nameCopied, tripUnavailable, respondFailed }

/**
 * The social side's singletons and view model factories, built once by the app graph. [auth] is
 * null in tests: the screens then answer a guest action with a toast instead of the sign-in sheet.
 */
class SocialFeature(
    val api: SocialApi,
    val trips: TripsFeature,
    val auth: AuthService?,
    private val accountIdSource: () -> UUID?,
    preferences: SharedPreferences,
    /** GET of a page of the website (`/s/<code>`), the HTML body; the share-code resolution reads it. */
    private val sharePage: suspend (String) -> String,
    val worker: CoroutineDispatcher = Dispatchers.IO,
    val clock: () -> Instant = Instant::now,
    /** The viewer's avatar, for the comment composer (iOS SettingsManager.avatarEmoji). */
    val ownAvatar: () -> String = { "🚗" },
    val ownAuthor: () -> com.onezee.triptrack.model.SocialAuthor? = {
        accountIdSource()?.let { com.onezee.triptrack.model.SocialAuthor(it, auth?.state?.value?.userName, ownAvatar(), 1) }
    },
    val commentArchive: com.onezee.triptrack.feature.feed.CommentArchive,
    /** iOS SettingsManager.showPlusBadge: one's own badge obeys the privacy switch. */
    val showsOwnBadge: () -> Boolean = { true },
    /** iOS AuthService.syncProfileToServer, run before one's own public preview loads. */
    val syncOwnProfile: suspend () -> Unit = {},
    /** The inbox's gear: the notification switches of the settings (null in tests, the gear hides). */
    val notificationPreferences: (() -> com.onezee.triptrack.feature.settings.NotificationPreferencesViewModel)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val accountId: UUID? get() = accountIdSource()
    val signedIn: Boolean get() = accountId != null

    val inbox = InboxStore(api, { signedIn }, worker)
    val invites = CompanionInvites(api, preferences, worker)

    private val mutableMessages = MutableSharedFlow<SocialMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<SocialMessage> = mutableMessages

    /** Set by the app graph: the shell's tab switch, for links that land on a tab. */
    @Volatile var selectTab: (AppTab) -> Unit = {}

    fun say(message: SocialMessage) { mutableMessages.tryEmit(message) }

    private val mutableCommentCounts = MutableSharedFlow<Pair<UUID, Int>>(extraBufferCapacity = 16)
    /** iOS `.tripCommentCountChanged`: a card behind the trip screen shows the new count on the way back. */
    val commentCounts: SharedFlow<Pair<UUID, Int>> = mutableCommentCounts

    fun commentCountChanged(tripId: UUID, delta: Int) {
        mutableCommentCounts.tryEmit(tripId to delta)
        trips.feed.bumpCommentCount(tripId, delta)
    }

    /**
     * Opens a link on the feed's stack (iOS: every hand-off lands in FeedView's path). A profile
     * and a trip are entry points and replace the stack; a journey is pushed, because it also
     * comes from cards inside an already open profile.
     */
    private var linkJob: kotlinx.coroutines.Job? = null

    fun open(link: DeepLink) {
        linkJob?.cancel()
        linkJob = scope.launch {
            val feed = trips.feedNavigator
            when (link) {
                is DeepLink.Profile -> { selectTab(AppTab.home); feed.popToRoot(); feed.push(TripsDestination.PublicProfile(link.id)) }
                is DeepLink.Trip -> openTrip(link.id)
                is DeepLink.Journey -> openJourney(link.id)
                is DeepLink.SharedTrip, is DeepLink.SharedJourney -> {
                    val target = try {
                        withContext(worker) { ShareCodePage.target(sharePage(checkNotNull(ShareCodePage.path(link))), link) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    when (target) {
                        is DeepLink.Trip -> openTrip(target.id)
                        is DeepLink.Journey -> openJourney(target.id)
                        else -> say(SocialMessage.tripUnavailable)
                    }
                }
                DeepLink.Recording -> selectTab(AppTab.record)
                DeepLink.Feed -> selectTab(AppTab.home)
            }
        }
    }

    /** One's own trip opens the local copy; anyone else's from `/social/trip`, as a feed card would. */
    private suspend fun openTrip(id: UUID) {
        selectTab(AppTab.home)
        val local = withContext(worker) { trips.repository.fetchEntity(id) }
        val social = if (local != null) null else try { withContext(worker) { api.trip(id, includeTrack = false).item } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        if (local == null && social == null) { say(SocialMessage.tripUnavailable); return }
        trips.feedNavigator.popToRoot()
        trips.feedNavigator.push(TripsDestination.TripDetail(id, social = social))
    }

    private suspend fun openJourney(id: UUID) {
        selectTab(AppTab.home)
        val own = withContext(worker) { trips.journey(id) } != null
        trips.feedNavigator.push(if (own) TripsDestination.JourneyDetail(id) else TripsDestination.SocialJourney(id))
    }

    /** A push arrived while the app runs: the inbox reloads (iOS `willPresent`). */
    fun pushArrived(category: String?) { if (DeepLink.refreshesInbox(category)) scope.launch { inbox.refresh() } }

    /** A notification was tapped: its route, and the inbox reloads (iOS `didReceive`). */
    fun pushTapped(category: String?, data: Map<String, String>) {
        DeepLink.fromPush(category, data)?.let(::open)
        pushArrived(category)
    }

    /** A hard sign-out or another account: nothing of the last account's social state survives. */
    fun clear() {
        linkJob?.cancel()
        inbox.clear()
        invites.clear()
        trips.feed.clearSocial()
    }

    fun close() { inbox.close(); scope.cancel() }

    fun comments(id: UUID) = com.onezee.triptrack.feature.feed.TripCommentsViewModel(
        id, trips.social, commentArchive, me = ownAuthor, worker = worker,
        countChanged = ::commentCountChanged, clock = clock)

    // MARK: - Screens

    fun profile(id: UUID, preloaded: com.onezee.triptrack.model.SocialAuthor?) = PublicProfileViewModel(id, preloaded, this)
    fun followList(id: UUID, mode: FollowListMode) = FollowListViewModel(id, mode, api, { signedIn }, worker)
    fun garage(id: UUID) = PublicGarageViewModel(id, api, worker)
    fun vehicle(id: UUID, vehicleId: UUID, preloaded: com.onezee.triptrack.model.PublicVehicle?) = PublicVehicleViewModel(id, vehicleId, preloaded, api, worker)
    fun journeys(id: UUID) = PublicJourneysViewModel(id, api, worker)
}

/** The social side for screens composed inside a tab's stack; provided by the shell. */
val LocalSocial = staticCompositionLocalOf<SocialFeature?> { null }
