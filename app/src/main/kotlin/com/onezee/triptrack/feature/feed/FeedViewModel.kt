package com.onezee.triptrack.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.trip.TripMessage
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.trip.CurrentRecordingSource
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: FeedViewModel.swift, SocialFeedStore.swift. Keep this VM in the tab's retained owner.
class FeedViewModel(
    private val repository: TripRepository,
    private val socialApi: TripSocialApi,
    private val worker: CoroutineDispatcher,
    private val signedIn: () -> Boolean,
    private val isOffline: () -> Boolean,
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
    changes: Flow<Unit> = emptyFlow(),
    recording: CurrentRecordingSource? = null,
) : ViewModel() {
    private val mutable = MutableStateFlow(FeedState())
    val state = mutable.asStateFlow()
    private var allTrips = emptyList<Trip>()
    private var journeys = emptyList<Journey>()
    private var filtered = emptyList<Trip>()
    private var page = 1
    private var mineJob: Job? = null
    private val jobs = mutableMapOf<FeedMode, Job>()
    private val generations = mutableMapOf<FeedMode, Int>()
    private val retries = mutableMapOf<FeedMode, Int>()
    private val knownReactions = mutableMapOf<Pair<FeedMode, UUID>, String?>()
    // The wire items behind the cards: a card opens its trip with the item, as iOS pushes SocialFeedTrip.
    private val socialItems = mutableMapOf<UUID, SocialFeedTrip>()
    private var sessionGeneration = 0

    init {
        if (recording != null) viewModelScope.launch { recording.currentRecording.collect { current -> mutable.update { it.copy(currentRecording = current) } } }
        // Throttle with a trailing read: a continuous sync drain must not starve Mine.
        viewModelScope.launch { changes.conflate().collect { loadTrips().join(); delay(800) } }
    }

    fun show(mode: FeedMode = state.value.mode) {
        mutable.update { it.copy(mode = mode) }
        if (mode == FeedMode.mine) loadTrips() else loadIfNeeded(mode)
    }

    fun loadTrips(): Job {
        mineJob?.cancel()
        mutable.update { it.copy(isLoadingMine = true) }
        return viewModelScope.launch {
            try {
                val fetched = withContext(worker) { repository.fetchAllTrips() to repository.fetchJourneys() }
                allTrips = fetched.first
                journeys = fetched.second
                applyFilters()
                mutable.update { it.copy(isLoadingMine = false, message = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(isLoadingMine = false, message = TripMessage.loadFailed) } }
        }.also { mineJob = it }
    }

    fun setFilters(filters: FeedFilters) { mutable.update { it.copy(filters = filters) }; applyFilters() }
    fun resetFilters() = setFilters(FeedFilters())
    fun resetSecondaryFilters() = setFilters(state.value.filters.copy(region = null))

    private fun applyFilters() {
        val filter = state.value.filters
        val start = filter.dateFrom?.atZone(zone)?.toLocalDate()?.atStartOfDay(zone)?.toInstant()
        val end = filter.dateFrom?.let { (filter.dateTo ?: it).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant() }
        filtered = allTrips.filter { trip ->
            (filter.region == null || trip.region?.let { it.contains(filter.region, true) || filter.region.contains(it, true) } == true) &&
                (start == null || trip.startDate >= start && trip.startDate < end!!)
        }
        page = 1
        val days = allTrips.groupBy { it.startDate.atZone(zone).toLocalDate() }.mapValues { (_, trips) -> trips.sumOf { it.distance } / 1000 }
        mutable.update { it.copy(totalTripCount = filtered.size, totalKm = filtered.sumOf { it.distance } / 1000,
            totalDuration = filtered.sumOf { it.duration }, kmByDay = days, maxKmDay = days.values.maxOrNull() ?: 1.0,
            uniqueRegions = allTrips.mapNotNull { trip -> trip.region }.distinct().sorted(),
            vehicleIdByTripId = allTrips.mapNotNull { trip -> trip.vehicleId?.let { trip.id to it } }.toMap(),
            hasAnyPrivateTrip = allTrips.any { trip -> trip.isPrivate }) }
        publishMine()
    }

    fun loadMoreMine(currentTripId: UUID) {
        val visible = state.value.trips
        if (!state.value.hasMoreMine || visible.takeLast(5).none { it.id == currentTripId }) return
        page++
        publishMine()
    }

    private fun publishMine() {
        val visible = filtered.take(page * 20)
        val filter = state.value.filters
        val range = HistoryFolding.dayRange(filter.dateFrom, filter.dateTo, zone)
        // A journey card summarizes its whole filtered window, regardless of the raw trip page.
        val rows = HistoryFolding.fold(filtered, journeys, range).mapNotNull { row -> when (row) {
            is HistoryRow.Trip -> MineRowState.TripRow(TripSummary.from(row.value))
            is HistoryRow.Journey -> MineRowState.JourneyRow(JourneyRowState(row.value1.id, row.value1.title, row.value1.startDate, row.value1.endDate), row.legs.map(TripSummary::from))
            is HistoryRow.Routine -> null
        } }
        mutable.update { it.copy(trips = visible.map(TripSummary::from), mineRows = rows, hasMoreMine = visible.size < filtered.size,
            sections = visible.groupBy { trip -> YearMonth.from(trip.startDate.atZone(zone)) }.toSortedMap(reverseOrder())
                .map { (month, trips) -> TripSection(month, trips.map(TripSummary::from)) }) }
    }

    private fun social(mode: FeedMode) = if (mode == FeedMode.following) state.value.following else state.value.all
    private fun updateSocial(mode: FeedMode, change: (SocialFeedState) -> SocialFeedState) {
        mutable.update { if (mode == FeedMode.following) it.copy(following = change(it.following)) else it.copy(all = change(it.all)) }
    }

    fun loadIfNeeded(mode: FeedMode = state.value.mode): Job? {
        if (mode == FeedMode.mine) return loadTrips()
        if (mode == FeedMode.following && !signedIn() || jobs[mode]?.isActive == true) return null
        val current = social(mode)
        if (current.cards.isNotEmpty() && current.lastLoadedAt?.let { java.time.Duration.between(it, clock()) < java.time.Duration.ofMinutes(15) } == true) return null
        return refresh(mode)
    }

    fun refresh(mode: FeedMode = state.value.mode): Job? {
        if (mode == FeedMode.mine) return loadTrips()
        if (mode == FeedMode.following && !signedIn()) return null
        jobs.remove(mode)?.cancel()
        val generation = (generations[mode] ?: 0) + 1
        generations[mode] = generation
        if (isOffline()) {
            updateSocial(mode) { it.copy(isLoading = false, isLoadingMore = false, error = if (it.cards.isEmpty()) TripMessage.loadFailed else it.error) }
            return null
        }
        updateSocial(mode) { it.copy(isLoading = true, isLoadingMore = false, nextCursor = null, hasMore = true) }
        return viewModelScope.launch {
            try { fetchPage(mode, null, generation, true) }
            finally { if (generations[mode] == generation) updateSocial(mode) { it.copy(isLoading = false) } }
        }.also { jobs[mode] = it }
    }

    fun loadMore(mode: FeedMode, currentId: UUID): Job? {
        val current = social(mode)
        if (!current.hasMore || current.isLoading || current.isLoadingMore || current.cards.lastOrNull()?.trip?.id != currentId) return null
        val generation = generations[mode] ?: 0
        updateSocial(mode) { it.copy(isLoadingMore = true) }
        return viewModelScope.launch {
            try { fetchPage(mode, current.nextCursor, generation, false) }
            finally { if (generations[mode] == generation) updateSocial(mode) { it.copy(isLoadingMore = false) } }
        }
    }

    private suspend fun fetchPage(mode: FeedMode, cursor: String?, generation: Int, replace: Boolean) {
        try {
            val (response, served) = withContext(worker) {
                val response = socialApi.feed(SocialFeedRequest(20, cursor, if (mode == FeedMode.following) "following" else null))
                val hidden = response.trips.mapNotNull { repository.fetchEntity(it.id)?.takeIf { row -> row.isPrivate }?.id }.toSet()
                response to response.trips.filterNot { it.id in hidden }.map(SocialCardState::from)
            }
            currentCoroutineContext().ensureActive()
            if (generations[mode] != generation) return
            for (trip in response.trips) { knownReactions[mode to trip.id] = trip.myReaction; socialItems[trip.id] = trip }
            updateSocial(mode) { old -> old.copy(cards = if (replace) served else (old.cards + served).distinctBy { it.trip.id },
                nextCursor = response.nextCursor, hasMore = response.nextCursor != null, error = null, lastLoadedAt = clock()) }
            retries[mode] = 0
        } catch (cancelled: CancellationException) { scheduleColdStartRetry(mode, generation, replace); throw cancelled }
        catch (error: Exception) {
            if (generations[mode] != generation) return
            updateSocial(mode) { it.copy(error = TripMessage.loadFailed) }
            val transient = error is ApiError.Network || error is ApiError.UnknownServer || error is ApiError.TooManyRequests || error is ApiError.InvalidHttpStatus && error.status >= 500
            if (transient) scheduleColdStartRetry(mode, generation, replace)
        }
    }

    private fun scheduleColdStartRetry(mode: FeedMode, generation: Int, replace: Boolean) {
        if (!replace || generations[mode] != generation || social(mode).cards.isNotEmpty() || isOffline() || (retries[mode] ?: 0) >= 2) return
        retries[mode] = (retries[mode] ?: 0) + 1
        viewModelScope.launch { delay(1500); if (generations[mode] == generation && jobs[mode]?.isActive != true && social(mode).cards.isEmpty()) refresh(mode) }
    }

    /** The server item a card was built from; what its trip screen opens with. */
    fun socialTrip(id: UUID): SocialFeedTrip? = socialItems[id]

    fun consumeMessage() { mutable.update { it.copy(message = null) } }

    fun networkRestored() { for (mode in listOf(FeedMode.all, FeedMode.following)) if (social(mode).cards.isEmpty() && social(mode).error != null) { retries[mode] = 0; loadIfNeeded(mode) } }
    fun removeOptimistically(id: UUID) { for (mode in listOf(FeedMode.all, FeedMode.following)) updateSocial(mode) { it.copy(cards = it.cards.filterNot { card -> card.trip.id == id }, lastLoadedAt = null) } }
    fun privacyChanged(id: UUID, isPrivate: Boolean) {
        if (isPrivate) removeOptimistically(id) else for (mode in listOf(FeedMode.all, FeedMode.following)) updateSocial(mode) { it.copy(lastLoadedAt = null) }
        loadTrips()
    }
    fun bumpCommentCount(id: UUID, delta: Int) { mapCards(id) { it.copy(commentCount = maxOf(0, it.commentCount + delta)) } }
    fun bumpPhotoCount(id: UUID, delta: Int) { mapCards(id) { card -> val count = maxOf(0, card.photoCount + delta); card.copy(photoCount = count, thumbnail = card.thumbnail.takeIf { count > 0 }) } }
    private fun mapCards(id: UUID, change: (SocialCardState) -> SocialCardState) {
        for (mode in listOf(FeedMode.all, FeedMode.following)) updateSocial(mode) { it.copy(cards = it.cards.map { card -> if (card.trip.id == id) change(card) else card }) }
    }
    fun toggleReaction(id: UUID, emoji: String, mode: FeedMode = state.value.mode): Job? {
        if (!signedIn()) return null
        val session = sessionGeneration
        val source = if (mode == FeedMode.following) FeedMode.following else FeedMode.all
        val cached = social(source).cards.firstOrNull { it.trip.id == id }
        val previous = if (cached != null) cached.myReaction else knownReactions[source to id]
        val next = if (previous == emoji) null else emoji
        knownReactions[source to id] = next
        updateSocial(source) { page -> page.copy(cards = page.cards.map { card ->
            if (card.trip.id != id) return@map card
            val breakdown = card.breakdown.associate { it.emoji to it.count }.toMutableMap()
            previous?.let { breakdown[it] = (breakdown[it] ?: 1) - 1 }
            next?.let { breakdown[it] = (breakdown[it] ?: 0) + 1 }
            card.copy(myReaction = next, reactionCount = maxOf(0, card.reactionCount + if (next == null) -1 else if (previous == null) 1 else 0),
                breakdown = breakdown.filterValues { it > 0 }.map { ReactionTally(it.key, it.value) }.sortedByDescending { it.count })
        }) }
        return viewModelScope.launch {
            try { withContext(worker) { socialApi.react(id, previous, emoji) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (session != sessionGeneration) return@launch
                knownReactions[source to id] = previous
                if (cached != null) updateSocial(source) { it.copy(cards = it.cards.map { card -> if (card.trip.id == id) card.copy(reactionCount = cached.reactionCount, myReaction = cached.myReaction, breakdown = cached.breakdown) else card }) }
                mutable.update { it.copy(message = TripMessage.actionFailed) }
            }
        }
    }
    fun clearSocial() {
        sessionGeneration++
        jobs.values.forEach(Job::cancel)
        for (mode in listOf(FeedMode.all, FeedMode.following)) generations[mode] = (generations[mode] ?: 0) + 1
        knownReactions.clear()
        socialItems.clear()
        mutable.update { it.copy(all = SocialFeedState(), following = SocialFeedState()) }
    }
}
