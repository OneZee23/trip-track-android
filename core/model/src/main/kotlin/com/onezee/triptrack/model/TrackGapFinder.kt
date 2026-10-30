// iOS: TrackGapFinder.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TrackGapFinder.swift
object TrackGapFinder {

    // iOS: TrackGapFinder.swift
    data class Point(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Instant,
        val isInterpolated: Boolean,
        val recordingSegmentIndex: Int = 0,
    )

    // iOS: TrackGapFinder.swift
    data class Gap(
        val from: Point,
        val to: Point,
    )
}
