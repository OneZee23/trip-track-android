package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant

// iOS: TripRouteLocator.swift. Reuse the shared Fix and recording distance gate.
val TripRouteLocator.tapRadius: Double get() = 120.0
val TripRouteLocator.distinctPassGap: Double get() = 600.0

fun TripRouteLocator.passes(
    near: GeoPoint, points: List<TrackPoint>, radius: Double = tapRadius, startDate: Instant? = null,
): List<TripRouteLocator.Fix> {
    if (points.size < 2) return emptyList()
    return TripRouteIndex(points, startDate).passes(near, radius)
}

fun TripRouteLocator.fix(at: Instant, points: List<TrackPoint>, startDate: Instant? = null): TripRouteLocator.Fix? {
    if (points.isEmpty() || at < points.first().timestamp || at > points.last().timestamp) return null
    return TripRouteIndex(points, startDate).fix(at)
}

fun TripRouteLocator.distancePrefix(points: List<TrackPoint>): List<Double> {
    val prefix = MutableList(points.size) { 0.0 }
    var anchorIndex = points.indexOfFirst { it.countsForDistance }
    if (anchorIndex < 0) return prefix
    var total = 0.0
    for (i in anchorIndex + 1 until points.size) {
        val point = points[i]
        if (point.countsForDistance) {
            val anchor = points[anchorIndex]
            if (point.recordingSegmentIndex != anchor.recordingSegmentIndex) {
                anchorIndex = i
            } else {
                val metres = anchor.coordinate.distanceFrom(point.coordinate)
                if (metres >= TripDistanceGate.minStep) {
                    if (TripDistanceGate.isPlausibleSegment(metres, point.timestamp.secondsSince(anchor.timestamp))) total += metres
                    anchorIndex = i
                }
            }
        }
        // Coarse and filled points retain their index and inherit the trusted odometer.
        prefix[i] = total
    }
    return prefix
}

fun TripRouteLocator.fix(
    index: Int, points: List<TrackPoint>, prefix: List<Double>, origin: Instant? = null,
): TripRouteLocator.Fix {
    val point = points[index]
    return TripRouteLocator.Fix(index, point.coordinate, point.timestamp, prefix[index],
        maxOf(0.0, point.timestamp.secondsSince(origin ?: points[0].timestamp)))
}

// iOS: TripRouteLocator.swift (Index). A snapshot shares one prefix across all photo readings.
class TripRouteIndex(points: List<TrackPoint>, private val startDate: Instant? = null) {
    private val points = points.map { it.copy() }
    private val prefix = TripRouteLocator.distancePrefix(this.points)

    fun passes(near: GeoPoint, radius: Double = TripRouteLocator.tapRadius): List<TripRouteLocator.Fix> {
        if (points.size < 2) return emptyList()
        val result = mutableListOf<TripRouteLocator.Fix>()
        var bestIndex: Int? = null
        var bestDistance = Double.MAX_VALUE
        var lastHitTime: Instant? = null
        fun flush() {
            bestIndex?.let { result += TripRouteLocator.fix(it, points, prefix, startDate) }
            bestIndex = null
            bestDistance = Double.MAX_VALUE
        }
        for ((i, point) in points.withIndex()) {
            val metres = near.distanceFrom(point.coordinate)
            if (!(metres <= radius)) continue
            if (lastHitTime?.let { point.timestamp.secondsSince(it) > TripRouteLocator.distinctPassGap } == true) flush()
            lastHitTime = point.timestamp
            if (metres < bestDistance) {
                bestDistance = metres
                bestIndex = i
            }
        }
        flush()
        return result
    }

    fun fix(at: Instant): TripRouteLocator.Fix? {
        val index = nearestTimeIndex(at, points) ?: return null
        return TripRouteLocator.fix(index, points, prefix, startDate)
    }

    fun fix(photo: TripPhoto): TripRouteLocator.Fix? {
        photo.exifCoordinate?.let { passes(it, 300.0).firstOrNull()?.let { pass -> return pass } }
        return photo.capturedAt?.let { fix(it) }
    }
}

// iOS: TripRouteLocator.swift / TripPhotoPlacement.swift. Equal distances choose the later point.
internal fun nearestTimeIndex(at: Instant, points: List<TrackPoint>): Int? {
    if (points.isEmpty() || at < points.first().timestamp || at > points.last().timestamp) return null
    var low = 0
    var high = points.lastIndex
    while (low < high) {
        val mid = (low + high) / 2
        if (points[mid].timestamp < at) low = mid + 1 else high = mid
    }
    if (low > 0 && at.secondsSince(points[low - 1].timestamp) < points[low].timestamp.secondsSince(at)) low--
    return low
}
