// iOS: GarageDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.PublicVehicleSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: GarageDTOs.swift
@KeepGeneratedSerializer
@Serializable(with = PublicVehicleSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicVehicle(
    val id: UUID,
    val name: String,
    val avatarEmoji: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarStyle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vehicleType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val make: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val year: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bodyType: String? = null,
    val level: Int,
    val odometerKm: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val about: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isArchived: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val soldAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val plate: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val mapVisible: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photosVisible: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val createdAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val mainPhoto: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photos: List<PublicVehiclePhoto>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var cardStyle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var tripPhotos: List<PublicGarageTripPhoto>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var drive2Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var modTags: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var paintFinish: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var mods: List<PublicVehicleMod>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogModelId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogGenerationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogVariantId: String? = null,
)

// iOS: GarageDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicVehiclePhoto(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thumbUrl: String? = null,
    val isMain: Boolean,
)

// iOS: GarageDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicGarageResponse(
    val vehicles: List<PublicVehicle>,
)

// iOS: GarageDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicGarageTripPhoto(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thumbUrl: String? = null,
    val timestamp: Instant,
)
