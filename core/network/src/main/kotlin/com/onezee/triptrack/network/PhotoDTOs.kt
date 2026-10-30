// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class PhotoUploadResponse(
    val photoId: UUID,
    val url: String,
    val uploadStatus: Short,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class PhotoURLRequest(
    val photoId: UUID,
    val type: String,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class PhotoURLResponse(
    val url: String,
    val expiresAt: String,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class PhotoDeleteRequest(
    val photoId: UUID,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class VehiclePhotoMainRequest(
    val vehicleId: UUID,
    val photoId: UUID,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class OwnedVehiclePhoto(
    val id: UUID,
    val vehicleId: UUID,
    val url: String? = null,
    val thumbUrl: String? = null,
    val isMain: Boolean,
    val takenAt: Instant? = null,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class VehiclePhotoListResponse(
    val photos: List<OwnedVehiclePhoto>,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class ProfilePhotoListResponse(
    val photos: List<ProfilePhotoDTO>,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class ProfilePhotoDeleteRequest(
    val photoId: String,
)

// iOS: Services/Sync/R2PhotoStorage.swift, VehiclePhotoRestore.swift, ProfilePhotoStore.swift
@Serializable
data class ProfilePhotoOrderRequest(
    val photoIds: List<String>,
)

