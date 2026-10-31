package com.onezee.triptrack.map.replay

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.model.GeoPoint

// iOS: RouteMapView.swift, Coordinator.fraction / progressFraction.
object PlaybackProgress {
    fun cumulative(coords: List<GeoPoint>): List<Double> {
        var total = 0.0
        return coords.mapIndexed { i, coordinate ->
            if (i > 0) total += GeometryUtils.haversineDistance(coords[i - 1], coordinate)
            total
        }
    }

    fun fraction(cum: List<Double>, coords: List<GeoPoint>, car: GeoPoint, trailIndex: Int): Double {
        val total = cum.lastOrNull() ?: return 0.0
        if (total <= 0 || cum.size != coords.size) return 0.0
        val index = trailIndex.coerceIn(0, coords.lastIndex)
        val partial = GeometryUtils.haversineDistance(coords[index], car)
        return ((cum[index] + partial) / total).coerceIn(0.0, 1.0)
    }
}
