package com.onezee.triptrack.fogspike.fog

// iOS: FogVeilPainter.Palette (.mist, .night) and AtlasMapAppearance.Style. "Fog" and "cells"
// share the light mist; only "night" uses the dark slate.
data class FogPalette(val red: Int, val green: Int, val blue: Int, val alpha: Float) {
    val argb: Int get() = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    companion object {
        val MIST = FogPalette(0xEE, 0xEE, 0xEC, 0.87f)
        val NIGHT = FogPalette(0x26, 0x2B, 0x36, 0.70f)
    }
}

enum class FogStyle(val key: String, val palette: FogPalette, val cells: Boolean) {
    FOG("fog", FogPalette.MIST, false),
    NIGHT("night", FogPalette.NIGHT, false),
    CELLS("cells", FogPalette.MIST, true);

    companion object {
        fun of(key: String?): FogStyle = entries.firstOrNull { it.key == key } ?: FOG
    }
}
