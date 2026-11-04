package com.onezee.triptrack.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.*
import com.onezee.triptrack.feature.feed.*
import com.onezee.triptrack.feature.drafts.DraftDecision
import com.onezee.triptrack.feature.drafts.DraftDecisionQueue
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: TripDetailView.swift (rebuildPhotoPins), TripPhotoPlacement.prepareAsync
fun interface TripPhotoPreparer {
    suspend fun prepare(trip: Trip): TripPhotoPlacement.Prepared
}

// iOS: TripDetailSections.swift (DetailChartPoint)
data class DetailChartPoint(val km: Double, val mean: Double, val low: Double, val high: Double, val date: Instant?)

// iOS: TripDetailView.swift (PhotoPin)
data class PhotoPinState(val id: UUID, val filename: String, val coordinate: GeoPoint,
    val elapsed: Double?, val metres: Double?)

// iOS: OwnTripPhotosModel.swift (Item / Source)
data class DetailPhotoState(val id: UUID, val timestamp: Instant, val filename: String?,
    val thumbnailUrl: String?, val originalUrl: String?, val isMissing: Boolean)

// iOS: TripDetailView.swift (reactionEntries)
data class ReactorState(val author: AuthorState, val emoji: String, val createdAt: Instant)

// iOS: TripDetailView.swift
data class TripDetailState(
    val sourceTrip: Trip? = null,
    val trip: TripSummary? = null, val isLoading: Boolean = true, val isOwn: Boolean = true, val noAccess: Boolean = false,
    val isRemoteBacked: Boolean = false, val loadFailed: Boolean = false, val geometryRevision: Long = 0,
    val coordinates: List<GeoPoint> = emptyList(), val speeds: List<Double> = emptyList(),
    val routeBreaks: List<Int> = emptyList(),
    val replay: TripReplayInput = TripReplayInput(emptyList(), emptyList(), emptyList()),
    val isPreviewRoute: Boolean = false, val photoPins: List<PhotoPinState> = emptyList(),
    val checkpoints: List<CheckpointState> = emptyList(), val moments: List<MomentState> = emptyList(),
    val segments: List<SegmentState> = emptyList(), val placeChips: Map<UUID, PlaceChip> = emptyMap(),
    val photos: List<DetailPhotoState> = emptyList(), val photosLoadFailed: Boolean = false,
    val speedSeries: List<DetailChartPoint> = emptyList(), val elevationSeries: List<DetailChartPoint> = emptyList(),
    val drivingTime: Double = 0.0, val stoppedTime: Double = 0.0, val elevationGain: Double = 0.0,
    val maxAltitude: Double = 0.0, val hasElevation: Boolean = false,
    val energyMode: TripEnergyMode = TripEnergyMode.auto, val energyEstimate: EnergyEstimate? = null,
    val canChooseEnergyMode: Boolean = false, val reactions: List<ReactorState> = emptyList(),
    val myReaction: String? = null, val reactionsLoadFailed: Boolean = false,
    val reactionCount: Int = 0, val reactionBreakdown: List<ReactionTally> = emptyList(), val isReacting: Boolean = false,
    val draftResolved: Boolean = false,
    val isWorking: Boolean = false, val publishRequested: Boolean = false, val confirmation: HouseConfirmation? = null,
    val exported: TripExport? = null, val deleted: Boolean = false, val message: TripMessage? = null,
) {
    val canShare: Boolean get() = trip != null && (isOwn || !trip.isPrivate)
    val showsMoments: Boolean get() = isOwn && trip != null
    val canAddSegment: Boolean get() = isOwn && checkpoints.size >= 2 && segments.size < TripSegment.maxPerTrip
}

// iOS: TripDetailView.swift. Preview metadata is present before any coroutine runs.
class TripDetailViewModel(
    private val tripId: UUID,
    private val repository: TripRepository,
    private val api: TripSocialApi,
    private val worker: CoroutineDispatcher,
    private val accountId: () -> UUID?,
    preview: Trip? = null,
    private val social: SocialFeedTrip? = null,
    private val places: PlaceStore? = null,
    private val vehicles: VehicleStore? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Instant = Instant::now,
    private val photoPreparer: TripPhotoPreparer = TripPhotoPreparer { trip ->
        TripPhotoPlacement.prepare(trip.photos, trip.checkpoints, trip.trackPoints, trip.startDate)
    },
    private val hydrateManualTrip: suspend (UUID) -> Trip? = { null },
    private val localPhotoExists: (String) -> Boolean = { true },
    changes: Flow<UUID> = emptyFlow(),
    private val draftDecisions: DraftDecisionQueue? = null,
    private val placeManager: com.onezee.triptrack.data.PlaceManager? = null,
    private val recording: () -> Boolean = { false },
    placeChanges: Flow<Unit> = emptyFlow(),
    private val nameCheckpoint: suspend (TripCheckpoint, UUID) -> Unit = { _, _ -> },
) : ViewModel() {
    private val opening = preview?.takeIf { it.id == tripId } ?: social?.takeIf { it.id == tripId }?.asTrip()
    private val mutable = MutableStateFlow(TripDetailState(trip = opening?.copy(trackPoints = emptyList())?.let(TripSummary::from),
        energyMode = opening?.energyMode ?: TripEnergyMode.auto, myReaction = social?.myReaction,
        reactionCount = social?.reactionCount ?: 0, reactionBreakdown = social?.reactionBreakdown.orEmpty().toList()))
    val state = mutable.asStateFlow()
    private var trip: Trip? = null
    private var revision = 0L
    private var loadJob: Job? = null
    private var loadGeneration = 0
    private var preparation: Job? = null
    private var privacyWatch: Job? = null
    private var confirmationAction: (suspend () -> Unit)? = null
    private var remotePhotos = emptyList<SocialTripPhoto>()
    private var activeReactions = 0

    init {
        viewModelScope.launch { changes.filter { it == tripId }.collect { load() } }
        viewModelScope.launch { placeChanges.collect { load() } }
    }

    fun load(): Job {
        loadJob?.cancel()
        val generation = ++loadGeneration
        mutable.update { it.copy(isLoading = true, loadFailed = false) }
        return viewModelScope.launch {
            try {
                val local = withContext(worker) { repository.fetchTripDetail(tripId) }
                if (local != null) {
                    replaceTrip(local, own = true, remote = false).join()
                    if (local.source == TripOrigin.manual) {
                        val hydrated = withContext(worker) { hydrateManualTrip(tripId) }
                        if (hydrated != null) replaceTrip(hydrated, true, false).join()
                    }
                } else if (social != null) {
                    replaceTrip(social.asTrip(), social.author.id == accountId(), true).join()
                    seedSocial(social)
                    refreshRemote()
                } else mutable.update { it.copy(isOwn = false, loadFailed = true) }
                if (trip?.isOnServer == true) { loadPhotos(); loadReactions() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(loadFailed = trip == null, message = TripMessage.loadFailed) } }
            finally { if (generation == loadGeneration) mutable.update { it.copy(isLoading = false) } }
        }.also { loadJob = it }
    }

    private suspend fun refreshRemote() {
        try {
            val response = withContext(worker) { api.trip(tripId) }
            val item = response.item.asTrip()
            val breaks = response.breaks.orEmpty()
            val points = response.track.orEmpty().map { point ->
                TrackPoint(latitude = point.lat, longitude = point.lon, speed = point.speed, timestamp = point.t,
                    recordingSegmentIndex = breaks.count { point.t >= it })
            }
            replaceTrip(item.copy(trackPoints = points, recordingBreaks = breaks), response.item.author.id == accountId(), true).join()
            seedSocial(response.item)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: com.onezee.triptrack.network.ApiError.TripNotFound) {
            mutable.update { it.copy(noAccess = true, isLoading = false) }
        }
        catch (_: Exception) { /* A refused refresh preserves the copy the reader opened. */ }
    }

    private fun seedSocial(item: SocialFeedTrip) {
        mutable.update { it.copy(myReaction = item.myReaction, drivingTime = (item.drivingTime ?: 0).toDouble(),
            stoppedTime = (item.stoppedTime ?: 0).toDouble(), elevationGain = item.elevation ?: 0.0,
            maxAltitude = item.maxAltitude ?: 0.0, hasElevation = (item.source ?: TripOrigin.recorded) == TripOrigin.recorded && item.maxAltitude != null && (item.elevation ?: 0.0) > 0.5,
            reactionCount = item.reactionCount, reactionBreakdown = item.reactionBreakdown.toList()) }
    }

    fun replaceTrip(updated: Trip, own: Boolean = state.value.isOwn, remote: Boolean = state.value.isRemoteBacked): Job {
        require(updated.id == tripId)
        preparation?.cancel()
        revision++
        val expected = revision
        trip = updated
        // Even a same-id replacement may contain a completely different route.
        Trip.invalidatePreviewCache(tripId)
        mutable.update { it.copy(trip = TripSummary.from(updated.copy(trackPoints = emptyList())), isOwn = own, isRemoteBacked = remote,
            energyMode = updated.energyMode, geometryRevision = expected, photoPins = emptyList(), moments = emptyList(),
            checkpoints = emptyList(), placeChips = emptyMap(), segments = emptyList(), energyEstimate = null) }
        return viewModelScope.launch {
            try {
                val prepared = withContext(worker) { prepare(updated, own, remote, expected) }
                if (revision == expected) mutable.value = prepared.copy(isLoading = state.value.isLoading,
                    isWorking = state.value.isWorking, confirmation = state.value.confirmation,
                    publishRequested = state.value.publishRequested, myReaction = state.value.myReaction,
                    reactions = state.value.reactions, reactionCount = state.value.reactionCount,
                    reactionBreakdown = state.value.reactionBreakdown, isReacting = state.value.isReacting,
                    exported = state.value.exported)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == expected) mutable.update { it.copy(message = TripMessage.loadFailed) } }
        }.also { preparation = it }
    }

    private suspend fun prepare(t: Trip, own: Boolean, remote: Boolean, expected: Long): TripDetailState {
        currentCoroutineContext().ensureActive()
        val points = t.trackPoints
        // iOS draws someone else's trip from its preview, one solid line. The server's track is thinned
        // to about a kilometre a step, so the 1 km gap rule would shred it into dashes: it is one line too.
        val previewRoute = remote || (points.size <= 1 && t.previewPolyline != null)
        val coords = if (points.size > 1) points.map { it.coordinate } else t.previewCoordinates
        val replayBreaks = if (points.size > 1) points.indices.filter { it > 0 && points[it].recordingSegmentIndex != points[it - 1].recordingSegmentIndex }
            else t.previewBreaks.orEmpty()
        val replay = if (points.size > 1) TripReplay.prepare(points) else TripReplay.prepare(coords, timestamps = emptyList(), breaks = replayBreaks)
        val prepared = if (own) photoPreparer.prepare(t) else TripPhotoPlacement.Prepared(emptyMap(), emptyList(), emptyMap())
        currentCoroutineContext().ensureActive()
        val photoById = t.photos.associateBy { it.id }
        val moments = if (own) TripMoments.build(t.checkpoints, prepared.links, prepared.placed.mapNotNull { item ->
            val photo = photoById[item.id] ?: return@mapNotNull null
            val fix = prepared.fixes[item.id] ?: return@mapNotNull null
            TripMoments.PlacedPhoto(photo, fix)
        }).map { moment -> when (moment) {
            is TripMoment.Checkpoint -> MomentState(moment.id, moment.value1.id, moment.number, moment.elapsedFromStart, moment.distanceFromStart, moment.photos.map(PhotoState::from))
            is TripMoment.Photos -> MomentState(moment.id, null, null, moment.elapsedFromStart, moment.distanceFromStart, moment.photos.map(PhotoState::from))
        } } else emptyList()
        val alive = if (own) places?.fetchPlaces()?.map { it.id }?.toSet().orEmpty() else emptySet()
        val placeIds = t.checkpoints.mapNotNull { it.placeId }.filter { it in alive }.distinct()
        val passes = places?.passes(placeIds).orEmpty()
        val chips = if (own) t.checkpoints.mapNotNull { cp -> cp.placeId?.takeIf { it in alive }?.let { placeId ->
            val stats = PlaceStats.build(passes[placeId].orEmpty(), clock())
            cp.id to PlaceChip(placeId, stats.passCount, (stats.directions.firstOrNull()?.median ?: stats.medianElapsed).takeIf { stats.passCount > 1 })
        } }.toMap() else emptyMap()
        val checkpoints = if (own) t.checkpoints.mapIndexed { index, cp -> CheckpointState(cp.id, cp.timestamp,
            if (cp.hasCoordinate) GeoPoint(cp.latitude, cp.longitude) else null, cp.name, index + 1,
            cp.distanceFromStart, cp.elapsedFromStart, prepared.links[cp.id].orEmpty().map(PhotoState::from), cp.placeId,
            cp.photoId, cp.photoIds.toList()) } else emptyList()
        val segments = if (own) t.segments.map { segment ->
            val resolved = TripSegmentMetrics.resolve(segment, t.checkpoints)
            val from = resolved?.from?.placeId?.takeIf { it in alive }
            val to = resolved?.to?.placeId?.takeIf { it in alive }
            SegmentState(segment.id, segment.fromCheckpointId, segment.toCheckpointId, segment.name, resolved?.elapsed, resolved?.metres,
                if (from != null && to != null) SegmentHistory.times(passes[from].orEmpty(), passes[to].orEmpty()) else emptyList())
        } else emptyList()
        val measured = t.measuredPoints
        val elevation = chartSeries(measured) { it.altitude }
        val flat = (elevation.maxOfOrNull { it.mean } ?: 0.0) - (elevation.minOfOrNull { it.mean } ?: 0.0) <= 0.5
        val split = t.movementSplit()
        val vehicle = t.vehicleId?.let { vehicles?.vehicle(it) }
        val estimate = energy(t, vehicle, t.energyMode)
        return TripDetailState(trip = TripSummary.from(t), isOwn = own, isRemoteBacked = remote, geometryRevision = expected,
            sourceTrip = t,
            coordinates = coords, speeds = if (points.size > 1) points.map { it.speed } else emptyList(), routeBreaks = replayBreaks, replay = replay,
            isPreviewRoute = previewRoute, photoPins = prepared.placed.map { pin -> val fix = prepared.fixes[pin.id]
                PhotoPinState(pin.id, pin.filename, pin.coordinate, fix?.elapsedFromStart, fix?.distanceFromStart) },
            checkpoints = checkpoints, moments = moments, segments = segments, placeChips = chips,
            photos = mergedPhotos(t.photos, remotePhotos), speedSeries = if (t.source == TripOrigin.recorded) chartSeries(points.filterNot { it.isInterpolated }) { maxOf(0.0, it.speed) } else emptyList(),
            elevationSeries = if (flat) emptyList() else elevation, hasElevation = !flat && t.source == TripOrigin.recorded,
            drivingTime = split.driving, stoppedTime = split.stopped,
            elevationGain = measured.zipWithNext().sumOf { (a, b) -> maxOf(0.0, b.altitude - a.altitude) },
            maxAltitude = measured.maxOfOrNull { it.altitude } ?: 0.0, energyMode = t.energyMode, energyEstimate = estimate,
            canChooseEnergyMode = own && vehicle?.powertrain == Powertrain.pluginHybrid)
    }

    private suspend fun energy(t: Trip, vehicle: Vehicle?, mode: TripEnergyMode): EnergyEstimate? {
        if (vehicle == null || t.distance <= 100) return null
        if (vehicle.powertrain != Powertrain.pluginHybrid) return EnergyEstimate.resolve(vehicle, t.distance, t.averageSpeed, mode)
        val start = t.startDate.atZone(zone).toLocalDate().atStartOfDay(zone)
        val sameDay = repository.fetchTrips(start.toInstant(), start.plusDays(1).toInstant().minusNanos(1))
            .filter { it.vehicleId == vehicle.id }.map { if (it.id == t.id) it.copy(energyMode = mode) else it }
        return EnergyEstimate.day(vehicle, sameDay)[t.id]
    }

    fun setEnergyMode(mode: TripEnergyMode): Job? {
        val t = trip ?: return null
        if (!state.value.canChooseEnergyMode || state.value.isWorking) return null
        mutable.update { it.copy(energyMode = mode) }
        return action {
            repository.setEnergyMode(mode, tripId)
            val estimate = energy(t, t.vehicleId?.let { vehicles?.vehicle(it) }, mode)
            withContext(Dispatchers.Main.immediate) { trip = t.copy(energyMode = mode); mutable.update { it.copy(energyEstimate = estimate) } }
        }
    }
    private suspend fun loadPhotos() {
        try {
            val remote = withContext(worker) { api.photos(tripId) }
            remotePhotos = remote
            val photos = withContext(worker) { mergedPhotos(trip?.photos.orEmpty(), remote) }
            mutable.update { it.copy(photos = photos, photosLoadFailed = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(photosLoadFailed = true) } }
    }
    private fun mergedPhotos(local: List<TripPhoto>, remote: List<SocialTripPhoto>): List<DetailPhotoState> {
        val remoteById = remote.associateBy { it.id }
        return (local.map { photo ->
            val hasFile = localPhotoExists(photo.filename)
            val twin = remoteById[photo.id]
            DetailPhotoState(photo.id, photo.timestamp, photo.filename.takeIf { hasFile }, twin?.thumbnailUrl,
                twin?.originalUrl, !hasFile && twin?.thumbnailUrl == null && twin?.originalUrl == null)
        } + remote.map { DetailPhotoState(it.id, it.timestamp, null, it.thumbnailUrl, it.originalUrl, false) }).distinctBy { it.id }
    }
    suspend fun loadReactions() {
        if (trip?.isOnServer != true) return
        try {
            val entries = withContext(worker) { api.reactions(tripId) }
            mutable.update { it.copy(reactions = entries.map { entry -> ReactorState(AuthorState.from(entry.user), entry.emoji, entry.createdAt) },
                myReaction = entries.firstOrNull { entry -> entry.user.id == accountId() }?.emoji, reactionsLoadFailed = false,
                reactionCount = if (entries.isEmpty()) it.reactionCount else entries.size,
                reactionBreakdown = if (entries.isEmpty()) it.reactionBreakdown else entries.groupingBy { entry -> entry.emoji }.eachCount()
                    .map { (emoji, count) -> ReactionTally(emoji, count) }.sortedWith(compareByDescending<ReactionTally> { tally -> tally.count }.thenBy { tally -> tally.emoji })) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.update { it.copy(reactionsLoadFailed = true) } }
    }

    fun toggleReaction(emoji: String): Job? {
        val current = state.value
        if (accountId() == null || trip?.isOnServer != true) return null
        activeReactions++
        val next = emoji.takeUnless { it == current.myReaction }
        val breakdown = current.reactionBreakdown.associate { it.emoji to it.count }.toMutableMap()
        current.myReaction?.let { breakdown[it] = (breakdown[it] ?: 1) - 1 }
        next?.let { breakdown[it] = (breakdown[it] ?: 0) + 1 }
        mutable.update { it.copy(isReacting = true, myReaction = next,
            reactionCount = maxOf(0, current.reactionCount + if (next == null) -1 else if (current.myReaction == null) 1 else 0),
            reactionBreakdown = breakdown.filterValues { it > 0 }.map { ReactionTally(it.key, it.value) }.sortedByDescending { it.count }) }
        return viewModelScope.launch {
            try { withContext(worker) { api.react(tripId, current.myReaction, emoji) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(myReaction = current.myReaction, reactionCount = current.reactionCount,
                reactionBreakdown = current.reactionBreakdown, message = TripMessage.actionFailed) } }
            finally { activeReactions--; mutable.update { it.copy(isReacting = activeReactions > 0) } }
        }
    }

    fun confirmDraft(): Job? {
        if (!state.value.isOwn || trip?.confirmation != TripConfirmation.draft) return null
        return action {
            val queue = requireNotNull(draftDecisions)
            queue.enqueue(tripId, DraftDecision.confirm)
            queue.decisionsQueued()
            withContext(Dispatchers.Main.immediate) { mutable.update { it.copy(draftResolved = true) } }
        }
    }

    fun requestPublish() {
        if (!state.value.isOwn) return
        if (accountId() == null) mutable.update { it.copy(message = TripMessage.signInToPublish) }
        else mutable.update { it.copy(publishRequested = true) }
    }
    fun cancelPublish() { mutable.update { it.copy(publishRequested = false) } }
    fun publish(): Job? {
        if (!state.value.publishRequested || !state.value.isOwn) return null
        cancelPublish()
        return privacy(false)
    }
    fun requestHide(): Job? {
        val t = trip ?: return null
        if (!state.value.isOwn) return null
        if (accountId() == null && t.isOnServer) { mutable.update { it.copy(message = TripMessage.signInToPublish) }; return null }
        return viewModelScope.launch {
            val journey = withContext(worker) { repository.journeyContaining(tripId) }?.takeIf { !it.isPrivate }
            confirmationAction = { applyPrivacyChange(true) }
            mutable.update { it.copy(confirmation = HouseConfirmation(titleKey = "tripDetailMakeTripPrivate", messageKey = "tripDetailThisTripWill",
                destructiveKey = "makePrivateAction", itemIds = listOf(tripId), journeyTitle = journey?.title)) }
        }
    }
    fun requestDelete() {
        if (!state.value.isOwn || trip == null) return
        val draft = trip?.confirmation == TripConfirmation.draft
        confirmationAction = {
            if (draft) {
                val queue = requireNotNull(draftDecisions)
                queue.enqueue(tripId, DraftDecision.discard)
                queue.decisionsQueued()
            } else {
                repository.deleteTrip(tripId)
                placeManager?.forget(tripId)
            }
        }
        mutable.update { it.copy(confirmation = HouseConfirmation(titleKey = "deleteTrip", messageKey = null,
            destructiveKey = "delete", itemIds = listOf(tripId))) }
    }
    // The house dialog closes itself before it runs the chosen answer (iOS AppConfirmDialog does the
    // same), so an answer arrives right after this dismissal: the question is kept until another one.
    private var dismissed: HouseConfirmation? = null
    fun cancelConfirmation() { dismissed = state.value.confirmation ?: dismissed; mutable.update { it.copy(confirmation = null) } }
    fun acceptConfirmation(id: UUID): Job? {
        val confirmation = (state.value.confirmation ?: dismissed)?.takeIf { it.id == id } ?: return null
        val work = confirmationAction ?: return null
        val deleting = confirmation.destructiveKey == "delete"
        if (!deleting && accountId() == null && trip?.isOnServer == true) {
            mutable.update { it.copy(message = TripMessage.signInToPublish) }; return null
        }
        dismissed = null
        confirmationAction = null
        mutable.update { it.copy(confirmation = null) }
        return action {
            work()
            withContext(Dispatchers.Main.immediate) {
                if (deleting) { loadJob?.cancel(); privacyWatch?.cancel(); preparation?.cancel(); revision++; mutable.update { it.copy(deleted = true, isLoading = false) } }
            }
        }
    }
    private fun privacy(isPrivate: Boolean): Job? {
        if (accountId() == null && (!isPrivate || trip?.isOnServer == true)) { mutable.update { it.copy(message = TripMessage.signInToPublish) }; return null }
        return action { applyPrivacyChange(isPrivate) }
    }
    private suspend fun applyPrivacyChange(isPrivate: Boolean) {
        repository.updatePrivacy(tripId, isPrivate)
        val local = repository.fetchTripDetail(tripId)
        withContext(Dispatchers.Main.immediate) {
            val updated = local ?: trip?.copy(isPrivate = isPrivate) ?: return@withContext
            replaceTrip(updated).join()
            loadReactions()
            watchPrivacy(!isPrivate)
        }
    }
    private fun watchPrivacy(expectedOnServer: Boolean) {
        privacyWatch?.cancel()
        privacyWatch = viewModelScope.launch {
            repeat(40) {
                delay(500)
                val fresh = withContext(worker) { repository.fetchTripDetail(tripId) }
                if (fresh?.isOnServer == expectedOnServer) { replaceTrip(fresh).join(); loadReactions(); return@launch }
            }
        }
    }
    fun export(format: TripExportFormat): Job? {
        val t = trip ?: return null
        if (t.trackPoints.isEmpty()) { mutable.update { it.copy(message = TripMessage.exportFailed) }; return null }
        return action {
            val content = when (format) { TripExportFormat.gpx -> GPXExporter.gpx(t, t.trackPoints); TripExportFormat.csv -> CSVExporter.csv(t, t.trackPoints) }
            val name = GPXExporter.displayName(t).replace("/", "-").replace(":", "-").trim().ifEmpty { "TripTrack" }
            withContext(Dispatchers.Main.immediate) { mutable.update { it.copy(exported = TripExport("$name.${format.extension}", format, content)) } }
        }
    }
    /** The screen showed the message as a toast; the next failure of the same kind shows again. */
    fun consumeMessage() { mutable.update { it.copy(message = null) } }
    fun consumeExport() { mutable.update { it.copy(exported = null) } }
    fun addSegment(from: UUID, to: UUID): Job? {
        if (!state.value.canAddSegment) return null
        return edit { repository.addSegment(tripId, from, to) }
    }
    fun addCheckpoint(fix: TripRouteLocator.Fix): Job? = edit {
        val checkpoint = TripCheckpoint(timestamp = fix.timestamp, latitude = fix.coordinate.latitude,
            longitude = fix.coordinate.longitude, distanceFromStart = fix.distanceFromStart, elapsedFromStart = fix.elapsedFromStart)
        if (repository.addCheckpoint(checkpoint, tripId) != null) {
            nameCheckpoint(checkpoint, tripId)
            placeManager?.registerCheckpoint(checkpoint, tripId, recording = recording())
        }
    }
    /** iOS applyEdits for the description alone: the text the owner wrote, trimmed; one's own trip only. */
    fun updateDescription(text: String): Job? = edit { repository.updateNotes(tripId, text.trim()) }
    fun updateHiddenParts(parts: TripHiddenParts?): Job? = edit { repository.updateHiddenParts(tripId, parts) }
    fun updateTripDetails(title: String, description: String, vehicleId: UUID?, isPrivate: Boolean, isTransfer: Boolean): Job? {
        val current = trip ?: return null
        if (!state.value.isOwn) return null
        if (current.isPrivate != isPrivate && accountId() == null && (!isPrivate || current.isOnServer)) {
            mutable.update { it.copy(message = TripMessage.signInToPublish) }
            return null
        }
        return action {
            val cleanTitle = title.trim()
            val storedTitle = if (current.titleIsCustom) current.title.orEmpty() else ""
            if (cleanTitle != storedTitle) repository.updateTitle(tripId, cleanTitle)
            if (description.trim() != current.tripDescription.orEmpty()) repository.updateNotes(tripId, description.trim())
            if (isTransfer != current.isTransfer) repository.updateTransfer(tripId, isTransfer)
            if (!isTransfer && current.vehicleId != vehicleId) repository.updateVehicle(tripId, vehicleId)
            withContext(Dispatchers.Main.immediate) { load() }.join()
            if (current.isPrivate != isPrivate) withContext(Dispatchers.Main.immediate) {
                if (isPrivate) requestHide() else requestPublish()
            }
        }
    }
    fun renameSegment(id: UUID, name: String?): Job? = edit { repository.updateSegment(id, name) }
    fun deleteSegment(id: UUID): Job? = edit { repository.deleteSegment(id) }
    fun updateCheckpoint(id: UUID, name: String?, coverId: UUID?, photoIds: List<UUID>): Job? = edit { repository.updateCheckpoint(id, name, coverId, photoIds) }
    fun deleteCheckpoint(id: UUID): Job? = edit { repository.deleteCheckpoint(id) }
    private fun edit(work: suspend () -> Any?): Job? {
        if (!state.value.isOwn) return null
        return action { work(); withContext(Dispatchers.Main.immediate) { load() } }
    }
    private fun action(work: suspend () -> Unit): Job? {
        if (state.value.isWorking) return null
        mutable.update { it.copy(isWorking = true, message = null) }
        return viewModelScope.launch {
            try { withContext(worker) { work() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = TripMessage.actionFailed) } }
            finally { mutable.update { it.copy(isWorking = false) } }
        }
    }
}

// iOS: TripDetailView.swift (chartSeries), TripDetailSections.swift (ChartSeriesBuilder.buckets)
internal fun chartSeries(points: List<TrackPoint>, value: (TrackPoint) -> Double): List<DetailChartPoint> {
    if (points.size < 2) return emptyList()
    val km = DoubleArray(points.size)
    for (i in 1 until points.size) km[i] = km[i - 1] + GeometryUtils.haversineDistance(points[i - 1].coordinate, points[i].coordinate) / 1000
    val total = km.last()
    if (total <= 0.05) return emptyList()
    val n = minOf(200, points.size)
    val width = total / n
    val buckets = points.indices.groupBy { minOf(n - 1, maxOf(0, (km[it] / width).toInt())) }
    return (0 until n).mapNotNull { i -> buckets[i]?.let { indices ->
        val values = indices.map { value(points[it]) }
        DetailChartPoint((i + 0.5) * width, values.average(), values.min(), values.max(), points[indices.first()].timestamp)
    } }
}
