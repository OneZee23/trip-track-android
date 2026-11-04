// iOS: TripReplayInput.swift
package com.onezee.triptrack.model

import java.time.Instant

// iOS: TripReplayInput.swift
data class TripReplayInput(
    val coords: List<GeoPoint>,
    val speeds: List<Double>,
    val timestamps: List<Instant>,
    val breaks: List<Int> = emptyList(),
)
