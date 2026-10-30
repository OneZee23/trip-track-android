// iOS: PlaceChip.swift
package com.onezee.triptrack.model

import java.util.UUID

// iOS: PlaceChip.swift
data class PlaceChip(
    val placeId: UUID,
    val count: Int,
    val usual: Double? = null,
)
