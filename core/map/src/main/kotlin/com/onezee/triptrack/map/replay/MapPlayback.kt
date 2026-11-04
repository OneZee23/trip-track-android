package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint

// iOS: RouteMapView.swift, playbackCarCoord / updateCarHeading / carState.
data class MapPlayback(
    val position: GeoPoint,
    val course: Double,
    val colorArgb: Int,
    val collapsed: Boolean = false,
    val mood: MapCarMood = MapCarMood.normal,
    val accuracyMetres: Double? = null,
    val coneHalfAngle: Double? = null,
    val pulse: Double? = null,
    val opacity: Double = 1.0,
)

// iOS: MapCarMarker.State. Pause takes precedence over a lost signal.
enum class MapCarMood(val saturation: Float, val alpha: Float) {
    normal(1f, 1f), paused(.35f, 1f), lost(0f, .55f),
}
