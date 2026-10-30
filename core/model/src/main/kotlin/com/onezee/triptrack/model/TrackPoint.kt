// iOS: TrackPoint.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

// iOS: TrackPoint.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TrackPoint(
    @Required
    val id: UUID = UUID.randomUUID(),
    val latitude: Double,
    val longitude: Double,
    @Required
    val altitude: Double = 0.0,
    // Metres per second.
    @Required
    val speed: Double = 0.0,
    // Degrees; -1 means unknown.
    @Required
    val course: Double = -1.0,
    // Metres.
    @Required
    val horizontalAccuracy: Double = 0.0,
    @Required
    val timestamp: Instant = Instant.now(),
    @Required
    val isInterpolated: Boolean = false,
    // Derived from recordingBreaks; never persisted.
    @Transient
    var recordingSegmentIndex: Int = 0,
)
