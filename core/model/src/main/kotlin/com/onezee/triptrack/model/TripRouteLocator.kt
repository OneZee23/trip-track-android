// iOS: TripRouteLocator.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TripRouteLocator.swift
object TripRouteLocator {

    // iOS: TripRouteLocator.swift
    data class Fix(
        val index: Int,
        val coordinate: GeoPoint,
        val timestamp: Instant,
        val distanceFromStart: Double,
        val elapsedFromStart: Double,
    )
}
