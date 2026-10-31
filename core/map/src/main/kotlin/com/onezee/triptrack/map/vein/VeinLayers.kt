package com.onezee.triptrack.map.vein

import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.map.fog.FogPalette
import com.onezee.triptrack.map.fog.FogRules
import com.onezee.triptrack.map.route.SpeedColorScale
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

/**
 * iOS: RouteVeinOverlay with the `.network` style. Every opened road as one terracotta thread inside
 * its cleared corridor: the fog says WHERE the person has been, the vein which roads.
 *
 * Drawn from the revealed layer's own runs, never from trips: a hundred drives down one street are
 * one run there, and one thread here.
 */
data class MapVein(val revealed: RevealedLayer, val palette: FogPalette)

/**
 * The vein's numbers (iOS RouteVeinRenderer), shared by the map layers and the share poster: the
 * poster draws the same strokes the screen does.
 */
object VeinStyle {
    // The fog's level thresholds as MapLibre zooms: fine from ~9.6, mid from ~6.3.
    val FINE_FROM = MapPoints.mapLibreZoom(FogRules.LOD_FINE_ABOVE)
    val MID_FROM = MapPoints.mapLibreZoom(FogRules.LOD_MID_ABOVE)

    // iOS RouteVeinRenderer: paper #C8472D, night #F0A070, drawn at 0.9.
    val PAPER = SpeedColorScale.argb(0xC8, 0x47, 0x2D, 1.0)
    val NIGHT = SpeedColorScale.argb(0xF0, 0xA0, 0x70, 1.0)
    const val LINE_ALPHA = 0.9

    // A white casing on the paper mist, none under the night: there the corridor is the light.
    const val CASING_EXTRA = 2.0
    private val CASING_LIGHT = SpeedColorScale.argb(0xFF, 0xFF, 0xFF, 0.94)

    /** iOS RouteVeinRenderer.width(for:): thicker from afar, a hair would vanish on a country. */
    fun width(lod: RevealedLayer.LOD): Double = when (lod) {
        RevealedLayer.LOD.fine -> 2.2
        RevealedLayer.LOD.mid -> 2.8
        RevealedLayer.LOD.far -> 3.0
    }

    /** iOS RouteVeinRenderer.halo(for:): width and alpha; none up close, where the corridor is the light. */
    fun halo(lod: RevealedLayer.LOD): Pair<Double, Double>? = when (lod) {
        RevealedLayer.LOD.fine -> null
        RevealedLayer.LOD.mid -> 8.0 to 0.10
        RevealedLayer.LOD.far -> 10.0 to 0.14
    }

    fun veinColour(palette: FogPalette): Int = if (palette.isDark) NIGHT else PAPER

    fun casing(palette: FogPalette): Int? = if (palette.isDark) null else CASING_LIGHT

    fun withAlpha(argb: Int, alpha: Double): Int = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (argb and 0xFFFFFF)
}

/**
 * The MapLibre side: a source per level of detail and, per level, a halo, a casing and the line,
 * each level shown only in the zoom band of [FogRules]' thresholds, the levels the fog draws.
 */
internal class VeinLayers {
    private var installed: MapVein? = null

    val firstLayerId: String get() = layerId(RevealedLayer.LOD.far, HALO)

    fun addTo(style: Style, vein: MapVein?) {
        for (lod in RevealedLayer.LOD.entries) style.addSource(GeoJsonSource(sourceId(lod)))
        for (lod in LEVELS) for (part in PARTS) style.addLayer(layer(lod, part))
        installed = null
        update(style, vein, force = true)
    }

    fun update(style: Style, vein: MapVein?, force: Boolean = false) {
        val previous = installed
        // By identity, as the fog's mesh: comparing layers by content walks every point.
        if (!force && previous != null && vein != null && previous.revealed === vein.revealed && previous.palette == vein.palette) return
        if (!force && previous == null && vein == null) return
        installed = vein
        val palette = vein?.palette ?: FogPalette.MIST
        for (lod in LEVELS) for (part in PARTS) style.getLayer(layerId(lod, part))?.let { paint(it, lod, part, palette, vein != null) }
        if (force || previous?.revealed !== vein?.revealed) {
            for (lod in RevealedLayer.LOD.entries) {
                val lines = vein?.revealed?.polylines(lod).orEmpty().filter { it.size > 1 }
                val features = if (lines.isEmpty()) emptyList() else listOf(Feature.fromGeometry(MultiLineString.fromLngLats(
                    lines.map { line -> line.map { Point.fromLngLat(it.longitude, it.latitude) } },
                )))
                style.getSourceAs<GeoJsonSource>(sourceId(lod))?.setGeoJson(FeatureCollection.fromFeatures(features))
            }
        }
    }

    private fun layer(lod: RevealedLayer.LOD, part: String): Layer = LineLayer(layerId(lod, part), sourceId(lod)).also { layer ->
        when (lod) {
            RevealedLayer.LOD.fine -> layer.minZoom = VeinStyle.FINE_FROM.toFloat()
            RevealedLayer.LOD.mid -> { layer.minZoom = VeinStyle.MID_FROM.toFloat(); layer.maxZoom = VeinStyle.FINE_FROM.toFloat() }
            RevealedLayer.LOD.far -> layer.maxZoom = VeinStyle.MID_FROM.toFloat()
        }
        paint(layer, lod, part, FogPalette.MIST, shown = false)
    }

    private fun paint(layer: Layer, lod: RevealedLayer.LOD, part: String, palette: FogPalette, shown: Boolean) {
        val core = VeinStyle.width(lod)
        val colour = VeinStyle.veinColour(palette)
        val visible = shown && when (part) {
            HALO -> VeinStyle.halo(lod) != null
            CASING -> VeinStyle.casing(palette) != null
            else -> true
        }
        val common = arrayOf(
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
        )
        when (part) {
            HALO -> {
                val (width, alpha) = VeinStyle.halo(lod) ?: (0.0 to 0.0)
                layer.setProperties(*common, PropertyFactory.lineWidth(width.toFloat()), PropertyFactory.lineColor(VeinStyle.withAlpha(colour, alpha)))
            }
            CASING -> layer.setProperties(*common, PropertyFactory.lineWidth((core + VeinStyle.CASING_EXTRA).toFloat()),
                PropertyFactory.lineColor(VeinStyle.casing(palette) ?: 0))
            else -> layer.setProperties(*common, PropertyFactory.lineWidth(core.toFloat()),
                PropertyFactory.lineColor(VeinStyle.withAlpha(colour, VeinStyle.LINE_ALPHA)))
        }
    }

    companion object {
        private const val SOURCE = "triptrack-vein"
        private const val HALO = "halo"
        private const val CASING = "casing"
        private const val LINE = "line"
        private val PARTS = listOf(HALO, CASING, LINE)

        // Drawn far first, so a band boundary never stacks a coarse line over a fine one.
        private val LEVELS = listOf(RevealedLayer.LOD.far, RevealedLayer.LOD.mid, RevealedLayer.LOD.fine)

        fun layerId(lod: RevealedLayer.LOD, part: String) = "$SOURCE-${lod.name}-$part"

        private fun sourceId(lod: RevealedLayer.LOD) = "$SOURCE-${lod.name}"
    }
}
