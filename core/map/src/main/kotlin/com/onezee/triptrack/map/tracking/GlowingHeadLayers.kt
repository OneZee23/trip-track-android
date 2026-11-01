// iOS: GlowingHeadOverlay.swift. A separate source keeps head updates from rebuilding the route.
package com.onezee.triptrack.map.tracking

import com.onezee.triptrack.map.route.RouteLayers
import com.onezee.triptrack.model.GeoPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

internal class GlowingHeadLayers {
    fun addTo(style: Style, points: List<GeoPoint>) {
        style.addSource(GeoJsonSource(SOURCE))
        style.addSource(GeoJsonSource(TIP))
        style.addLayer(LineLayer(GLOW, SOURCE).withProperties(lineColor(ACCENT), lineOpacity(.3f),
            lineWidth(12f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        style.addLayer(LineLayer(LINE, SOURCE).withProperties(lineColor(ACCENT), lineOpacity(.95f),
            lineWidth(5f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)))
        style.addLayer(CircleLayer(DOT, TIP).withProperties(circleRadius(6f), circleColor(android.graphics.Color.WHITE),
            circleStrokeColor(ACCENT), circleStrokeWidth(2f)))
        update(style, points)
    }

    fun update(style: Style, points: List<GeoPoint>) {
        val empty = FeatureCollection.fromFeatures(emptyArray())
        val source = style.getSourceAs<GeoJsonSource>(SOURCE) ?: return
        if (points.size < 2) source.setGeoJson(empty)
        else source.setGeoJson(LineString.fromLngLats(points.map(RouteLayers::point)))
        style.getSourceAs<GeoJsonSource>(TIP)?.let {
            if (points.size < 2) it.setGeoJson(empty) else it.setGeoJson(RouteLayers.point(points.last()))
        }
    }

    private companion object {
        const val SOURCE = "triptrack-head"
        const val TIP = "triptrack-head-tip"
        const val GLOW = "triptrack-head-glow"
        const val LINE = "triptrack-head-line"
        const val DOT = "triptrack-head-dot"
        val ACCENT = android.graphics.Color.rgb(194, 69, 43)
    }
}
