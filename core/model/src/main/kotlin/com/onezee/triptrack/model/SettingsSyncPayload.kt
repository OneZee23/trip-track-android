// iOS: SettingsSyncPayload.swift
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

// iOS: SettingsSyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SettingsSyncPayload(
    val id: UUID,
    val avatarEmoji: String,
    val themeMode: String,
    val language: String,
    val distanceUnit: String,
    val volumeUnit: String,
    val fuelConsumption: Double,
    val fuelPrice: Double,
    val fuelCurrency: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val selectedVehicleId: UUID? = null,
    val profileLevel: Int,
    val profileXp: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lastTripDate: Instant? = null,
    val conflictVersion: Int,
    val lastModifiedAt: Instant,
)
