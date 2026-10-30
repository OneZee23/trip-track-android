// iOS: TrackPointPayload.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TrackPointPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TrackPointPayload(
    val id: UUID,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Double,
    val course: Double,
    val horizontalAccuracy: Double,
    val timestamp: Instant,
    val isInterpolated: Boolean,
)
