package com.onezee.triptrack.feature.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.feed.*
import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: Journey.swift
data class JourneyState(val id: UUID, val title: String?, val startDate: Instant, val endDate: Instant?,
    val excludedIds: List<UUID>, val coverPhotoId: UUID?, val isPrivate: Boolean, val isOnServer: Boolean) {
    companion object { fun from(j: Journey) = JourneyState(j.id, j.title, j.startDate, j.endDate, j.excludedTripIds.toList(), j.coverPhotoId, j.isPrivate, j.serverCreatedAt != null) }
}

// iOS: JourneyComposerSheet.swift
data class JourneyComposerState(val candidates: List<TripSummary>, val selected: Set<UUID>, val title: String = "",
    val groups: Map<LocalDate, List<TripSummary>> = emptyMap(), val selectedMetres: Double = 0.0,
    val isLoading: Boolean = false, val isSaving: Boolean = false, val created: JourneyState? = null,
    val message: TripMessage? = null) { val canCreate: Boolean get() = selected.isNotEmpty() && !isSaving && !isLoading }

// iOS: JourneyComposerSheet.swift
class JourneyComposerViewModel(
    private val anchor: Trip,
    private val manager: JourneyManager,
    private val worker: CoroutineDispatcher,
    preselected: List<Trip>? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private var candidates = preselected?.sortedBy { it.startDate } ?: emptyList()
    private val mutable = MutableStateFlow(JourneyComposerState(candidates.map(TripSummary::from), candidates.map { it.id }.toSet()))
    val state = mutable.asStateFlow()
    init {
        publishCandidates(state.value.selected)
        if (preselected == null) {
            mutable.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                try {
                    val (window, selected) = withContext(worker) {
                        val window = (manager.neighbours(anchor) + anchor).distinctBy { it.id }.sortedBy { it.startDate }
                        window to JourneySuggester.chainAround(anchor, window).map { it.id }.toSet()
                    }
                    candidates = window
                    publishCandidates(selected)
                    mutable.update { it.copy(isLoading = false) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { it.copy(isLoading = false, message = TripMessage.loadFailed) } }
            }
        }
    }
    fun setTitle(title: String) { mutable.update { it.copy(title = titlePrefix(title), message = null) } }
    fun toggle(id: UUID) {
        if (candidates.none { it.id == id }) return
        publishCandidates(if (id in state.value.selected) state.value.selected - id else state.value.selected + id)
    }
    private fun publishCandidates(selected: Set<UUID>) {
        val summaries = candidates.map(TripSummary::from)
        mutable.update { it.copy(candidates = summaries, selected = selected, selectedMetres = candidates.filter { trip -> trip.id in selected }.sumOf { trip -> trip.distance },
            groups = summaries.groupBy { trip -> trip.startDate.atZone(zone).toLocalDate() }, message = null) }
    }
    fun create(): Job? {
        if (!state.value.canCreate) return null
        val selected = candidates.filter { it.id in state.value.selected }
        val title = state.value.title
        mutable.update { it.copy(isSaving = true) }
        return viewModelScope.launch {
            try { val created = withContext(worker) { manager.create(selected, title) }; mutable.update { it.copy(created = JourneyState.from(created)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = if (error is JourneyError.Overlaps) TripMessage.journeyOverlaps else TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isSaving = false) } }
        }
    }
}

// iOS: JourneyEditSheet.swift
data class JourneyEditState(val title: String, val start: Instant, val end: Instant, val coverPhotoId: UUID?,
    val startBounds: ClosedRange<Instant>, val endBounds: ClosedRange<Instant>, val windowTripCount: Int = 0,
    val removedLegs: List<TripSummary> = emptyList(), val returningIds: Set<UUID> = emptySet(),
    val canSave: Boolean = true, val isLoading: Boolean = true, val isSaving: Boolean = false,
    val saved: Boolean = false, val confirmation: HouseConfirmation? = null, val message: TripMessage? = null)

// iOS: JourneyEditSheet.swift. JourneyWindow owns all date rules and their unchanged iOS fixtures.
class JourneyEditViewModel(
    private val journey: Journey,
    private val repository: TripRepository,
    private val manager: JourneyManager,
    private val worker: CoroutineDispatcher,
    private val now: Instant,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val opened = JourneyWindow.clampedWindow(journey.startDate, journey.endDate, now, zone)
    private val mutable = MutableStateFlow(JourneyEditState(titlePrefix(journey.title.orEmpty()), opened.start, opened.end,
        journey.coverPhotoId, JourneyWindow.startBounds(opened.start, opened.end, now), JourneyWindow.endBounds(opened.start, opened.end, now)))
    val state = mutable.asStateFlow()
    private var recountJob: Job? = null
    init { recount() }
    fun setTitle(title: String) { mutable.update { it.copy(title = titlePrefix(title)) } }
    fun toggleCover(id: UUID) { mutable.update { state -> state.copy(coverPhotoId = id.takeUnless { id == state.coverPhotoId }) } }
    fun setStart(date: Instant) {
        val fixed = JourneyWindow.notFuture(date, now, zone)
        setDates(fixed, maxOf(fixed, state.value.end))
    }
    fun setEnd(date: Instant) {
        val fixed = JourneyWindow.notFuture(date, now, zone)
        setDates(minOf(fixed, state.value.start), fixed)
    }
    private fun setDates(start: Instant, end: Instant) {
        mutable.update { it.copy(start = start, end = end, startBounds = JourneyWindow.startBounds(start, end, now),
            endBounds = JourneyWindow.endBounds(start, end, now), message = null) }
        recount()
    }
    fun toggleReturning(id: UUID) {
        if (state.value.removedLegs.none { it.id == id }) return
        mutable.update { it.copy(returningIds = if (id in it.returningIds) it.returningIds - id else it.returningIds + id) }
        recount()
    }
    fun plannedWindow(): Journey {
        val value = state.value
        val start = minOf(value.start, value.end)
        val end = maxOf(value.start, value.end)
        fun sameDay(a: Instant, b: Instant) = a.atZone(zone).toLocalDate() == b.atZone(zone).toLocalDate()
        return journey.copy(startDate = if (sameDay(start, journey.startDate)) journey.startDate else start.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(),
            endDate = if (journey.endDate?.let { sameDay(end, it) } == true) journey.endDate else JourneyWindow.endOfDay(end, zone),
            excludedTripIds = journey.excludedTripIds.filterNot { it in value.returningIds })
    }
    private fun recount() {
        recountJob?.cancel()
        mutable.update { it.copy(isLoading = true, canSave = false) }
        val planned = plannedWindow()
        recountJob = viewModelScope.launch {
            try {
                val (count, removed) = withContext(worker) {
                    val count = repository.trips(planned).size
                    val shelf = planned.copy(excludedTripIds = emptyList())
                    count to repository.trips(shelf).filter { it.id in journey.excludedTripIds }.map(TripSummary::from)
                }
                mutable.update { it.copy(windowTripCount = count, removedLegs = removed, isLoading = false,
                    canSave = !JourneyWindow.datesMoved(it.start, it.end, opened, zone) || count > 0) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(isLoading = false, message = TripMessage.loadFailed) } }
        }
    }
    fun save(): Job? {
        if (!state.value.canSave || state.value.isSaving) return null
        val planned = plannedWindow().copy(title = state.value.title.trim().takeIf { it.isNotEmpty() }, coverPhotoId = state.value.coverPhotoId)
        mutable.update { it.copy(isSaving = true) }
        return viewModelScope.launch {
            try { withContext(worker) { manager.update(planned) }; mutable.update { it.copy(saved = true) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = if (error is JourneyError.Overlaps) TripMessage.journeyOverlaps else TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isSaving = false) } }
        }
    }
    fun askDelete() { mutable.update { it.copy(confirmation = HouseConfirmation(titleKey = "journeyDelete", messageKey = "journeyDeleteHint", destructiveKey = "delete", itemIds = listOf(journey.id))) } }
    fun cancelDelete() { mutable.update { it.copy(confirmation = null) } }
    fun acceptDelete(id: UUID): Job? {
        if (state.value.confirmation?.id != id || state.value.isSaving) return null
        mutable.update { it.copy(confirmation = null, isSaving = true) }
        return viewModelScope.launch {
            try { withContext(worker) { manager.delete(journey.id) }; mutable.update { it.copy(saved = true) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isSaving = false) } }
        }
    }
    companion object { const val titleLimit = 60 }
}

// iOS: JourneyEditSheet.swift (String.prefix counts Character, not UTF-16 code units).
private fun titlePrefix(title: String): String = Regex("\\X").findAll(title).take(JourneyEditViewModel.titleLimit).joinToString("") { it.value }

// iOS: JourneyAggregate.swift (Day / Item)
data class JourneyDayState(val number: Int, val date: Instant, val items: List<JourneyItemState>)
// iOS: JourneyAggregate.swift (leg and local)
data class JourneyItemState(val trips: List<TripSummary>, val localAnchor: GeoPoint?, val lastDayNumber: Int?)
// iOS: JourneyPublishSheet.swift
data class JourneyPublishState(val privateLegs: List<TripSummary>, val alreadyPublic: Int)
// iOS: JourneyDetailView.swift
data class JourneyDetailState(val journey: JourneyState? = null, val isSocial: Boolean,
    val author: AuthorState? = null, val trips: List<TripSummary> = emptyList(), val days: List<JourneyDayState> = emptyList(),
    val coordinates: List<GeoPoint> = emptyList(), val cover: PhotoState? = null, val totalMetres: Double = 0.0,
    val drivingSeconds: Double = 0.0, val calendarDays: Int = 0, val legCount: Int = 0,
    val loaded: Boolean = false, val isWorking: Boolean = false, val deleted: Boolean = false,
    val publish: JourneyPublishState? = null, val confirmation: HouseConfirmation? = null, val message: TripMessage? = null,
    val checkpoints: List<CheckpointState> = emptyList(), val firstStart: GeoPoint? = null, val farthestEnd: GeoPoint? = null,
    val startName: String? = null, val farthestName: String? = null, val localNames: Map<UUID, String> = emptyMap(),
    val allowsPlayback: Boolean = false)

// iOS: JourneyDetailView.swift (ownJourneyId / social). Foreign mode never writes a local row.
class JourneyDetailViewModel(
    private val journeyId: UUID,
    private val repository: TripRepository,
    private val manager: JourneyManager,
    private val worker: CoroutineDispatcher,
    private val signedIn: () -> Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val social: SocialJourneyResponse? = null,
    changes: Flow<Unit> = emptyFlow(),
) : ViewModel() {
    private val mutable = MutableStateFlow(JourneyDetailState(isSocial = social != null))
    val state = mutable.asStateFlow()
    private var reloadJob: Job? = null
    private var confirmationAction: (suspend () -> Unit)? = null
    init { reload(); if (social == null) viewModelScope.launch { changes.collect { reload() } } }
    fun reload(): Job {
        reloadJob?.cancel()
        return viewModelScope.launch {
            try {
                val built = withContext(worker) {
                    val journey = if (social != null) Journey(id = social.journey.id, title = social.journey.title,
                        startDate = social.journey.startDate, endDate = social.journey.endDate, isPrivate = social.journey.isPrivate)
                        else repository.fetchJourney(journeyId) ?: return@withContext null
                    val trips = social?.legs?.map { it.asTrip() } ?: repository.trips(journey)
                    val aggregate = JourneyAggregate.build(trips, zone)
                    JourneyDetailState(journey = JourneyState.from(journey), isSocial = social != null,
                        author = social?.journey?.author?.let(AuthorState::from), trips = trips.map(TripSummary::from),
                        coordinates = trips.flatMap { it.previewCoordinates }, cover = if (social != null) null else trips.flatMap { it.photos }.firstOrNull { it.id == journey.coverPhotoId }?.let(PhotoState::from),
                        days = aggregate.days.map { day -> JourneyDayState(day.number, day.date, day.items.map { item -> when (item) {
                            is JourneyAggregate.Item.Leg -> JourneyItemState(listOf(TripSummary.from(item.value)), null, null)
                            is JourneyAggregate.Item.Local -> JourneyItemState(item.value1.map(TripSummary::from), item.anchor, item.lastDayNumber)
                        } }) }, totalMetres = aggregate.totalMetres, drivingSeconds = aggregate.drivingSeconds,
                        calendarDays = aggregate.calendarDays, legCount = aggregate.legCount, loaded = true,
                        firstStart = aggregate.firstStart, farthestEnd = aggregate.farthestEnd,
                        checkpoints = trips.flatMap { trip -> trip.checkpoints.mapIndexed { index, cp ->
                            CheckpointState(cp.id, cp.timestamp, if (cp.hasCoordinate) GeoPoint(cp.latitude, cp.longitude) else null,
                                cp.name, index + 1, cp.distanceFromStart, cp.elapsedFromStart, emptyList(), cp.placeId)
                        } })
                }
                mutable.value = built ?: state.value.copy(deleted = true, loaded = true)
                if (built != null) loadLocalityNames(built)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loaded = true, message = TripMessage.loadFailed) } }
        }.also { reloadJob = it }
    }
    private suspend fun loadLocalityNames(snapshot: JourneyDetailState) {
        val anchors = snapshot.days.flatMap { it.items }.mapNotNull { item ->
            val anchor = item.localAnchor ?: return@mapNotNull null
            item.trips.firstOrNull()?.id?.let { it to anchor }
        }.toMap()
        fun key(coordinate: GeoPoint) = GeohashEncoder.encode(coordinate.latitude, coordinate.longitude, 5)
        val coordinates = listOfNotNull(snapshot.firstStart, snapshot.farthestEnd) + anchors.values
        if (coordinates.isEmpty()) return
        try {
            val names = withContext(worker) { repository.cachedLocalities(coordinates.map(::key).toSet()) }
            mutable.update { it.copy(startName = snapshot.firstStart?.let { names[key(it)] },
                farthestName = snapshot.farthestEnd?.let { names[key(it)] }, localNames = anchors.mapNotNull { (id, anchor) -> names[key(anchor)]?.let { id to it } }.toMap()) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Cached names are optional; the route and legs are already visible. */ }
    }
    fun requestPublish(): Job? {
        if (social != null) return null
        if (!signedIn()) { mutable.update { it.copy(message = TripMessage.signInToPublish) }; return null }
        return action {
            val j = repository.fetchJourney(journeyId) ?: throw JourneyError.NotFound
            val legs = repository.trips(j)
            val presentation = JourneyPublishState(legs.filter { it.isPrivate }.map(TripSummary::from), legs.count { !it.isPrivate })
            withContext(Dispatchers.Main.immediate) { mutable.update { it.copy(publish = presentation) } }
        }
    }
    fun cancelPublish() { mutable.update { it.copy(publish = null) } }
    fun publish(): Job? {
        if (social != null || state.value.publish == null) return null
        mutable.update { it.copy(publish = null) }
        return action(reloadAfter = true) { manager.publish(journeyId) }
    }
    fun requestHide() {
        val j = state.value.journey ?: return
        if (social != null) return
        if (!signedIn() && j.isOnServer) { mutable.update { it.copy(message = TripMessage.signInToPublish) }; return }
        confirm("journeyHideTitle", "journeyHideMessage", "journeyHide") { manager.hide(journeyId) }
    }
    fun requestDelete() { if (social == null) confirm("journeyDelete", "journeyDeleteHint", "delete") { manager.delete(journeyId) } }
    fun requestRemoveLeg(id: UUID) {
        if (social != null || state.value.trips.none { it.id == id }) return
        confirm("journeyRemoveLeg", "journeyRemoveLegHint", "journeyRemoveLeg") {
            val j = repository.fetchJourney(journeyId) ?: throw JourneyError.NotFound
            manager.update(j.copy(excludedTripIds = (j.excludedTripIds + id).distinct()))
        }
    }
    private fun confirm(title: String, message: String, destructive: String, action: suspend () -> Unit) {
        confirmationAction = action
        mutable.update { it.copy(confirmation = HouseConfirmation(titleKey = title, messageKey = message, destructiveKey = destructive, itemIds = listOf(journeyId))) }
    }
    fun cancelConfirmation() { confirmationAction = null; mutable.update { it.copy(confirmation = null) } }
    fun acceptConfirmation(id: UUID): Job? {
        if (state.value.confirmation?.id != id) return null
        val confirmedAction = confirmationAction ?: return null
        cancelConfirmation()
        return action(reloadAfter = true, work = confirmedAction)
    }
    private fun action(reloadAfter: Boolean = false, work: suspend () -> Unit): Job? {
        if (state.value.isWorking) return null
        mutable.update { it.copy(isWorking = true, message = null) }
        return viewModelScope.launch {
            try { withContext(worker) { work() }; if (reloadAfter) reload().join() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = if (error is JourneyError.SignInRequired) TripMessage.signInToPublish else TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isWorking = false) } }
        }
    }
}
