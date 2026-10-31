// iOS: PlaceManager.swift (createPlace, matchAllTrips)
package com.onezee.triptrack.feature.places

import com.onezee.triptrack.data.PlaceStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.logic.PlaceMatcher
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.withRecordingBreaks
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlaceSuggestion
import com.onezee.triptrack.model.Trip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * iOS: PlaceManager.createPlace. A place made by hand (a suggestion saved from the Places tab, the
 * debug seed) gets its history at once: every trip whose PREVIEW comes near is matched on its track,
 * and only those. Saving through here, not through the store, is what keeps «Здесь N раз» true from
 * the first frame of the new place.
 */
class PlaceHistory(
    private val places: PlaceStore,
    private val repository: TripRepository,
    private val worker: CoroutineDispatcher,
) : PlaceCreator {
    override suspend fun create(suggestion: PlaceSuggestion) {
        createPlace(suggestion.cell, GeoPoint(suggestion.latitude, suggestion.longitude), suggestion.name)
    }

    suspend fun createPlace(cell: String, coordinate: GeoPoint, name: String?): Place = withContext(worker) {
        val place = places.upsertPlace(cell, coordinate, name).place
        matchAllTrips(place)
        place
    }

    /** iOS matchAllTrips: the preview prefilters, the track points are read for candidates only. */
    suspend fun matchAllTrips(place: Place) = withContext(worker) {
        for (ref in repository.tripPreviews(needingPlaceMatch = false)) {
            val cells = PlaceMatcher.cells(ref.previewPolyline?.let { Trip.decodePolyline(it) }.orEmpty())
            if (!PlaceMatcher.isCandidate(place, cells)) continue
            val trip = repository.fetchTripDetail(ref.id)?.let { it.withRecordingBreaks(it.recordingBreaks) } ?: continue
            if (trip.trackPoints.size < 2) continue
            val passes = PlaceMatcher.passes(place, trip.id, trip.trackPoints, trip.startDate)
            if (passes.isNotEmpty()) places.replacePasses(place.id, trip.id, passes)
            yield()
        }
    }
}
