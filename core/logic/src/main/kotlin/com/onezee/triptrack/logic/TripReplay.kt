package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripReplayInput
import java.time.Instant
import kotlin.math.sqrt

// iOS: GeometryUtils.swift (significantIndices)
fun GeometryUtils.significantIndices(coords: List<GeoPoint>, budget: Int): List<Int> {
    if (coords.size <= budget || budget < 2) return coords.indices.toList()
    // iOS: GeometryUtils.swift (local Segment)
    data class Segment(val start: Int, val end: Int, val peak: Int, val deviation: Double)
    fun segment(start: Int, end: Int): Segment? {
        if (end - start <= 1) return null
        var peak = start + 1
        var deviation = -1.0
        for (i in start + 1 until end) {
            val a = coords[start]
            val b = coords[end]
            val p = coords[i]
            val dx = b.longitude - a.longitude
            val dy = b.latitude - a.latitude
            val len = dx * dx + dy * dy
            val t = if (len > 0) (((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / len).coerceIn(0.0, 1.0) else 0.0
            val px = p.longitude - (a.longitude + t * dx)
            val py = p.latitude - (a.latitude + t * dy)
            val distance = sqrt(px * px + py * py)
            if (distance > deviation) { deviation = distance; peak = i }
        }
        return Segment(start, end, peak, deviation)
    }
    val kept = mutableSetOf(0, coords.lastIndex)
    val pending = mutableListOf<Segment>()
    segment(0, coords.lastIndex)?.let(pending::add)
    while (kept.size < budget && pending.isNotEmpty()) {
        var worst = 0
        for (i in 1 until pending.size) if (pending[i].deviation > pending[worst].deviation) worst = i
        val chosen = pending.removeAt(worst)
        if (chosen.deviation <= 0) break
        kept.add(chosen.peak)
        segment(chosen.start, chosen.peak)?.let(pending::add)
        segment(chosen.peak, chosen.end)?.let(pending::add)
    }
    return kept.sorted()
}

// iOS: TripReplayInput.swift. Async cancellation and dispatcher ownership stay with the screen.
object TripReplay {
    fun prepare(points: List<TrackPoint>): TripReplayInput = prepare(
        points.map { it.coordinate }, points.map { it.speed }, points.map { it.timestamp },
        points.indices.filter { it > 0 && points[it].recordingSegmentIndex != points[it - 1].recordingSegmentIndex },
    )

    fun prepare(coords: List<GeoPoint>, speeds: List<Double> = emptyList(), timestamps: List<Instant> = emptyList(), breaks: List<Int> = emptyList()): TripReplayInput {
        if (coords.size <= 300) return TripReplayInput(coords.toList(), speeds.toList(), timestamps.toList(), breaks.filter { it in 1 until coords.size })
        val boundaries = (listOf(0) + breaks.filter { it in 1 until coords.size }.distinct().sorted() + coords.size)
        val selected = mutableListOf<Int>()
        val selectedBreaks = mutableListOf<Int>()
        for ((runIndex, pair) in boundaries.zipWithNext().withIndex()) {
            val start = pair.first
            val endExclusive = pair.second
            if (runIndex > 0) selectedBreaks += selected.size
            val run = coords.subList(start, endExclusive)
            val budget = maxOf(2, (300.0 * run.size / coords.size).toInt())
            selected += GeometryUtils.significantIndices(run, budget).map { it + start }
        }
        val indices = selected.distinct()
        return TripReplayInput(indices.map { coords[it] },
            if (speeds.size == coords.size) indices.map { speeds[it] } else emptyList(),
            if (timestamps.size == coords.size) indices.map { timestamps[it] } else emptyList(), selectedBreaks)
    }
}
