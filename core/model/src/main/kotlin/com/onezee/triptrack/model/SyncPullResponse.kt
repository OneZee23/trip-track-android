// iOS: SyncPullResponse.swift
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

// iOS: SyncPullResponse.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SyncPullResponse(
    val trips: TripsSection,
    val vehicles: VehiclesSection,
    val photos: PhotosSection,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val settings: SettingsSyncPayload? = null,
    val serverTime: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ownedCounts: OwnedCounts? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val journeys: JourneysSection? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val discoveries: DiscoveriesSection? = null,
) {

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class TripsSection(
        val upserted: List<TripSyncPayload>,
        val deleted: List<UUID>,
    )

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class VehiclesSection(
        val upserted: List<VehicleSyncPayload>,
        val deleted: List<UUID>,
    )

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class PhotosSection(
        val upserted: List<PhotoSyncPayload>,
        val deleted: List<UUID>,
    )

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class JourneysSection(
        val upserted: List<JourneySyncPayload>,
        val deleted: List<UUID>,
    )

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class DiscoveriesSection(
        val upserted: List<DiscoverySyncPayload>,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val deleted: List<String>? = null,
    )

    // iOS: SyncPullResponse.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class OwnedCounts(
        val trips: Int,
        val vehicles: Int,
        val photos: Int,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val journeys: Int? = null,
    )
}

// iOS: SyncPullResponse.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class DiscoverySyncPayload(
    val secretId: String,
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val symbol: String? = null,
    val foundAt: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val verified: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tripId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val latitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val longitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val story: String? = null,
)

// iOS: SyncPullResponse.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SyncManifestResponse(
    val trips: List<UUID>,
    val vehicles: List<UUID>,
    val photos: List<UUID>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val truncated: Boolean? = null,
)
