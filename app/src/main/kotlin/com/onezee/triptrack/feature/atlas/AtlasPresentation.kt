// iOS: AtlasPresentation.swift, JournalBuilder.swift
package com.onezee.triptrack.feature.atlas

import com.onezee.triptrack.logic.*
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.model.*
import java.text.Normalizer
import java.util.Locale

// iOS: AtlasPresentation.swift (AtlasOverviewIndex).
data class AtlasOverview(
    val countries: List<AtlasOverviewIndex.Country> = emptyList(),
    val tripsByDate: List<MapTripPin> = emptyList(),
    val cities: List<AtlasOverviewIndex.City> = emptyList(),
    val catalogCities: List<AtlasOverviewIndex.City> = emptyList(),
    val catalogRegions: List<AtlasOverviewIndex.SearchRegion> = emptyList(),
) {
    fun search(query: String, language: String): AtlasOverviewIndex.SearchResults {
        val term = query.trim()
        if (term.isEmpty()) return AtlasOverviewIndex.SearchResults(countries, sortedCities(language), emptyList())
        fun fold(text: String) = Normalizer.normalize(text.lowercase(Locale.forLanguageTag(language)), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val needle = fold(term)
        return AtlasOverviewIndex.SearchResults(emptyList(), catalogCities.filter {
            needle in fold(it.stat.localizedName(language))
        }.sortedWith(compareByDescending<AtlasOverviewIndex.City> { it.stat.coverage > 0 }
            .thenBy { it.stat.localizedName(language) }.thenBy { it.id }), catalogRegions.filter {
            needle in fold(if (language == "ru") it.region.nameRu else it.region.nameEn)
        }.sortedWith(compareByDescending<AtlasOverviewIndex.SearchRegion> { it.visited != null }
            .thenBy { if (language == "ru") it.region.nameRu else it.region.nameEn }.thenBy { it.region.id }))
    }
    fun sortedCities(language: String) = cities.sortedWith(compareBy<AtlasOverviewIndex.City> { it.stat.localizedName(language) }.thenBy { it.id })
    companion object {
        fun build(exploration: MapExploration, atlas: RegionAtlas, historical: MapExploration): AtlasOverview {
            fun id(region: String, city: MapCityStat) = "$region:${city.name}:${city.coordinate.latitude}:${city.coordinate.longitude}"
            val historicalCities = historical.regions.flatMap { region -> region.cities.filter { it.coverage > 0 }.map { id(region.id, it) } }.toSet()
            val current = exploration.regions.flatMap { region -> region.cities.filter { it.coverage > 0 }.map {
                AtlasOverviewIndex.City(id(region.id, it), region.id, it, true)
            } }.associateBy { it.id }
            val allCities = current.toMutableMap()
            for (city in atlas.citiesByRegion.values.flatten()) {
                val stat = MapCityStat(city.name, city.nameEn, city.coordinate, 0.0)
                val key = id(city.regionId, stat)
                allCities.putIfAbsent(key, AtlasOverviewIndex.City(key, city.regionId, stat, key in historicalCities))
            }
            val regions = atlas.regions.associateBy { it.id }.toMutableMap()
            for (region in exploration.regions) regions.putIfAbsent(region.id,
                com.onezee.triptrack.model.RegionAtlas.Region(region.id, region.countryCode, region.nameRu, region.nameEn, region.center, region.bounds, emptyList()))
            val historicalIds = historical.regions.map { it.id }.toSet()
            return AtlasOverview(
                exploration.regions.groupBy { it.countryCode }.map { (code, values) -> AtlasOverviewIndex.Country(code, values, values.sumOf { it.km }) }
                    .sortedWith(compareByDescending<AtlasOverviewIndex.Country> { it.drivenKm }.thenBy { it.id }),
                exploration.trips.sortedWith(compareByDescending<MapTripPin> { it.startDate }.thenBy { it.id.toString() }),
                current.values.toList(), allCities.values.toList(), regions.values.map {
                    AtlasOverviewIndex.SearchRegion(it, exploration.region(it.id), it.id in historicalIds)
                },
            )
        }
    }
}

// iOS: RiddleHint.swift. Discovery owner supplies planned hints, never GPS or a matcher.
data class AtlasRiddleHint(val id: String, val type: RiddleType, val centre: GeoPoint, val radiusMetres: Double)

// iOS: JournalBuilder.swift (Journal.NearbyRiddle).
data class NearbyRiddle(val id: String, val type: RiddleType, val metresToEdge: Double)

// iOS: JournalBuilder.swift (Journal).
data class AtlasJournal(
    val regions: List<MapRegionStat> = emptyList(),
    val finds: List<Discovery> = emptyList(),
    val riddles: List<NearbyRiddle> = emptyList(),
) {
    companion object {
        fun build(exploration: MapExploration, revealed: RevealedLayer, seals: List<Discovery>, hints: List<AtlasRiddleHint>) = AtlasJournal(
            exploration.regions,
            seals.sortedWith(compareByDescending<Discovery> { it.foundAt }.thenBy { it.id.toString() }),
            hints.map { hint -> NearbyRiddle(hint.id, hint.type,
                ((revealed.regionCentroids.values.minOfOrNull { GeometryUtils.haversineDistance(hint.centre, it) } ?: 0.0) - hint.radiusMetres).coerceAtLeast(0.0)) },
        )
    }
}
