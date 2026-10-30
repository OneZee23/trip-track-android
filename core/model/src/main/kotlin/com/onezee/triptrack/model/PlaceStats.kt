// iOS: PlaceStats.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: PlaceStats.swift
data class PlaceStats(
    val passCount: Int,
    val firstAt: Instant? = null,
    val lastAt: Instant? = null,
    val medianElapsed: Double? = null,
    val isFrequentGuest: Boolean,
    val directions: List<Direction>,
) {
    companion object {}

    // iOS: PlaceStats.swift
    data class Direction(
        val course: Double,
        val count: Int,
        val median: Double,
        val best: Double,
        val worst: Double,
        val lastAt: Instant,
        val latestTripId: UUID,
    )
}
