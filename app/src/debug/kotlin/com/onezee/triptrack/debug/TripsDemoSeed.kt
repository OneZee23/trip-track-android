package com.onezee.triptrack.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.feature.journey.JourneyManager
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.model.TripRouteLocator
import com.onezee.triptrack.logic.distancePrefix
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.logic.fix
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripCheckpoint
import java.io.ByteArrayOutputStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * iOS: DebugMapSeed.swift with `-seed-map-demo -seed-journey-demo -seed-segment-demo -seed-drafts`
 * and its photos. Fills an empty store with the same drives so the trip screens can be looked at and
 * screenshot-tested: real roads around Krasnodar, Rostov and Batumi with the iOS titles, a journey
 * (Краснодар → Ростов-на-Дону and a city leg), two checkpoints and a segment on that road, three
 * photos on the most recent trip and four drafts, one per group of the drafts list.
 *
 * Debug builds only. Every part is idempotent: a second run seeds nothing twice. [now] is the time
 * base, fixed by the screenshot tests so dates on the cards never move.
 */
class TripsDemoSeed(
    private val database: TripTrackDatabase,
    private val repository: TripRepository,
    private val journeys: JourneyManager,
    private val now: Instant = Instant.now(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun run() {
        if (database.tripDao().countCompletedTrips() == 0) seedTrips()
        seedPhotos()
        seedJourney()
        seedSegment()
        seedDrafts()
        seedGeocodeCache()
    }

    private class Route(val title: String, val region: String, val daysAgo: Long, val waypoints: List<Pair<Double, Double>>, val repeats: Int = 1)

    private suspend fun seedTrips() {
        for (route in ROUTES) for (pass in 0 until route.repeats) {
            // Real GPS never repeats itself: every pass wanders a few metres either side.
            val coordinates = densify(route.waypoints, 400.0).mapIndexed { index, point ->
                val phase = index * 0.6 + pass * 1.7
                GeoPoint(point.latitude + sin(phase) * 0.00008, point.longitude + cos(phase * 1.3) * 0.00008)
            }
            val start = now.minus(Duration.ofDays(route.daysAgo + pass * 2L))
            val seconds = coordinates.size * 24L
            val id = UUID.randomUUID()
            val distance = pathLength(coordinates)
            database.tripDao().upsertTrip(TripEntity(
                id = id, startDate = start, endDate = start.plusSeconds(seconds), title = route.title, region = route.region,
                isPrivate = true, distance = distance, maxSpeed = 32.0, averageSpeed = distance / max(seconds, 1),
                previewPolyline = Trip.encodePolyline(coordinates), isTrackProcessed = true, lastModifiedAt = start,
            ))
            database.tripDao().insertTrackPoints(coordinates.mapIndexed { index, point ->
                // Enough spread that the speed gradient has something to show.
                TrackPointEntity(UUID.randomUUID(), altitude = 40.0 + (index % 12) * 6.0, course = 0.0, horizontalAccuracy = 5.0,
                    latitude = point.latitude, longitude = point.longitude, speed = 14.0 + (index * 7) % 18,
                    timestamp = start.plusSeconds(index * 24L), tripId = id)
            })
        }
    }

    /** The MOST RECENT trip carries them: it is the one opened first, by hand and in the tests. */
    private suspend fun seedPhotos() {
        val trip = database.tripDao().completedTrips().maxByOrNull { it.startDate } ?: return
        if (database.tripDao().countPhotos(trip.id) > 0) return
        val points = database.tripDao().orderedTrackPoints(trip.id)
        if (points.size < 8) return
        val near = points[points.size * 45 / 100]
        val far = points[points.size * 75 / 100]
        val seeds = listOf(Triple("Море", 0xFF30B0C7.toInt(), near) to 0.0, Triple("Перевал", 0xFFFF9500.toInt(), near) to 0.00018,
            Triple("Заправка", 0xFF5856D6.toInt(), far) to 0.0)
        for ((seed, offset) in seeds) {
            val (caption, colour, point) = seed
            // The capture time lies INSIDE the track: «how far to here» is read from it.
            repository.addPhoto(trip.id, photo(caption, colour), caption, point.timestamp, point.latitude + offset, point.longitude + offset)
        }
    }

    private suspend fun seedJourney() {
        if (repository.fetchJourneys().isNotEmpty()) return
        val completed = database.tripDao().completedTrips()
        val road = completed.filter { it.title == ROSTOV_ROAD }.maxByOrNull { it.startDate } ?: return
        val city = completed.filter { it.title == ROSTOV_CITY }
        if (city.isEmpty()) return
        // The city legs are public, so the publish sheet has both kinds of leg to name.
        city.forEach { database.tripDao().upsertTrip(it.copy(isPrivate = false)) }
        val legs = (listOf(road) + city).mapNotNull { repository.fetchTripDetail(it.id) }
        if (legs.size == 1 + city.size) journeys.create(legs, "Демо-путешествие")
    }

    /** Checkpoints at 30 % and 70 % of the road by the odometer's own prefix, and a segment between. */
    private suspend fun seedSegment() {
        val entity = database.tripDao().completedTrips().filter { it.title == ROSTOV_ROAD }.maxByOrNull { it.startDate } ?: return
        val trip = repository.fetchTripDetail(entity.id) ?: return
        if (trip.segments.isNotEmpty() || trip.trackPoints.size <= 10) return
        val points = trip.trackPoints
        val prefix = TripRouteLocator.distancePrefix(points)
        val total = prefix.lastOrNull() ?: return
        if (total <= 0) return
        suspend fun checkpoint(fraction: Double, name: String): TripCheckpoint? {
            val index = prefix.indexOfFirst { it >= total * fraction }.takeIf { it >= 0 } ?: return null
            val fix = TripRouteLocator.fix(index, points, prefix, trip.startDate)
            return repository.addCheckpoint(TripCheckpoint(timestamp = fix.timestamp, latitude = fix.coordinate.latitude,
                longitude = fix.coordinate.longitude, distanceFromStart = fix.distanceFromStart,
                elapsedFromStart = fix.elapsedFromStart, name = name), trip.id)
        }
        val from = checkpoint(0.3, "Кореновск") ?: return
        val to = checkpoint(0.7, "Батайск") ?: return
        repository.addSegment(trip.id, from.id, to.id)
    }

    /** Hours of the day are fixed, so «18:01» on a card is the same number on every run. */
    private suspend fun seedDrafts() {
        if (repository.fetchDraftTrips().isNotEmpty()) return
        val today = now.atZone(zone).toLocalDate()
        for (draft in DRAFTS) {
            val start = today.minusDays(draft.daysAgo).atTime(draft.hour, draft.minute).atZone(zone).toInstant()
            val coordinates = densify(draft.waypoints, 600.0)
            val seconds = coordinates.size * 30L
            val id = UUID.randomUUID()
            val distance = pathLength(coordinates)
            database.tripDao().upsertTrip(TripEntity(
                id = id, startDate = start, endDate = start.plusSeconds(seconds), title = draft.title, region = "Краснодарский край",
                isPrivate = true, confirmation = "draft", previewPolyline = Trip.encodePolyline(coordinates), distance = distance,
                maxSpeed = 30.0, averageSpeed = distance / max(seconds, 1), isTrackProcessed = true, lastModifiedAt = start,
            ))
            database.tripDao().insertTrackPoints(coordinates.mapIndexed { index, point ->
                TrackPointEntity(UUID.randomUUID(), altitude = 40.0, course = 0.0, horizontalAccuracy = 5.0, latitude = point.latitude,
                    longitude = point.longitude, speed = 16.0 + (index * 5) % 14, timestamp = start.plusSeconds(index * 30L), tripId = id)
            })
        }
    }

    /** The geocoder never answers in a test: the names of the journey's ends come from its cache. */
    private suspend fun seedGeocodeCache() {
        for ((point, name) in CITIES) {
            val key = GeohashEncoder.encode(point.first, point.second, 5)
            if (repository.cachedLocality(key) == null) repository.saveGeocode(key, name, null)
        }
    }

    private class Draft(val title: String, val daysAgo: Long, val hour: Int, val minute: Int, val waypoints: List<Pair<Double, Double>>)

    companion object {
        const val ROSTOV_ROAD = "Краснодар → Ростов-на-Дону"
        const val ROSTOV_CITY = "По Ростову"
        const val CITY_LOOP = "По городу"

        private val ROUTES = listOf(
            Route("Краснодар → Майкоп", "Krasnodar Krai", 9, listOf(45.030 to 38.960, 45.010 to 38.915, 44.985 to 38.875, 44.960 to 38.845), 3),
            Route("Краснодар → Геленджик", "Krasnodar Krai", 86, listOf(45.035 to 38.975, 44.900 to 38.780, 44.780 to 38.500, 44.640 to 38.230, 44.561 to 38.077)),
            Route("Краснодар → Горячий Ключ", "Krasnodar Krai", 72, listOf(45.035 to 38.975, 44.900 to 39.020, 44.760 to 39.080, 44.630 to 39.130)),
            Route("Утренний круг по бетонке", "Krasnodar Krai", 6, listOf(45.035 to 38.975, 45.090 to 39.060, 45.120 to 38.980, 45.060 to 38.900, 45.035 to 38.975), 22),
            Route("На работу", "Krasnodar Krai", 2, listOf(45.035 to 38.975, 45.020 to 39.030, 45.010 to 39.090), 9),
            Route(CITY_LOOP, "Krasnodar Krai", 1, listOf(45.0355 to 38.9750, 45.0355 to 38.9820, 45.0310 to 38.9820, 45.0310 to 38.9900,
                45.0260 to 38.9900, 45.0260 to 38.9800, 45.0300 to 38.9800, 45.0300 to 38.9720, 45.0355 to 38.9720, 45.0355 to 38.9750), 6),
            Route("Геленджик → Джубга", "Krasnodar Krai", 54, listOf(44.561 to 38.077, 44.480 to 38.300, 44.400 to 38.520, 44.320 to 38.700)),
            Route("Сочи → Красная Поляна", "Krasnodar Krai", 33, listOf(43.585 to 39.723, 43.560 to 39.850, 43.600 to 40.020, 43.660 to 40.130, 43.680 to 40.200)),
            Route("Адлер → Сочи", "Krasnodar Krai", 30, listOf(43.430 to 39.920, 43.470 to 39.870, 43.520 to 39.800, 43.585 to 39.723)),
            Route("Сочи → Лазаревское", "Krasnodar Krai", 21, listOf(43.585 to 39.723, 43.700 to 39.560, 43.820 to 39.400, 43.910 to 39.330)),
            Route(ROSTOV_ROAD, "Rostov Oblast", 14, listOf(45.035 to 38.975, 45.400 to 39.100, 45.900 to 39.300, 46.500 to 39.500, 47.222 to 39.719)),
            Route(ROSTOV_CITY, "Rostov Oblast", 12, listOf(47.222 to 39.719, 47.260 to 39.760, 47.240 to 39.820, 47.200 to 39.740)),
            Route("Батуми → Кобулети", "Adjara", 5, listOf(41.640 to 41.640, 41.720 to 41.700, 41.790 to 41.760)),
        )

        private val DRAFTS = listOf(
            Draft("Краснодар — Горячий Ключ", 0, 18, 1, listOf(45.035 to 38.975, 44.842 to 39.045, 44.629 to 39.131)),
            Draft("Поездка по городу", 0, 9, 24, listOf(45.041 to 38.961, 45.058 to 38.988, 45.070 to 39.010)),
            Draft("Краснодар — Джубга", 1, 15, 42, listOf(45.035 to 38.975, 44.700 to 38.840, 44.320 to 38.705)),
            Draft("Вечерняя поездка", 6, 20, 15, listOf(45.020 to 38.930, 45.055 to 38.985, 45.090 to 39.040)),
        )

        private val CITIES = listOf(
            (45.035 to 38.975) to "Краснодар",
            (47.222 to 39.719) to "Ростов-на-Дону",
            (47.260 to 39.760) to "Ростов-на-Дону",
            (47.200 to 39.740) to "Ростов-на-Дону",
            (44.561 to 38.077) to "Геленджик",
            (43.585 to 39.723) to "Сочи",
        )

        fun densify(waypoints: List<Pair<Double, Double>>, stepMetres: Double): List<GeoPoint> {
            val out = ArrayList<GeoPoint>()
            for (i in 1 until waypoints.size) {
                val a = GeoPoint(waypoints[i - 1].first, waypoints[i - 1].second)
                val b = GeoPoint(waypoints[i].first, waypoints[i].second)
                val steps = max(1, (GeometryUtils.haversineDistance(a, b) / stepMetres).toInt())
                for (step in 0 until steps) {
                    val t = step.toDouble() / steps
                    out += GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                }
            }
            waypoints.lastOrNull()?.let { out += GeoPoint(it.first, it.second) }
            return out
        }

        private fun pathLength(coordinates: List<GeoPoint>): Double =
            coordinates.zipWithNext().sumOf { (a, b) -> GeometryUtils.haversineDistance(a, b) }

        /** iOS photoImage: a coloured frame with its caption, recognisable on a 40 pt pin, not pretty. */
        private fun photo(caption: String, colour: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colour)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 120f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(caption, 600f, 500f, paint)
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        }
    }
}
