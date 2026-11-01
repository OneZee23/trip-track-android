// iOS: MapCarMarkerTests.swift (paint and mood cases, same brightness corridor and saturation).
package com.onezee.triptrack.map.replay

import android.graphics.BitmapFactory
import android.graphics.Color
import com.onezee.triptrack.map.R
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapCarPaintTests {
    private val colors = listOf(0xFFD4D9E3, 0xFF2A2C33, 0xFFA2A8B4, 0xFF707580, 0xFFE03B2C, 0xFF3B7DD8, 0xFFF2C41B, 0xFF4CAF50, 0xFFEB571E).map(Long::toInt)
    private fun hsv(color: Int) = FloatArray(3).also { Color.colorToHSV(color, it) }
    @Test fun testEveryLayerShipsWithTheAppAndSharesOneSquareCanvas() {
        val resources = RuntimeEnvironment.getApplication().resources
        val images = listOf(R.drawable.map_car_body, R.drawable.map_car_shade, R.drawable.map_car_ink).map { BitmapFactory.decodeResource(resources, it) }
        images.forEach { image -> assertNotNull(image); assertEquals(image.width, image.height); assertEquals(images.first().width, image.width); image.recycle() }
    }
    @Test fun testPaintStaysInsideTheReadableCorridorInEveryMood() {
        MapCarMood.entries.forEach { mood -> colors.forEach { base ->
            val brightness = MapCarPaint.components(base, mood)[2]
            assertTrue(brightness >= MapCarPaint.minBrightness - .0001)
            assertTrue(brightness <= MapCarPaint.maxBrightness + .0001)
        } }
    }
    @Test fun testClampingKeepsTheHue() { assertEquals(hsv(colors[4])[0], hsv(MapCarPaint.color(colors[4], MapCarMood.normal))[0], .36f) }
    @Test fun testPauseDimsThePaintButKeepsIt() {
        val normal = hsv(MapCarPaint.color(colors[4], MapCarMood.normal))[1]
        val paused = hsv(MapCarPaint.color(colors[4], MapCarMood.paused))[1]
        assertTrue(paused < normal && paused > 0)
        assertEquals(1f, MapCarMood.paused.alpha)
    }
    @Test fun testLostSignalHasNoColourLeft() {
        colors.forEach { assertEquals(0f, hsv(MapCarPaint.color(it, MapCarMood.lost))[1], .0001f) }
        assertTrue(MapCarMood.lost.alpha < 1 && MapCarMood.lost.alpha > .3)
    }
}
