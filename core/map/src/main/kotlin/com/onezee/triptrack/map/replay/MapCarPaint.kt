// iOS: MapCarMarker.swift. Clamp brightness before desaturation, keeping every mood readable.
package com.onezee.triptrack.map.replay

import android.graphics.Color

internal object MapCarPaint {
    const val minBrightness = .22f
    const val maxBrightness = .86f
    fun components(argb: Int, mood: MapCarMood): FloatArray {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hsv[2] = hsv[2].coerceIn(minBrightness, maxBrightness)
        hsv[1] *= mood.saturation
        return hsv
    }
    fun color(argb: Int, mood: MapCarMood): Int = Color.HSVToColor(components(argb, mood))
}
