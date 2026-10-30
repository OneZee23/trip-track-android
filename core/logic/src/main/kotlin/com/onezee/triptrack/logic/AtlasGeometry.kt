package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.*

// iOS: GeometryUtils.swift. Spherical distance is used for Atlas attribution, unlike the recording
// odometer.
object GeometryUtils {
    fun haversineDistance(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude * PI / 180
        val lat2 = b.latitude * PI / 180
        val dLat = (b.latitude - a.latitude) * PI / 180
        val dLon = (b.longitude - a.longitude) * PI / 180
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLon * sinLon
        return 6_371_000 * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}

// iOS: TerritoryManager.swift (GeoBounds)
fun GeoBounds.contains(point: GeoPoint): Boolean =
    point.latitude in minLat..maxLat && point.longitude in minLon..maxLon

val GeoBounds.area: Double
    get() = (maxLat - minLat) * (maxLon - minLon)

fun GeoBounds.intersects(other: GeoBounds): Boolean =
    maxLat >= other.minLat &&
        minLat <= other.maxLat &&
        maxLon >= other.minLon &&
        minLon <= other.maxLon
