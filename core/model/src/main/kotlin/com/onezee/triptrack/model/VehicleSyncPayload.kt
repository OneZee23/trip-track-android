// iOS: VehicleSyncPayload.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import com.onezee.triptrack.model.serialization.VehicleSyncPayloadSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers

// iOS: VehicleSyncPayload.swift
@KeepGeneratedSerializer
@Serializable(with = VehicleSyncPayloadSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class VehicleSyncPayload(
    val id: UUID,
    val name: String,
    val avatarEmoji: String,
    val odometerKm: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var manualOdometerKm: Double? = null,
    @Transient
    var manualOdometerKnown: Boolean = false,
    @Transient
    var soldAtKnown: Boolean = false,
    val level: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stickersJson: String? = null,
    val cityConsumption: Double,
    val highwayConsumption: Double,
    val fuelPrice: Double,
    val conflictVersion: Int,
    val lastModifiedAt: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vehicleType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarStyle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val plate: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val plateVisible: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val visibleToOthers: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val fuelCurrency: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val dashboardUnits: DashboardUnits? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cardStyle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var drive2Url: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var modTags: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var paintFinish: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var mods: List<VehicleMod>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogModelId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogGenerationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogVariantId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val powertrain: Powertrain? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val electricConsumption: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val electricityPrice: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val electricRangeKm: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val about: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val make: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val model: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val year: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bodyType: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val mapVisible: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val photosVisible: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isArchived: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val soldAt: Instant? = null,
)
