// iOS: TripPhoto.swift
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

// iOS: TripPhoto.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripPhoto(
    val id: UUID,
    val filename: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val caption: String? = null,
    val timestamp: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var capturedAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var exifLatitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var exifLongitude: Double? = null,
)
