// iOS: AtlasPresentation.swift (AtlasPreviewLayer). Temporary layers never touch persistent cells.
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.ZoneId
import java.util.UUID

// iOS: AtlasPresentation.swift.
object AtlasPreviewLayer {
    fun build(trips: List<Trip>, atlas: RegionAtlas?): RevealedLayer {
        val claimed = mutableMapOf<String, MutableSet<RevealGrid.Cell>>()
        val runs = mutableListOf<List<GeoPoint>>()
        for (trip in trips.sortedWith(compareBy<Trip> { it.startDate }.thenBy { it.id.toString() })) {
            if (trip.previewPolyline?.isNotEmpty() != true) continue
            val coordinates = trip.previewCoordinates
            if (coordinates.size < 2) continue
            for ((key, patch) in RevealBuilder.patches(coordinates) { claimed[it].orEmpty() }) {
                claimed.getOrPut(key) { mutableSetOf() }.addAll(patch.cells)
                runs.addAll(patch.runs)
            }
        }
        return RevealedLayer.build(runs, claimed.values.sumOf { it.size }, atlas)
    }
}

// iOS: VehicleYearDistance.swift.
fun vehicleYearDistances(trips: List<Trip>, vehicleId: UUID, zone: ZoneId): List<VehicleYearDistance> =
    trips.filter { it.vehicleId == vehicleId && !it.isTransfer && it.confirmation == TripConfirmation.confirmed }
        .groupBy { it.startDate.atZone(zone).year }.toSortedMap()
        .map { (year, trips) -> VehicleYearDistance(year, trips.sumOf { it.scoringKm }) }
