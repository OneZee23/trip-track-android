// iOS: TripSyncPayload.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripPhotoMetadataPayload(
    val id: UUID,
    val filename: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val caption: String? = null,
    val timestamp: Instant,
    val sortOrder: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var capturedAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var exifLatitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var exifLongitude: Double? = null,
)

// iOS: TripSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripCheckpointPayload(
    val id: UUID,
    val timestamp: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val latitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val longitude: Double? = null,
    val distanceFromStart: Double,
    val elapsedFromStart: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photoId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photoIds: List<UUID>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val placeId: UUID? = null,
    val sortOrder: Int,
)

// iOS: TripSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripSegmentPayload(
    val id: UUID,
    val fromCheckpointId: UUID,
    val toCheckpointId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
)

// iOS: TripSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripSyncPayload(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    val distance: Double,
    val maxSpeed: Double,
    val averageSpeed: Double,
    val fuelUsed: Double,
    val elevation: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxAltitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val drivingTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stoppedTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val region: String? = null,
    val isPrivate: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vehicleId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isTransfer: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fuelCurrency: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previewPolyline: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val badgesJson: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val xpEarned: Int? = null,
    val conflictVersion: Int,
    val lastModifiedAt: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val serverCreatedAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val trackPoints: List<TrackPointPayload>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photos: List<TripPhotoMetadataPayload>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var checkpoints: List<TripCheckpointPayload>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var segments: List<TripSegmentPayload>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var source: TripOrigin? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var energyMode: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var recordingBreaks: List<Instant>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previewBreaks: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val hiddenParts: TripHiddenParts? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val hiddenRanges: List<HiddenRange>? = null,
)
