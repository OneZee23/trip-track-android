package com.onezee.triptrack.map.area

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * iOS: MKCircleRenderer with a `fillColor` and a `strokeColor` of one colour, for the one area a screen draws
 * on its map (the home zone in the picker: the accent at 16 % inside and 70 % along a 2 pt edge). A ring of
 * points, because MapLibre has no circle in metres; [colour] is opaque and the two opacities are separate, so
 * the fill and the edge stay one colour at two strengths whatever the alpha handling of a colour int.
 */
data class MapArea(
    val ring: List<GeoPoint>,
    val colour: Int,
    val fillOpacity: Double,
    val strokeOpacity: Double,
    val strokeWidthPoints: Double,
) {
    companion object {
        private const val METRES_PER_DEGREE = 111_320.0

        /** A closed ring of [steps] + 1 points [metres] from [centre]. */
        fun disc(centre: GeoPoint, metres: Double, steps: Int = 64): List<GeoPoint> = (0..steps).map { i ->
            val angle = 2 * Math.PI * i / steps
            val dLat = metres * cos(angle) / METRES_PER_DEGREE
            val dLon = metres * sin(angle) / (METRES_PER_DEGREE * max(0.01, cos(Math.toRadians(centre.latitude))))
            GeoPoint(centre.latitude + dLat, centre.longitude + dLon)
        }
    }
}

/**
 * The MapLibre side of [MapArea]: one polygon source with a fill layer and an edge layer over it. It stands
 * above the map's own layers and below the fog, the vein, the routes and the markers.
 */
internal class AreaLayers {
    private var installed: MapArea? = null

    fun addTo(style: Style, area: MapArea?) {
        style.addSource(GeoJsonSource(SOURCE))
        style.addLayer(FillLayer(FILL, SOURCE).withProperties(fillColor(0xFF000000.toInt()), fillOpacity(0f)))
        style.addLayer(LineLayer(EDGE, SOURCE).withProperties(lineColor(0xFF000000.toInt()), lineOpacity(0f), lineWidth(1f), lineJoin(Property.LINE_JOIN_ROUND)))
        installed = null
        update(style, area)
    }

    fun update(style: Style, area: MapArea?) {
        if (area == installed) return
        installed = area
        val source = style.getSourceAs<GeoJsonSource>(SOURCE) ?: return
        if (area == null || area.ring.size < 3) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
            return
        }
        source.setGeoJson(Polygon.fromLngLats(listOf(area.ring.map { Point.fromLngLat(it.longitude, it.latitude) })))
        style.getLayerAs<FillLayer>(FILL)?.setProperties(fillColor(area.colour), fillOpacity(area.fillOpacity.toFloat()))
        style.getLayerAs<LineLayer>(EDGE)?.setProperties(
            lineColor(area.colour), lineOpacity(area.strokeOpacity.toFloat()), lineWidth(area.strokeWidthPoints.toFloat()),
        )
    }

    private companion object {
        const val SOURCE = "triptrack-area"
        const val FILL = "triptrack-area-fill"
        const val EDGE = "triptrack-area-edge"
    }
}
