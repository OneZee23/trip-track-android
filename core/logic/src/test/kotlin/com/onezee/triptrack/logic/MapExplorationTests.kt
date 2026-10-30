package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*
import org.junit.Test

// iOS: MyMapAtlasTests.swift (exploration and revealed-layer cases).
class MapExplorationTests {
    private val atlas
        get() = RegionAtlas.shared

    private fun line(from: GeoPoint, to: GeoPoint, steps: Int): List<GeoPoint> =
        (0..steps).map { i ->
            val t = i.toDouble() / steps
            GeoPoint(
                from.latitude + (to.latitude - from.latitude) * t,
                from.longitude + (to.longitude - from.longitude) * t,
            )
        }

    private fun route(steps: Int = 40) =
        line(GeoPoint(45.035, 38.975), GeoPoint(44.630, 39.130), steps)

    private fun trip(
        route: List<GeoPoint>,
        start: Instant = Instant.ofEpochSecond(1_750_000_000),
    ): Trip =
        Trip(
            startDate = start,
            endDate = start.plusSeconds(route.size * 30L),
            distance = 50_000.0,
            trackPoints =
                route.mapIndexed { i, c ->
                    TrackPoint(
                        latitude = c.latitude,
                        longitude = c.longitude,
                        altitude = 0.0,
                        speed = 20.0,
                        timestamp = start.plusSeconds(i * 30L),
                    )
                },
            previewPolyline = Trip.encodePolyline(route),
        )

    private fun hashes(route: List<GeoPoint>) =
        route.map { GeohashEncoder.encode(it.latitude, it.longitude, 6) }.toSet()

    @Test
    fun testTripKilometresLandInTheRegionTheyWereDrivenIn() {
        val route = route()
        val exploration = MapExploration.build(listOf(trip(route)), hashes(route), atlas)
        assertEquals(2, exploration.regionCount)
        val krai = assertNotNull(exploration.region("RU-KDA"))
        assertEquals(1, krai.tripCount)
        assertNotNull(exploration.region("RU-AD"))
        assertEquals(50.0, exploration.totalKm, 8.0)
        assertTrue(krai.km > 0)
        assertEquals(1, exploration.tripCount)
        assertFalse(exploration.isEmpty)
    }

    @Test
    fun testCityIsOpenedWhenYouDroveThroughIt() {
        val route = line(GeoPoint(45.010, 38.950), GeoPoint(45.060, 39.010), 60)
        val exploration = MapExploration.build(listOf(trip(route)), hashes(route), atlas)
        val city =
            assertNotNull(
                exploration.region("RU-KDA")?.cities?.firstOrNull { it.name == "Краснодар" }
            )
        assertTrue(city.coverage > 0)
        assertTrue(city.coverage <= 1)
        assertTrue(city.coverage < 0.5)
    }

    @Test
    fun testRegionKeepsTheEarliestTripDate() {
        val route = route()
        val earlier = Instant.ofEpochSecond(1_700_000_000)
        val later = Instant.ofEpochSecond(1_780_000_000)
        val exploration =
            MapExploration.build(
                listOf(trip(route, later), trip(route, earlier)),
                hashes(route),
                atlas,
            )
        assertEquals(earlier, exploration.region("RU-KDA")?.firstVisited)
    }

    @Test
    fun testTeleportSegmentsAreNotCounted() {
        val jump = listOf(GeoPoint(45.035, 38.975), GeoPoint(55.755, 37.617))
        assertTrue(MapExploration.build(listOf(trip(jump)), emptySet(), atlas).regions.isEmpty())
    }

    @Test
    fun testEmptyInputProducesEmptyExploration() {
        val exploration = MapExploration.build(emptyList(), emptySet(), atlas)
        assertTrue(exploration.isEmpty)
        assertEquals(0, exploration.regionCount)
        assertEquals(0.0, exploration.totalKm)
    }

    @Test
    fun testExplorationNeedsNoTrackPoints() {
        val trip =
            Trip(
                startDate = Instant.ofEpochSecond(1_750_000_000),
                distance = 50_000.0,
                previewPolyline = Trip.encodePolyline(route()),
            )
        val exploration = MapExploration.build(listOf(trip), emptySet(), atlas)
        assertEquals(1, exploration.tripCount)
        assertEquals(50.0, exploration.totalKm, 1.0)
        assertTrue(assertNotNull(exploration.region("RU-KDA")).km > 0)
        assertTrue(exploration.trips.first().route.isNotEmpty())
    }

    @Test
    fun testRegionKilometresSplitTheTripsRealDistance() {
        val route = line(GeoPoint(45.035, 38.975), GeoPoint(47.222, 39.719), 120)
        val exploration =
            MapExploration.build(listOf(trip(route).copy(distance = 300_000.0)), emptySet(), atlas)
        assertTrue(exploration.regionCount >= 2)
        assertEquals(300.0, exploration.regions.sumOf { it.km }, 1.0)
        for (region in exploration.regions) {
            assertTrue(region.km > 0)
            assertTrue(region.km < 300)
        }
    }

    @Test
    fun testOpenedKilometresPerRegionSumToTheHeaderNumber() {
        val route = line(GeoPoint(45.035, 38.975), GeoPoint(45.045, 41.969), 400)
        val layer = RevealedLayer.build(listOf(route), 0, atlas)
        assertTrue(layer.openedKm > 100)
        assertTrue(layer.regionKm.size >= 2)
        assertEquals(layer.openedKm, layer.regionKm.values.sum(), 0.001)
        assertEquals(layer.regionKm["RU-KDA"] ?: -1.0, layer.openedKm("RU-KDA"))
        assertEquals(0.0, layer.openedKm("RU-ARK"))
    }

    @Test
    fun testExplorationCarriesTheLayersOpenedKilometres() {
        val route = route(200)
        val layer = RevealedLayer.build(listOf(route), 0, atlas)
        val exploration =
            MapExploration.build(listOf(trip(route)), emptySet(), atlas, layer.regionKm)
        assertEquals(
            layer.openedKm("RU-KDA"),
            assertNotNull(exploration.region("RU-KDA")).openedKm,
            0.0001,
        )
        assertEquals(
            0.0,
            assertNotNull(
                    MapExploration.build(listOf(trip(route)), emptySet(), atlas).region("RU-KDA")
                )
                .openedKm,
        )
    }
}
