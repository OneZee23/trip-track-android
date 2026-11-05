package com.onezee.triptrack.map.route

import com.onezee.triptrack.model.GeoPoint
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Routes to draw: prepared shapes (see [RouteGeometry]) and one appearance for all of them. */
data class MapRoutes(val shapes: List<RouteShape>, val appearance: RouteAppearance, val litFraction: Double? = null)

/**
 * The MapLibre side of route drawing: one GeoJSON source per kind and four layers, in drawing order
 * casing → line → dash → endpoints. The casing is a separate wider stroke under the line, as
 * CasedPolylineRenderer strokes its path before the line on iOS.
 */
internal class RouteLayers {
    private var appearance: RouteAppearance? = null
    private var shapes: List<RouteShape>? = null
    private var opacities: List<Double>? = null
    private var litFraction: Double? = null

    val layerIds: List<String> = listOf(HIDDEN_CASING, HIDDEN_LINE, CASING, LINE, DASH, ENDS)

    fun addTo(style: Style, routes: MapRoutes?) {
        shapes = null
        opacities = null
        style.addSource(GeoJsonSource(LINES_SOURCE))
        style.addSource(GeoJsonSource(ENDS_SOURCE))
        val looks = routes?.appearance ?: RouteAppearance.plain
        for (layer in layers(looks)) style.addLayer(layer)
        appearance = looks
        if (routes != null) update(style, routes)
    }

    fun update(style: Style, routes: MapRoutes?) {
        val looks = routes?.appearance ?: RouteAppearance.plain
        val geometryChanged = routes?.shapes != shapes
        val colorsChanged = looks.lineColorOverride != appearance?.lineColorOverride
        if (looks != appearance) {
            // Width, casing and dash length are layer properties: a new appearance restyles them.
            restyle(style, looks)
            appearance = looks
        }
        val fraction = routes?.litFraction
        if (!geometryChanged && !colorsChanged && fraction != null && litFraction != null && !ReplayRoute.shouldRelight(fraction, litFraction!!)) return
        litFraction = fraction
        val nextOpacities = routes?.shapes.orEmpty().flatMap { shape -> shape.lines.map { ReplayRoute.opacity(it, fraction) } }
        if (nextOpacities != opacities || geometryChanged) {
            val opacity = Expression.switchCase(Expression.all(
                Expression.eq(Expression.get(HAS_SPEED), true),
                Expression.gt(Expression.get(END_FRACTION), Expression.literal(routes?.litFraction ?: 1.0)),
            ), Expression.literal(ReplayRoute.dimmedAhead), Expression.literal(1.0))
            for (id in listOf(CASING, LINE, DASH)) style.getLayerAs<LineLayer>(id)?.setProperties(PropertyFactory.lineOpacity(opacity))
        }
        opacities = nextOpacities
        if (!geometryChanged && !colorsChanged) return
        shapes = routes?.shapes
        val ends = ArrayList<Feature>()
        for (shape in routes?.shapes.orEmpty()) {
            if (looks.showsEndpoints) {
                val first = shape.lines.firstOrNull()?.coordinates?.firstOrNull()
                val last = shape.lines.lastOrNull()?.coordinates?.lastOrNull()
                if (first != null) ends += endpoint(first, RouteAppearance.START)
                if (last != null && shape.lines.sumOf { it.coordinates.size } > 1) ends += endpoint(last, RouteAppearance.END)
            }
        }
        style.getSourceAs<GeoJsonSource>(LINES_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(lineFeatures(routes, looks)))
        style.getSourceAs<GeoJsonSource>(ENDS_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(ends))
    }

    private fun layers(looks: RouteAppearance): List<Layer> = listOf(
        LineLayer(HIDDEN_CASING, LINES_SOURCE).withFilter(Expression.eq(Expression.get(HIDDEN), true)),
        LineLayer(HIDDEN_LINE, LINES_SOURCE).withFilter(Expression.eq(Expression.get(HIDDEN), true)),
        LineLayer(CASING, LINES_SOURCE).withFilter(Expression.all(Expression.eq(Expression.get(HIDDEN), false), Expression.eq(Expression.get(DASHED), false))),
        LineLayer(LINE, LINES_SOURCE).withFilter(Expression.all(Expression.eq(Expression.get(HIDDEN), false), Expression.eq(Expression.get(DASHED), false))),
        LineLayer(DASH, LINES_SOURCE).withFilter(Expression.all(Expression.eq(Expression.get(HIDDEN), false), Expression.eq(Expression.get(DASHED), true))),
        CircleLayer(ENDS, ENDS_SOURCE),
    ).also { restyle(it, looks) }

    private fun restyle(style: Style, looks: RouteAppearance) {
        restyle(layerIds.mapNotNull { style.getLayer(it) }, looks)
    }

    private fun restyle(layers: List<Layer>, looks: RouteAppearance) {
        val width = looks.widthPoints.toFloat()
        for (layer in layers) when (layer.id) {
            HIDDEN_CASING -> layer.setProperties(
                PropertyFactory.lineColor(0xFFFFFFFF.toInt()),
                PropertyFactory.lineWidth(width + 4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            HIDDEN_LINE -> layer.setProperties(
                *hiddenLineProperties(looks).toTypedArray(),
            )
            CASING -> layer.setProperties(
                PropertyFactory.lineColor(looks.casingArgb ?: 0),
                PropertyFactory.lineWidth(width + looks.casingExtraPoints.toFloat()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.visibility(if (looks.casingArgb != null) Property.VISIBLE else Property.NONE),
            )
            LINE -> layer.setProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get(COLOR))),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            // MapLibre scales dash lengths by the line width and keeps them in screen units at every
            // zoom, so the 6 pt dash of iOS is 6 / width here (MapKit needed ScreenScaledDashRenderer
            // to stop its tiles from stretching the dash between zoom levels).
            DASH -> layer.setProperties(
                PropertyFactory.lineColor(RouteAppearance.UNKNOWN_SPEED),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                PropertyFactory.lineDasharray(
                    RouteAppearance.UNKNOWN_SPEED_DASH.map { (it / looks.widthPoints).toFloat() }.toTypedArray(),
                ),
            )
            ENDS -> layer.setProperties(
                PropertyFactory.circleColor(Expression.toColor(Expression.get(COLOR))),
                PropertyFactory.circleRadius((RouteAppearance.ENDPOINT_DIAMETER / 2).toFloat()),
            )
        }
    }

    private fun endpoint(at: GeoPoint, argb: Int): Feature =
        Feature.fromGeometry(point(at)).also { it.addStringProperty(COLOR, rgba(argb)) }

    internal fun lineFeatures(routes: MapRoutes?, looks: RouteAppearance): List<Feature> = buildList {
        for (shape in routes?.shapes.orEmpty()) for (line in shape.lines + shape.hiddenLines) {
            if (line.coordinates.size < 2) continue
            val feature = Feature.fromGeometry(LineString.fromLngLats(line.coordinates.map(::point)))
            feature.addStringProperty(COLOR, rgba(looks.argb(line)))
            feature.addBooleanProperty(DASHED, line.isUnknownSpeed)
            feature.addBooleanProperty(HIDDEN, line.hidden)
            feature.addBooleanProperty(HAS_SPEED, line.speed != null)
            feature.addNumberProperty(END_FRACTION, line.endFraction)
            add(feature)
        }
    }

    internal fun hiddenLineProperties(looks: RouteAppearance): List<PropertyValue<*>> = listOf(
        PropertyFactory.lineColor(rgba(looks.hiddenInkArgb)),
        PropertyFactory.lineWidth(looks.widthPoints.toFloat()),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineDasharray(listOf(0.1f, 7.2f).map { it / looks.widthPoints.toFloat() }.toTypedArray()),
    )

    companion object {
        const val CASING = "triptrack-route-casing"
        const val HIDDEN_CASING = "triptrack-route-hidden-casing"
        const val HIDDEN_LINE = "triptrack-route-hidden-line"
        const val LINE = "triptrack-route-line"
        const val DASH = "triptrack-route-dash"
        const val ENDS = "triptrack-route-ends"
        private const val LINES_SOURCE = "triptrack-route-lines"
        private const val ENDS_SOURCE = "triptrack-route-ends-source"
        private const val COLOR = "color"
        private const val DASHED = "dashed"
        private const val HIDDEN = "hidden"
        private const val HAS_SPEED = "has-speed"
        private const val END_FRACTION = "end-fraction"

        fun point(at: GeoPoint): Point = Point.fromLngLat(at.longitude, at.latitude)

        /** CSS colour MapLibre parses, from ARGB. */
        fun rgba(argb: Int): String {
            val alpha = (argb ushr 24) / 255.0
            return "rgba(${(argb shr 16) and 0xFF}, ${(argb shr 8) and 0xFF}, ${argb and 0xFF}, ${"%.3f".format(java.util.Locale.ROOT, alpha)})"
        }
    }
}
