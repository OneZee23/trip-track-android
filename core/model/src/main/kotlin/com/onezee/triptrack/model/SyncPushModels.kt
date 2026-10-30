// iOS: SyncPushModels.swift
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

// iOS: SyncPushModels.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SyncPushRequest(
    val trips: List<TripSyncPayload>,
)

// iOS: SyncPushModels.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SyncPushResponse(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val trips: List<TripResult>? = null,
) {

    // iOS: SyncPushModels.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class TripResult(
        val id: UUID,
        val conflictVersion: Int,
        val status: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val serverCreatedAt: Instant? = null,
    )
}
