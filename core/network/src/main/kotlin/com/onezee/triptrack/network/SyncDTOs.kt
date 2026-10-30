// iOS: Services/Sync/APISyncTransport.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class TripUpsertResponse(
    val id: UUID,
    val conflictVersion: Int,
    val serverCreatedAt: Instant,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class TripDetailRequest(
    val id: UUID,
    val includeTrackPoints: Boolean,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class TripDeleteRequest(
    val id: UUID,
    val conflictVersion: Int,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class VehicleUpsertResponse(
    val id: UUID,
    val conflictVersion: Int,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class VehicleDeleteRequest(
    val id: UUID,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class VehicleListResponse(
    val vehicles: List<VehicleSyncPayload>,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class JourneyUpsertResponse(
    val id: UUID,
    val conflictVersion: Int,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class JourneyListResponse(
    val journeys: List<JourneySyncPayload>,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class JourneyDeleteRequest(
    val id: UUID,
)

// iOS: Services/Sync/APISyncTransport.swift
@Serializable
data class SettingsUpsertResponse(
    val conflictVersion: Int,
)

