// iOS: PlaceManager.swift (createPlace, matchAllTrips)
package com.onezee.triptrack.feature.places

import com.onezee.triptrack.data.PlaceManager
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.PlaceSuggestion

/** The Places tab and debug seed share the recording manager's history path. */
class PlaceHistory(private val manager: PlaceManager) : PlaceCreator {
    override suspend fun create(suggestion: PlaceSuggestion) {
        createPlace(suggestion.cell, GeoPoint(suggestion.latitude, suggestion.longitude), suggestion.name)
    }

    suspend fun settle() = manager.settle()

    suspend fun createPlace(cell: String, coordinate: GeoPoint, name: String?) =
        manager.createPlace(cell, coordinate, name)
}
