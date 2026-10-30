package com.onezee.triptrack.map.fog

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.model.GeoPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CustomLayer

/** What the last installed mesh holds; for the demo screen and the report. */
data class FogMeshInfo(
    val segments: List<Int>,
    val chunks: List<Int>,
    val byteSize: Int,
    val buildMs: Double,
)

/**
 * iOS: FogMetalVeil. The Atlas fog of war, rebuilt every frame on the GPU from the segments of the
 * revealed world: nothing to stretch, reload or cut into tiles, which is what the raster veil of
 * 0.7.0 did and the owner rejected.
 *
 * The picture is drawn by the native custom layer inside MapLibre's frame; this class owns its state:
 * the mesh (built off the main thread, newest wins), the look, the live hole around the car, and the
 * rules about the background and memory. Everything except the mesh build runs on the main thread.
 */
class FogLayer private constructor(private val state: Long) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    // Guards the native state against close(): a build finishing after close() must not touch it.
    private val lock = Any()
    private val token = AtomicInteger()
    private var closed = false
    private var installedSignature: FogMeshSignature? = null

    /** Asked on the main thread whenever the fog changed and MapLibre has to draw a frame. */
    var onRepaint: (() -> Unit)? = null

    /** Called on the main thread when a new mesh reached the native layer. */
    var onMeshInstalled: ((FogMeshInfo) -> Unit)? = null

    /**
     * Adds the layer on top of every layer of [style], labels included, as the veil sits above MapKit's
     * map on iOS: under a fog, anything not above it does not exist for the eye. Each style needs a new
     * host; MapLibre owns it and deletes it with the layer.
     */
    fun addTo(style: Style, below: String? = null) {
        val host = synchronized(lock) { if (closed) return else NativeFog.nCreateHost(state) }
        val layer = CustomLayer(LAYER_ID, host)
        if (below != null && style.getLayer(below) != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
    }

    /**
     * The revealed world. The buffers are built off the main thread and swapped in whole; a layer that
     * a newer one overtook never installs its buffers (iOS: meshToken). The same layer twice builds
     * nothing ([FogMeshSignature]).
     */
    fun setRevealed(layer: RevealedLayer) {
        val signature = FogMeshSignature.of(layer)
        if (signature == installedSignature) return
        installedSignature = signature
        val mine = token.incrementAndGet()
        builder.execute {
            if (mine != token.get()) return@execute
            val started = SystemClock.elapsedRealtimeNanos()
            val mesh = FogMesh.build(layer)
            val buildMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
            val staged = synchronized(lock) {
                if (closed || mine != token.get()) return@execute
                val ok = RevealedLayer.LOD.entries.all { lod ->
                    val data = mesh.lod(lod)
                    NativeFog.nStageLod(state, lod.ordinal, data.segments, data.chunks, data.ranges)
                }
                if (ok) NativeFog.nCommit(state)
                ok
            }
            if (!staged) {
                Log.e(TAG, "the native layer refused a mesh")
                return@execute
            }
            val info = FogMeshInfo(
                segments = RevealedLayer.LOD.entries.map { mesh.lod(it).segmentCount },
                chunks = RevealedLayer.LOD.entries.map { mesh.lod(it).chunkCount },
                byteSize = mesh.byteSize,
                buildMs = buildMs,
            )
            main.post {
                if (isClosed()) return@post
                onMeshInstalled?.invoke(info)
                onRepaint?.invoke()
            }
        }
    }

    /**
     * Palette, cells and coverage resolution as one value, and one frame for all of them: a style set
     * in two steps once showed a frame that belongs to no style (see [FogLook]).
     */
    fun setLook(look: FogLook, visible: Boolean = true) {
        val p = look.palette
        synchronized(lock) {
            if (closed) return
            NativeFog.nSetStyle(
                state, p.red / 255f, p.green / 255f, p.blue / 255f, p.alpha, look.cells, look.halfResolutionCoverage, visible,
            )
        }
        onRepaint?.invoke()
    }

    /**
     * The hole around the car on a live recording (iOS: FogMetalVeil.setLiveReveal): tied to the
     * GROUND, so the native layer projects it every frame. Two numbers in a uniform and no mesh
     * rebuild, however often the reveal grows. [progress] 0 or a null [point] removes it.
     */
    fun setLiveReveal(point: GeoPoint?, progress: Double, metres: Double = FogRules.REVEAL_METRES) {
        val radius = if (point != null && progress > 0) metres * progress else 0.0
        synchronized(lock) {
            if (closed) return
            NativeFog.nSetReveal(state, point?.latitude ?: 0.0, point?.longitude ?: 0.0, radius)
        }
        onRepaint?.invoke()
    }

    /** GPU timer queries for the demo screen; off by default. */
    fun setDiagnostics(enabled: Boolean) {
        synchronized(lock) { if (!closed) NativeFog.nSetDiagnostics(state, enabled) }
    }

    /**
     * The map became visible again: frames may touch the GPU, and the one skipped in the background
     * is asked for anew (iOS: draws on didBecomeActive, not on willEnterForeground).
     */
    fun onStart() {
        synchronized(lock) { if (!closed) NativeFog.nSetSuspended(state, false) }
        onRepaint?.invoke()
    }

    /**
     * The map is about to stop: IN THE BACKGROUND THE GPU IS NOT TOUCHED. MapLibre pauses its GL thread
     * in `MapView.onStop`; the coverage texture is given back first, on that thread, because Android
     * sends memory trims after the thread is paused, when no GL call can reach the context.
     */
    fun onStop(mapView: MapView) {
        releaseCoverage(mapView)
        synchronized(lock) { if (!closed) NativeFog.nSetSuspended(state, true) }
    }

    /**
     * Memory is short: the coverage texture goes back (iOS: didReceiveMemoryWarning). It is the only
     * thing held besides the mesh; the next frame makes a new one. Runs on MapLibre's GL thread and
     * does nothing when that thread is paused.
     */
    fun releaseCoverage(mapView: MapView) {
        if (mapView.isDestroyed) return
        try {
            mapView.queueEvent {
                synchronized(lock) { if (!closed) NativeFog.nReleaseCoverage(state) }
            }
        } catch (_: IllegalStateException) {
            // No renderer yet: nothing has been drawn, so nothing is held.
        }
    }

    fun stats(): FogStats? = synchronized(lock) { if (closed) null else FogStats.parse(NativeFog.nStats(state)) }

    private fun isClosed(): Boolean = synchronized(lock) { closed }

    /** Drops Kotlin's reference to the native state; hosts MapLibre still owns keep theirs. */
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            token.incrementAndGet()
            NativeFog.nRelease(state)
        }
        onRepaint = null
        onMeshInstalled = null
    }

    companion object {
        const val LAYER_ID = "triptrack-fog"
        private const val TAG = "TripTrackFog"

        // One background thread for every map: builds are rare and a newer one makes an older one moot.
        private val builder: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "fog-mesh").apply { isDaemon = true }
        }

        /**
         * Null when this device cannot draw the fog: the native library is missing, or the loaded
         * MapLibre is not the one the C++ host was built for. The map then shows without fog, the way
         * iOS stays on the raster veil when Metal is unavailable.
         */
        fun create(): FogLayer? {
            if (!NativeFog.isLoaded) return null
            if (!MapLibreAbi.matchesLoaded) {
                Log.e(TAG, "MapLibre ${MapLibreAbi.loaded()} is not ${MapLibreAbi.pinnedVersion} (opengl): no fog")
                return null
            }
            val state = NativeFog.nCreate(FogRules.nativeRules())
            return if (state == 0L) null else FogLayer(state)
        }
    }
}
