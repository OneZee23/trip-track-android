package com.onezee.triptrack.location.world

import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.logic.PlaceMatcher
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.isDraft
import com.onezee.triptrack.logic.isJunk
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.RoadCompletionInfo
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripCheckpoint
import java.time.Instant
import java.util.UUID

/** iOS: RoadCollectionManager.processTrip. The road card of a finished trip. */
fun interface RoadCollector {
    suspend fun processTrip(trip: Trip, history: List<Trip>): RoadCompletionInfo?

    companion object {
        /** No road collection yet: the matching (fingerprints, similarity) is not ported. */
        val None = RoadCollector { _, _ -> null }
    }
}

/** iOS: RevealedLayerStore.ingest(tripId:). The fog module burns the finished track into the atlas. */
fun interface RevealIngest {
    suspend fun ingest(tripId: UUID)

    companion object {
        val None = RevealIngest { }
    }
}

/** iOS: PlaceManager.registerCheckpoint. A checkpoint becomes a place; drafts' checkpoints wait for «Mine». */
fun interface CheckpointPlaces {
    suspend fun registerCheckpoint(checkpoint: TripCheckpoint, tripId: UUID)

    companion object {
        val None = CheckpointPlaces { _, _ -> }
    }
}

/**
 * iOS: TripWorldEntry.swift. One door into the world: rewards and badges, the car's odometer,
 * roads, the sync queue, places and the fog. The finish of a confirmed trip and «Mine» on a draft a
 * week later go through the same code, so the late answer does exactly what the finish would have.
 */
class TripWorldEntry(
    private val trips: TripRepository,
    private val rewards: TripRewards,
    private val places: PlaceStore,
    private val sync: SyncRequestSink,
    private val localUserId: () -> UUID?,
    private val roads: RoadCollector = RoadCollector.None,
    private val reveal: RevealIngest = RevealIngest.None,
    private val checkpointPlaces: CheckpointPlaces = CheckpointPlaces.None,
    private val clock: () -> Instant = Instant::now,
) {
    enum class Route {
        /** Under 500 m and two minutes, or never at driving speed: deleted without a word. */
        discardJunk,
        /** A draft: the summary without rewards, and the question «Yours?». */
        awaitConfirmation,
        /** A confirmed trip enters the world at once. */
        enterWorld,
    }

    /** Junk beats draft: there is nothing to ask about a lap of the car park. */
    fun route(trip: Trip): Route = when {
        trip.isJunk(clock()) -> Route.discardJunk
        trip.isDraft -> Route.awaitConfirmation
        else -> Route.enterWorld
    }

    /** iOS: TripWorldEntry.rewards. The sync queue is here, not at the stop: a draft has none until «Mine». */
    suspend fun rewards(trip: Trip): TripRewardsResult {
        // The library without points; the finished trip brings its own.
        val allTrips = trips.fetchAllTrips().let { library ->
            if (library.any { it.id == trip.id }) library.map { if (it.id == trip.id) trip else it } else library + trip
        }
        val result = rewards.processCompletedTrip(trip, allTrips)
        sync.enqueue(SyncRequest(SyncEntityType.trip, trip.id, SyncAction.update))
        // iOS: SettingsManager.earnedProgressDidChange, the settings row carries XP and level.
        localUserId()?.let { sync.enqueue(SyncRequest(SyncEntityType.settings, it, SyncAction.update)) }
        val road = roads.processTrip(trip, allTrips)
        sync.enqueue(SyncRequest(SyncEntityType.trip, trip.id, SyncAction.upload))
        return result.copy(roadCard = road)
    }

    /**
     * iOS: TripWorldEntry.placesAndReveal. On the FINAL track. A draft's checkpoints waited without a
     * place; they get one here. Then the passes of this trip through every known place, then the fog.
     */
    suspend fun placesAndReveal(trip: Trip) {
        for (checkpoint in trip.checkpoints) {
            if (checkpoint.placeId == null) checkpointPlaces.registerCheckpoint(checkpoint, trip.id)
        }
        processPlaces(trip.id)
        reveal.ingest(trip.id)
    }

    /**
     * iOS: PlaceManager.process(tripId:). Only a completed, confirmed trip is matched, and only
     * against places its preview comes near; an existing pass of a place it no longer reaches is
     * replaced by nothing.
     */
    private suspend fun processPlaces(tripId: UUID) {
        val ref = trips.tripPreviews(needingPlaceMatch = false).firstOrNull { it.id == tripId } ?: return
        val known = places.fetchPlaces()
        if (known.isNotEmpty()) {
            val cells = PlaceMatcher.cells(ref.previewPolyline?.let { Trip.decodePolyline(it) }.orEmpty())
            val candidates = known.filter { PlaceMatcher.isCandidate(it, cells) }
            if (candidates.isNotEmpty()) {
                val trip = trips.fetchTripDetail(tripId)?.let { it.withRecordingBreaks(it.recordingBreaks) }
                if (trip != null && trip.trackPoints.size > 1) {
                    val existing = places.passesOfTrip(tripId)
                    for (place in candidates) {
                        val passes = PlaceMatcher.passes(place, tripId, trip.trackPoints, trip.startDate)
                        if (passes.isNotEmpty() || existing.any { it.placeId == place.id }) {
                            places.replacePasses(place.id, tripId, passes)
                        }
                    }
                }
            }
        }
        trips.markPlacesMatched(listOf(tripId))
    }
}
