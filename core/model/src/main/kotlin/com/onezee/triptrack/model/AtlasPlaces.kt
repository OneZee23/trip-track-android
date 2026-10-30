// iOS: AtlasPlaces.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: AtlasPlaces.swift
data class AtlasPlacePin(
    val id: UUID,
    val coordinate: GeoPoint,
    val name: String? = null,
    val lastAt: Instant? = null,
    val passCount: Int,
    val usual: Double? = null,
    val inPeriod: Boolean,
)

// iOS: AtlasPlaces.swift
object AtlasPlaces {

    // iOS: AtlasPlaces.swift
    data class Seed(
        val id: UUID,
        val coordinate: GeoPoint,
        val name: String? = null,
        val usual: Double? = null,
        val passDates: List<Instant>,
    )
}
