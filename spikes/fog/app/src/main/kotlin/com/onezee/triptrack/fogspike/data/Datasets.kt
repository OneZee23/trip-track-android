package com.onezee.triptrack.fogspike.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic libraries around Krasnodar. Trips are latitude and longitude interleaved. */
enum class Dataset(val key: String) {
    /** A dozen drives: ten through town, two to the coast and the hills. */
    SMALL("small"),

    /** 400 trips × 2,000 points: 360 drives through town and 40 to towns up to 300 km away. */
    STRESS("stress"),

    /** The iPhone stress seed (`-seed-hang-stress`) formula as is, for numbers comparable with iOS. */
    IOS("ios");

    companion object {
        fun of(key: String?): Dataset = entries.firstOrNull { it.key == key } ?: SMALL
    }
}

object Datasets {
    const val CENTRE_LAT = 45.0355
    const val CENTRE_LON = 38.9753

    // Public town coordinates, used as far ends of the long drives.
    private val towns = listOf(
        44.7235 to 37.7686, // Novorossiysk
        44.5622 to 38.0848, // Gelendzhik
        43.5855 to 39.7231, // Sochi
        47.2357 to 39.7015, // Rostov-on-Don
        44.6098 to 40.1006, // Maykop
        44.8857 to 37.3199, // Anapa
        44.1053 to 39.0802, // Tuapse
        44.9892 to 41.1234, // Armavir
        45.0448 to 41.9691, // Stavropol
        46.7055 to 38.2739, // Yeysk
        44.6344 to 39.1358, // Goryachy Klyuch
        45.2558 to 38.1248, // Slavyansk-na-Kubani
    )

    fun trips(dataset: Dataset): List<DoubleArray> = when (dataset) {
        Dataset.SMALL -> small()
        Dataset.STRESS -> stress()
        Dataset.IOS -> iosStress()
    }

    private fun small(): List<DoubleArray> {
        val random = Random(7)
        val trips = ArrayList<DoubleArray>()
        repeat(10) { trips += cityWalk(random, points = 600 + random.nextInt(900), radiusKm = 6.0) }
        trips += intercity(random, towns[1], points = 1500)
        trips += intercity(random, towns[10], points = 1500)
        return trips
    }

    private fun stress(): List<DoubleArray> {
        val random = Random(42)
        val trips = ArrayList<DoubleArray>(400)
        repeat(400) { i ->
            trips += if (i % 10 == 9) {
                intercity(random, towns[random.nextInt(towns.size)], points = 2000)
            } else {
                cityWalk(random, points = 2000, radiusKm = 20.0)
            }
        }
        return trips
    }

    // iOS: DebugMapSeed.runHangStressIfNeeded — 400 straight trips, each starting 0.01° further
    // along the diagonal.
    private fun iosStress(): List<DoubleArray> = List(400) { t ->
        val latBase = 45.0 + t * 0.01
        val lonBase = 38.9 + t * 0.01
        DoubleArray(4000) { k ->
            val p = k / 2
            if (k % 2 == 0) latBase + p * 0.00003 else lonBase + p * 0.00002
        }
    }

    // A drive on a north-aligned street grid with 250 m blocks: mostly straight on, sometimes a
    // turn, a point every ten metres with a few metres of GPS noise.
    private fun cityWalk(random: Random, points: Int, radiusKm: Double): DoubleArray {
        val blockLat = 250.0 / 111_320.0
        val blockLon = 250.0 / (111_320.0 * cos(CENTRE_LAT * PI / 180))
        val radiusBlocks = (radiusKm * 1000 / 250).toInt()
        var gx = random.nextInt(-radiusBlocks / 3, radiusBlocks / 3 + 1)
        var gy = random.nextInt(-radiusBlocks / 3, radiusBlocks / 3 + 1)
        var dir = random.nextInt(4)
        val out = DoubleArray(points * 2)
        var written = 0
        val noise = 3.0 / 111_320.0
        while (written < points) {
            val turn = random.nextDouble()
            dir = when {
                turn < 0.62 -> dir
                turn < 0.81 -> (dir + 1) % 4
                turn < 0.98 -> (dir + 3) % 4
                else -> (dir + 2) % 4
            }
            var nx = gx + DX[dir]
            var ny = gy + DY[dir]
            if (hypot(nx.toDouble(), ny.toDouble()) > radiusBlocks) {
                dir = (dir + 2) % 4
                nx = gx + DX[dir]
                ny = gy + DY[dir]
            }
            // 25 points per block: one every ten metres.
            for (s in 1..25) {
                if (written >= points) break
                val t = s / 25.0
                out[2 * written] = CENTRE_LAT + (gy + (ny - gy) * t) * blockLat + random.nextDouble(-noise, noise)
                out[2 * written + 1] = CENTRE_LON + (gx + (nx - gx) * t) * blockLon + random.nextDouble(-noise, noise)
                written++
            }
            gx = nx
            gy = ny
        }
        return out
    }

    // A road to another town: a gentle meander around the straight line, evenly sampled.
    private fun intercity(random: Random, town: Pair<Double, Double>, points: Int): DoubleArray {
        val out = DoubleArray(points * 2)
        val (lat1, lon1) = town
        val phase = random.nextDouble(0.0, 2 * PI)
        val amplitude = random.nextDouble(0.02, 0.08)
        for (p in 0 until points) {
            val t = p.toDouble() / (points - 1)
            val lat = CENTRE_LAT + (lat1 - CENTRE_LAT) * t
            val lon = CENTRE_LON + (lon1 - CENTRE_LON) * t
            // Offset across the line, zero at both ends.
            val swing = amplitude * sin(PI * t) * sin(6 * PI * t + phase)
            val nLat = -(lon1 - CENTRE_LON)
            val nLon = lat1 - CENTRE_LAT
            val norm = hypot(nLat, nLon).coerceAtLeast(1e-9)
            out[2 * p] = lat + swing * nLat / norm
            out[2 * p + 1] = lon + swing * nLon / norm
        }
        return out
    }

    private val DX = intArrayOf(1, 0, -1, 0)
    private val DY = intArrayOf(0, 1, 0, -1)
}
