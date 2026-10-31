// iOS: PlacesMapView.swift (PlacePin, Coordinator.sync, rendererFor)
package com.onezee.triptrack.feature.places.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.feature.atlas.ui.placeCallout
import com.onezee.triptrack.feature.atlas.ui.worldArgb
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.model.AtlasPlacePin
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.LocalLiveMaps
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos

/**
 * iOS: PlacePin. A suggestion is drawn hollow and muted, «a pin CAN go here», and the comparison
 * includes it: a cell changes its look when its suggestion becomes a place at the same point.
 * [name] labels the pin only while it is chosen; only a place's own screen passes one.
 */
data class PlacePin(val id: UUID, val coordinate: GeoPoint, val isSuggested: Boolean = false, val name: String? = null)

/**
 * iOS: PlacesMapView. The pins of places and suggestions on ONE map, and on a place's screen the
 * threads of its passes. The frame takes all pins and threads; a single pin is a yard of 1.5 km.
 * The style follows the app's theme, as RouteMapView's standard configuration does.
 */
@Composable
fun PlacesMapLayer(
    pins: List<PlacePin>,
    routes: List<List<GeoPoint>>,
    selectedId: UUID?,
    mapState: MapHostState,
    chrome: MapInsets,
    strings: AppStrings,
    modifier: Modifier = Modifier,
    onPinTap: (UUID) -> Unit = {},
    testTag: String = "places_map",
) {
    if (!LocalLiveMaps.current) {
        Box(modifier.fillMaxSize().background(AtlasTheme.colors.searchBackground).testTag(testTag))
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // iOS PlacePinView.setSelectedAppearance: the chosen pin is labelled when it has a name.
    val selectedPin = pins.firstOrNull { it.id == selectedId }
    val callout = remember(selectedPin, strings) {
        selectedPin?.let { pin ->
            pin.name?.takeIf { it.isNotEmpty() }?.let { placeCallout(context, density, AtlasPlacePin(pin.id, pin.coordinate, it, null, 0, null, true), strings) }
        }
    }
    val markers = remember(pins, selectedId, callout) {
        pins.map { pin ->
            val selected = pin.id == selectedId
            MapMarker(
                id = "${if (pin.isSuggested) "hint" else "place"}:${pin.id}",
                position = pin.coordinate,
                kind = if (pin.isSuggested) MapMarker.Kind.PLACE_SUGGESTION else MapMarker.Kind.PLACE,
                selected = selected,
                // The name stands only over the CHOSEN pin: ten names over a city cover each other.
                callout = if (selected) callout else null,
            )
        }
    }
    val threads = remember(routes) {
        routes.filter { it.size > 1 }.takeIf { it.isNotEmpty() }?.let { lines ->
            // The passes' threads are a muted accent: context, not content.
            MapRoutes(lines.map { RouteGeometry.build(it, preview = true) }, RouteAppearance(3.0, null, lineColorOverride = worldArgb(0xC8, 0x47, 0x2D, 0.35), showsEndpoints = false))
        }
    }
    LaunchedEffect(pins, routes) { frame(pins, routes)?.let(mapState::execute) }
    val dark = AppTheme.isDark
    // Read once, as MapHost reads its style once.
    val style = remember { LiveMaps.style(dark) }
    MapHost(
        state = mapState,
        modifier = modifier.fillMaxSize().testTag(testTag),
        routes = threads,
        markers = markers,
        onMarkerTap = { marker -> marker.id.substringAfter(':').let { runCatching { UUID.fromString(it) }.getOrNull() }?.let(onPinTap) },
        chromeInsets = chrome,
        styleUri = style,
    )
}

/** iOS sync: the area of every pin and thread, 40 of air; a single pin without threads is 1.5 km. */
fun frame(pins: List<PlacePin>, routes: List<List<GeoPoint>>): MapCameraCommand? {
    val points = pins.map { it.coordinate } + routes.flatten()
    if (points.isEmpty()) return null
    if (pins.size == 1 && routes.none { it.size > 1 }) {
        val centre = pins[0].coordinate
        val dLat = 750 / 111_320.0
        val dLon = 750 / (111_320.0 * maxOf(0.01, cos(centre.latitude * PI / 180)))
        return MapCameraCommand.FitRoute(GeoBounds(centre.latitude - dLat, centre.latitude + dLat, centre.longitude - dLon, centre.longitude + dLon),
            MapInsets.Zero, animated = false)
    }
    return MapCameraCommand.FitRoute(GeoBounds(points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude }),
        MapInsets.all(40.0), animated = false)
}
