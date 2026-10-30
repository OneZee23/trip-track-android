// iOS: PlacePass.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: PlacePass.swift
data class PlacePass(
    val id: UUID = UUID.randomUUID(),
    val placeId: UUID,
    val tripId: UUID,
    val timestamp: Instant,
    val elapsedFromStart: Double,
    val distanceFromStart: Double,
    val course: Double,
) {
    companion object {
        const val unknownCourse = -1.0
    }
}
