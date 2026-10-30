package com.onezee.triptrack.fogspike.candidates

import android.graphics.Bitmap
import android.os.SystemClock
import com.onezee.triptrack.fogspike.data.RevealedLayerLite
import com.onezee.triptrack.fogspike.fog.FogMesh
import com.onezee.triptrack.fogspike.fog.FogPalette
import com.onezee.triptrack.fogspike.fog.FogRules
import com.onezee.triptrack.fogspike.fog.FogStyle
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * C: a raster mask served as map tiles. Tiles are drawn on the phone (same corridor, feather
 * and max-union rules as A, in software) and handed to MapLibre through an OkHttp interceptor,
 * so the map treats them like any raster source: drawn for whole zoom levels, scaled between
 * them, faded in when they arrive. This is the 0.7.0 MKOverlayRenderer approach the owner
 * rejected, measured again on Android.
 */
class RasterTileFog : FogCandidate {
    override val key = "c"
    private var renderer: TileRenderer? = null
    private var revision = 0

    override fun install(map: MapLibreMap, style: Style, layer: RevealedLayerLite, mesh: FogMesh, fogStyle: FogStyle, frames: Long) {
        val tiles = TileRenderer(mesh, fogStyle.palette)
        renderer = tiles
        FogTileInterceptor.renderer = tiles
        addSource(style)
        FogCandidate.addProbe(style, frames)
    }

    override fun setFogStyle(style: Style, fogStyle: FogStyle) {
        val current = renderer ?: return
        // Tiles bake the palette in, so a new palette is a new tile set.
        val tiles = TileRenderer(current.mesh, fogStyle.palette)
        renderer = tiles
        FogTileInterceptor.renderer = tiles
        style.removeLayer(LAYER_ID)
        style.removeSource(SOURCE_ID)
        revision += 1
        addSource(style, below = "frame-probe")
    }

    override fun report(): Map<String, Any> = renderer?.report() ?: emptyMap()

    override fun release() {
        FogTileInterceptor.renderer = null
    }

    private fun addSource(style: Style, below: String? = null) {
        val tileSet = TileSet("2.2.0", "https://${FogTileInterceptor.HOST}/$revision/{z}/{x}/{y}.png")
        tileSet.setMaxZoom(20f)
        style.addSource(RasterSource(SOURCE_ID, tileSet, 256))
        val raster = RasterLayer(LAYER_ID, SOURCE_ID)
        if (below != null && style.getLayer(below) != null) style.addLayerBelow(raster, below) else style.addLayer(raster)
    }

    companion object {
        const val SOURCE_ID = "fog-c"
        const val LAYER_ID = "fog-c"
    }
}

/** Serves fog tiles for a host that never resolves; every other request goes to the network. */
object FogTileInterceptor : Interceptor {
    // A reserved top-level domain: the request can never leave the phone by mistake.
    const val HOST = "fog.invalid"

    @Volatile
    var renderer: TileRenderer? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != HOST) return chain.proceed(request)
        val segments = request.url.pathSegments
        val bytes = runCatching {
            val z = segments[1].toInt()
            val x = segments[2].toInt()
            val y = segments[3].removeSuffix(".png").toInt()
            renderer?.render(z, x, y)
        }.getOrNull()
        val builder = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        return if (bytes == null) {
            builder.code(404).message("No fog").body(ByteArray(0).toResponseBody(null)).build()
        } else {
            builder.code(200).message("OK")
                .header("Cache-Control", "no-store")
                .body(bytes.toResponseBody(PNG))
                .build()
        }
    }

    private val PNG = "image/png".toMediaType()
}

/** Draws one fog tile: 512 px for a 256 dp tile, coverage by capsule SDF with max union. */
class TileRenderer(val mesh: FogMesh, private val palette: FogPalette) {
    private val tiles = ConcurrentLinkedQueue<DoubleArray>()
    private val inFlight = AtomicInteger()
    private val peakInFlight = AtomicInteger()

    fun render(z: Int, x: Int, y: Int): ByteArray {
        val running = inFlight.incrementAndGet()
        peakInFlight.accumulateAndGet(running) { a, b -> max(a, b) }
        try {
            val started = SystemClock.elapsedRealtimeNanos()
            val size = PIXELS
            val tileMapSize = FogRules.WORLD / 2.0.pow(z)
            val minX = x * tileMapSize
            val minY = y * tileMapSize
            val pxPerMapPoint = size / tileMapSize
            // A 256 dp tile of zoom z is drawn 1:1 at MapLibre zoom z - 1 (MapLibre counts 512 dp tiles).
            val displayZoom = z - 1.0
            val lat = FogRules.latOf(minY + tileMapSize / 2)
            val metresPerPoint = FogRules.metresPerPoint(lat, displayZoom)
            val halfWidth = FogRules.haloHalfWidthMetres(metresPerPoint) / metresPerPoint * (size / 256.0)
            val feather = halfWidth * FogRules.FEATHER_RATIO
            val inner = halfWidth - feather
            val lod = mesh.lods[FogRules.lod(FogRules.pointsPerMapPoint(displayZoom))]
            val pad = (halfWidth + feather) / pxPerMapPoint
            val coverage = FloatArray(size * size)
            var drawn = 0
            for (c in 0 until lod.chunkCount) {
                val b = 6 * c
                if (lod.bounds[b + 4] < minX - pad || lod.bounds[b + 2] > minX + tileMapSize + pad ||
                    lod.bounds[b + 5] < minY - pad || lod.bounds[b + 3] > minY + tileMapSize + pad
                ) continue
                val ox = (lod.bounds[b] - minX) * pxPerMapPoint
                val oy = (lod.bounds[b + 1] - minY) * pxPerMapPoint
                val first = lod.ranges[2 * c]
                val count = lod.ranges[2 * c + 1]
                for (s in first until first + count) {
                    val ax = ox + lod.segments[4 * s] * pxPerMapPoint
                    val ay = oy + lod.segments[4 * s + 1] * pxPerMapPoint
                    val bx = ox + lod.segments[4 * s + 2] * pxPerMapPoint
                    val by = oy + lod.segments[4 * s + 3] * pxPerMapPoint
                    val x0 = max(0, floor(min(ax, bx) - halfWidth).toInt())
                    val x1 = min(size - 1, floor(max(ax, bx) + halfWidth).toInt())
                    val y0 = max(0, floor(min(ay, by) - halfWidth).toInt())
                    val y1 = min(size - 1, floor(max(ay, by) + halfWidth).toInt())
                    if (x0 > x1 || y0 > y1) continue
                    drawn += 1
                    val dx = bx - ax
                    val dy = by - ay
                    val bb = dx * dx + dy * dy
                    for (py in y0..y1) {
                        val cy = py + 0.5
                        for (px in x0..x1) {
                            val cx = px + 0.5
                            val pax = cx - ax
                            val pay = cy - ay
                            val h = if (bb > 1e-9) ((pax * dx + pay * dy) / bb).coerceIn(0.0, 1.0) else 0.0
                            val ex = pax - dx * h
                            val ey = pay - dy * h
                            val d = sqrt(ex * ex + ey * ey)
                            if (d >= halfWidth) continue
                            val value = if (d <= inner) 1f else (1.0 - smoothstep(inner, halfWidth, d)).toFloat()
                            val i = py * size + px
                            if (value > coverage[i]) coverage[i] = value
                        }
                    }
                }
            }
            val pixels = IntArray(size * size)
            val rgb = (palette.red shl 16) or (palette.green shl 8) or palette.blue
            for (i in pixels.indices) {
                val a = (palette.alpha * (1f - coverage[i]) * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[i] = (a shl 24) or rgb
            }
            val rendered = SystemClock.elapsedRealtimeNanos()
            val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream(64 * 1024)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            val encoded = SystemClock.elapsedRealtimeNanos()
            tiles += doubleArrayOf(z.toDouble(), (rendered - started) / 1e6, (encoded - rendered) / 1e6, drawn.toDouble())
            return out.toByteArray()
        } finally {
            inFlight.decrementAndGet()
        }
    }

    fun report(): Map<String, Any> {
        val list = tiles.toList()
        return mapOf(
            "tiles" to list.size,
            "renderMs" to list.map { it[1] },
            "encodeMs" to list.map { it[2] },
            "segmentsPerTile" to list.map { it[3] },
            "zoomOfTile" to list.map { it[0] },
            "peakConcurrentTiles" to peakInFlight.get(),
        )
    }

    private fun smoothstep(edge0: Double, edge1: Double, x: Double): Double {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    companion object {
        const val PIXELS = 512
    }
}
