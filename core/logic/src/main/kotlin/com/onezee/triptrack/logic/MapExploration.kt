package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.RegionAtlas.City
import java.time.Instant
import java.util.UUID
import kotlin.math.*

// iOS: MapExploration.swift
val MapExploration.isEmpty: Boolean
    get() = trips.isEmpty()
val MapExploration.tripCount: Int
    get() = trips.size
val MapExploration.regionCount: Int
    get() = regions.size
val MapExploration.countryCodes: List<String>
    get() =
        regions
            .groupBy { it.countryCode }
            .entries
            .sortedByDescending { (_, regions) -> regions.sumOf { it.km } }
            .map { it.key }

fun MapExploration.region(id: String): MapRegionStat? = regions.firstOrNull { it.id == id }

fun MapExploration.trip(id: UUID): MapTripPin? = trips.firstOrNull { it.id == id }

val MapRegionStat.tripCount: Int
    get() = tripIds.size
val MapRegionStat.visitedCityCount: Int
    get() = cities.size

fun MapRegionStat.localizedName(language: String): String = if (language == "ru") nameRu else nameEn

val MapCityStat.id: String
    get() = name

fun MapCityStat.localizedName(language: String): String = if (language == "ru") name else nameEn

val MapExploration.Companion.km2PerTile: Double
    get() = 0.72
val MapExploration.Companion.regionTileTarget: Int
    get() = 5_000

fun MapExploration.Companion.build(
    trips: List<Trip>,
    visitedHashes: Set<String>,
    atlas: RegionAtlas,
    openedKmByRegion: Map<String, Double> = emptyMap(),
): MapExploration {
    val metresByRegion = mutableMapOf<Int, Double>()
    val tripIdsByRegion = mutableMapOf<Int, MutableList<UUID>>()
    val firstVisits = mutableMapOf<Int, Instant>()
    val pins = mutableListOf<MapTripPin>()
    val metresHere = mutableMapOf<Int, Double>()
    var totalKm = 0.0
    for (trip in trips) {
        val coords = trip.previewCoordinates
        if (coords.size < 2) continue
        var homeRegion: Int? = null
        var lastIndex: Int? = null
        var pathMetres = 0.0
        metresHere.clear()
        for ((a, b) in coords.zipWithNext()) {
            val metres = GeometryUtils.haversineDistance(a, b)
            if (!(metres < 20_000)) {
                lastIndex = null
                continue
            }
            val mid = GeoPoint((a.latitude + b.latitude) / 2, (a.longitude + b.longitude) / 2)
            val index = lastIndex?.takeIf { atlas.regionAtIndex(it, mid) } ?: atlas.regionIndex(mid)
            lastIndex = index
            if (index == null) continue
            pathMetres += metres
            metresHere[index] = (metresHere[index] ?: 0.0) + metres
            if (homeRegion == null) homeRegion = index
        }
        if (pathMetres > 0)
            for ((index, metres) in metresHere) {
                metresByRegion[index] =
                    (metresByRegion[index] ?: 0.0) + trip.distance * (metres / pathMetres)
                tripIdsByRegion.getOrPut(index) { mutableListOf() }.add(trip.id)
                firstVisits[index] = minOf(firstVisits[index] ?: trip.startDate, trip.startDate)
            }
        totalKm += trip.distance / 1000
        pins +=
            MapTripPin(
                trip.id,
                coords[coords.size / 2],
                coords,
                trip.title,
                trip.startDate,
                trip.distance,
                trip.duration,
                trip.averageSpeed,
                trip.maxSpeed,
                trip.photos.firstOrNull()?.filename,
                homeRegion?.let { atlas.regions[it].id },
            )
    }
    val tilesByRegion = mutableMapOf<Int, Int>()
    val tileCells = mutableMapOf<Int, MutableList<GeoPoint>>()
    var lastIndex: Int? = null
    for (hash in visitedHashes.sorted()) {
        val coord = GeohashEncoder.centerCoordinate(hash)
        tileCells.getOrPut(mapCellKey(coord)) { mutableListOf() }.add(coord)
        val index = lastIndex?.takeIf { atlas.regionAtIndex(it, coord) } ?: atlas.regionIndex(coord)
        lastIndex = index
        if (index != null) tilesByRegion[index] = (tilesByRegion[index] ?: 0) + 1
    }
    val regions =
        metresByRegion
            .map { (index, metres) ->
                val region = atlas.regions[index]
                val all = atlas.cities(region.id)
                val visited =
                    all.mapNotNull { city ->
                            val coverage = cityCoverage(city, tileCells)
                            if (coverage > 0)
                                MapCityStat(city.name, city.nameEn, city.coordinate, coverage)
                            else null
                        }
                        .sortedByDescending { it.coverage }
                MapRegionStat(
                    region.id,
                    region.countryCode,
                    region.nameRu,
                    region.nameEn,
                    metres / 1000,
                    tripIdsByRegion[index].orEmpty(),
                    visited,
                    all.size,
                    tilesByRegion[index] ?: 0,
                    firstVisits[index],
                    openedKmByRegion[region.id] ?: 0.0,
                    region.center,
                    region.bounds,
                )
            }
            .sortedByDescending { it.km }
    return MapExploration(regions, pins, totalKm)
}

private const val mapCellSize = 0.25

private fun mapCellKey(coord: GeoPoint): Int =
    floor(coord.latitude / mapCellSize).toInt() * 10_000 +
        floor(coord.longitude / mapCellSize).toInt()

private fun cityCoverage(city: City, tileCells: Map<Int, List<GeoPoint>>): Double {
    val radius = city.radiusMeters
    val span = ceil((radius / 111_000) / mapCellSize).toInt() + 1
    val baseLat = floor(city.coordinate.latitude / mapCellSize).toInt()
    val baseLon = floor(city.coordinate.longitude / mapCellSize).toInt()
    var hits = 0
    for (dLat in -span..span) for (dLon in -span..span) {
        for (coord in tileCells[(baseLat + dLat) * 10_000 + baseLon + dLon].orEmpty()) {
            if (GeometryUtils.haversineDistance(coord, city.coordinate) <= radius) hits++
        }
    }
    if (hits == 0) return 0.0
    val discTiles = PI * (radius / 1000).pow(2) / MapExploration.km2PerTile
    return min(1.0, hits / max(discTiles, 1.0))
}
