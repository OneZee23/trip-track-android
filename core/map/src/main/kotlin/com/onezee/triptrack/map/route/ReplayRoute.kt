package com.onezee.triptrack.map.route

// iOS: RouteMapView.swift, Coordinator.relight(to:), dimmedAhead.
object ReplayRoute {
    const val dimmedAhead = 0.3

    fun shouldRelight(fraction: Double, previous: Double): Boolean =
        kotlin.math.abs(fraction - previous) > .0005 || fraction >= 1 || fraction <= 0

    fun opacity(line: RouteLine, fraction: Double?): Double =
        if (fraction == null || line.speed == null || line.endFraction <= fraction) 1.0 else dimmedAhead
}
