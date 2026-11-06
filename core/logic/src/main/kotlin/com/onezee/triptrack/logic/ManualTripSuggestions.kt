// iOS: ManualTripSuggestions.swift, ManualTripFrequentPlaces.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import java.time.Instant

data class ManualTripSuggestionPoint(val name: String, val point: GeoPoint, val locality: String? = null)
data class ManualTripFrequentPlace<T>(val value: T, val passCount: Int, val createdAt: Instant)

object ManualTripSuggestions {
    const val SAME_SPOT_METRES = 150.0
    const val LIMIT = 5

    fun <T> topFrequentPlaces(places: List<ManualTripFrequentPlace<T>>, limit: Int = 3): List<T> =
        places.asSequence()
            .filter { it.passCount > 0 }
            .sortedWith(compareByDescending<ManualTripFrequentPlace<T>> { it.passCount }.thenByDescending { it.createdAt })
            .take(limit.coerceAtLeast(0))
            .map { it.value }
            .toList()

    fun build(
        home: ManualTripSuggestionPoint?,
        places: List<ManualTripSuggestionPoint>,
        recent: List<ManualTripSuggestionPoint>,
        excluding: GeoPoint? = null,
        limit: Int = LIMIT,
    ): List<ManualTripSuggestionPoint> {
        val result = mutableListOf<ManualTripSuggestionPoint>()
        fun add(candidate: ManualTripSuggestionPoint) {
            if (candidate.name.isBlank()) return
            if (excluding?.distanceFrom(candidate.point)?.let { it < SAME_SPOT_METRES } == true) return
            if (result.none { it.point.distanceFrom(candidate.point) < SAME_SPOT_METRES }) result += candidate
        }
        home?.let(::add)
        places.forEach(::add)
        recent.forEach(::add)
        return result.take(limit.coerceAtLeast(0))
    }
}
