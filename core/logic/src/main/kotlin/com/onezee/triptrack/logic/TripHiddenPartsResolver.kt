package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenRange
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripDistanceGate
import java.time.Instant

// iOS: TripHiddenPartsResolver.swift.
data class TripHiddenPartsResolution(
    val hidden: List<Boolean>,
    val visibleRuns: List<IntRange>,
    val hiddenRanges: List<HiddenRange>,
    val previewPoints: List<GeoPoint>,
    val previewBreaks: List<Int>,
    val visibleDistance: Double,
    val visibleStart: Instant?,
    val visibleEnd: Instant?,
)

// iOS: TripHiddenPartsResolver.swift.
object TripHiddenPartsResolver {
    const val sliverMetres = 100.0
    const val photoNearMetres = 150.0

    fun resolve(
        points: List<TrackPoint>,
        recordingBreaks: List<Instant>,
        startMetres: Int,
        endMetres: Int,
        sections: List<HiddenSection>,
        home: Pair<GeoPoint, Double>?,
    ): TripHiddenPartsResolution {
        if (points.isEmpty()) return TripHiddenPartsResolution(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), 0.0, null, null)
        val segment = points.map { point -> recordingBreaks.count { point.timestamp >= it } }
        val distance = prefix(points, segment)
        val total = distance.last()
        val hidden = BooleanArray(points.size) { i ->
            val point = points[i]
            (startMetres > 0 && distance[i] < startMetres) ||
                (endMetres > 0 && total - distance[i] < endMetres) ||
                sections.any { point.timestamp >= it.from && point.timestamp <= it.to } ||
                (home != null && GeometryUtils.haversineDistance(point.coordinate, home.first) < home.second)
        }
        var changed = true
        while (changed) {
            changed = false
            visibleRuns(hidden, segment).forEach { run ->
                val surrounded = run.first > 0 && hidden[run.first - 1] && run.last < hidden.lastIndex && hidden[run.last + 1]
                if (run.count() < 2 || (surrounded && distance[run.last] - distance[run.first] < sliverMetres)) {
                    run.forEach { hidden[it] = true }
                    changed = true
                }
            }
        }
        val runs = visibleRuns(hidden, segment)
        val hiddenRanges = indexRuns(hidden).map { HiddenRange(points[it.first].timestamp, points[it.last].timestamp) }
        val previewPoints = mutableListOf<GeoPoint>()
        val previewBreaks = mutableListOf<Int>()
        runs.forEachIndexed { index, range ->
            if (index > 0) previewBreaks += previewPoints.size
            previewPoints += range.map { points[it].coordinate }
        }
        val visibleDistance = runs.sumOf { distance[it.last] - distance[it.first] }
        return TripHiddenPartsResolution(
            hidden = hidden.toList(), visibleRuns = runs, hiddenRanges = hiddenRanges,
            previewPoints = previewPoints, previewBreaks = previewBreaks,
            visibleDistance = visibleDistance, visibleStart = runs.firstOrNull()?.let { points[it.first].timestamp },
            visibleEnd = runs.lastOrNull()?.let { points[it.last].timestamp },
        )
    }

    fun photoIsHidden(
        capturedAt: Instant?, coordinate: GeoPoint?, points: List<TrackPoint>, result: TripHiddenPartsResolution,
        home: Pair<GeoPoint, Double>?,
    ): Boolean {
        if (capturedAt != null) return result.hiddenRanges.any { capturedAt >= it.from && capturedAt <= it.to }
        if (coordinate == null) return false
        if (home != null && GeometryUtils.haversineDistance(coordinate, home.first) < home.second) return true
        return points.indices.any { result.hidden[it] && GeometryUtils.haversineDistance(coordinate, points[it].coordinate) <= photoNearMetres }
    }

    private fun prefix(points: List<TrackPoint>, segment: List<Int>): List<Double> {
        val out = ArrayList<Double>(points.size)
        var total = 0.0
        var anchor = 0
        points.indices.forEach { i ->
            if (i > 0) {
                if (segment[i] != segment[anchor]) anchor = i
                else {
                    val from = points[anchor]
                    val to = points[i]
                    val metres = from.coordinate.distanceFrom(to.coordinate)
                    val dt = to.timestamp.secondsSince(from.timestamp)
                    if (metres >= TripDistanceGate.minStep && TripDistanceGate.isPlausibleSegment(metres, dt)) total += metres
                    anchor = i
                }
            }
            out += total
        }
        return out
    }

    private fun visibleRuns(hidden: BooleanArray, segment: List<Int>): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var i = 0
        while (i < hidden.size) {
            if (hidden[i]) { i++; continue }
            val start = i
            while (i + 1 < hidden.size && !hidden[i + 1] && segment[i + 1] == segment[start]) i++
            runs += start..i
            i++
        }
        return runs
    }

    private fun indexRuns(hidden: BooleanArray): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var i = 0
        while (i < hidden.size) {
            if (!hidden[i]) { i++; continue }
            val start = i
            while (i + 1 < hidden.size && hidden[i + 1]) i++
            runs += start..i
            i++
        }
        return runs
    }
}
