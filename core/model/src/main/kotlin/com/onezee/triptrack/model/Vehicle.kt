// iOS: Vehicle.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import com.onezee.triptrack.model.serialization.VehicleSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Vehicle.swift
@KeepGeneratedSerializer
@Serializable(with = VehicleSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class Vehicle(
    @Required
    val id: UUID = UUID.randomUUID(),
    @Required
    var name: String = "",
    @Required
    var avatarEmoji: String = "🏎️",
    @EncodeDefault
    var avatarStyle: String = "car",
    @EncodeDefault
    var type: VehicleType = VehicleType.car,
    @EncodeDefault
    var plate: String = "",
    @EncodeDefault
    var plateVisible: Boolean = false,
    @EncodeDefault
    var visibleToOthers: Boolean = true,
    // Kilometres, including manual trips.
    @Required
    var odometerKm: Double = 0.0,
    // Kilometres from the dashboard; null means never entered.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var manualOdometerKm: Double? = null,
    // Kilometres from recorded trips only; a locally derived snapshot.
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var rewardOdometerKm: Double? = null,
    @Required
    var level: Int = 1,
    @EncodeDefault
    var stickers: List<VehicleSticker> = emptyList(),
    @Required
    var createdAt: Instant = Instant.now(),
    // Litres per 100 kilometres.
    @Required
    var cityConsumption: Double = 10.0,
    // Litres per 100 kilometres.
    @Required
    var highwayConsumption: Double = 6.0,
    @Required
    var fuelPrice: Double = 56.0,
    @EncodeDefault
    var fuelCurrency: String = "€",
    @EncodeDefault
    var dashboardUnits: DashboardUnits = DashboardUnits.app,
    @EncodeDefault
    var powertrain: Powertrain = Powertrain.fuel,
    // kWh per 100 kilometres.
    @EncodeDefault
    var electricConsumption: Double = 0.0,
    @EncodeDefault
    var electricityPrice: Double = 0.0,
    // Kilometres.
    @EncodeDefault
    var electricRangeKm: Double = 0.0,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var cardStyle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var drive2URL: String? = null,
    @EncodeDefault
    var modTags: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var paintFinish: String? = null,
    @EncodeDefault
    var mods: List<VehicleMod> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogModelId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogGenerationId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var catalogVariantId: String? = null,
    @EncodeDefault
    var about: String = "",
    @EncodeDefault
    var make: String = "",
    @EncodeDefault
    var model: String = "",
    @EncodeDefault
    var year: Int = 0,
    @EncodeDefault
    var bodyType: String = "",
    @EncodeDefault
    var photosVisible: Boolean = false,
    @EncodeDefault
    var mapVisible: Boolean = true,
    @EncodeDefault
    var isArchived: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var soldAt: Instant? = null,
)
