// iOS: PublicVehicleMod.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: PublicVehicleMod.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicVehicleMod(
    val id: UUID,
    val title: String,
    val date: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val odometerKm: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photos: List<PublicVehiclePhoto>? = null,
)
