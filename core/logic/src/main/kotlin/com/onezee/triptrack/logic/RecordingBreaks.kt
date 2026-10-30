package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TrackPoint
import java.time.Instant

// iOS: RecordingBreaks.swift
object RecordingBreaks {
    // Instant cannot represent the non-finite dates discarded by iOS.
    fun normalized(breaks: List<Instant>): List<Instant> = breaks.distinct().sorted()

    fun segmentIndex(at: Instant, breaks: List<Instant>): Int = index(at, normalized(breaks))

    fun annotate(points: List<TrackPoint>, breaks: List<Instant>): List<TrackPoint> {
        val sorted = normalized(breaks)
        return points.map { point -> point.copy(recordingSegmentIndex = index(point.timestamp, sorted)) }
    }

    fun crosses(from: Instant, to: Instant, breaks: List<Instant>): Boolean {
        val sorted = normalized(breaks)
        return index(from, sorted) != index(to, sorted)
    }

    private fun index(at: Instant, sortedBreaks: List<Instant>): Int {
        var low = 0
        var high = sortedBreaks.size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (sortedBreaks[middle] <= at) low = middle + 1 else high = middle
        }
        return low
    }
}
