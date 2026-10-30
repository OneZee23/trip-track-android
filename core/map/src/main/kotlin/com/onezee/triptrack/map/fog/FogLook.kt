package com.onezee.triptrack.map.fog

import com.onezee.triptrack.model.AtlasMapAppearance

// iOS: FogVeilPainter.Palette (.mist, .night): the flat colour and nominal opacity of the Metal fog.
// The raster palettes' other colours (haze, border, opacity range) belong to the raster veil, which
// Android does not have.
enum class FogPalette(val red: Int, val green: Int, val blue: Int, val alpha: Float, val isDark: Boolean) {
    // The 0.8.1 paper atlas: neutral #EEEEEC at 87%.
    MIST(0xEE, 0xEE, 0xEC, 0.87f, false),

    // The "night map": a translucent slate, so the real map stays visible everywhere under it.
    NIGHT(0x26, 0x2B, 0x36, 0.70f, true),
}

// iOS: AtlasMapAppearance.usesDarkFog. Only "Ночь" has a dark fog; "Клетки" shares the mist.
val AtlasMapAppearance.usesDarkFog: Boolean get() = style == AtlasMapAppearance.Style.night

// iOS: AtlasMapAppearance.usesCells. The opened world is quantised into world-anchored squares.
val AtlasMapAppearance.usesCells: Boolean get() = style == AtlasMapAppearance.Style.cells

/**
 * Everything that decides how the fog looks, set as ONE value: a style is a palette and a cells flag
 * at once, and setting them apart once left a frame of night fog WITH cells on the iPhone (owner,
 * 27 September). [halfResolutionCoverage] is the Android-only fill option (see FogRules).
 */
data class FogLook(
    val palette: FogPalette = FogPalette.MIST,
    val cells: Boolean = false,
    val halfResolutionCoverage: Boolean = false,
) {
    companion object {
        fun of(appearance: AtlasMapAppearance, halfResolutionCoverage: Boolean = false): FogLook = FogLook(
            palette = if (appearance.usesDarkFog) FogPalette.NIGHT else FogPalette.MIST,
            cells = appearance.usesCells,
            halfResolutionCoverage = halfResolutionCoverage,
        )
    }
}
