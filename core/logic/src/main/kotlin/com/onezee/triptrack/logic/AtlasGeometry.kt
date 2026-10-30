package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.*

// iOS: GeometryUtils.swift. Spherical distance is used for Atlas attribution, unlike the recording
// odometer.
object GeometryUtils {
    fun haversineDistance(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dLat = (b.latitude - a.latitude) * PI / 180
        val dLon = (b.longitude - a.longitude) * PI / 180
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 6_371_000 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Epsilon of the stored preview polyline, in degrees (~3 m at mid-latitudes). */
    const val previewEpsilon = 0.00003

    /**
     * iOS: simplifyRDP. Ramer–Douglas–Peucker in degrees, keeping the points iOS keeps: the first
     * farthest point wins a tie. Iterative, because the recursive form is as deep as the track in
     * the worst case and an eight-hour drive is tens of thousands of points.
     */
    fun simplifyRDP(coords: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        if (coords.size <= 2) return coords
        val keep = BooleanArray(coords.size)
        keep[0] = true
        keep[coords.lastIndex] = true
        val ranges = ArrayDeque<Pair<Int, Int>>()
        ranges.addLast(0 to coords.lastIndex)
        while (ranges.isNotEmpty()) {
            val (start, end) = ranges.removeLast()
            if (end - start < 2) continue
            var maxDistance = 0.0
            var maxIndex = start
            for (i in start + 1 until end) {
                val d = perpendicularDistance(coords[i], coords[start], coords[end])
                if (d > maxDistance) {
                    maxDistance = d
                    maxIndex = i
                }
            }
            if (maxDistance > epsilon) {
                keep[maxIndex] = true
                ranges.addLast(start to maxIndex)
                ranges.addLast(maxIndex to end)
            }
        }
        return coords.filterIndexed { index, _ -> keep[index] }
    }

    // iOS: perpendicularDistance. Degrees, to the segment rather than the infinite line.
    fun perpendicularDistance(point: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        val lengthSquared = dx * dx + dy * dy
        if (!(lengthSquared > 0)) {
            val px = point.longitude - lineStart.longitude
            val py = point.latitude - lineStart.latitude
            return sqrt(px * px + py * py)
        }
        val t = max(0.0, min(1.0, ((point.longitude - lineStart.longitude) * dx +
            (point.latitude - lineStart.latitude) * dy) / lengthSquared))
        val px = point.longitude - (lineStart.longitude + t * dx)
        val py = point.latitude - (lineStart.latitude + t * dy)
        return sqrt(px * px + py * py)
    }
}

// iOS: TerritoryManager.swift (GeoBounds)
fun GeoBounds.contains(point: GeoPoint): Boolean =
    point.latitude in minLat..maxLat && point.longitude in minLon..maxLon

val GeoBounds.area: Double
    get() = (maxLat - minLat) * (maxLon - minLon)

fun GeoBounds.intersects(other: GeoBounds): Boolean =
    maxLat >= other.minLat &&
        minLat <= other.maxLat &&
        maxLon >= other.minLon &&
        minLon <= other.maxLon
