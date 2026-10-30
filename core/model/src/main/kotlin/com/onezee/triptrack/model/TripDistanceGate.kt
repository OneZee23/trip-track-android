// iOS: TripDistanceGate.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TripDistanceGate.swift
object TripDistanceGate {

    // iOS: TripDistanceGate.swift
    data class Sample(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Instant? = null,
        val recordingSegmentIndex: Int = 0,
        val speed: Double? = null,
    )
}
