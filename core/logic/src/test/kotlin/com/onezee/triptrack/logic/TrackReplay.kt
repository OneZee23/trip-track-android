package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*

// iOS: Support/TrackReplay.swift (pure recording and straight-fill stages).
internal object TrackReplay {
    // iOS: Support/TrackReplay.swift, Result
    data class Result(
        val recordedGaps: Int, val openGaps: Int, val coarsePoints: Int,
        val filledPoints: Int, val fillRuns: Int, val distance: Double,
    )

    fun run(fixes: List<LocationFix>, recordingLimit: Double = FixGate.recordingAccuracyLimit): Result {
        val manager = RecordingMath(Trip(startDate = fixes.firstOrNull()?.timestamp ?: TrackTestKit.epoch))
        for (fix in fixes) {
            if (FixGate.decide(fix.horizontalAccuracy, 0.0, fix.speed, true, recordingLimit) == FixGate.Decision.Accept) {
                manager.handleNewLocation(fix)
            }
        }
        val recorded = manager.trip.trackPoints.map { it.gapPoint() }
        val trip = PostTripTrackProcessor.process(manager.trip.copy(endDate = fixes.lastOrNull()?.timestamp))
        val points = trip.trackPoints.sortedBy { it.timestamp }
        var fillRuns = 0
        var inRun = false
        for (point in points) {
            if (point.isInterpolated && !inRun) fillRuns++
            inRun = point.isInterpolated
        }
        return Result(
            TrackGapFinder.openGaps(recorded).size, TrackGapFinder.openGaps(points.map { it.gapPoint() }).size,
            points.count { !it.isInterpolated && it.horizontalAccuracy > TripDistanceGate.odometerAccuracyLimit },
            points.count { it.isInterpolated }, fillRuns, trip.distance,
        )
    }
}
