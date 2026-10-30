// iOS: PhotoSyncPayload.swift
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

// iOS: PhotoSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PhotoSyncPayload(
    val id: UUID,
    val tripId: UUID,
    val filename: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val caption: String? = null,
    val timestamp: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val remoteUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thumbnailUrl: String? = null,
    val sortOrder: Int,
    val uploadStatus: Short,
    val lastModifiedAt: Instant,
)
