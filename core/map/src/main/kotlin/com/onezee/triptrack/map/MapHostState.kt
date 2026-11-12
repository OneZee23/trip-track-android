package com.onezee.triptrack.map

import android.graphics.PointF
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.fog.FogLook
import com.onezee.triptrack.map.fog.FogMeshInfo
import com.onezee.triptrack.map.fog.FogRules
import com.onezee.triptrack.map.fog.FogStats
import com.onezee.triptrack.model.GeoPoint

/** The fog on a map: the revealed world, how it looks, and the live hole around the car if any. */
data class MapFog(
    val revealed: RevealedLayer,
    val look: FogLook = FogLook(),
    val liveReveal: LiveReveal? = null,
)

/** iOS: FogMetalVeil.setLiveReveal. [progress] grows 0 → 1 over the reveal animation. */
data class LiveReveal(val point: GeoPoint, val progress: Double, val metres: Double = FogRules.REVEAL_METRES)

/**
 * Where the camera stands after a move: MapLibre zoom, heading, the scale at the centre and how many
 * degrees of latitude the view shows (iOS `region.span.latitudeDelta`, which the Atlas' zoom levels
 * are counted in).
 */
data class MapCamera(
    val centre: GeoPoint,
    val zoom: Double,
    val bearing: Double,
    val metresPerPoint: Double,
    val latitudeSpan: Double = 0.0,
)

/**
 * A tap on the map that hit no marker: where it landed and at what zoom, so a screen can run its own
 * hit tests (a road under the finger, the region behind it) in map points.
 */
data class MapTap(val coordinate: GeoPoint, val zoom: Double)

/**
 * iOS draws every map with Apple Maps' standard map; TripTrack's two styles are drawn after it (tools/mapstyle) on
 * OpenFreeMap's vector tiles, which stay free, keyless and accountless. The styles ship as assets; tiles, fonts and the
 * shield sprite still come from OpenFreeMap.
 */
object MapStyles {
    const val LIGHT = "asset://maps/triptrack-light.json"
    const val DARK = "asset://maps/triptrack-dark.json"

    fun of(dark: Boolean): String = if (dark) DARK else LIGHT
}

/**
 * The handle a screen keeps for its [MapHost]: camera commands and what the map reports back. Commands
 * given before the map is ready and laid out wait for it (iOS: a camera applied to an MKMapView with a
 * zero frame lands at a broken zoom). Main thread only.
 */
@Stable
class MapHostState {
    private val pending = ArrayList<MapCameraCommand>()

    internal var controller: MapController? = null
        set(value) {
            field = value
            if (value != null) {
                pending.forEach(value::execute)
                pending.clear()
            }
        }

    fun execute(command: MapCameraCommand) {
        controller?.execute(command) ?: pending.add(command)
    }

    fun screenPosition(point: GeoPoint): PointF? = controller?.screenPosition(point)

    fun coordinateAt(x: Float, y: Float): GeoPoint? = controller?.coordinateAt(x, y)

    /** The camera after the last completed move; null until the map has one. */
    var camera: MapCamera? by mutableStateOf(null)
        internal set

    var projectionRevision: Int by mutableStateOf(0)
        internal set

    /** iOS: didChange userTrackingMode. Only a person's gesture cancels camera following. */
    var gestureRevision: Int by mutableStateOf(0)
        internal set

    /** The mesh the fog draws now; null until the first one is built. */
    var fogMesh: FogMeshInfo? by mutableStateOf(null)
        internal set

    /** Null until known; false when this device cannot draw the fog and the map shows without it. */
    var fogAvailable: Boolean? by mutableStateOf(null)
        internal set

    /**
     * iOS MapCameraCommand.userLocation: frame 3 km around the map's last fix, or follow the person
     * until the next pan when there is none yet. False when the map has no location at all: no
     * permission, or the dot is not shown.
     */
    fun locateUser(): Boolean = controller?.locateUser() ?: false

    /** Native counters of the fog layer; for the debug screen. */
    fun fogStats(): FogStats? = controller?.fogStats()

    /** GPU timer queries in the fog layer, where the driver has them; for the debug screen. */
    fun setFogDiagnostics(enabled: Boolean) {
        controller?.setFogDiagnostics(enabled)
    }
}
