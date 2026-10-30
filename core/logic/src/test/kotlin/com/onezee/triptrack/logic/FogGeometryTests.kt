package com.onezee.triptrack.logic

import kotlin.math.round
import kotlin.test.*
import org.junit.Test

// iOS: HaloWidthTests.swift
class HaloWidthTests {
    @Test
    fun testHaloHalfWidthTable() {
        assertEquals(50.0, FogVeilRenderer.haloHalfWidth(1.0), 0.001)
        assertEquals(1_800.0, FogVeilRenderer.haloHalfWidth(100.0), 0.001)
        assertEquals(18_000.0, FogVeilRenderer.haloHalfWidth(1_000.0), 0.001)
    }

    @Test
    fun testFloorHandsOverToPointsWithoutAJump() {
        val hinge = FogVeilRenderer.streetHalfWidthMetres / FogVeilRenderer.haloHalfWidthPoints
        assertEquals(50.0 / 18.0, hinge, 1e-9)
        assertEquals(
            FogVeilRenderer.streetHalfWidthMetres,
            FogVeilRenderer.haloHalfWidth(hinge - 0.01),
            1e-9,
        )
        assertEquals(
            FogVeilRenderer.haloHalfWidth(hinge),
            FogVeilRenderer.haloHalfWidth(hinge - 1e-9),
            1e-3,
        )
    }

    @Test
    fun testHaloNeverShrinksAsYouZoomOut() {
        var previous = 0.0
        for (i in 1..4_000) {
            val halo = FogVeilRenderer.haloHalfWidth(i * 0.5)
            assertTrue(halo >= previous)
            previous = halo
        }
    }

    @Test
    fun testCorridorOnScreenIsThirtySixPointsWhereverYouAre() {
        // Frozen from MKMapPointsPerMeterAtLatitude at 0°, 45.035°, 68.97°.
        for (metre in listOf(6.74355336508965, 9.494969876073583, 18.63082113355785)) for (zoom in
            listOf(1e-3, 3e-4, 1e-4, 3e-5)) {
            val width = FogVeilRenderer.corridorWidth(zoom, metre)
            if (
                1 / (zoom * metre) >
                    FogVeilRenderer.streetHalfWidthMetres / FogVeilRenderer.haloHalfWidthPoints
            )
                assertEquals(36.0, width * zoom, 0.001)
        }
    }

    @Test
    fun testDegenerateInputsGiveNoCorridor() {
        assertEquals(0.0, FogVeilRenderer.corridorWidth(0.0, 1.0))
        assertEquals(0.0, FogVeilRenderer.corridorWidth(1e-4, 0.0))
    }

    // iOS: FogVeilRendererTests.swift (pure geometry cases).
    @Test
    fun testLodThresholds() {
        for ((zoom, expected) in
            listOf(
                0.06 to RevealedLayer.LOD.fine,
                2e-3 to RevealedLayer.LOD.fine,
                1.5e-3 to RevealedLayer.LOD.mid,
                5e-4 to RevealedLayer.LOD.mid,
                1.5e-4 to RevealedLayer.LOD.far,
                3e-5 to RevealedLayer.LOD.far,
            )) assertEquals(expected, FogVeilRenderer.lod(zoom))
    }

    @Test
    fun testCorridorWidthTakesMetresUpCloseAndScreenPointsFarAway() {
        val metre = 9.494969876073583
        assertEquals(
            FogVeilRenderer.streetHalfWidthMetres * 2 * metre,
            FogVeilRenderer.corridorWidth(0.06, metre),
            0.001,
        )
        assertEquals(36.0, FogVeilRenderer.corridorWidth(3e-5, metre) * 3e-5, 0.001)
    }
}

// iOS: RegionLabelLODTests.swift (all three suites).
class RegionLabelLODTests {
    @Test
    fun testRegionVisibleAtThreshold() {
        assertTrue(RegionLabelLOD.level(140.0))
    }

    @Test
    fun testRegionHiddenJustBelowThreshold() {
        assertFalse(RegionLabelLOD.level(139.9))
    }

    @Test
    fun testRegionVisibleWhenLargeEnough() {
        assertTrue(RegionLabelLOD.level(500.0))
    }

    @Test
    fun testVeryLargeBboxStaysVisible() {
        assertTrue(RegionLabelLOD.level(10_000.0))
    }

    @Test
    fun testTinyBboxStaysHidden() {
        assertFalse(RegionLabelLOD.level(10.0))
    }

    private val label = MapRect(100.0, 100.0, 120.0, 30.0)

    @Test
    fun testALabelUnderTheDotYields() {
        assertTrue(RegionLabelLOD.yieldsToUserDot(label, MapPoint(160.0, 115.0)))
    }

    @Test
    fun testALabelTouchingTheDotHaloYields() {
        assertTrue(RegionLabelLOD.yieldsToUserDot(label, MapPoint(232.0, 115.0)))
    }

    @Test
    fun testALabelClearOfTheDotStays() {
        assertFalse(RegionLabelLOD.yieldsToUserDot(label, MapPoint(260.0, 115.0)))
    }

    @Test
    fun testWithoutALocationNothingYields() {
        assertFalse(RegionLabelLOD.yieldsToUserDot(label, null))
    }

    @Test
    fun testAnUnmeasuredLabelDecidesNothing() {
        assertFalse(
            RegionLabelLOD.yieldsToUserDot(MapRect(0.0, 0.0, 0.0, 0.0), MapPoint(160.0, 115.0))
        )
    }

    @Test
    fun testLabelUnderTheClockYields() {
        assertTrue(RegionLabelLOD.yieldsToStatusBar(MapRect(230.0, 26.0, 150.0, 24.0), 59.0))
    }

    @Test
    fun testLabelTouchingTheSafeAreaFromAboveYields() {
        assertTrue(RegionLabelLOD.yieldsToStatusBar(MapRect(100.0, 58.0, 150.0, 24.0), 59.0))
    }

    @Test
    fun testLabelBelowTheSafeAreaStays() {
        assertFalse(RegionLabelLOD.yieldsToStatusBar(MapRect(100.0, 59.0, 150.0, 24.0), 59.0))
        assertFalse(RegionLabelLOD.yieldsToStatusBar(MapRect(100.0, 300.0, 150.0, 24.0), 59.0))
    }

    @Test
    fun testNothingToYieldToWithoutASafeAreaOrASize() {
        assertFalse(RegionLabelLOD.yieldsToStatusBar(MapRect(0.0, 0.0, 150.0, 24.0), 0.0))
        assertFalse(RegionLabelLOD.yieldsToStatusBar(MapRect(0.0, 0.0, 0.0, 0.0), 59.0))
    }
}

// iOS: FogMetalCellsTests.swift (the six CPU-only grid cases).
class FogCellGridTests {
    private val krasnodar = MapPoint(163275866.112, 96531310.61060245)
    private val metre = 9.494148642179455

    private fun rect(metresPerPoint: Double): MapRect {
        val width = 300 * metresPerPoint * metre
        return MapRect(krasnodar.x - width / 2, krasnodar.y - width / 2, width, width)
    }

    @Test
    fun testGridExistsAtStreetZoom() {
        val grid = assertNotNull(FogCellGrid.make(rect(4.0), 300.0))
        assertTrue(grid.sideScreenPoints >= FogCellGrid.minScreenPoints)
        assertEquals(0.0, grid.sideMapPoints % FogCellGrid.baseMapPoints, 1e-9)
    }

    @Test
    fun testZoomingOutDoublesTheCellInsteadOfLosingIt() {
        var previous = assertNotNull(FogCellGrid.make(rect(4.0), 300.0)).sideMapPoints
        for (metres in listOf(8.0, 16.0, 32.0, 64.0, 128.0)) {
            val grid = assertNotNull(FogCellGrid.make(rect(metres), 300.0))
            assertTrue(grid.sideScreenPoints >= FogCellGrid.minScreenPoints)
            val ratio = grid.sideMapPoints / previous
            assertEquals(round(ratio), ratio, 1e-9)
            assertEquals(0.0, grid.sideMapPoints % FogCellGrid.baseMapPoints, 1e-9)
            previous = grid.sideMapPoints
        }
    }

    @Test
    fun testGridTurnsItselfOffAtWorldZoom() {
        assertNull(FogCellGrid.make(rect(400_000.0), 300.0))
    }

    @Test
    fun testGridRefusesNonsense() {
        assertNull(FogCellGrid.make(rect(4.0), 0.0))
        assertNull(FogCellGrid.make(MapRect(0.0, 0.0, 0.0, 0.0), 300.0))
    }

    @Test
    fun testSideDependsOnZoomAndNothingElse() {
        val span = 40_000.0
        val points =
            listOf(
                krasnodar,
                MapPoint(158883963.7902222, 62267852.16131657),
                MapPoint(158511136.76799998, 133979117.46528517),
                MapPoint(246968076.0604445, 161087114.81389248),
            )
        val sides =
            points.map {
                assertNotNull(
                        FogCellGrid.make(
                            MapRect(it.x - span / 2, it.y - span / 2, span, span),
                            300.0,
                        )
                    )
                    .sideMapPoints
            }
        assertEquals(1, sides.toSet().size)
    }

    @Test
    fun testPanningDoesNotChangeTheSide() {
        val box = rect(4.0)
        val a = assertNotNull(FogCellGrid.make(box, 300.0))
        val b =
            assertNotNull(
                FogCellGrid.make(
                    box.copy(x = box.x + a.sideMapPoints / 2, y = box.y + a.sideMapPoints / 3),
                    300.0,
                )
            )
        assertEquals(a.sideMapPoints, b.sideMapPoints)
    }

    @Test
    fun gridOriginRemainsOnWorldBoundariesAfterPanning() {
        val box = rect(4.0)
        val a = assertNotNull(FogCellGrid.make(box, 300.0))
        val moved = box.copy(y = box.y + a.sideMapPoints / 2)
        val b = assertNotNull(FogCellGrid.make(moved, 300.0))
        assertEquals(0.0, (box.minX + a.originOffset.x) % a.sideMapPoints, 1e-9)
        assertEquals(0.0, (moved.minY + b.originOffset.y) % b.sideMapPoints, 1e-9)
        assertTrue(a.originOffset.y <= 0 && a.originOffset.y > -a.sideMapPoints)
        assertTrue(b.originOffset.y <= 0 && b.originOffset.y > -b.sideMapPoints)
    }
}
