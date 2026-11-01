package com.onezee.triptrack.debug

import com.onezee.triptrack.data.GeohashOf
import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.RoomTerritoryStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.feature.atlas.RevealedLayerStore
import com.onezee.triptrack.feature.places.PlaceHistory
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.cell
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * iOS: DebugMapSeed with `-seed-places-rich -seed-places-many`, on top of [TripsDemoSeed]:
 *
 * - «rich»: «Краснодар → Ростов-на-Дону» cloned twice more, 40 and 66 days back, with the same
 *   points moved in time, so the places on that road have three passes: «Здесь 3 раза» and the
 *   usual time become visible without three days of real driving.
 * - «many»: the ten places of the iOS seed, through the same door a saved suggestion takes
 *   ([PlaceHistory.createPlace], with its history): search, order and groups appear only from eight.
 * - the territory and the opened world built from the library, as the launch migrations do.
 *
 * Debug builds only, idempotent: a second run adds nothing.
 */
class PlacesDemoSeed(
    private val database: TripTrackDatabase,
    private val places: PlaceStore,
    private val history: PlaceHistory,
    private val revealed: RevealedLayerStore,
    private val now: Instant = Instant.now(),
) {
    suspend fun run(many: Boolean = true) {
        seedRich()
        if (many) seedMany()
        // The launch migrations over the seeded library: the territory (cities) and the opened world.
        RoomTerritoryStore(database).rebuildFromTrips(GeohashOf { lat, lon -> GeohashEncoder.encode(lat, lon, 6) })
        revealed.rebuildIfNeeded()
    }

    private suspend fun seedRich() {
        val dao = database.tripDao()
        val road = dao.completedTrips().filter { it.title == TripsDemoSeed.ROSTOV_ROAD }
        if (road.size >= 3) return
        val original = road.maxByOrNull { it.startDate } ?: return
        val points = dao.orderedTrackPoints(original.id)
        if (points.isEmpty()) return
        for (daysAgo in listOf(40L, 66L)) {
            val start = now.minus(Duration.ofDays(daysAgo))
            val offset = Duration.between(original.startDate, start)
            val id = UUID.randomUUID()
            dao.upsertTrip(original.copy(id = id, startDate = start, endDate = original.endDate?.plus(offset), isPrivate = true,
                lastModifiedAt = start, placesMatchedAt = null))
            dao.insertTrackPoints(points.map { point: TrackPointEntity ->
                point.copy(id = UUID.randomUUID(), tripId = id, timestamp = point.timestamp.plus(offset))
            })
        }
    }

    private suspend fun seedMany() {
        if (places.fetchPlaces().size >= 8) return
        for ((name, latitude, longitude) in MANY) {
            history.createPlace(Place.cell(latitude, longitude), GeoPoint(latitude, longitude), name)
            history.settle()
        }
    }

    private companion object {
        /** iOS seedManyPlaces: the same ten names and points. */
        val MANY = listOf(
            Triple("Дом", 45.035, 38.975),
            Triple("Работа", 45.052, 38.997),
            Triple("Красная площадь", 45.023, 38.970),
            Triple("Аэропорт", 45.035, 39.171),
            Triple("Дача", 44.900, 38.800),
            Triple("Море", 44.561, 38.077),
            Triple("Заправка на выезде", 45.010, 39.050),
            Triple("Горячий Ключ", 44.629, 39.131),
            Triple("Джубга", 44.320, 38.705),
            Triple("Ростов-на-Дону", 47.222, 39.719),
        )
    }
}
