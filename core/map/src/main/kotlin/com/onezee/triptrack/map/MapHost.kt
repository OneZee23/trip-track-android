package com.onezee.triptrack.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezee.triptrack.logic.RegionLabelAnnotation
import com.onezee.triptrack.map.area.MapArea
import com.onezee.triptrack.map.attribution.MapAttribution
import com.onezee.triptrack.map.attribution.MapAttributionSheet
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.replay.MapPlayback
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.vein.MapVein
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/**
 * The map: MapLibre with OpenFreeMap tiles, the Atlas fog drawn inside its frame, routes and markers
 * above the fog, and camera commands through [state].
 *
 * [chromeInsets] is what the screen's own chrome covers (a sheet, a header), added to the
 * [systemInsets] the map reaches under; together they play the role of MapKit's safe area. Camera
 * fits clear it, and MapLibre's logo, attribution and compass stay outside it.
 *
 * No tilt, rotation allowed: the fog's frame is affine only without perspective, as on iOS.
 */
@Composable
fun MapHost(
    state: MapHostState,
    modifier: Modifier = Modifier,
    fog: MapFog? = null,
    routes: MapRoutes? = null,
    markers: List<MapMarker> = emptyList(),
    onMarkerTap: (MapMarker) -> Unit = {},
    chromeInsets: MapInsets = MapInsets.Zero,
    /**
     * The system bars this map reaches under; a full-screen map takes them all. iOS gives a view only
     * its own overlap with the unsafe area, so a map inside a card or a sheet passes none: the whole
     * window's insets on the 139 dp summary hero left the route no height, and the hero showed the
     * whole region (emulator run, 1 Nov 2026). No default, like Measure's unit: a new map does not
     * build without naming them.
     */
    systemInsets: WindowInsets,
    styleUri: String = MapStyles.LIGHT,
    /**
     * Render into a TextureView instead of a SurfaceView. For a map that lives inside moving UI (a
     * hero in a scrolling page, a layer that fades in, one map handed between two slots): a
     * SurfaceView ignores the alpha of its parents and kept its old buffer size after such a move.
     * Fixed for the life of the map.
     */
    textureMode: Boolean = false,
    /** Every opened road as a thread over the fog (the Atlas). */
    vein: MapVein? = null,
    /** A tap that hit no marker. Null leaves taps to MapLibre. */
    onMapTap: ((MapTap) -> Unit)? = null,
    /** The map's own location dot (iOS showsUserLocation); needs a location permission. */
    showsUserLocation: Boolean = false,
    playback: MapPlayback? = null,
    /** iOS: fitInsets and layoutMargins are independent; the trip fits once around its chrome. */
    cameraChromeInsets: MapInsets = chromeInsets,
    /** iOS: RegionLabelAnnotation. Installed once; visibility follows every map frame. */
    regionLabels: List<RegionLabelAnnotation> = emptyList(),
    /** iOS: MapViewRepresentable locks pan, zoom and rotation during recording. */
    gesturesEnabled: Boolean = true,
    liveHead: List<com.onezee.triptrack.model.GeoPoint> = emptyList(),
    /** iOS: an MKCircle with a fill and an edge; one tinted disc under the fog and the routes (the home picker). */
    area: MapArea? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(textureMode)).also { it.onCreate(null) }
    }
    val controller = remember(mapView) { MapController(mapView, state, density, styleUri) }
    var attributions by remember(mapView) { mutableStateOf<List<MapAttribution>?>(null) }

    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event -> controller.moveTo(event.targetState) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.dispose()
        }
    }

    DisposableEffect(context, controller) {
        val callbacks = object : ComponentCallbacks2 {
            // Since Android 14 only TRIM_MEMORY_UI_HIDDEN and TRIM_MEMORY_BACKGROUND arrive, and both
            // after the map stopped; the controller already gave the texture back at that point.
            override fun onTrimMemory(level: Int) {
                @Suppress("DEPRECATION")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) controller.trimMemory(lowMemory = false)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = controller.trimMemory(lowMemory = true)
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }

    DisposableEffect(state, controller) {
        state.controller = controller
        onDispose { if (state.controller === controller) state.controller = null }
    }

    // Insets are absolute left and right, like UIEdgeInsets, whatever the reading direction.
    val system = systemInsets.asPaddingValues()
    val systemArea = MapInsets(
        top = system.calculateTopPadding().value.toDouble(),
        left = system.calculateLeftPadding(LayoutDirection.Ltr).value.toDouble(),
        bottom = system.calculateBottomPadding().value.toDouble(),
        right = system.calculateRightPadding(LayoutDirection.Ltr).value.toDouble(),
    )
    SideEffect {
        controller.onMarkerTap = onMarkerTap
        controller.onMapTap = onMapTap
        controller.onAttribution = { attributions = it }
        controller.setSafeArea(systemArea + chromeInsets)
        controller.setCameraSafeArea(systemArea + cameraChromeInsets)
        controller.setFog(fog)
        controller.setArea(area)
        controller.setVein(vein)
        controller.setRoutes(routes)
        controller.setPlayback(playback)
        controller.setMarkers(markers)
        controller.setRegionLabels(regionLabels)
        controller.setShowsUserLocation(showsUserLocation)
        controller.setGesturesEnabled(gesturesEnabled)
        controller.setLiveHead(liveHead)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
    attributions?.let { MapAttributionSheet(it, onDismissRequest = { attributions = null }) }
}
