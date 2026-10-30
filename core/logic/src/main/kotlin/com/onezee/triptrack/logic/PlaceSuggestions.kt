package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID

// iOS: PlaceSuggestions.swift
val PlaceSuggestion.id: UUID get() = Place.id(cell)
val PlaceSuggestion.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)

// iOS: PlaceSuggestions.swift
object PlaceSuggestions {
    const val limit = 5
    const val minTrips = 2
    const val smallLibrary = 5
    const val namePrecision = 5

    fun build(previews: List<TripPreviewRef>, taken: Set<UUID>): List<PlaceSuggestion> {
        val threshold = if (previews.size < smallLibrary) 1 else minTrips
        val buckets = mutableMapOf<String, Bucket>()
        for (ref in previews) {
            val coordinates = ref.previewCoordinates
            if (coordinates.isEmpty()) continue
            val counted = mutableSetOf<String>()
            for (end in listOf(coordinates.first(), coordinates.last())) {
                val cell = Place.cell(end.latitude, end.longitude)
                if (!counted.add(cell)) continue
                val bucket = buckets.getOrPut(cell) { Bucket() }
                bucket.latitude += end.latitude
                bucket.longitude += end.longitude
                bucket.trips++
                bucket.lastAt = maxOf(bucket.lastAt, ref.startDate)
            }
        }
        val seen = mutableSetOf<String>()
        return buckets.mapNotNull { (cell, bucket) ->
            if (bucket.trips < threshold || Place.id(cell) in taken) null
            else PlaceSuggestion(cell, bucket.latitude / bucket.trips, bucket.longitude / bucket.trips, bucket.trips, bucket.lastAt)
        }.sortedWith(compareByDescending<PlaceSuggestion> { it.trips }.thenByDescending { it.lastAt }.thenBy { it.cell })
            .filter { seen.add(GeohashEncoder.encode(it.latitude, it.longitude, namePrecision)) }.take(limit)
    }

    // iOS: PlaceSuggestions.swift (Bucket).
    private class Bucket(var latitude: Double = 0.0, var longitude: Double = 0.0, var trips: Int = 0,
        var lastAt: Instant = Instant.ofEpochSecond(-62_135_769_600))
}
