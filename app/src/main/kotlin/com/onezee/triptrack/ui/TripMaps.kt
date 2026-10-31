package com.onezee.triptrack.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.onezee.triptrack.map.MapStyles

/**
 * Whether MapLibre's native library loads here. Where it does not (an unsupported ABI, or a JVM
 * test), the screens keep their static route: the map is a picture of the trip, never the only way
 * to see it.
 */
object LiveMaps {
    val available: Boolean by lazy { runCatching { System.loadLibrary("maplibre") }.isSuccess }

    /**
     * The trip map follows the app's theme (iOS «Trip detail first frame»: the explicit theme reaches
     * the map, a map that inherited the phone's style showed night inside a light app).
     */
    fun style(dark: Boolean): String = if (dark) DARK_STYLE else MapStyles.OPEN_FREE_MAP_LIBERTY

    private const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"
}

/** False in screenshot tests and on devices without the map's native library. */
val LocalLiveMaps = staticCompositionLocalOf { LiveMaps.available }
