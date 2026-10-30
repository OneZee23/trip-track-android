package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.sqrt

// iOS: GeometryUtils.swift (gap splitting, Ramer-Douglas-Peucker, polyline length). Extensions on
// the GeometryUtils object already ported in AtlasGeometry.kt, so callers keep the iOS call shape.

// Default gap threshold in metres for splitting a route into continuous segments.
val GeometryUtils.defaultGapThreshold: Double
    get() = 1_000.0

/** Split into continuous segments, breaking at gaps longer than [threshold] metres (haversine). */
fun GeometryUtils.splitByGaps(coords: List<GeoPoint>, threshold: Double): List<List<GeoPoint>> {
    if (coords.size < 2) return emptyList()
    val segments = ArrayList<List<GeoPoint>>()
    var current = arrayListOf(coords[0])
    for (i in 1 until coords.size) {
        if (haversineDistance(coords[i - 1], coords[i]) > threshold) {
            if (current.size >= 2) segments += current
            current = arrayListOf(coords[i])
        } else {
            current += coords[i]
        }
    }
    if (current.size >= 2) segments += current
    return segments
}

/**
 * Length of a polyline in metres. The replay measures progress as a SHARE of the path, so the length
 * of a piece and of the whole must come from one function.
 */
fun GeometryUtils.polylineLength(coords: List<GeoPoint>): Double {
    if (coords.size < 2) return 0.0
    var total = 0.0
    for (i in 1 until coords.size) total += haversineDistance(coords[i - 1], coords[i])
    return total
}

/** Simplify a polyline with Ramer-Douglas-Peucker; [epsilon] is in degrees (~0.00003 ≈ 3 m). */
fun GeometryUtils.simplifyRDP(coords: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
    if (coords.size <= 2) return coords
    return simplifyIndices(coords, 0, coords.size - 1, epsilon).sorted().map { coords[it] }
}

/**
 * The indices RDP keeps between [startIndex] and [endIndex], both always kept; useful when parallel
 * arrays such as speeds must stay aligned.
 *
 * Iterative where iOS recurses: the kept set is the same, but an eight-hour track at one point a
 * second is ~29,000 points, and a recursion that deep is beyond a worker thread's stack on Android.
 */
fun GeometryUtils.simplifyIndices(coords: List<GeoPoint>, startIndex: Int, endIndex: Int, epsilon: Double): Set<Int> {
    val kept = HashSet<Int>()
    val pending = ArrayDeque<Pair<Int, Int>>()
    pending.addLast(startIndex to endIndex)
    while (pending.isNotEmpty()) {
        val (start, end) = pending.removeLast()
        if (end - start <= 1) {
            kept += start
            kept += end
            continue
        }
        var maxDist = 0.0
        var maxIndex = start
        for (i in start + 1 until end) {
            val d = perpendicularDistance(coords[i], coords[start], coords[end])
            if (d > maxDist) {
                maxDist = d
                maxIndex = i
            }
        }
        if (maxDist > epsilon) {
            pending.addLast(start to maxIndex)
            pending.addLast(maxIndex to end)
        } else {
            kept += start
            kept += end
        }
    }
    return kept
}

/** Distance from a point to a segment in coordinate degrees, as iOS measures it for RDP. */
fun GeometryUtils.perpendicularDistance(point: GeoPoint, lineStart: GeoPoint, lineEnd: GeoPoint): Double {
    val dx = lineEnd.longitude - lineStart.longitude
    val dy = lineEnd.latitude - lineStart.latitude
    val lenSq = dx * dx + dy * dy
    if (!(lenSq > 0)) {
        val px = point.longitude - lineStart.longitude
        val py = point.latitude - lineStart.latitude
        return sqrt(px * px + py * py)
    }
    val t = (((point.longitude - lineStart.longitude) * dx + (point.latitude - lineStart.latitude) * dy) / lenSq)
        .coerceIn(0.0, 1.0)
    val px = point.longitude - (lineStart.longitude + t * dx)
    val py = point.latitude - (lineStart.latitude + t * dy)
    return sqrt(px * px + py * py)
}
