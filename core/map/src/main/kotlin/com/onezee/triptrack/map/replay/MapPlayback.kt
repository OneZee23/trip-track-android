package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint

// iOS: RouteMapView.swift, playbackCarCoord / updateCarHeading / carState.
data class MapPlayback(val position: GeoPoint, val course: Double, val colorArgb: Int, val collapsed: Boolean = false)
