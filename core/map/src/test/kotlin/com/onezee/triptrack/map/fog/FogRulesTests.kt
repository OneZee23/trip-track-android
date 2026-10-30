package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.FogCellGrid
import com.onezee.triptrack.logic.FogVeilRenderer
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.AtlasMapAppearance
import kotlin.math.pow
import kotlin.test.*
import org.junit.Test

// The numbers the native layer receives must be core:logic's and the iPhone's: one source each.
class FogRulesTests {
    // core:logic writes the thresholds inline; these constants are what the native layer compares.
    @Test
    fun lodThresholdsAgreeWithCoreLogic() {
        assertEquals(RevealedLayer.LOD.fine, FogVeilRenderer.lod(FogRules.LOD_FINE_ABOVE * 1.0001))
        assertEquals(RevealedLayer.LOD.mid, FogVeilRenderer.lod(FogRules.LOD_FINE_ABOVE))
        assertEquals(RevealedLayer.LOD.mid, FogVeilRenderer.lod(FogRules.LOD_MID_ABOVE * 1.0001))
        assertEquals(RevealedLayer.LOD.far, FogVeilRenderer.lod(FogRules.LOD_MID_ABOVE))
    }

    @Test
    fun nativeRulesAreTheSharedConstantsInOrder() {
        assertContentEquals(
            doubleArrayOf(50.0, 18.0, 0.4, 1.5e-3, 1.5e-4, 512.0, 14.0, 12.0, 0.55),
            FogRules.nativeRules(),
        )
        assertEquals(FogVeilRenderer.streetHalfWidthMetres, FogRules.nativeRules()[0])
        assertEquals(FogCellGrid.maxDoublings.toDouble(), FogRules.nativeRules()[7])
    }

    // The corridor: a 50 m floor up close, 18 screen points per side further out (HaloWidthTests).
    @Test
    fun halfWidthFollowsTheHaloRule() {
        assertEquals(50.0 / 0.3, FogRules.halfWidthPoints(0.3), 1e-9)
        assertEquals(18.0, FogRules.halfWidthPoints(300.0), 1e-9)
        val hinge = FogVeilRenderer.streetHalfWidthMetres / FogVeilRenderer.haloHalfWidthPoints
        assertEquals(18.0, FogRules.halfWidthPoints(hinge), 1e-9)
    }

    @Test
    fun chunkSizesPerLevel() {
        assertEquals(2.0.pow(15), FogRules.chunkSize(RevealedLayer.LOD.fine))
        assertEquals(2.0.pow(20), FogRules.chunkSize(RevealedLayer.LOD.mid))
        assertEquals(2.0.pow(20), FogRules.chunkSize(RevealedLayer.LOD.far))
    }

    // MapLibre's zoom against MapKit's zoom scale: the native layer converts with the same formula.
    @Test
    fun mapLibreZoomConvertsToPointsPerMapPoint() {
        assertEquals(512.0 / MapPoints.WORLD, MapPoints.pointsPerMapPoint(0.0), 1e-18)
        for (zoom in listOf(3.0, 8.5, 15.25)) assertEquals(zoom, MapPoints.mapLibreZoom(MapPoints.pointsPerMapPoint(zoom)), 1e-12)
        // Street zoom 15.5 is fine, city 11.5 mid, country 5 far.
        assertEquals(RevealedLayer.LOD.fine, FogVeilRenderer.lod(MapPoints.pointsPerMapPoint(15.5)))
        assertEquals(RevealedLayer.LOD.mid, FogVeilRenderer.lod(MapPoints.pointsPerMapPoint(9.0)))
        assertEquals(RevealedLayer.LOD.far, FogVeilRenderer.lod(MapPoints.pointsPerMapPoint(5.0)))
    }

    // iOS: FogVeilPainter.Palette.mist (#EEEEEC × 0.87) and .night (#262B36 × 0.70).
    @Test
    fun palettesAreTheIPhoneOnes() {
        assertEquals(listOf(0xEE, 0xEE, 0xEC), FogPalette.MIST.let { listOf(it.red, it.green, it.blue) })
        assertEquals(0.87f, FogPalette.MIST.alpha)
        assertFalse(FogPalette.MIST.isDark)
        assertEquals(listOf(0x26, 0x2B, 0x36), FogPalette.NIGHT.let { listOf(it.red, it.green, it.blue) })
        assertEquals(0.70f, FogPalette.NIGHT.alpha)
        assertTrue(FogPalette.NIGHT.isDark)
    }

    // "Клетки" shares the mist with "Туман"; only "Ночь" is dark (AtlasMapAppearance.usesDarkFog).
    @Test
    fun stylesMapToPaletteAndCells() {
        assertEquals(FogLook(FogPalette.MIST, cells = false), FogLook.of(AtlasMapAppearance(style = AtlasMapAppearance.Style.fog)))
        assertEquals(FogLook(FogPalette.NIGHT, cells = false), FogLook.of(AtlasMapAppearance(style = AtlasMapAppearance.Style.night)))
        assertEquals(FogLook(FogPalette.MIST, cells = true), FogLook.of(AtlasMapAppearance(style = AtlasMapAppearance.Style.cells)))
        assertTrue(FogLook.of(AtlasMapAppearance(), halfResolutionCoverage = true).halfResolutionCoverage)
    }
}
