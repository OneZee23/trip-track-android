package com.onezee.triptrack.map.replay

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.PlaceMatcher
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.abs
import kotlin.math.pow

// iOS: CarHeadingPolicy.swift. Geometry ahead is stable even when frame-to-frame movement is subpixel.
object CarHeadingPolicy {
    const val minCourseSpeed = 1.4
    const val coneThreshold = 20.0
    const val coneMaxHalfAngle = 60.0
    const val lookahead = 12.0
    const val minLookaheadSpan = 1.0
    const val deadZone = 2.0
    const val framePull = 0.2
    const val maxTurnRate = 180.0

    fun liveCourse(course: Double?, courseAccuracy: Double, rawSpeed: Double): Double? =
        course?.takeIf { it >= 0 && courseAccuracy >= 0 && rawSpeed >= minCourseSpeed }?.let(::normalized)

    fun coneHalfAngle(courseAccuracy: Double, rawSpeed: Double): Double? =
        courseAccuracy.takeIf { it > coneThreshold && rawSpeed >= minCourseSpeed }?.coerceAtMost(coneMaxHalfAngle)

    fun screenAngle(course: Double, cameraHeading: Double): Double = normalized(course - cameraHeading)

    fun courseAlongRoute(position: GeoPoint, passed: Int, coords: List<GeoPoint>, lookahead: Double = this.lookahead): Double? {
        if (coords.size < 2) return null
        var remaining = lookahead
        var previous = position
        for (i in maxOf(0, passed + 1) until coords.size) {
            remaining -= GeometryUtils.haversineDistance(previous, coords[i])
            if (remaining <= 0) return bearingIfMeaningful(position, coords[i])
            previous = coords[i]
        }
        return bearingIfMeaningful(position, coords.last())
    }

    private fun bearingIfMeaningful(a: GeoPoint, b: GeoPoint): Double? =
        if (GeometryUtils.haversineDistance(a, b) >= minLookaheadSpan) PlaceMatcher.bearing(a, b) else null

    fun normalized(degrees: Double): Double = ((degrees % 360) + 360) % 360
    fun shortestDelta(current: Double, target: Double): Double = ((target - current) % 360 + 540) % 360 - 180

    fun smoothed(current: Double, target: Double?, dt: Double, instant: Boolean = false): Double {
        if (target == null) return normalized(current)
        val delta = shortestDelta(current, target)
        if (abs(delta) <= deadZone) return normalized(current)
        if (instant) return normalized(target)
        if (dt <= 0) return normalized(current)
        val pull = 1 - (1 - framePull).pow(minOf(dt, 1.0) * 60)
        val cap = maxTurnRate * dt
        return normalized(current + (delta * pull).coerceIn(-cap, cap))
    }

    // iOS: MapCarMarker.swift, collapsed(metersPerPoint:wasCollapsed:)
    fun collapsed(metresPerPoint: Double, wasCollapsed: Boolean): Boolean =
        if (metresPerPoint <= 0) wasCollapsed else metresPerPoint > if (wasCollapsed) 18.0 else 24.0
}
