package com.onezee.triptrack.fogspike.candidates

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.onezee.triptrack.fogspike.data.RevealedLayerLite
import com.onezee.triptrack.fogspike.fog.FogMesh
import com.onezee.triptrack.fogspike.fog.FogRules
import com.onezee.triptrack.fogspike.fog.FogStyle
import org.locationtech.jts.algorithm.Orientation
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.buffer.BufferOp
import org.locationtech.jts.operation.buffer.BufferParameters
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.fillAntialias
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/**
 * B: style layers only. The fog is one polygon, the world with the revealed corridors cut out as
 * holes, rebuilt per zoom band off the main thread (buffer and union with JTS) and drawn by
 * MapLibre's own fill layer, so it moves with the map. What a polygon cannot do: a feather (its
 * edge is hard), and a corridor width that follows the zoom continuously — inside a band the
 * holes scale with the map, at the band edge the geometry is replaced.
 */
class PolygonFog : FogCandidate {
    override val key = "b"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val builds = Collections.synchronizedList(mutableListOf<Map<String, Any>>())
    private lateinit var map: MapLibreMap
    private lateinit var revealed: RevealedLayerLite
    private var source: GeoJsonSource? = null
    private var fill: FillLayer? = null
    private var band = Int.MIN_VALUE
    private var shownBand = Int.MIN_VALUE
    private var generation = 0

    override fun install(map: MapLibreMap, style: Style, layer: RevealedLayerLite, mesh: FogMesh, fogStyle: FogStyle, frames: Long) {
        this.map = map
        revealed = layer
        val geojson = GeoJsonSource(SOURCE_ID, GeoJsonOptions().withTolerance(0.375f).withBuffer(32))
        style.addSource(geojson)
        val fogFill = FillLayer(LAYER_ID, SOURCE_ID).withProperties(
            fillColor(fogStyle.palette.argb),
            fillOpacity(fogStyle.palette.alpha),
            fillAntialias(true),
        )
        style.addLayer(fogFill)
        FogCandidate.addProbe(style, frames)
        source = geojson
        fill = fogFill
        map.addOnCameraMoveListener { check() }
        map.addOnCameraIdleListener { check() }
        check()
    }

    override fun setFogStyle(style: Style, fogStyle: FogStyle) {
        fill?.setProperties(fillColor(fogStyle.palette.argb), fillOpacity(fogStyle.palette.alpha))
    }

    override fun isReady(): Boolean = shownBand == band

    override fun report(): Map<String, Any> = mapOf("builds" to builds.toList())

    override fun release() {
        worker.shutdownNow()
    }

    private fun check() {
        val zoom = map.cameraPosition.zoom
        val next = floor(zoom).toInt()
        if (next == band) return
        band = next
        val gen = ++generation
        val requested = SystemClock.elapsedRealtime()
        worker.execute {
            // JTS on a mature library can run out of heap; a failed band is a result, not a crash.
            try {
                build(next, gen, requested)
            } catch (failure: Throwable) {
                builds += mapOf("band" to next, "error" to failure.toString())
            }
        }
    }

    private fun build(band: Int, gen: Int, requested: Long) {
        if (gen != generation) return
        val started = SystemClock.elapsedRealtimeNanos()
        // The band's middle sets the width; at its edges the true width is off by up to √2.
        val zoom = band + 0.5
        val lat0 = revealed.fine.firstOrNull()?.get(0) ?: 45.0
        val lon0 = revealed.fine.firstOrNull()?.get(1) ?: 39.0
        val metresPerPoint = FogRules.metresPerPoint(lat0, zoom)
        val halfWidth = FogRules.haloHalfWidthMetres(metresPerPoint)
        val lod = FogRules.lod(FogRules.pointsPerMapPoint(zoom))
        val kx = 111_320.0 * cos(lat0 * PI / 180)
        val ky = 111_320.0
        val factory = GeometryFactory()
        // Lines are thinned first: buffering every 45 m vertex of a city at country zoom is wasted.
        val lines = revealed.lod(lod).map { run ->
            val coords = Array(run.size / 2) { i -> Coordinate((run[2 * i + 1] - lon0) * kx, (run[2 * i] - lat0) * ky) }
            DouglasPeuckerSimplifier.simplify(factory.createLineString(coords), halfWidth * 0.25) as LineString
        }
        val inputVertices = lines.sumOf { it.numPoints }
        val params = BufferParameters(3, BufferParameters.CAP_ROUND, BufferParameters.JOIN_ROUND, 5.0)
        val union = BufferOp.bufferOp(factory.createMultiLineString(lines.toTypedArray()), halfWidth, params)
        val buffered = SystemClock.elapsedRealtimeNanos()
        val json = fogJson(union, lon0, lat0, kx, ky)
        val serialised = SystemClock.elapsedRealtimeNanos()
        val record = mutableMapOf<String, Any>(
            "band" to band,
            "lod" to lod,
            "halfWidthMetres" to halfWidth,
            "inputVertices" to inputVertices,
            "outputVertices" to union.numPoints,
            "holes" to union.numGeometries,
            "bufferMs" to (buffered - started) / 1e6,
            "jsonMs" to (serialised - buffered) / 1e6,
            "jsonBytes" to json.length,
        )
        main.post {
            if (gen != generation) {
                record["stale"] = true
                builds += record
                return@post
            }
            source?.setGeoJson(json)
            shownBand = band
            record["requestToMapMs"] = SystemClock.elapsedRealtime() - requested
            builds += record
        }
    }

    companion object {
        const val SOURCE_ID = "fog-b"
        const val LAYER_ID = "fog-b"

        // The world as one polygon whose holes are the corridors; fog left inside a corridor loop
        // (a block nobody drove into) comes back as separate island polygons. Outer rings run
        // counter-clockwise and holes clockwise, or MapLibre would read the holes as new polygons.
        fun fogJson(union: Geometry, lon0: Double, lat0: Double, kx: Double, ky: Double): String {
            val sb = StringBuilder(1 shl 16)
            sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"MultiPolygon\",\"coordinates\":[[")
            sb.append("[[-180,-85],[180,-85],[180,85],[-180,85],[-180,-85]]")
            val islands = ArrayList<LinearRing>()
            for (i in 0 until union.numGeometries) {
                val polygon = union.getGeometryN(i) as? Polygon ?: continue
                sb.append(',')
                ring(sb, polygon.exteriorRing, wantClockwise = true, lon0, lat0, kx, ky)
                for (h in 0 until polygon.numInteriorRing) islands += polygon.getInteriorRingN(h)
            }
            sb.append(']')
            for (island in islands) {
                sb.append(",[")
                ring(sb, island, wantClockwise = false, lon0, lat0, kx, ky)
                sb.append(']')
            }
            sb.append("]}}")
            return sb.toString()
        }

        private fun ring(sb: StringBuilder, ring: LinearRing, wantClockwise: Boolean, lon0: Double, lat0: Double, kx: Double, ky: Double) {
            val coords = ring.coordinates
            val clockwise = !Orientation.isCCW(coords)
            val reverse = clockwise != wantClockwise
            sb.append('[')
            for (k in coords.indices) {
                val c = coords[if (reverse) coords.size - 1 - k else k]
                if (k > 0) sb.append(',')
                sb.append('[')
                appendDegrees(sb, lon0 + c.x / kx)
                sb.append(',')
                appendDegrees(sb, lat0 + c.y / ky)
                sb.append(']')
            }
            sb.append(']')
        }

        // Six decimals (about 10 cm) without String.format, which costs more than the buffer.
        private fun appendDegrees(sb: StringBuilder, value: Double) {
            val micro = Math.round(value * 1e6)
            if (micro < 0) sb.append('-')
            val abs = kotlin.math.abs(micro)
            sb.append(abs / 1_000_000)
            sb.append('.')
            val frac = (abs % 1_000_000).toString()
            for (i in frac.length until 6) sb.append('0')
            sb.append(frac)
        }
    }
}
