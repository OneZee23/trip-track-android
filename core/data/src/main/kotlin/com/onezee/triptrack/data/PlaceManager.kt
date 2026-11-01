// iOS: PlaceManager.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.logic.PlaceMatcher
import com.onezee.triptrack.model.PlaceStats
import com.onezee.triptrack.logic.build
import com.onezee.triptrack.logic.cell
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.logic.hasCoordinate
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripConfirmation
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Checkpoints create places; completed trips leave passes. Room publishes the committed changes
 * through its invalidation tracker, like the other Android stores. Reads and writes stay on IO;
 * only the value types cross to Default for geometry, including a single trip with many places.
 */
class PlaceManager(
    private val repository: TripRepository,
    private val store: PlaceStore,
    private val history: PendingPlaceHistory,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val geometry: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutablePlaces = MutableStateFlow<List<Place>>(emptyList())
    val places = mutablePlaces.asStateFlow()
    @Volatile private var backfillTask: Job? = null
    private val reconciling = AtomicBoolean(false)
    private val historyEdits = Mutex()

    suspend fun reload() = withContext(io) { mutablePlaces.value = store.fetchPlaces() }
    suspend fun passes(placeId: UUID) = withContext(io) { store.passes(placeId) }
    suspend fun passes(placeIds: List<UUID>) = withContext(io) { store.passes(placeIds) }
    suspend fun stats(placeId: UUID) = withContext(io) { PlaceStats.build(store.passes(placeId)) }
    suspend fun passCount(placeId: UUID) = withContext(io) { store.passCount(placeId) }
    suspend fun settle() { backfillTask?.join() }

    suspend fun registerCheckpoint(checkpoint: TripCheckpoint, tripId: UUID, recording: Boolean = false) = withContext(io) {
        // A draft has no rollback in the places layer; it enters here only after «Mine».
        if (repository.fetchEntity(tripId)?.confirmation == TripConfirmation.draft.rawValue) return@withContext
        if (!checkpoint.hasCoordinate) return@withContext
        val cell = Place.cell(checkpoint.latitude, checkpoint.longitude)
        val (place, isNew) = store.upsertPlace(cell, checkpoint.coordinate, checkpoint.name)
        repository.setPlaceId(checkpoint.id, place.id)
        store.recomputeCentroid(place.id, repository.checkpointCoordinates(place.id))
        reload()
        // A warm geocoder cache can insert the row first: no passes also means new.
        if (isNew || store.passCount(place.id) == 0) {
            if (recording) historyEdits.withLock {
                if (place.id !in history.ids) history.ids = history.ids + place.id
            } else backfillTask = scope.launch(io) { matchAllTrips(place) }
        } else if (repository.tripPreviews(false).any { it.id == tripId }) {
            backfillTask = scope.launch(io) { process(tripId) }
        }
    }

    suspend fun createPlace(cell: String, coordinate: GeoPoint, name: String?): Place = withContext(io) {
        val place = store.upsertPlace(cell, coordinate, name).place
        reload()
        backfillTask = scope.launch(io) { matchAllTrips(place) }
        place
    }

    suspend fun adoptName(name: String, checkpointId: UUID, tripId: UUID) = withContext(io) {
        if (repository.fetchEntity(tripId)?.confirmation == TripConfirmation.draft.rawValue) return@withContext
        val checkpoint = repository.fetchTripDetail(tripId)?.checkpoints?.firstOrNull { it.id == checkpointId }
            ?: return@withContext
        val placeId = checkpoint.placeId
        if (placeId != null) {
            // This may be a tombstone. Naming it must never recreate it.
            store.adoptName(name, placeId)
        } else {
            if (!checkpoint.hasCoordinate) return@withContext
            store.upsertPlace(Place.cell(checkpoint.latitude, checkpoint.longitude), checkpoint.coordinate, name)
        }
        reload()
    }

    suspend fun process(tripId: UUID) = withContext(io) {
        val ref = repository.tripPreviews(false).firstOrNull { it.id == tripId }
        if (ref != null) {
            process(ref, store.fetchPlaces())
            repository.markPlacesMatched(listOf(tripId))
        }
        drainPendingHistory()
    }

    private suspend fun process(ref: TripPreviewRef, places: List<Place>) {
        val candidates = withContext(geometry) {
            val cells = PlaceMatcher.cells(ref.previewPolyline?.let { Trip.decodePolyline(it) }.orEmpty())
            places.filter { PlaceMatcher.isCandidate(it, cells) }
        }
        if (candidates.isEmpty()) return
        val trip = repository.fetchTripDetail(ref.id)?.let { it.withRecordingBreaks(it.recordingBreaks) } ?: return
        if (trip.trackPoints.size < 2) return
        val computed = withContext(geometry) {
            candidates.map { it to PlaceMatcher.passes(it, trip.id, trip.trackPoints, trip.startDate) }
        }
        val existing = store.passesOfTrip(ref.id)
        for ((place, passes) in computed) {
            if (passes.isNotEmpty() || existing.any { it.placeId == place.id }) {
                store.replacePasses(place.id, trip.id, passes)
            }
        }
    }

    suspend fun matchAllTrips(place: Place) = withContext(io) {
        for (ref in repository.tripPreviews(false)) {
            val candidate = withContext(geometry) {
                PlaceMatcher.isCandidate(place, PlaceMatcher.cells(ref.previewPolyline?.let { Trip.decodePolyline(it) }.orEmpty()))
            }
            if (!candidate) continue
            val trip = repository.fetchTripDetail(ref.id)?.let { it.withRecordingBreaks(it.recordingBreaks) } ?: continue
            if (trip.trackPoints.size < 2) continue
            val passes = withContext(geometry) { PlaceMatcher.passes(place, trip.id, trip.trackPoints, trip.startDate) }
            if (passes.isNotEmpty()) store.replacePasses(place.id, trip.id, passes)
            yield()
        }
    }

    suspend fun reconcile() {
        // A pull commonly overlaps startup. Do not queue a second library pass behind the first.
        if (!reconciling.compareAndSet(false, true)) return
        try {
            withContext(io) {
                for ((checkpoint, tripId) in repository.checkpointsWithoutPlace()) {
                    registerCheckpoint(checkpoint, tripId)
                    settle()
                }
                drainPendingHistory()
                val known = store.fetchPlaces()
                val pending = repository.tripPreviews(true)
                if (known.isEmpty()) {
                    repository.markPlacesMatched(pending.map { it.id })
                } else {
                    val done = mutableListOf<UUID>()
                    for (ref in pending) {
                        process(ref, known)
                        done.add(ref.id)
                        if (done.size >= MATCH_BATCH) {
                            repository.markPlacesMatched(done.toList())
                            done.clear()
                        }
                        yield()
                    }
                    repository.markPlacesMatched(done.toList())
                }
                mutablePlaces.value = known
            }
        } finally { reconciling.set(false) }
    }

    suspend fun rename(placeId: UUID, name: String?) = withContext(io) {
        store.rename(placeId, name)
        reload()
    }

    suspend fun forget(tripId: UUID) = withContext(io) {
        if (store.passesOfTrip(tripId).isNotEmpty()) store.deletePasses(tripId)
    }

    suspend fun delete(placeId: UUID) = withContext(io) {
        // Checkpoints keep their IDs; only a new checkpoint in this cell can recreate the place.
        store.deletePlace(placeId)
        reload()
    }

    private suspend fun drainPendingHistory() {
        val pending = historyEdits.withLock { history.ids }
        for (id in pending) {
            store.fetchPlace(id)?.let { matchAllTrips(it) }
            historyEdits.withLock { history.ids = history.ids.filterNot { it == id } }
        }
    }

    private companion object { const val MATCH_BATCH = 200 }
}
