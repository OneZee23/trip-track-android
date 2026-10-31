package com.onezee.triptrack.map

import android.view.View
import androidx.lifecycle.Lifecycle
import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.map.camera.CameraFraming
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.camera.MapSize
import com.onezee.triptrack.map.fog.FogLayer
import com.onezee.triptrack.map.fog.FogRules
import com.onezee.triptrack.map.fog.FogStats
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.marker.MarkerLayers
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteLayers
import com.onezee.triptrack.map.vein.MapVein
import com.onezee.triptrack.map.vein.VeinLayers
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Everything behind one [MapHost]: the MapView's lifecycle, the style and the order of our layers on
 * it, the camera, and the fog's background and memory rules. Main thread only.
 *
 * Layer order, bottom to top: the map style (labels included) → fog → vein → route casing, line, dash,
 * ends → markers. The fog is opaque enough that whatever lies under it does not exist for the eye, so
 * the vein, the route and the markers go above it.
 */
internal class MapController(
    private val mapView: MapView,
    private val state: MapHostState,
    private val density: Float,
    styleUri: String,
) {
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private val routeLayers = RouteLayers()
    private val veinLayers = VeinLayers()
    private val markerLayers = MarkerLayers(density)
    private var fogLayer: FogLayer? = null
    private var fogTried = false

    private var fog: MapFog? = null
    private var routes: MapRoutes? = null
    private var vein: MapVein? = null
    private var markers: List<MapMarker> = emptyList()
    var onMarkerTap: ((MapMarker) -> Unit)? = null

    /** A tap that hit no marker; null leaves the tap to MapLibre. */
    var onMapTap: ((MapTap) -> Unit)? = null
    private var showsUserLocation = false

    /** System bars plus the screen's chrome, in dp: what MapKit would call the safe area. */
    private var safeArea = MapInsets.Zero
    private val pendingCamera = ArrayList<MapCameraCommand>()
    private var defaults: Margins? = null
    private var viewState = Lifecycle.State.CREATED

    init {
        mapView.addOnLayoutChangeListener(View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> flushCamera() })
        mapView.getMapAsync { ready ->
            map = ready
            // No tilt: the fog's frame is affine only without perspective (iOS: isPitchEnabled = false).
            // Rotation stays: an affine frame expresses it exactly.
            ready.uiSettings.isTiltGesturesEnabled = false
            ready.uiSettings.isRotateGesturesEnabled = true
            ready.setMaxPitchPreference(0.0)
            // MapLibre's logo and attribution are views above the map, so the fog never covers them
            // and the carve window iOS needed under Apple's attribution has nothing to do here.
            ready.uiSettings.isLogoEnabled = true
            ready.uiSettings.isAttributionEnabled = true
            defaults = Margins.of(ready)
            applyMargins()
            ready.addOnCameraIdleListener { publishCamera() }
            ready.addOnMapClickListener { at ->
                val hit = markerLayers.hit(ready, ready.projection.toScreenLocation(at))
                if (hit != null) {
                    onMarkerTap?.invoke(hit)
                    return@addOnMapClickListener true
                }
                val tap = onMapTap ?: return@addOnMapClickListener false
                tap(MapTap(GeoPoint(at.latitude, at.longitude), ready.cameraPosition.zoom))
                true
            }
            ready.setStyle(Style.Builder().fromUri(styleUri)) { loaded -> onStyleLoaded(loaded) }
            flushCamera()
        }
    }

    private fun onStyleLoaded(loaded: Style) {
        style = loaded
        if (fog != null) ensureFog()?.addTo(loaded)
        veinLayers.addTo(loaded, vein)
        routeLayers.addTo(loaded, routes)
        markerLayers.addTo(loaded, markers)
        applyUserLocation()
    }

    // Inputs

    fun setFog(value: MapFog?) {
        // The layer by identity, as FogMeshSignature does: a data class comparison would walk every
        // point of a large library on the main thread at each recomposition.
        val previous = fog
        if (value === previous || (value != null && previous != null && value.revealed === previous.revealed &&
                value.look == previous.look && value.liveReveal == previous.liveReveal)
        ) {
            return
        }
        fog = value
        val layer = if (value != null) ensureFog() else fogLayer
        layer ?: return
        if (value == null) {
            layer.setVisible(false)
            return
        }
        layer.setLook(value.look, visible = true)
        layer.setRevealed(value.revealed)
        val reveal = value.liveReveal
        layer.setLiveReveal(reveal?.point, reveal?.progress ?: 0.0, reveal?.metres ?: FogRules.REVEAL_METRES)
    }

    fun setRoutes(value: MapRoutes?) {
        if (value == routes) return
        routes = value
        style?.let { routeLayers.update(it, value) }
    }

    fun setVein(value: MapVein?) {
        if (value === vein) return
        vein = value
        style?.let { veinLayers.update(it, value) }
    }

    fun setMarkers(value: List<MapMarker>) {
        if (value == markers) return
        markers = value
        style?.let { markerLayers.update(it, value) }
    }

    fun setSafeArea(value: MapInsets) {
        if (value == safeArea) return
        safeArea = value
        applyMargins()
    }

    /** The fog layer, created on first need: until a screen asks for fog, nothing native exists. */
    private fun ensureFog(): FogLayer? {
        fogLayer?.let { return it }
        if (fogTried) return null
        fogTried = true
        val layer = FogLayer.create()
        state.fogAvailable = layer != null
        layer ?: return null
        layer.onRepaint = { map?.triggerRepaint() }
        layer.onMeshInstalled = { state.fogMesh = it }
        fogLayer = layer
        // A fog asked for after the style loaded goes under our own layers, still above the map's.
        style?.let { layer.addTo(it, below = veinLayers.firstLayerId) }
        return layer
    }

    // User location (iOS: MKMapView.showsUserLocation, the map's own dot and its own location source)

    fun setShowsUserLocation(value: Boolean) {
        if (value == showsUserLocation) return
        showsUserLocation = value
        applyUserLocation()
    }

    /**
     * The map's own location dot, as on iOS where MapKit draws it from its own geolocation: the screen
     * never starts GPS for it. Needs the style and a location permission; without either it stays off.
     */
    private fun applyUserLocation() {
        val ready = map ?: return
        val loaded = style ?: return
        val component = ready.locationComponent
        try {
            if (showsUserLocation && hasLocationPermission()) {
                if (!component.isLocationComponentActivated) {
                    component.activateLocationComponent(LocationComponentActivationOptions.builder(mapView.context, loaded).build())
                }
                component.isLocationComponentEnabled = true
                component.renderMode = RenderMode.NORMAL
            } else if (component.isLocationComponentActivated) {
                component.isLocationComponentEnabled = false
            }
        } catch (_: SecurityException) {
            // The permission went away between the check and the call: no dot, no crash.
        }
    }

    /** iOS MapCameraCommand.userLocation: follow the person; the next pan of a finger ends it. */
    fun locateUser(): Boolean {
        val ready = map ?: return false
        val component = ready.locationComponent
        if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return false
        return try {
            component.cameraMode = CameraMode.TRACKING
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasLocationPermission(): Boolean {
        val context = mapView.context
        return listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION).any {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    fun fogStats(): FogStats? = fogLayer?.stats()

    fun setFogDiagnostics(enabled: Boolean) {
        fogLayer?.setDiagnostics(enabled)
    }

    // Lifecycle

    /** Moves the MapView one step at a time to [target], never past what it has been given. */
    fun moveTo(target: Lifecycle.State) {
        while (viewState < target && viewState != Lifecycle.State.DESTROYED) {
            when (viewState) {
                Lifecycle.State.CREATED -> {
                    // Frames may touch the GPU again before MapLibre resumes its thread.
                    fogLayer?.onStart()
                    mapView.onStart()
                    viewState = Lifecycle.State.STARTED
                }
                Lifecycle.State.STARTED -> {
                    mapView.onResume()
                    viewState = Lifecycle.State.RESUMED
                }
                else -> return
            }
        }
        while (viewState > target) {
            when (viewState) {
                Lifecycle.State.RESUMED -> {
                    mapView.onPause()
                    viewState = Lifecycle.State.STARTED
                }
                Lifecycle.State.STARTED -> {
                    // Release before MapLibre pauses its GL thread: afterwards no GL call can run.
                    fogLayer?.onStop(mapView)
                    mapView.onStop()
                    viewState = Lifecycle.State.CREATED
                }
                Lifecycle.State.CREATED -> {
                    destroy()
                    return
                }
                else -> return
            }
        }
    }

    /** Memory is short (onTrimMemory / onLowMemory): the fog gives its coverage texture back. */
    fun trimMemory(lowMemory: Boolean) {
        fogLayer?.let { if (!mapView.isDestroyed) it.releaseCoverage(mapView) }
        if (lowMemory && !mapView.isDestroyed) mapView.onLowMemory()
    }

    private fun destroy() {
        if (viewState == Lifecycle.State.DESTROYED) return
        if (viewState >= Lifecycle.State.STARTED) moveTo(Lifecycle.State.CREATED)
        viewState = Lifecycle.State.DESTROYED
        fogLayer?.close()
        fogLayer = null
        mapView.onDestroy()
        map = null
        style = null
    }

    fun dispose() {
        moveTo(Lifecycle.State.DESTROYED)
    }

    // Camera

    fun execute(command: MapCameraCommand) {
        pendingCamera += command
        flushCamera()
    }

    private fun flushCamera() {
        val ready = map ?: return
        if (mapView.width <= 1 || mapView.height <= 1 || pendingCamera.isEmpty()) return
        val commands = pendingCamera.toList()
        pendingCamera.clear()
        for (command in commands) apply(ready, command)
    }

    private fun apply(ready: MapLibreMap, command: MapCameraCommand) {
        val size = MapSize(mapView.width / density.toDouble(), mapView.height / density.toDouble())
        when (command) {
            is MapCameraCommand.Fit ->
                frame(ready, CameraFraming.atlasRect(command.bounds), safeArea + command.padding.insets(safeArea, size), command.animated)
            is MapCameraCommand.FitRoute ->
                frame(ready, CameraFraming.routeRect(command.bounds), safeArea + command.insets, command.animated)
            is MapCameraCommand.Move -> {
                val update = CameraUpdateFactory.newLatLngZoom(LatLng(command.centre.latitude, command.centre.longitude), command.zoom)
                if (command.animated) ready.animateCamera(update, CAMERA_MS) else ready.moveCamera(update)
            }
        }
    }

    /**
     * Fits a map rect into the view minus [insets] (dp), keeping the heading. The insets are the TOTAL
     * MapKit would use: the safe area plus the screen's own addition (see MapCameraCommand.Padding).
     */
    private fun frame(ready: MapLibreMap, rect: MapRect, insets: MapInsets, animated: Boolean) {
        val bounds = MapPoints.bounds(rect)
        val box = LatLngBounds.from(bounds.maxLat, bounds.maxLon, bounds.minLat, bounds.minLon)
        val padding = intArrayOf(px(insets.left), px(insets.top), px(insets.right), px(insets.bottom))
        val position = ready.getCameraForLatLngBounds(box, padding, ready.cameraPosition.bearing, 0.0) ?: return
        val update = CameraUpdateFactory.newCameraPosition(position)
        if (animated) ready.animateCamera(update, CAMERA_MS) else ready.moveCamera(update)
    }

    private fun publishCamera() {
        val ready = map ?: return
        val position = ready.cameraPosition
        val target = position.target ?: return
        state.camera = MapCamera(
            centre = GeoPoint(target.latitude, target.longitude),
            zoom = position.zoom,
            bearing = position.bearing,
            metresPerPoint = MapPoints.metresPerPoint(target.latitude, position.zoom),
            latitudeSpan = ready.projection.visibleRegion.latLngBounds.latitudeSpan,
        )
    }

    // Logo, attribution and compass stay clear of the system bars and the screen's chrome.

    private data class Margins(val logo: IntArray, val attribution: IntArray, val compass: IntArray) {
        companion object {
            fun of(map: MapLibreMap): Margins = with(map.uiSettings) {
                Margins(
                    intArrayOf(logoMarginLeft, logoMarginTop, logoMarginRight, logoMarginBottom),
                    intArrayOf(attributionMarginLeft, attributionMarginTop, attributionMarginRight, attributionMarginBottom),
                    intArrayOf(compassMarginLeft, compassMarginTop, compassMarginRight, compassMarginBottom),
                )
            }
        }
    }

    private fun applyMargins() {
        val ready = map ?: return
        val base = defaults ?: return
        val left = px(safeArea.left)
        val top = px(safeArea.top)
        val right = px(safeArea.right)
        val bottom = px(safeArea.bottom)
        with(ready.uiSettings) {
            setLogoMargins(base.logo[0] + left, base.logo[1], base.logo[2], base.logo[3] + bottom)
            setAttributionMargins(base.attribution[0] + left, base.attribution[1], base.attribution[2], base.attribution[3] + bottom)
            setCompassMargins(base.compass[0], base.compass[1] + top, base.compass[2] + right, base.compass[3])
        }
    }

    private fun px(dp: Double): Int = (dp * density).roundToInt()

    private companion object {
        const val CAMERA_MS = 600
    }
}
