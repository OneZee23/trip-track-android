// iOS: RegionAtlas.swift
package com.onezee.triptrack.model

object RegionAtlas {
    // iOS: RegionAtlas.swift
    data class Region(
        val id: String,
        val countryCode: String,
        val nameRu: String,
        val nameEn: String,
        val center: GeoPoint,
        val bounds: GeoBounds,
        val rings: List<List<Double>>,
        var localNames: Map<String, String> = emptyMap(),
    )

    // iOS: RegionAtlas.swift
    data class MapCountry(
        val id: String,
        val nameRu: String,
        val nameEn: String,
        val center: GeoPoint,
        val bounds: GeoBounds,
    )

    // iOS: RegionAtlas.swift
    data class City(
        val name: String,
        val nameEn: String,
        val regionId: String,
        val coordinate: GeoPoint,
        val population: Int,
    )
}
