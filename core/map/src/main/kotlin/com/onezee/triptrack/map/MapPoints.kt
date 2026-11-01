package com.onezee.triptrack.map

import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Map points as MapKit defines them (MKMapPoint, MKMapSize.world): Web Mercator scaled to 2^28 across
 * the world, origin top left, y down. The fog's chunk sizes, LOD thresholds and cell grid are numbers
 * in these units on iOS, so the Android port keeps the unit and converts MapLibre's camera to it.
 */
object MapPoints {
    const val WORLD = 268_435_456.0

    // Metres per map point follow MapLibre's spherical Web Mercator, the scale its own tiles and
    // scale bar use. MKMetersPerMapPointAtLatitude differs by about half a percent at 45°.
    private const val EARTH_CIRCUMFERENCE = 40_075_016.68557849

    fun x(longitude: Double): Double = (longitude + 180.0) / 360.0 * WORLD

    fun y(latitude: Double): Double {
        val s = sin(latitude.coerceIn(-85.0511, 85.0511) * PI / 180.0)
        return (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * WORLD
    }

    fun longitude(x: Double): Double = x / WORLD * 360.0 - 180.0

    // iOS: FogMesh.swift (projected segment endpoints). MapLibre repeats the projected world;
    // a short crossing of ±180° must not become a corridor across the entire planet.
    fun nearestWorldX(x: Double, reference: Double): Double = x + floor((reference - x) / WORLD + 0.5) * WORLD

    fun latitude(y: Double): Double {
        val n = PI - 2.0 * PI * y / WORLD
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    fun metresPerMapPoint(latitude: Double): Double = cos(latitude * PI / 180.0) * EARTH_CIRCUMFERENCE / WORLD

    fun mapPointsPerMetre(latitude: Double): Double = 1.0 / metresPerMapPoint(latitude)

    /** MapLibre draws 512 dp tiles, so its zoom z shows 512 · 2^z dp across the world. */
    fun pointsPerMapPoint(mapLibreZoom: Double): Double = 512.0 * 2.0.pow(mapLibreZoom) / WORLD

    /** The MapLibre zoom at which one map point covers [pointsPerMapPoint] screen points. */
    fun mapLibreZoom(pointsPerMapPoint: Double): Double = kotlin.math.log2(pointsPerMapPoint * WORLD / 512.0)

    fun metresPerPoint(latitude: Double, mapLibreZoom: Double): Double =
        metresPerMapPoint(latitude) / pointsPerMapPoint(mapLibreZoom)

    fun rect(bounds: GeoBounds): MapRect {
        val minX = x(bounds.minLon)
        val maxX = x(bounds.maxLon)
        val minY = y(bounds.maxLat)
        val maxY = y(bounds.minLat)
        return MapRect(minX, minY, maxX - minX, maxY - minY)
    }

    fun bounds(rect: MapRect): GeoBounds = GeoBounds(
        minLat = latitude(rect.maxY),
        maxLat = latitude(rect.minY),
        minLon = longitude(rect.minX),
        maxLon = longitude(rect.maxX),
    )

    fun point(coordinate: GeoPoint): com.onezee.triptrack.logic.MapPoint =
        com.onezee.triptrack.logic.MapPoint(x(coordinate.longitude), y(coordinate.latitude))
}
