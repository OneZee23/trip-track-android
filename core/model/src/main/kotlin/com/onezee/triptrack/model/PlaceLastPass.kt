// iOS: PlaceLastPass.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: PlaceLastPass.swift
object PlaceLastPass {

    // iOS: PlaceLastPass.swift
    data class Reading(
        val placeId: UUID,
        val at: Instant,
        val delta: Double,
        val isFaster: Boolean,
    )
}
