// iOS: Discovery.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(GeoPointSerializer::class, UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.DiscoverySerializer
import com.onezee.triptrack.model.serialization.GeoPointSerializer
import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Required
import kotlinx.serialization.UseSerializers

// iOS: Discovery.swift
@Serializable
enum class DiscoveryKind(val rawValue: String) {
    @SerialName("secret")
    secret("secret"),
    @SerialName("riddle")
    riddle("riddle"),
    @SerialName("milestone")
    milestone("milestone");

    companion object {
        fun fromRawValue(value: String): DiscoveryKind? = entries.find { it.rawValue == value }
    }
}

// iOS: Discovery.swift
@Serializable
enum class SealSymbol(val rawValue: String) {
    @SerialName("mountain.2")
    pass("mountain.2"),
    @SerialName("light.beacon.max")
    lighthouse("light.beacon.max"),
    @SerialName("flag.2.crossed")
    border("flag.2.crossed"),
    @SerialName("ferry")
    ferry("ferry"),
    @SerialName("water.waves")
    dam("water.waves"),
    @SerialName("road.lanes")
    bridge("road.lanes"),
    @SerialName("binoculars")
    viewpoint("binoculars"),
    @SerialName("telescope")
    observatory("telescope"),
    @SerialName("water.waves.and.arrow.down")
    seaRoad("water.waves.and.arrow.down"),
    @SerialName("arrow.up.left.and.arrow.down.right")
    extreme("arrow.up.left.and.arrow.down.right"),
    @SerialName("scope")
    centre("scope"),
    @SerialName("triangle")
    tripoint("triangle"),
    @SerialName("map")
    region("map"),
    @SerialName("mountain.2.fill")
    altitude("mountain.2.fill"),
    @SerialName("moon.stars")
    night("moon.stars"),
    @SerialName("globe")
    country("globe"),
    @SerialName("seal")
    generic("seal"),
    @SerialName("seal.komsomolsky")
    komsomolsky("seal.komsomolsky");

    companion object {
        fun fromRawValue(value: String): SealSymbol? = entries.find { it.rawValue == value }
    }
}

// iOS: Discovery.swift
@Serializable
enum class Milestone(val rawValue: String) {
    @SerialName("firstRegion")
    firstRegion("firstRegion"),
    @SerialName("easternmost")
    easternmost("easternmost"),
    @SerialName("westernmost")
    westernmost("westernmost"),
    @SerialName("northernmost")
    northernmost("northernmost"),
    @SerialName("southernmost")
    southernmost("southernmost"),
    @SerialName("above2000")
    above2000("above2000"),
    @SerialName("belowSea")
    belowSea("belowSea"),
    @SerialName("threeRegionsDay")
    threeRegionsDay("threeRegionsDay"),
    @SerialName("countryBorder")
    countryBorder("countryBorder"),
    @SerialName("nightPass")
    nightPass("nightPass");

    companion object {
        fun fromRawValue(value: String): Milestone? = entries.find { it.rawValue == value }
    }
}

// iOS: Discovery.swift
@KeepGeneratedSerializer
@Serializable(with = DiscoverySerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class Discovery(
    val kind: DiscoveryKind,
    val key: String,
    val tripId: UUID,
    val coordinate: GeoPoint,
    val foundAt: Instant,
    val symbol: SealSymbol,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val story: String? = null,
    @EncodeDefault
    val verified: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val finders: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val firstFinderName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val firstFinderAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rarity: String? = null,
) {
    @Required
    val id: UUID = id(kind, key)

    companion object {
        val namespace: UUID = UUID.fromString("5a3c8b2e-7f10-4c6e-9d21-0e6b3a9f4c11")
        fun id(kind: DiscoveryKind, key: String): UUID = uuidV5(namespace, "${kind.rawValue}:$key")
    }
}
