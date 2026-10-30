// iOS: MapExploration.swift
package com.onezee.triptrack.model

import java.time.Instant
import java.util.UUID

// iOS: MapExploration.swift
data class MapExploration(
    var regions: List<MapRegionStat> = emptyList(),
    var trips: List<MapTripPin> = emptyList(),
    var totalKm: Double = 0.0,
)

// iOS: MapExploration.swift
data class MapRegionStat(
    val id: String,
    val countryCode: String,
    val nameRu: String,
    val nameEn: String,
    val km: Double,
    val tripIds: List<UUID>,
    val cities: List<MapCityStat>,
    val totalCities: Int,
    val openedTiles: Int,
    val firstVisited: Instant?,
    val openedKm: Double,
    val center: GeoPoint,
    val bounds: GeoBounds,
)

// iOS: MapExploration.swift
data class MapCityStat(
    val name: String,
    val nameEn: String,
    val coordinate: GeoPoint,
    val coverage: Double,
)

// iOS: MapExploration.swift
data class MapTripPin(
    val id: UUID,
    val coordinate: GeoPoint,
    val route: List<GeoPoint>,
    val title: String?,
    val startDate: Instant,
    val distance: Double,
    val duration: Double,
    val avgSpeedMS: Double,
    val maxSpeedMS: Double,
    val photoFilename: String?,
    val regionId: String?,
)
