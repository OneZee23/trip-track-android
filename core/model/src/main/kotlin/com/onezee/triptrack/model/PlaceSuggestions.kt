// iOS: PlaceSuggestions.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: PlaceSuggestions.swift
data class PlaceSuggestion(
    val cell: String,
    val latitude: Double,
    val longitude: Double,
    val trips: Int,
    val lastAt: Instant,
    var name: String? = null,
)
