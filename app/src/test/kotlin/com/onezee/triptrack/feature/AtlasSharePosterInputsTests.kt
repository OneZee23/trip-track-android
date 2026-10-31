// iOS: AtlasSharePosterTests.swift (frame inputs; raster tests belong to rendering).
package com.onezee.triptrack.feature

import com.onezee.triptrack.feature.atlas.*
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.model.GeoPoint
import org.junit.Test
import kotlin.test.*

class AtlasSharePosterInputsTests {
    @Test fun emptyLayerHasNoPosterAndStraightRoadHasPortraitFrame() {
        assertNull(AtlasSharePosterInputs.frame(RevealedLayer.empty))
        val points = (0..119).map { GeoPoint(45.04, 38.90 + .04 * it / 119) }
        val layer = RevealedLayer.build(listOf(points), points.size, null)
        val frame = AtlasSharePosterInputs.frame(layer)!!
        assertEquals(540.0 / 960, frame.width / frame.height, .000001)
        assertTrue(frame.width > 0); assertTrue(frame.height > 0)
        assertNull(AtlasSharePosterInputs.frame(layer, 0.0))
        val inputs = AtlasSharePosterInputs.from(AtlasState(revealed = layer), "caption")!!
        assertEquals(540.0, inputs.width); assertEquals(960.0, inputs.height); assertEquals(3.0, inputs.scale)
        assertEquals(148.0, inputs.captionHeight); assertEquals(34.0, inputs.sealSize)
        assertEquals("caption", inputs.caption)
    }
}
