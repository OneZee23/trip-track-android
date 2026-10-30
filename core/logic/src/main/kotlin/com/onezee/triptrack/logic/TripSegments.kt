package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*

// iOS: TripSegment.swift
fun TripSegmentMetrics.resolve(segment: TripSegment, checkpoints: List<TripCheckpoint>): TripSegmentMetrics.Resolved? {
    val a = checkpoints.firstOrNull { it.id == segment.fromCheckpointId } ?: return null
    val b = checkpoints.firstOrNull { it.id == segment.toCheckpointId } ?: return null
    val (from, to) = if (a.elapsedFromStart <= b.elapsedFromStart) a to b else b to a
    return TripSegmentMetrics.Resolved(from, to, to.elapsedFromStart - from.elapsedFromStart, to.distanceFromStart - from.distanceFromStart)
}

// iOS: TripSegment.swift (TripSegmentName). The caller supplies the localized resource formatter.
object TripSegmentName {
    fun text(segment: TripSegment, checkpoints: List<TripCheckpoint>, defaultName: (Int) -> String): String {
        segment.name?.takeIf { it.isNotEmpty() }?.let { return it }
        val resolved = TripSegmentMetrics.resolve(segment, checkpoints) ?: return ""
        val sorted = checkpoints.sortedBy { it.elapsedFromStart }
        fun displayName(checkpoint: TripCheckpoint): String = checkpoint.name?.takeIf { it.isNotEmpty() }
            ?: defaultName((sorted.indexOfFirst { it.id == checkpoint.id }.takeIf { it >= 0 } ?: 0) + 1)
        return "${displayName(resolved.from)} → ${displayName(resolved.to)}"
    }
}

// iOS: SegmentHistory.swift
object SegmentHistory {
    fun times(fromPasses: List<PlacePass>, toPasses: List<PlacePass>): List<Double> {
        val fromByTrip = fromPasses.groupBy { it.tripId }
        return toPasses.groupBy { it.tripId }.mapNotNull { (trip, tos) ->
            val froms = fromByTrip[trip] ?: return@mapNotNull null
            tos.flatMap { to -> froms.map { from -> to.elapsedFromStart - from.elapsedFromStart } }
                .filter { it > 0 }.minOrNull()
        }.sorted()
    }

    fun clock(seconds: Double): String {
        val minutes = maxOf(0, seconds.toLong()) / 60
        return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
}

// iOS: CheckpointDefaultName.swift. All translated words come from generated string resources.
object CheckpointDefaultName {
    fun looksLikeDefaultCheckpointName(name: String, checkpointWords: Collection<String>): Boolean =
        name.isEmpty() || checkpointWords.any { Regex("^" + Regex.escape(it) + " \\p{Nd}+$").containsMatchIn(name) }
}
