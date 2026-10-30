package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripDistanceGate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos

// iOS: RevealedLayer.swift. Float32 pairs and little-endian run lengths are the stored contract.
data class RevealedTile(
    val key: String,
    val cells: ByteArray,
    val geometry: ByteArray,
    val updatedAt: Instant,
) {
    val cellSet: Set<RevealGrid.Cell>
        get() = RevealGrid.decode(cells, key).toSet()

    val runs: List<List<GeoPoint>>
        get() = decodeRuns(geometry)

    companion object {
        fun encodeRuns(runs: List<List<GeoPoint>>): ByteArray {
            val kept = runs.filter { it.size > 1 }
            val buffer =
                ByteBuffer.allocate(4 + kept.size * 4 + kept.sumOf { it.size * 8 })
                    .order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(kept.size)
            kept.forEach { buffer.putInt(it.size) }
            kept.forEach { buffer.put(Trip.encodePolyline(it)) }
            return buffer.array()
        }

        fun decodeRuns(data: ByteArray): List<List<GeoPoint>> {
            if (data.size < 4) return emptyList()
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int.toLong() and 0xffffffffL
            if (count == 0L || 4 + count * 4 > data.size) return emptyList()
            val lengths = List(count.toInt()) { buffer.int.toLong() and 0xffffffffL }
            if (buffer.position() + lengths.sum() * 8 > data.size) return emptyList()
            return lengths.map { length ->
                val chunk = ByteArray(length.toInt() * 8)
                buffer.get(chunk)
                Trip.decodePolyline(chunk)
            }
        }
    }
}

// iOS: RevealedLayer.swift. Each LOD is a batch of coordinate runs; the map adapter creates native
// paths.
data class RevealedLayer(
    val fine: List<List<GeoPoint>>,
    val mid: List<List<GeoPoint>>,
    val far: List<List<GeoPoint>>,
    val cellCount: Int,
    val openedKm: Double,
    val regionIds: Set<String>,
    val regionKm: Map<String, Double> = emptyMap(),
    val regionCentroids: Map<String, GeoPoint> = emptyMap(),
) {
    // iOS: RevealedLayer.swift
    enum class LOD {
        fine,
        mid,
        far,
    }

    // iOS: RevealedLayer.swift (step return value).
    data class Step(val advance: Boolean, val metres: Double)

    val isEmpty: Boolean
        get() = fine.isEmpty()

    fun openedKm(regionId: String): Double = regionKm[regionId] ?: 0.0

    fun polylines(lod: LOD): List<List<GeoPoint>> =
        when (lod) {
            LOD.fine -> fine
            LOD.mid -> mid
            LOD.far -> far
        }

    companion object {
        const val centroidSampleStride = 5
        const val midDegrees = 250.0 / 111_320.0
        const val farDegrees = 1_000.0 / 111_320.0
        val empty = RevealedLayer(emptyList(), emptyList(), emptyList(), 0, 0.0, emptySet())

        fun step(from: GeoPoint, to: GeoPoint): Step {
            val metres = to.distanceFrom(from)
            if (!(metres >= TripDistanceGate.minStep)) return Step(false, 0.0)
            return Step(true, if (TripDistanceGate.isPlausibleSegment(metres, 0.0)) metres else 0.0)
        }

        fun openedMetres(runs: List<List<GeoPoint>>): Double {
            var metres = 0.0
            for (run in runs) {
                if (run.size < 2) continue
                var anchor = run.first()
                for (point in run.drop(1)) {
                    val step = step(anchor, point)
                    if (!step.advance) continue
                    metres += step.metres
                    anchor = point
                }
            }
            return metres
        }

        fun nearest(target: GeoPoint, samples: List<GeoPoint>): GeoPoint {
            var best = target
            var bestDistance = Double.POSITIVE_INFINITY
            for (point in samples) {
                val lat = point.latitude - target.latitude
                val lon = (point.longitude - target.longitude) * cos(target.latitude * PI / 180)
                val distance = lat * lat + lon * lon
                if (distance < bestDistance) {
                    best = point
                    bestDistance = distance
                }
            }
            return best
        }

        fun stitch(runs: List<List<GeoPoint>>): List<List<GeoPoint>> {
            if (runs.size <= 1) return runs
            // Swift's Double keys treat both signs of zero as the same coordinate.
            fun key(point: GeoPoint) =
                GeoPoint(
                    if (point.latitude == 0.0) 0.0 else point.latitude,
                    if (point.longitude == 0.0) 0.0 else point.longitude,
                )
            val starts = mutableMapOf<GeoPoint, MutableList<Int>>()
            runs.forEachIndexed { index, run ->
                starts.getOrPut(key(run.first())) { mutableListOf() }.add(index)
            }
            val used = BooleanArray(runs.size)
            val predecessor = BooleanArray(runs.size)
            for (run in runs) for (index in starts[key(run.last())].orEmpty()) predecessor[index] =
                true
            val out = mutableListOf<List<GeoPoint>>()
            fun chain(first: Int) {
                used[first] = true
                val line = runs[first].toMutableList()
                while (true) {
                    val next = starts[key(line.last())]?.firstOrNull { !used[it] } ?: break
                    used[next] = true
                    line.addAll(runs[next].drop(1))
                }
                out += line
            }
            for (i in runs.indices) if (!used[i] && !predecessor[i]) chain(i)
            for (i in runs.indices) if (!used[i]) chain(i)
            return out
        }

        fun build(tiles: List<RevealedTile>, atlas: RegionAtlas?): RevealedLayer =
            build(tiles.flatMap { it.runs }, tiles.sumOf { it.cellSet.size }, atlas)

        fun build(runs: List<List<GeoPoint>>, cellCount: Int, atlas: RegionAtlas?): RevealedLayer {
            val fine = mutableListOf<List<GeoPoint>>()
            val mid = mutableListOf<List<GeoPoint>>()
            val far = mutableListOf<List<GeoPoint>>()
            var metres = 0.0
            val regionMetres = mutableMapOf<String, Double>()
            val regions = mutableSetOf<String>()
            val samples = mutableMapOf<String, MutableList<GeoPoint>>()
            for (run in runs) {
                if (run.size <= 1) continue
                fine += run
                decimate(run, midDegrees).takeIf { it.size > 1 }?.let { mid += it }
                decimate(run, farDegrees).takeIf { it.size > 1 }?.let { far += it }
                var currentRegion: String? = null
                var anchor: GeoPoint? = null
                // One anchor and one step own both the total and its regional split.
                for ((i, point) in run.withIndex()) {
                    if (atlas != null && (i % centroidSampleStride == 0 || i == run.lastIndex)) {
                        currentRegion = atlas.region(point)?.id
                        currentRegion?.let { id ->
                            regions += id
                            samples.getOrPut(id) { mutableListOf() }.add(point)
                        }
                    }
                    val from = anchor
                    if (from == null) {
                        anchor = point
                        continue
                    }
                    val step = step(from, point)
                    if (!step.advance) continue
                    metres += step.metres
                    currentRegion?.let {
                        regionMetres[it] = (regionMetres[it] ?: 0.0) + step.metres
                    }
                    anchor = point
                }
            }
            val centroids =
                samples.mapValues { (_, points) ->
                    nearest(
                        GeoPoint(
                            points.sumOf { it.latitude } / points.size,
                            points.sumOf { it.longitude } / points.size,
                        ),
                        points,
                    )
                }
            return RevealedLayer(
                fine,
                stitch(mid),
                stitch(far),
                cellCount,
                metres / 1000,
                regions,
                regionMetres.mapValues { it.value / 1000 },
                centroids,
            )
        }

        // iOS: RoadFog.swift (decimate). Retain the end even when the radial pass drops it.
        internal fun decimate(run: List<GeoPoint>, minDegrees: Double): List<GeoPoint> {
            if (run.size <= 2) return run
            val out = mutableListOf(run.first())
            for (point in run.drop(1)) {
                val lat = point.latitude - out.last().latitude
                val lon = point.longitude - out.last().longitude
                if (lat * lat + lon * lon >= minDegrees * minDegrees) out += point
            }
            if (
                run.last().latitude != out.last().latitude ||
                    run.last().longitude != out.last().longitude
            )
                out += run.last()
            return if (out.size > 1) out else run
        }
    }
}
