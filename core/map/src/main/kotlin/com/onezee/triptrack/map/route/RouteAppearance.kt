package com.onezee.triptrack.map.route

import com.onezee.triptrack.map.fog.FogPalette

/**
 * How route lines are stroked (iOS: RouteMapView.Coordinator.routeRenderer and rendererFor(overlay:),
 * CasedPolylineRenderer, RouteVeinRenderer.selectedWidth / casingExtra / resolvedColors).
 *
 * The difference between the two presets is not cosmetic. On a plain map a 4 pt line IS the trip;
 * inside a corridor cleared in the fog it would cover the very map the corridor was cleared for, so
 * there it is 3.2 pt with a casing, like the selected route of the Atlas. Without the casing the speed
 * gradient laid on the live map merges with the yellow highway under it.
 */
data class RouteAppearance(
    val widthPoints: Double,
    /** ARGB of the casing, or null for none. */
    val casingArgb: Int?,
    /** How much wider the casing is than the line, in screen points (1 pt per side). */
    val casingExtraPoints: Double = CASING_EXTRA,
    /**
     * A colour that replaces the speed gradient (iOS: RouteLineStyle.currentUIColor, a PRO cosmetic).
     * Android hides PRO, so screens pass null; the parameter keeps the iOS rule in one place.
     */
    val lineColorOverride: Int? = null,
    val showsEndpoints: Boolean = true,
    val hiddenInkArgb: Int = SpeedColorScale.argb(0x20, 0x21, 0x2B, 0.55),
) {
    companion object {
        const val SELECTED_WIDTH = 3.2
        const val PLAIN_WIDTH = 4.0
        const val CASING_EXTRA = 2.0

        // Dark casing on the night palette, a narrow white one on the paper mist (iOS resolvedColors).
        val CASING_DARK = SpeedColorScale.argb(0x14, 0x14, 0x1A, 0.55)
        val CASING_LIGHT = SpeedColorScale.argb(0xFF, 0xFF, 0xFF, 0.94)

        // A filled-in gap has no speed: a grey dash without a casing (a solid casing would turn the
        // dash back into a road), and a PRO colour does not repaint it. UIColor(white: 0.62, alpha: 0.9).
        val UNKNOWN_SPEED = SpeedColorScale.argb(158, 158, 158, 0.9)
        val UNKNOWN_SPEED_DASH = doubleArrayOf(6.0, 6.0)

        // A line without speeds: someone else's trip, a synced preview, a journey leg.
        val ACCENT = SpeedColorScale.argb(0xFC, 0x4C, 0x02, 0.9)

        // Start and end dots, 10 pt across.
        val START = SpeedColorScale.argb(48, 209, 88, 1.0)
        val END = SpeedColorScale.argb(255, 69, 58, 1.0)
        const val ENDPOINT_DIAMETER = 10.0

        /** A route inside the fog's cleared corridor. */
        fun overFog(palette: FogPalette): RouteAppearance =
            RouteAppearance(SELECTED_WIDTH, if (palette.isDark) CASING_DARK else CASING_LIGHT)

        /** A route on a plain map. */
        val plain = RouteAppearance(PLAIN_WIDTH, null)
    }

    /** The colour one line is drawn with. */
    fun argb(line: RouteLine): Int = when {
        line.isUnknownSpeed -> UNKNOWN_SPEED
        lineColorOverride != null -> lineColorOverride
        line.speed == null -> ACCENT
        else -> SpeedColorScale.argb(line.speed)
    }
}
