package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackGapFinder
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripDistanceGate
import java.time.Instant
import kotlin.math.*

// iOS: GapFill.swift (straight fill and resampling).
object GapFill {
    const val step = 20.0
    const val maxPoints = 500

    fun isFillable(gap: TrackGapFinder.Gap): Boolean =
        gap.seconds > 0 && gap.straightMetres / gap.seconds <= TripDistanceGate.maxPlausibleSpeed

    fun resample(path: List<GeoPoint>, from: Instant, to: Instant, altitudeFrom: Double, altitudeTo: Double): List<TrackPoint> {
        if (path.size < 2) return emptyList()
        val cumulative = mutableListOf(0.0)
        for ((a, b) in path.zipWithNext()) cumulative += cumulative.last() + a.distanceFrom(b)
        val total = cumulative.last()
        if (!(total > 0)) return emptyList()
        val count = min(maxPoints, max(0, ceil(total / step).toInt() - 1))
        val duration = to.secondsSince(from)
        var segment = 0
        return (1..count).map { k ->
            val distance = total * k / (count + 1)
            while (segment < path.size - 2 && cumulative[segment + 1] < distance) segment++
            val segmentLength = max(cumulative[segment + 1] - cumulative[segment], Math.ulp(1.0))
            val fractionInSegment = (distance - cumulative[segment]) / segmentLength
            val a = path[segment]
            val b = path[segment + 1]
            val fraction = distance / total
            TrackPoint(
                latitude = a.latitude + (b.latitude - a.latitude) * fractionInSegment,
                longitude = a.longitude + (b.longitude - a.longitude) * fractionInSegment,
                altitude = altitudeFrom + (altitudeTo - altitudeFrom) * fraction,
                speed = -1.0, course = bearing(a, b), horizontalAccuracy = -1.0,
                timestamp = from.addingSeconds(duration * fraction), isInterpolated = true,
            )
        }
    }

    fun isPlausible(routeMetres: Double, routeSeconds: Double, straightMetres: Double, gapSeconds: Double): Boolean =
        routeMetres <= 2.5 * straightMetres && routeSeconds <= max(3 * gapSeconds, gapSeconds + 300)

    fun isStraight(fill: List<GeoPoint>, from: GeoPoint, to: GeoPoint, tolerance: Double = 3.0): Boolean {
        // MKMapPoint uses Mercator; its map-point scale cancels in this ratio.
        fun mercatorY(latitude: Double) = ln(tan(PI / 4 + Math.toRadians(latitude) / 2))
        val ax = Math.toRadians(from.longitude)
        val ay = mercatorY(from.latitude)
        val dx = Math.toRadians(to.longitude) - ax
        val dy = mercatorY(to.latitude) - ay
        val length = hypot(dx, dy)
        if (!(length > 0)) return true
        val metresPerRadian = 6_378_137.0 * cos(Math.toRadians((from.latitude + to.latitude) / 2))
        return fill.all { point ->
            val offset = abs((Math.toRadians(point.longitude) - ax) * dy - (mercatorY(point.latitude) - ay) * dx) / length
            offset * metresPerRadian <= tolerance
        }
    }

    // iOS: PlaceMatcher.swift, bearing(from:to:)
    private fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x) * 180 / PI + 360) % 360
    }
}
