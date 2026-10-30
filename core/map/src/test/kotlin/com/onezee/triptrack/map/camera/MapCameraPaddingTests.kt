package com.onezee.triptrack.map.camera

import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoBounds
import kotlin.test.*
import org.junit.Test

// iOS: MapCameraPaddingTests.swift. The camera frames by the SUM of the safe area and the padding;
// the Atlas gave its sheet ~290 pt through the safe area and `.region` asked for 480 more: −68 pt
// remained on an 874 pt screen, and the frame came out five times too wide (owner, 26 September).
// Measured before the fix: ratio 5.34, after: 1.29.
class MapCameraPaddingTests {
    // The geometry it broke on: iPhone 17 Pro Max, the Atlas with a region card.
    private val size = MapSize(402.0, 874.0)
    private val safeArea = MapInsets(top = 62.0, left = 6.0, bottom = 290.0, right = 0.0)

    private fun viewport(insets: MapInsets) = MapSize(
        size.width - safeArea.left - safeArea.right - insets.left - insets.right,
        size.height - safeArea.top - safeArea.bottom - insets.top - insets.bottom,
    )

    @Test
    fun testRegionPaddingLeavesTheMapSomethingToDrawIn() {
        val chrome = MapCameraCommand.Padding.region.chrome
        // What stood before the fix: the chrome went to the camera as is.
        assertTrue(viewport(chrome).height < 0, "otherwise this test is not about that break")
        val fixed = viewport(MapCameraCommand.Padding.region.insets(safeArea, size))
        assertTrue(fixed.height >= MapCameraCommand.Padding.minimumViewport)
        assertTrue(fixed.width >= MapCameraCommand.Padding.minimumViewport)
    }

    // The addition is asked on top of the safe area, not instead: while there is room the sum is the
    // ordered chrome, so the map is framed exactly under the card.
    @Test
    fun testPaddingAddsOnlyWhatTheSafeAreaDoesNotAlreadyGive() {
        val tall = MapSize(402.0, 1600.0)
        val chrome = MapCameraCommand.Padding.region.chrome
        val insets = MapCameraCommand.Padding.region.insets(safeArea, tall)
        assertEquals(chrome.bottom, insets.bottom + safeArea.bottom, 0.01)
        assertEquals(chrome.top, insets.top + safeArea.top, 0.01)
        assertEquals(chrome.right, insets.right, 0.01, "no safe area on the right: the addition is the whole chrome")
    }

    @Test
    fun testSafeAreaLargerThanTheChromeAsksForNothing() {
        val generous = MapInsets(top = 200.0, left = 60.0, bottom = 600.0, right = 60.0)
        val insets = MapCameraCommand.Padding.overview.insets(generous, MapSize(402.0, 1600.0))
        assertEquals(MapInsets.Zero, insets)
    }

    // No inset is ever negative at any size: on negative ones the camera answers with the whole world.
    @Test
    fun testNoPaddingIsEverNegativeOnAnyGeometry() {
        var height = 80.0
        while (height <= 1600.0) {
            for (padding in MapCameraCommand.Padding.entries) {
                val insets = padding.insets(safeArea, MapSize(402.0, height))
                assertTrue(insets.top >= 0 && insets.bottom >= 0 && insets.left >= 0 && insets.right >= 0, "h=$height")
                val free = height - safeArea.top - safeArea.bottom
                if (free > MapCameraCommand.Padding.minimumViewport) {
                    assertTrue(insets.top + insets.bottom <= free - MapCameraCommand.Padding.minimumViewport + 0.01, "the map must keep room, h=$height")
                }
            }
            height += 40.0
        }
    }

    @Test
    fun chromeNumbersAreTheIPhoneOnes() {
        assertEquals(MapInsets(110.0, 40.0, 300.0, 40.0), MapCameraCommand.Padding.overview.chrome)
        assertEquals(MapInsets(110.0, 32.0, 480.0, 32.0), MapCameraCommand.Padding.region.chrome)
        assertEquals(MapInsets(110.0, 44.0, 310.0, 44.0), MapCameraCommand.Padding.trip.chrome)
        assertEquals(MapInsets.all(30.0), MapCameraCommand.DEFAULT_ROUTE_INSETS)
    }
}

// iOS: GeoBounds.mapRect (300 m) and RouteMapView.floored (400 m).
class CameraFramingTests {
    private val krasnodar = GeoBounds(minLat = 45.0355, maxLat = 45.0355, minLon = 38.9753, maxLon = 38.9753)

    @Test
    fun aPointBecomesABlockToLookAt() {
        val atlas = CameraFraming.atlasRect(krasnodar)
        val metres = 300 * MapPoints.mapPointsPerMetre(45.0355)
        assertEquals(metres, atlas.width, 1e-6)
        assertEquals(metres, atlas.height, 1e-6)
        assertEquals(MapPoints.x(38.9753), atlas.x + atlas.width / 2, 1e-6)
        val route = CameraFraming.routeRect(krasnodar)
        assertEquals(400 * MapPoints.mapPointsPerMetre(45.0355), route.width, 1e-3)
    }

    @Test
    fun aThinRectGrowsOnlyAcross() {
        val road = GeoBounds(minLat = 45.0, maxLat = 45.0, minLon = 38.0, maxLon = 39.0)
        val rect = CameraFraming.atlasRect(road)
        assertEquals(MapPoints.x(39.0) - MapPoints.x(38.0), rect.width, 1e-6)
        assertEquals(300 * MapPoints.mapPointsPerMetre(45.0), rect.height, 1e-6)
    }

    @Test
    fun aLargeRectStaysAsItIs() {
        val region = GeoBounds(minLat = 44.0, maxLat = 46.0, minLon = 38.0, maxLon = 40.0)
        assertEquals(MapPoints.rect(region), CameraFraming.atlasRect(region))
        assertEquals(MapPoints.rect(region), CameraFraming.routeRect(region))
    }

    @Test
    fun boundsAndRectsRoundTrip() {
        val region = GeoBounds(minLat = 44.1, maxLat = 46.9, minLon = 37.2, maxLon = 41.4)
        val back = MapPoints.bounds(MapPoints.rect(region))
        assertEquals(region.minLat, back.minLat, 1e-9)
        assertEquals(region.maxLat, back.maxLat, 1e-9)
        assertEquals(region.minLon, back.minLon, 1e-9)
        assertEquals(region.maxLon, back.maxLon, 1e-9)
    }
}
