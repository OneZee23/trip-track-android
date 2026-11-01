// iOS: MapCarMarker.swift, MapCarAnnotationView.swift (accuracy, course cone and live pulse).
package com.onezee.triptrack.map.tracking

import com.onezee.triptrack.map.replay.MapPlayback
import com.onezee.triptrack.map.route.RouteLayers
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

internal class CarStateLayers {
    private var current: MapPlayback? = null
    private var metresPerPoint = 0.0
    private var sourcePosition: com.onezee.triptrack.model.GeoPoint? = null
    private var coneKey: Triple<com.onezee.triptrack.model.GeoPoint, Double, Double?>? = null

    fun addTo(style: Style, car: MapPlayback?) {
        current = null; sourcePosition = null; coneKey = null
        style.addSource(GeoJsonSource(POSITION))
        style.addSource(GeoJsonSource(CONE_SOURCE))
        style.addLayer(CircleLayer(ACCURACY, POSITION).withProperties(circleColor(0xFFC2452B.toInt()), circleOpacity(.1f), circleStrokeColor(0xFFC2452B.toInt()), circleStrokeOpacity(.26f), circleStrokeWidth(1f)))
        style.addLayer(FillLayer(CONE, CONE_SOURCE).withProperties(fillColor(0xFFC2452B.toInt()), fillOpacity(.16f)))
        style.addLayer(CircleLayer(PULSE, POSITION).withProperties(circleColor(0), circleOpacity(0f), circleStrokeColor(0xFFC2452B.toInt()), circleStrokeWidth(2f)))
        update(style, car)
    }

    fun update(style: Style, car: MapPlayback?) {
        current = car
        val empty = FeatureCollection.fromFeatures(emptyArray())
        if (sourcePosition != car?.position) {
            sourcePosition = car?.position
            style.getSourceAs<GeoJsonSource>(POSITION)?.let { source ->
                if (car == null) source.setGeoJson(empty) else source.setGeoJson(RouteLayers.point(car.position))
            }
        }
        val key = car?.let { Triple(it.position, it.course, it.coneHalfAngle) }
        if (key != coneKey) {
            coneKey = key
            val half = car?.coneHalfAngle
            style.getSourceAs<GeoJsonSource>(CONE_SOURCE)?.let { source ->
                if (car == null || half == null || half <= 0) source.setGeoJson(empty)
                else {
                    val centre = RouteLayers.point(car.position)
                    val points = arrayListOf(centre)
                    for (i in 0..32) {
                        val angle = Math.toRadians(car.course - half + half * 2 * i / 32)
                        val lat = car.position.latitude + cos(angle) * 45 / 111_320
                        val lon = car.position.longitude + sin(angle) * 45 / (111_320 * cos(Math.toRadians(car.position.latitude)).coerceAtLeast(.01))
                        points += Point.fromLngLat(lon, lat)
                    }
                    points += centre
                    source.setGeoJson(Polygon.fromLngLats(listOf(points)))
                }
            }
        }
        restyle(style)
    }

    fun setScale(style: Style, metresPerPoint: Double) {
        if (this.metresPerPoint == metresPerPoint) return
        this.metresPerPoint = metresPerPoint
        restyle(style)
    }

    private fun restyle(style: Style) {
        val car = current
        val radius = if (metresPerPoint > 0) (car?.accuracyMetres ?: 0.0) / metresPerPoint else 0.0
        val alpha = car?.mood?.alpha ?: 0f
        style.getLayerAs<CircleLayer>(ACCURACY)?.setProperties(circleRadius(if (radius >= 22) radius.coerceAtMost(900.0).toFloat() else 0f), circleOpacity(.1f * alpha), circleStrokeOpacity(if (radius >= 22) .26f * alpha else 0f))
        val coneVisible = car != null && !car.collapsed && metresPerPoint > 0 && 45 / metresPerPoint >= 22
        style.getLayerAs<FillLayer>(CONE)?.setProperties(fillOpacity(if (coneVisible) .16f * alpha else 0f))
        val pulse = car?.pulse?.takeIf { !car.collapsed }
        style.getLayerAs<CircleLayer>(PULSE)?.setProperties(circleRadius((44 * .28 * (1 + (pulse ?: 0.0) * 1.4)).toFloat()),
            circleStrokeOpacity(if (pulse != null) (.55 * (1 - pulse)).toFloat() else 0f))
    }

    private companion object {
        const val POSITION = "triptrack-car-state-position"
        const val CONE_SOURCE = "triptrack-car-state-cone-source"
        const val ACCURACY = "triptrack-car-state-accuracy"
        const val CONE = "triptrack-car-state-cone"
        const val PULSE = "triptrack-car-state-pulse"
    }
}
