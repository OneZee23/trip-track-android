package com.onezee.triptrack.map.vein

import com.onezee.triptrack.logic.RevealedLayer.LOD
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.route.RouteAppearance
import kotlin.test.*
import org.junit.Test

// iOS: FogVeilVeinTests.swift (testStrokesCarryTheSameWidthsAsTheTiledRenderer,
// testLightPaletteAddsANarrowWhiteCasingWithoutWideningTheCore); RouteVeinRenderer's LOD table.
class VeinStyleTests {
    @Test fun testStrokesCarryTheSameWidthsAsTheTiledRenderer() {
        assertEquals(2.2, VeinStyle.width(LOD.fine))
        assertEquals(2.8, VeinStyle.width(LOD.mid))
        assertEquals(3.0, VeinStyle.width(LOD.far))
        assertNull(VeinStyle.halo(LOD.fine))
        assertEquals(8.0 to .10, VeinStyle.halo(LOD.mid))
        assertEquals(10.0 to .14, VeinStyle.halo(LOD.far))
        assertEquals(3.2, RouteAppearance.overFog(FogPalette.NIGHT).widthPoints)
        assertEquals(5.2, RouteAppearance.SELECTED_WIDTH + RouteAppearance.CASING_EXTRA)
    }

    @Test fun testLightPaletteAddsANarrowWhiteCasingWithoutWideningTheCore() {
        assertEquals(RouteAppearance.CASING_LIGHT, VeinStyle.casing(FogPalette.MIST))
        assertNull(VeinStyle.casing(FogPalette.NIGHT))
        assertEquals(2.0, VeinStyle.CASING_EXTRA)
        assertEquals(RouteAppearance.CASING_EXTRA, VeinStyle.CASING_EXTRA)
        assertEquals(3.2, RouteAppearance.overFog(FogPalette.MIST).widthPoints)
        assertEquals(VeinStyle.PAPER, VeinStyle.veinColour(FogPalette.MIST))
        assertEquals(VeinStyle.NIGHT, VeinStyle.veinColour(FogPalette.NIGHT))
    }
}
