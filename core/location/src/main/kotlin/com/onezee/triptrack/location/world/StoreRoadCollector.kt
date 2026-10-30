package com.onezee.triptrack.location.world

import com.onezee.triptrack.data.RoadStore
import com.onezee.triptrack.data.db.entity.RoadEntity
import com.onezee.triptrack.logic.RoadCollection
import com.onezee.triptrack.logic.SimilarRecordedTrips
import com.onezee.triptrack.logic.from
import com.onezee.triptrack.model.RoadCompletionInfo
import com.onezee.triptrack.model.RoadLevel
import com.onezee.triptrack.model.RoadRarity
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.util.UUID

/**
 * iOS: RoadCollectionManager.processTrip. The road a finished trip drove: an existing one levels up,
 * a new one is discovered. The collection is local and never restored by sync, so the count on the
 * card comes from the trip history instead (`SimilarRecordedTrips`), restored trips included.
 */
class StoreRoadCollector(
    private val roads: RoadStore,
    private val clock: () -> Instant = Instant::now,
) : RoadCollector {
    override suspend fun processTrip(trip: Trip, history: List<Trip>): RoadCompletionInfo? {
        if (trip.trackPoints.size < 2) return null
        val fingerprint = RoadCollection.fingerprint(trip.trackPoints)
        if (fingerprint.size < 2) return null
        val start = RoadCollection.neighbourhood(fingerprint.first())
        val end = RoadCollection.neighbourhood(fingerprint.last())
        val historyCount = SimilarRecordedTrips.count(trip, history)
        val cells = fingerprint.toSet()
        val existing = roads.roadsTouching(start + end).firstOrNull {
            RoadCollection.matches(it.startGeohash, it.endGeohash, it.geohashSequence, start, end, cells)
        }
        val now = clock()
        if (existing != null) {
            val times = existing.timesDriven + 1
            val level = RoadLevel.from(times)
            roads.update(existing.copy(timesDriven = times, lastDriven = now, level = level.rawValue))
            return RoadCompletionInfo(
                name = existing.name ?: RoadCollection.roadName(trip),
                rarity = existing.rarity?.let(RoadRarity::fromRawValue) ?: RoadRarity.common,
                level = level,
                timesDriven = historyCount,
                isNew = historyCount < 2,
            )
        }
        // Kilometres into the rarity are metric: the thresholds are the same game for everyone.
        val rarity = RoadRarity.from(trip.distance / 1000)
        val name = RoadCollection.roadName(trip)
        roads.insert(
            RoadEntity(
                id = UUID.randomUUID(), startGeohash = fingerprint.first(), endGeohash = fingerprint.last(),
                geohashSequence = fingerprint.joinToString(","), distanceKm = trip.distance / 1000, timesDriven = 1,
                rarity = rarity.rawValue, level = RoadLevel.discovered.rawValue, firstDriven = now, lastDriven = now, name = name,
            ),
        )
        return RoadCompletionInfo(name = name, rarity = rarity, level = RoadLevel.discovered, timesDriven = historyCount, isNew = historyCount < 2)
    }
}
