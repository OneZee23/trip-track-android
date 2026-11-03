// iOS: MapChromeButton.swift (MapChromeSurface)
package com.onezee.triptrack.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

/** SH-03 of the 3 November audit: the glass of back, «…» and expand reads like the iPhone's material. */
class MapChromeGlassTests {
    private fun Color.channels() = Triple((red * 255).roundToInt(), (green * 255).roundToInt(), (blue * 255).roundToInt())

    @Test fun overTheLightHeroTheGlassReadsMidGreyNotCharcoal() {
        // The iPhone's dark ultra-thin material turns the hero's (244, 242, 238) into (170, 169, 167).
        val (r, g, b) = ChromeGlass.compositeOver(Color(244 / 255f, 242 / 255f, 238 / 255f)).channels()
        assertEquals(170f, r.toFloat(), 2f)
        assertEquals(169f, g.toFloat(), 2f)
        assertEquals(167f, b.toFloat(), 2f)
    }
}
