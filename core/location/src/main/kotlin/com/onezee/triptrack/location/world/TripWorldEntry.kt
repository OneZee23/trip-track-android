package com.onezee.triptrack.location.world

import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.logic.isDraft
import com.onezee.triptrack.logic.isJunk
import com.onezee.triptrack.model.RoadCompletionInfo
import com.onezee.triptrack.model.Trip
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

/**
 * iOS: TripWorldEntry.swift. One door into the world: rewards and badges, the car's odometer,
 * roads, the sync queue, places and the fog. The finish of a confirmed trip and «Mine» on a draft a
 * week later go through the same code, so the late answer does exactly what the finish would have.
 */
class TripWorldEntry(
    private val trips: TripRepository,
    private val rewards: TripRewards,
    private val places: PlaceManager,
    private val sync: SyncRequestSink,
    private val localUserId: () -> UUID?,
    private val roads: RoadCollector = RoadCollector.None,
    private val reveal: RevealIngest = RevealIngest.None,
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
            if (checkpoint.placeId == null) places.registerCheckpoint(checkpoint, trip.id)
        }
        places.process(trip.id)
        reveal.ingest(trip.id)
    }

}
