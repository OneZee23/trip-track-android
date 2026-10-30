// iOS: TripCheckpoint.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripCheckpoint.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripCheckpoint(
    @Required
    val id: UUID = UUID.randomUUID(),
    var timestamp: Instant,
    var latitude: Double,
    var longitude: Double,
    var distanceFromStart: Double,
    var elapsedFromStart: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var name: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var photoId: UUID? = null,
    @Required
    var photoIds: List<UUID> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var placeId: UUID? = null,
)
