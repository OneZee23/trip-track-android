package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TripDistanceGate

// iOS: TripManager.swift, handleNewLocation / shouldStoreShapePoint.
data class RecordingDecision(
    val storeShape: Boolean,
    val distanceAnchor: LocationFix?,
    val distanceGain: Double = 0.0,
    val gapMaximum: Double = 0.0,
    val recordingSpeed: Double = 0.0,
    val rejectedAsDrift: Boolean = false,
) {
    companion object {
        const val minRecordDistance = 5.0
        const val driftSpeedThreshold = 1.0
        const val driftCalcSpeedLimit = 5.0
        const val shapeMinSpeed = 0.5
        const val shapeMinDistance = 1.0
        const val shapeMinInterval = 1.0 / 3.0
        const val shapeCourseDelta = 12.0

        fun shouldStoreShapePoint(candidate: LocationFix, last: LocationFix, rawSpeed: Double): Boolean {
            val meters = candidate.distanceFrom(last)
            // Preserve every point admitted by the old five-metre rule.
            if (meters >= minRecordDistance) return true
            if (!(rawSpeed >= shapeMinSpeed && meters >= shapeMinDistance)) return false
            val dt = candidate.timestamp.secondsSince(last.timestamp)
            if (dt < shapeMinInterval) return false
            return dt >= 1.0 || courseDelta(candidate.course, last.course) >= shapeCourseDelta
        }

        fun decide(
            raw: LocationFix,
            filtered: LocationFix,
            lastShape: LocationFix?,
            lastDistance: LocationFix?,
            resetAfterGap: Boolean,
        ): RecordingDecision {
            if (lastShape != null && filtered.timestamp <= lastShape.timestamp) {
                return RecordingDecision(false, lastDistance)
            }
            val trusted = TripDistanceGate.countsForDistance(raw.horizontalAccuracy, false)
            val speed = when {
                TripDistanceGate.isPlausibleSpeed(filtered.speed) -> filtered.speed
                TripDistanceGate.isPlausibleSpeed(raw.speed) -> raw.speed
                trusted && lastDistance != null -> {
                    val dt = filtered.timestamp.secondsSince(lastDistance.timestamp)
                    val estimate = if (dt > 0) filtered.distanceFrom(lastDistance) / dt else -1.0
                    if (TripDistanceGate.isPlausibleSpeed(estimate)) estimate else 0.0
                }
                else -> 0.0
            }
            var distanceAnchor = lastDistance
            var gain = 0.0
            var gapMaximum = 0.0
            if (trusted) {
                if (lastDistance == null) distanceAnchor = filtered
                else {
                    val meters = filtered.distanceFrom(lastDistance)
                    if (meters >= minRecordDistance) {
                        val dt = filtered.timestamp.secondsSince(lastDistance.timestamp)
                        if (dt > 0 && !resetAfterGap && filtered.speed >= 0 &&
                            filtered.speed < driftSpeedThreshold && meters / dt > driftCalcSpeedLimit) {
                            // Drift cancels both shape and distance without moving either anchor.
                            return RecordingDecision(false, lastDistance, rejectedAsDrift = true)
                        }
                        if (TripDistanceGate.isPlausibleSegment(meters, dt)) {
                            gain = meters
                            gapMaximum = TripDistanceGate.maximumRecordedSpeed(listOf(
                                TripDistanceGate.Sample(lastDistance.latitude, lastDistance.longitude, lastDistance.timestamp),
                                TripDistanceGate.Sample(filtered.latitude, filtered.longitude, filtered.timestamp),
                            ))
                        }
                        distanceAnchor = filtered
                    }
                }
            }
            val store = (trusted || raw.speed >= shapeMinSpeed) &&
                (lastShape == null || shouldStoreShapePoint(filtered, lastShape, raw.speed))
            return RecordingDecision(store, distanceAnchor, gain, gapMaximum, speed)
        }
    }
}
