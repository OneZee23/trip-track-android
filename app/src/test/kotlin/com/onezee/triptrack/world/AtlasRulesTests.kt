// iOS: AtlasPlaceTapTests (the target), MapZoomLevel, Coordinator.trips(near:) and handleTap after the
// pins, syncTripPins, PlaceUsuallyLineTests.
package com.onezee.triptrack.world

import com.onezee.triptrack.feature.atlas.ui.AtlasMapTap
import com.onezee.triptrack.feature.atlas.ui.MapZoomLevel
import com.onezee.triptrack.feature.atlas.ui.RoadHits
import com.onezee.triptrack.feature.atlas.ui.RouteIndex
import com.onezee.triptrack.feature.atlas.ui.tripPhotoPins
import com.onezee.triptrack.map.MapTap
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.MapTripPin
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class AtlasRulesTests {
    private fun trip(vararg points: Pair<Double, Double>, id: UUID = UUID.randomUUID(), days: Long = 0, photo: String? = null) = MapTripPin(
        id, GeoPoint(points[0].first, points[0].second), points.map { GeoPoint(it.first, it.second) }, null,
        Instant.ofEpochSecond(1_760_000_000 - days * 86_400), 10_000.0, 1_200.0, 10.0, 20.0, photo, null,
    )

    @Test fun zoomLevelsByLatitudeSpan() {
        assertEquals(MapZoomLevel.far, MapZoomLevel.of(3.1))
        assertEquals(MapZoomLevel.region, MapZoomLevel.of(3.0))
        assertEquals(MapZoomLevel.region, MapZoomLevel.of(0.41))
        assertEquals(MapZoomLevel.close, MapZoomLevel.of(0.4))
        // Places appear as one comes closer: none on a country, all from the region level.
        assertFalse(MapZoomLevel.placesVisible(5.0))
        assertTrue(MapZoomLevel.placesVisible(1.0))
        assertTrue(MapZoomLevel.placesVisible(0.05))
    }

    /** 26 pt of reach, measured in map points at the tap's zoom; every road within it, nearest first. */
    @Test fun aRoadTapAnswersWithEveryTripOnItNearestFirst() {
        val near = trip(45.0300 to 38.9000, 45.0300 to 39.0000)
        val alsoNear = trip(45.0302 to 38.9000, 45.0302 to 39.0000)
        val far = trip(45.2000 to 38.9000, 45.2000 to 39.0000)
        val routes = RouteIndex.build(listOf(near, alsoNear, far))
        // Zoom 14 at 45°: about 6.6 m per point, so 26 pt reach ≈ 170 m; the tap is 11 m off the first road.
        val hits = RoadHits.trips(MapTap(GeoPoint(45.0301, 38.95), 14.0), routes)
        assertEquals(listOf(near.id, alsoNear.id), hits)
        // Far from every road nothing is hit.
        assertTrue(RoadHits.trips(MapTap(GeoPoint(45.1, 38.95), 14.0), routes).isEmpty())
    }

    @Test fun theReachShrinksWithZoomingIn() {
        val road = trip(45.0300 to 38.9000, 45.0300 to 39.0000)
        val routes = RouteIndex.build(listOf(road))
        // 300 m off the road: inside the reach at zoom 12 (≈ 26 × 26 m), outside at zoom 16 (≈ 26 × 1.7 m).
        val tap = GeoPoint(45.0300 + 300 / 111_320.0, 38.95)
        assertEquals(listOf(road.id), RoadHits.trips(MapTap(tap, 12.0), routes))
        assertTrue(RoadHits.trips(MapTap(tap, 16.0), routes).isEmpty())
    }

    /** From the country view a tap is a region and flies there; closer, a road wins, a miss stays put. */
    @Test fun aTapIsARoadUpCloseAndARegionFromAfar() {
        val road = trip(45.0300 to 38.9000, 45.0300 to 39.0000)
        val routes = RouteIndex.build(listOf(road))
        val onRoad = MapTap(GeoPoint(45.0300, 38.95), 13.0)
        assertEquals(AtlasMapTap.Road(listOf(road.id)), AtlasMapTap.resolve(onRoad, latitudeSpan = 0.2, routes = routes))
        // The same point from afar is the region behind it, and the camera goes there.
        val fromAfar = AtlasMapTap.resolve(onRoad, latitudeSpan = 8.0, routes = routes)
        assertIs<AtlasMapTap.Ground>(fromAfar)
        assertTrue(fromAfar.zoomToRegion)
        // A miss at street level is a miss: no flight out to the whole krai.
        val miss = AtlasMapTap.resolve(MapTap(GeoPoint(45.1, 38.95), 13.0), latitudeSpan = 0.2, routes = routes)
        assertIs<AtlasMapTap.Ground>(miss)
        assertFalse(miss.zoomToRegion)
    }

    /** «Фото из поездок»: a pin per trip with a photo; an open trip keeps only its own; off is none. */
    @Test fun tripPhotoPinsFollowTheToggleAndTheOpenTrip() {
        val withPhoto = trip(45.0 to 39.0, photo = "a.jpg")
        val alsoWithPhoto = trip(45.1 to 39.1, photo = "b.jpg")
        val withoutPhoto = trip(45.2 to 39.2)
        val trips = listOf(withPhoto, alsoWithPhoto, withoutPhoto)
        assertEquals(listOf(withPhoto, alsoWithPhoto), tripPhotoPins(trips, showsPhotos = true, openTrip = null))
        assertTrue(tripPhotoPins(trips, showsPhotos = false, openTrip = null).isEmpty())
        assertEquals(listOf(alsoWithPhoto), tripPhotoPins(trips, showsPhotos = true, openTrip = alsoWithPhoto.id))
        assertTrue(tripPhotoPins(trips, showsPhotos = true, openTrip = withoutPhoto.id).isEmpty(), "an open trip without a photo has no pin")
    }

    @Test fun routesWithOnePointAreNotIndexed() {
        assertTrue(RouteIndex.build(listOf(trip(45.0 to 39.0))).isEmpty())
    }
}
