// iOS: AtlasPresentation.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import java.time.Instant
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: AtlasPresentation.swift
sealed interface AtlasPeriod {
    data object AllTime : AtlasPeriod
    data object ThisYear : AtlasPeriod
    data object Last30Days : AtlasPeriod
    data class Custom(val start: Instant, val end: Instant) : AtlasPeriod
}

// iOS: AtlasPresentation.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class AtlasMapAppearance(
    @Required
    var style: Style = Style.fog,
    @Required
    var showsPhotos: Boolean = true,
) {

    // iOS: AtlasPresentation.swift
    @Serializable
    enum class Style(val rawValue: String) {
        @SerialName("fog")
        fog("fog"),
        @SerialName("night")
        night("night"),
        @SerialName("cells")
        cells("cells");

        companion object {
            fun fromRawValue(value: String): Style? = entries.find { it.rawValue == value }
        }
    }
}

// iOS: AtlasPresentation.swift
data class AtlasOverviewIndex(
    val countries: List<Country>,
    val tripsByDate: List<MapTripPin>,
    private val russianCities: List<City>,
    private val internationalCities: List<City>,
    private val catalogCities: List<City>,
    private val catalogRegions: List<SearchRegion>,
) {
    // iOS: AtlasPresentation.swift
    data class City(
        val id: String,
        val regionID: String,
        val stat: MapCityStat,
        val wasVisited: Boolean,
    )

    // iOS: AtlasPresentation.swift
    data class SearchRegion(
        val region: RegionAtlas.Region,
        val visited: MapRegionStat?,
        val wasVisited: Boolean,
    )

    // iOS: AtlasPresentation.swift
    data class Country(
        val id: String,
        val regions: List<MapRegionStat>,
        val drivenKm: Double,
    )

    // iOS: AtlasPresentation.swift
    data class SearchResults(
        val countries: List<Country>,
        val cities: List<City>,
        val regions: List<SearchRegion>,
    )
}
