// iOS: GamificationModels.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: GamificationModels.swift
enum class VolumeUnit(val rawValue: String) {
    liters("liters"),
    gallons("gallons");

    companion object {
        fun fromRawValue(value: String): VolumeUnit? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
enum class FuelCurrency(val rawValue: String) {
    rub("₽"),
    usd("$"),
    eur("€"),
    kzt("₸"),
    gel("₾"),
    tryLira("₺"),
    cny("¥"),
    byn("Br"),
    gbp("£"),
    uah("₴"),
    inr("₹"),
    brl("R$");

    companion object {
        const val storageKey = "fuelCurrency"
        const val defaultSymbol = "€"

        fun fromRawValue(value: String): FuelCurrency? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
enum class DriverRank(val rawValue: String) {
    novice("novice"),
    driver("driver"),
    traveler("traveler"),
    explorer("explorer"),
    navigator("navigator"),
    trucker("trucker"),
    legend("legend"),
    pioneer("pioneer"),
    nomad("nomad"),
    conqueror("conqueror"),
    cartographer("cartographer"),
    odysseus("odysseus"),
    roadSpirit("roadSpirit"),
    eternal("eternal");

    companion object {
        fun fromRawValue(value: String): DriverRank? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
@Serializable
enum class VehicleSticker(val rawValue: String) {
    @SerialName("flag100km")
    flag100km("flag100km"),
    @SerialName("route500km")
    route500km("route500km"),
    @SerialName("bronzeFrame")
    bronzeFrame("bronzeFrame"),
    @SerialName("silverFrame")
    silverFrame("silverFrame"),
    @SerialName("goldenFrame")
    goldenFrame("goldenFrame"),
    @SerialName("regionMap")
    regionMap("regionMap"),
    @SerialName("platinumFrame")
    platinumFrame("platinumFrame"),
    @SerialName("mountain")
    mountain("mountain"),
    @SerialName("night")
    night("night"),
    @SerialName("winter")
    winter("winter");

    companion object {
        fun fromRawValue(value: String): VehicleSticker? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
@Serializable
enum class RoadRarity(val rawValue: String) {
    @SerialName("common")
    common("common"),
    @SerialName("uncommon")
    uncommon("uncommon"),
    @SerialName("rare")
    rare("rare"),
    @SerialName("epic")
    epic("epic"),
    @SerialName("legendary")
    legendary("legendary");

    companion object {
        fun fromRawValue(value: String): RoadRarity? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
enum class RoadLevel(val rawValue: Int) {
    discovered(1),
    bronze(2),
    silver(3),
    gold(4),
    mastered(5);

    companion object {
        fun fromRawValue(value: Int): RoadLevel? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
data class XPBreakdown(
    var base: Int = 0,
    var newRegionBonus: Int = 0,
    var longTripBonus: Int = 0,
    var firstTripOfDay: Int = 0,
    var regionDiscovery: Int = 0,
)

// iOS: GamificationModels.swift
data class TripCompletionData(
    val xpEarned: Int,
    val xpBreakdown: XPBreakdown,
    val previousLevel: Int,
    val newLevel: Int,
    val previousXP: Int,
    val newXP: Int,
    val previousRank: DriverRank,
    val newRank: DriverRank,
    val vehicleOdometerBefore: Double,
    val vehicleOdometerAfter: Double,
    val vehicleLevelBefore: Int,
    val vehicleLevelAfter: Int,
    val newBadges: List<Badge>,
    val repeatedBadgeCounts: Map<String, Int>,
    val currentStreak: Int,
    var roadCard: RoadCompletionInfo? = null,
    var discoveries: TripDiscoveries? = null,
)

// iOS: GamificationModels.swift
data class RoadCompletionInfo(
    val name: String,
    val rarity: RoadRarity,
    val level: RoadLevel,
    val timesDriven: Int,
    val isNew: Boolean,
)

// iOS: GamificationModels.swift
data class RoadCard(
    val id: UUID,
    var name: String,
    var rarity: RoadRarity,
    var level: RoadLevel,
    var timesDriven: Int,
    var distanceKm: Double,
    var geohashSequence: List<String>,
    var firstDriven: Instant,
    var lastDriven: Instant,
)

// iOS: GamificationModels.swift
enum class ZoneStatus(val rawValue: String) {
    undiscovered("undiscovered"),
    discovered("discovered"),
    explored("explored"),
    mapped("mapped"),
    conquered("conquered"),
    mastered("mastered");

    companion object {
        fun fromRawValue(value: String): ZoneStatus? = entries.find { it.rawValue == value }
    }
}

// iOS: GamificationModels.swift
data class ZoneCard(
    val id: String,
    var name: String,
    var tileCount: Int,
    var totalTiles: Int,
    var status: ZoneStatus,
    var firstVisited: Instant? = null,
)

// iOS: GamificationModels.swift
data class TerritoryCard(
    val id: String,
    var name: String,
    var zones: List<ZoneCard>,
)
