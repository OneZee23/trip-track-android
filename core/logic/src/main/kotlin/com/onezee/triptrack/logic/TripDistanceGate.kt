package com.onezee.triptrack.logic

import com.onezee.triptrack.model.FixGate
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripDistanceGate

// iOS: TripDistanceGate.swift. Keep the shared sample in core:model.
val TripDistanceGate.maxPlausibleSpeed: Double get() = 83.0
val TripDistanceGate.maxSegmentDistance: Double get() = 1000.0
val TripDistanceGate.minStep: Double get() = 5.0
val TripDistanceGate.odometerAccuracyLimit: Double get() = 65.0

fun TripDistanceGate.isPlausibleSpeed(speed: Double): Boolean =
    speed.isFinite() && speed >= 0 && speed <= FixGate.maxSpeedMS

fun TripDistanceGate.isPlausibleSegment(meters: Double, dt: Double): Boolean =
    if (dt > 0) meters / dt <= maxPlausibleSpeed else meters < maxSegmentDistance

fun TripDistanceGate.countsForDistance(horizontalAccuracy: Double, isInterpolated: Boolean): Boolean =
    !isInterpolated && horizontalAccuracy <= odometerAccuracyLimit

fun TripDistanceGate.totalDistance(samples: List<TripDistanceGate.Sample>, minStep: Double = this.minStep): Double {
    if (samples.size < 2) return 0.0
    var total = 0.0
    var anchor = samples.first()
    for (sample in samples.drop(1)) {
        if (sample.recordingSegmentIndex != anchor.recordingSegmentIndex) {
            anchor = sample
            continue
        }
        val meters = GeoPoint(sample.latitude, sample.longitude).distanceFrom(GeoPoint(anchor.latitude, anchor.longitude))
        if (!(meters >= minStep)) continue
        val from = anchor.timestamp
        val to = sample.timestamp
        val dt = if (from != null && to != null) to.secondsSince(from) else 0.0
        if (isPlausibleSegment(meters, dt)) total += meters
        // A rejected teleport must not pin the odometer to an old position.
        anchor = sample
    }
    return total
}

fun TripDistanceGate.maximumRecordedSpeed(samples: List<TripDistanceGate.Sample>): Double {
    var maximum = samples.mapNotNull { it.speed }.filter { isPlausibleSpeed(it) }.maxOrNull() ?: 0.0
    for ((from, to) in samples.zipWithNext()) {
        if (from.recordingSegmentIndex != to.recordingSegmentIndex) continue
        val start = from.timestamp ?: continue
        val end = to.timestamp ?: continue
        val dt = end.secondsSince(start)
        if (dt <= 10 || !dt.isFinite()) continue
        val a = GeoPoint(from.latitude, from.longitude)
        val b = GeoPoint(to.latitude, to.longitude)
        if (!a.isValid || !b.isValid) continue
        val meters = a.distanceFrom(b)
        if (meters > 0 && meters.isFinite() && isPlausibleSegment(meters, dt)) maximum = maxOf(maximum, meters / dt)
    }
    return maximum
}
