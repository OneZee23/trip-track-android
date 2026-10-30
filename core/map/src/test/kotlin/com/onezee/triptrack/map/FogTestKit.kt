package com.onezee.triptrack.map

import com.onezee.triptrack.logic.RevealBuilder
import com.onezee.triptrack.logic.RevealGrid
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.logic.RevealedTile
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant

/** Fixtures shared by the fog tests. */
object FogTestKit {
    /**
     * iOS: DebugMapSeed.runHangStressIfNeeded and FogMetalBudgetTests.seedStressLibrary: 400 diagonal
     * trips of 2,000 points (~7 km each), each starting 0.01° further, stored as Float32 previews.
     * Oldest first, as the reveal build reads them: trip 399 started earliest.
     */
    fun iosStressPreviews(trips: Int = 400, pointsPerTrip: Int = 2_000): List<List<GeoPoint>> =
        (trips - 1 downTo 0).map { t ->
            val latBase = 45.0 + t * 0.01
            val lonBase = 38.9 + t * 0.01
            val coords = List(pointsPerTrip) { p -> GeoPoint(latBase + p * 0.00003, lonBase + p * 0.00002) }
            Trip.decodePolyline(Trip.encodePolyline(coords))
        }

    /**
     * The revealed world as the iPhone's RevealedLayerStore builds and reads it: patches against
     * everything claimed before, merged per tile, runs stored as Float32 (RevealedTile.encodeRuns),
     * then RevealedLayer.build from the tiles. The budget numbers of the iPhone docs come from this
     * path, so the Android numbers are compared on it too.
     */
    fun storedLayer(previews: List<List<GeoPoint>>): RevealedLayer {
        val cells = LinkedHashMap<String, MutableSet<RevealGrid.Cell>>()
        val runs = LinkedHashMap<String, MutableList<List<GeoPoint>>>()
        for (coords in previews) {
            val patches = RevealBuilder.patches(coords) { key -> cells[key] ?: emptySet() }
            for ((key, patch) in patches) {
                cells.getOrPut(key) { HashSet() }.addAll(patch.cells)
                runs.getOrPut(key) { mutableListOf() }.addAll(patch.runs)
            }
        }
        val stamp = Instant.ofEpochSecond(1_760_000_000)
        val tiles = cells.keys.map { key ->
            RevealedTile(key, RevealGrid.encode(cells.getValue(key).toList(), key), RevealedTile.encodeRuns(runs[key].orEmpty()), stamp)
        }
        return RevealedLayer.build(tiles, null)
    }

    /** A layer with the same polylines on all three levels, so the chosen level cannot hide a line. */
    fun layer(fine: List<List<GeoPoint>>, mid: List<List<GeoPoint>> = fine, far: List<List<GeoPoint>> = fine) =
        RevealedLayer(fine, mid, far, cellCount = fine.size, openedKm = 0.0, regionIds = emptySet())
}
