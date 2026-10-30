package com.onezee.triptrack.debug

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seeded libraries for the map demo. Each trip is a PREVIEW as stored (Float32 pairs), oldest first,
 * the way the reveal build reads them.
 */
enum class DemoDataset(val key: String) {
    /** iOS: DebugMapSeed.routes (`-seed-map-demo`): real roads around Krasnodar, Rostov and Batumi. */
    DEMO("demo"),

    /** iOS: DebugMapSeed.runHangStressIfNeeded (`-seed-hang-stress`): 400 trips × 2,000 points. */
    IOS_STRESS("ios-stress"),

    /**
     * The fog spike's dense set, also 400 × 2,000: 360 drives on a city grid, 40 to towns up to
     * 300 km away. Its corridors overlap far more than the iOS seed's, so it is the GPU fill test.
     */
    CITY_STRESS("city-stress");

    fun next(): DemoDataset = entries[(ordinal + 1) % entries.size]
}

object MapDemoSeeds {
    val krasnodar = GeoPoint(45.0355, 38.9753)

    fun previews(dataset: DemoDataset): List<List<GeoPoint>> = when (dataset) {
        DemoDataset.DEMO -> demo()
        DemoDataset.IOS_STRESS -> iosStress()
        DemoDataset.CITY_STRESS -> cityStress()
    }.map { stored(it) }

    /** A trip with speeds for the route demo, with a filled-in gap of unknown speed in the middle. */
    fun routeWithSpeeds(): Pair<List<GeoPoint>, List<Double>> {
        val coords = densify(CITY_ROUTE, stepMetres = 15.0)
        // iOS seed: "enough spread that the speed gradient has something to show", here up to 34 m/s.
        val speeds = coords.indices.map { i -> if (i in coords.size / 2 until coords.size / 2 + 12) -1.0 else 4.0 + (i * 7) % 31 }
        return coords to speeds
    }

    private fun stored(coords: List<GeoPoint>): List<GeoPoint> = Trip.decodePolyline(Trip.encodePolyline(coords))

    private class Route(val waypoints: List<Pair<Double, Double>>, val repeats: Int = 1)

    private val CITY_ROUTE = listOf(
        45.0355 to 38.9750, 45.0355 to 38.9820, 45.0310 to 38.9820, 45.0310 to 38.9900, 45.0260 to 38.9900,
        45.0260 to 38.9800, 45.0300 to 38.9800, 45.0300 to 38.9720, 45.0355 to 38.9720, 45.0355 to 38.9750,
    )

    private val ROUTES = listOf(
        Route(listOf(45.030 to 38.960, 45.010 to 38.915, 44.985 to 38.875, 44.960 to 38.845), repeats = 3),
        Route(listOf(45.035 to 38.975, 44.900 to 38.780, 44.780 to 38.500, 44.640 to 38.230, 44.561 to 38.077)),
        Route(listOf(45.035 to 38.975, 44.900 to 39.020, 44.760 to 39.080, 44.630 to 39.130)),
        Route(listOf(45.035 to 38.975, 45.090 to 39.060, 45.120 to 38.980, 45.060 to 38.900, 45.035 to 38.975), repeats = 22),
        Route(listOf(45.035 to 38.975, 45.020 to 39.030, 45.010 to 39.090), repeats = 9),
        Route(CITY_ROUTE, repeats = 6),
        Route(listOf(44.561 to 38.077, 44.480 to 38.300, 44.400 to 38.520, 44.320 to 38.700)),
        Route(listOf(43.585 to 39.723, 43.560 to 39.850, 43.600 to 40.020, 43.660 to 40.130, 43.680 to 40.200)),
        Route(listOf(43.430 to 39.920, 43.470 to 39.870, 43.520 to 39.800, 43.585 to 39.723)),
        Route(listOf(43.585 to 39.723, 43.700 to 39.560, 43.820 to 39.400, 43.910 to 39.330)),
        Route(listOf(45.035 to 38.975, 45.400 to 39.100, 45.900 to 39.300, 46.500 to 39.500, 47.222 to 39.719)),
        Route(listOf(47.222 to 39.719, 47.260 to 39.760, 47.240 to 39.820, 47.200 to 39.740)),
        Route(listOf(41.640 to 41.640, 41.720 to 41.700, 41.790 to 41.760)),
    )

    // Real GPS never repeats itself: every pass wanders a few metres either side of the last one,
    // which is exactly what the fog has to collapse into one road.
    private fun demo(): List<List<GeoPoint>> = ROUTES.flatMap { route ->
        (0 until route.repeats).map { pass ->
            densify(route.waypoints, stepMetres = 400.0).mapIndexed { index, point ->
                val phase = index * 0.6 + pass * 1.7
                GeoPoint(point.latitude + sin(phase) * 0.00008, point.longitude + cos(phase * 1.3) * 0.00008)
            }
        }
    }

    // 400 diagonal trips of 2,000 points, each starting 0.01° further; trip 399 is the oldest.
    private fun iosStress(): List<List<GeoPoint>> = (399 downTo 0).map { t ->
        val latBase = 45.0 + t * 0.01
        val lonBase = 38.9 + t * 0.01
        List(2_000) { p -> GeoPoint(latBase + p * 0.00003, lonBase + p * 0.00002) }
    }

    private val TOWNS = listOf(
        44.7235 to 37.7686, 44.5622 to 38.0848, 43.5855 to 39.7231, 47.2357 to 39.7015, 44.6098 to 40.1006,
        44.8857 to 37.3199, 44.1053 to 39.0802, 44.9892 to 41.1234, 45.0448 to 41.9691, 46.7055 to 38.2739,
        44.6344 to 39.1358, 45.2558 to 38.1248,
    )

    private fun cityStress(): List<List<GeoPoint>> {
        val random = Random(42)
        return List(400) { i ->
            if (i % 10 == 9) intercity(random, TOWNS[random.nextInt(TOWNS.size)], 2_000) else cityWalk(random, 2_000, 20.0)
        }
    }

    // A drive on a north-aligned grid of 250 m blocks: mostly straight on, sometimes a turn, a point
    // every ten metres with a few metres of GPS noise.
    private fun cityWalk(random: Random, points: Int, radiusKm: Double): List<GeoPoint> {
        val blockLat = 250.0 / 111_320.0
        val blockLon = 250.0 / (111_320.0 * cos(krasnodar.latitude * PI / 180))
        val radiusBlocks = (radiusKm * 1000 / 250).toInt()
        var gx = random.nextInt(-radiusBlocks / 3, radiusBlocks / 3 + 1)
        var gy = random.nextInt(-radiusBlocks / 3, radiusBlocks / 3 + 1)
        var dir = random.nextInt(4)
        val dx = intArrayOf(1, 0, -1, 0)
        val dy = intArrayOf(0, 1, 0, -1)
        val noise = 3.0 / 111_320.0
        val out = ArrayList<GeoPoint>(points)
        while (out.size < points) {
            val turn = random.nextDouble()
            dir = when {
                turn < 0.62 -> dir
                turn < 0.81 -> (dir + 1) % 4
                turn < 0.98 -> (dir + 3) % 4
                else -> (dir + 2) % 4
            }
            var nx = gx + dx[dir]
            var ny = gy + dy[dir]
            if (hypot(nx.toDouble(), ny.toDouble()) > radiusBlocks) {
                dir = (dir + 2) % 4
                nx = gx + dx[dir]
                ny = gy + dy[dir]
            }
            for (s in 1..25) {
                if (out.size >= points) break
                val t = s / 25.0
                out += GeoPoint(
                    krasnodar.latitude + (gy + (ny - gy) * t) * blockLat + random.nextDouble(-noise, noise),
                    krasnodar.longitude + (gx + (nx - gx) * t) * blockLon + random.nextDouble(-noise, noise),
                )
            }
            gx = nx
            gy = ny
        }
        return out
    }

    // A road to another town: a gentle meander around the straight line, evenly sampled.
    private fun intercity(random: Random, town: Pair<Double, Double>, points: Int): List<GeoPoint> {
        val (lat1, lon1) = town
        val phase = random.nextDouble(0.0, 2 * PI)
        val amplitude = random.nextDouble(0.02, 0.08)
        val nLat = -(lon1 - krasnodar.longitude)
        val nLon = lat1 - krasnodar.latitude
        val norm = max(hypot(nLat, nLon), 1e-9)
        return List(points) { p ->
            val t = p.toDouble() / (points - 1)
            val swing = amplitude * sin(PI * t) * sin(6 * PI * t + phase)
            GeoPoint(
                krasnodar.latitude + (lat1 - krasnodar.latitude) * t + swing * nLat / norm,
                krasnodar.longitude + (lon1 - krasnodar.longitude) * t + swing * nLon / norm,
            )
        }
    }

    // iOS: DebugMapSeed.densify.
    private fun densify(waypoints: List<Pair<Double, Double>>, stepMetres: Double): List<GeoPoint> {
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
}
