package com.onezee.triptrack.fogspike.candidates

import com.onezee.triptrack.fogspike.data.RevealedLayerLite
import com.onezee.triptrack.fogspike.fog.FogMesh
import com.onezee.triptrack.fogspike.fog.FogStyle
import com.onezee.triptrack.fogspike.fog.NativeFog
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

/** One way of drawing the fog. Every candidate gets the same revealed layer and mesh. */
interface FogCandidate {
    val key: String

    /**
     * Called on the main thread once the style has loaded. [frames] is a native fog state whose
     * render callback timestamps every map frame: candidate A draws with it, the others add an
     * empty probe layer, so frame times are measured the same way for all of them.
     */
    fun install(map: MapLibreMap, style: Style, layer: RevealedLayerLite, mesh: FogMesh, fogStyle: FogStyle, frames: Long)

    fun setFogStyle(style: Style, fogStyle: FogStyle)

    /** Whether the fog for the current camera is on its way to the screen. */
    fun isReady(): Boolean = true

    /** Numbers the candidate measured itself: build times, tile times, vertex counts. */
    fun report(): Map<String, Any> = emptyMap()

    fun release() {}

    companion object {
        fun of(key: String?): FogCandidate = when (key) {
            "b" -> PolygonFog()
            "c" -> RasterTileFog()
            "none" -> NoFog()
            else -> CustomLayerFog()
        }

        val KEYS = listOf("a", "b", "c", "none")

        fun addProbe(style: Style, frames: Long) {
            style.addLayer(CustomLayer("frame-probe", NativeFog.createHost(frames, true)))
        }
    }
}

/** The map alone, for the cost of the map itself. */
class NoFog : FogCandidate {
    override val key = "none"

    override fun install(map: MapLibreMap, style: Style, layer: RevealedLayerLite, mesh: FogMesh, fogStyle: FogStyle, frames: Long) {
        FogCandidate.addProbe(style, frames)
    }

    override fun setFogStyle(style: Style, fogStyle: FogStyle) {}
}

/** A: the iPhone 0.8.0 Metal fog ported to GLES and drawn inside MapLibre's frame. */
class CustomLayerFog : FogCandidate {
    override val key = "a"
    private var state = 0L
    private var layer: CustomLayer? = null

    override fun install(map: MapLibreMap, style: Style, layer: RevealedLayerLite, mesh: FogMesh, fogStyle: FogStyle, frames: Long) {
        state = frames
        NativeFog.upload(state, mesh)
        NativeFog.setStyle(state, fogStyle)
        // On top of every map layer, labels included, as the veil on iOS sits above MapKit's map.
        val custom = CustomLayer(LAYER_ID, NativeFog.createHost(state, false))
        style.addLayer(custom)
        this.layer = custom
    }

    override fun setFogStyle(style: Style, fogStyle: FogStyle) {
        NativeFog.setStyle(state, fogStyle)
        layer?.update()
    }

    override fun report(): Map<String, Any> {
        val info = NativeFog.info(state)
        return mapOf(
            "gpuTimer" to (info[0] > 0),
            "uploadMs" to info[1],
            "uploadBytes" to info[2],
            "centreErrorDp" to info[3],
            "shaderFailed" to (info[4] > 0),
        )
    }

    companion object {
        const val LAYER_ID = "fog-a"
    }
}
