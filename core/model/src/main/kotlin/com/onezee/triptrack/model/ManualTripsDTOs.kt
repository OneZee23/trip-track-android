// iOS: ManualTripRouter.swift, ManualTripSuggestions.swift. Android uses the verified server proxy.
package com.onezee.triptrack.model

import kotlinx.serialization.Serializable

@Serializable
data class ManualTripCapabilities(
    val manualTrips: Boolean = false,
    val search: Boolean = false,
    val routing: Boolean = false,
)

@Serializable
data class ManualTripSearchRequest(val query: String, val language: String)

@Serializable
data class ManualTripPlaceDTO(val name: String, val latitude: Double, val longitude: Double)

@Serializable
data class ManualTripSearchResponse(val places: List<ManualTripPlaceDTO> = emptyList())

@Serializable
data class ManualTripRouteRequest(val waypoints: List<List<Double>>)

@Serializable
data class ManualTripRouteResponse(
    val coordinates: List<List<Double>>,
    val distance: Double,
    val durationSeconds: Double,
)
