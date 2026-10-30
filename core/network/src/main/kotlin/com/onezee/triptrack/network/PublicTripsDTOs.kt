// iOS: Services/TripSource.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/TripSource.swift
@Serializable
data class PublicTripDTO(
    val id: UUID,
    val startDate: Instant,
    val endDate: Instant? = null,
    val distance: Double,
    val duration: Int? = null,
    val region: String? = null,
    val previewPolyline: String? = null,
    val title: String? = null,
)

// iOS: Services/TripSource.swift
@Serializable
data class PublicTripsResponse(
    val trips: List<PublicTripDTO>,
    val nextCursor: String? = null,
)

