package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant

// iOS: TripConfirmation.swift. Legacy nil and unknown values enter the world as confirmed.
fun TripConfirmation.Companion.permitsWorldEntry(rawValue: String?): Boolean = rawValue != TripConfirmation.draft.rawValue
val Trip.isDraft: Boolean get() = confirmation == TripConfirmation.draft

// iOS: TrackPoint.swift / Trip.swift
val TrackPoint.countsForDistance: Boolean
    get() = TripDistanceGate.countsForDistance(horizontalAccuracy, isInterpolated)
val TrackPoint.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)
val Trip.measuredPoints: List<TrackPoint>
    get() = RecordingBreaks.annotate(trackPoints, recordingBreaks).filter { it.countsForDistance }

// iOS: Trip.swift, recordingBreaks setter. Use at storage/recording boundaries
// because core:model cannot depend on the algorithms in core:logic.
fun Trip.withRecordingBreaks(breaks: List<Instant>): Trip {
    val normalized = RecordingBreaks.normalized(breaks)
    return copy(recordingBreaks = normalized, trackPoints = RecordingBreaks.annotate(trackPoints, normalized))
}

// iOS: Trip.swift, movementSplit
data class MovementSplit(val driving: Double, val stopped: Double, val movingDistance: Double)

fun Trip.movementSplit(): MovementSplit {
    val points = measuredPoints
    var driving = 0.0
    var stopped = 0.0
    var movingDistance = 0.0
    var movingAnchor: TrackPoint? = null
    for ((previous, point) in points.zipWithNext()) {
        if (point.recordingSegmentIndex != previous.recordingSegmentIndex) {
            movingAnchor = null
            continue
        }
        val dt = point.timestamp.secondsSince(previous.timestamp)
        if (dt <= 0 || dt > 60) {
            movingAnchor = null
            continue
        }
        val averageSpeed = (point.speed + previous.speed) / 2.0
        if (averageSpeed < 5.0 / 3.6) {
            stopped += dt
            movingAnchor = null
        } else {
            val anchor = movingAnchor ?: previous
            val meters = point.coordinate.distanceFrom(anchor.coordinate)
            val anchorDt = point.timestamp.secondsSince(anchor.timestamp)
            if (!TripDistanceGate.isPlausibleSegment(meters, anchorDt)) {
                movingAnchor = null
                continue
            }
            driving += dt
            if (meters >= TripDistanceGate.minStep) {
                movingDistance += meters
                movingAnchor = point
            } else if (movingAnchor == null) movingAnchor = previous
        }
    }
    return MovementSplit(driving, stopped, movingDistance)
}

val Trip.drivingTime: Double get() = movementSplit().driving
val Trip.stoppedTime: Double get() = movementSplit().stopped
val Trip.movingAverageSpeedMS: Double get() = movementSplit().let {
    if (it.driving > 0) it.movingDistance / it.driving else averageSpeed
}

// iOS: Trip.swift, TripJunkClassifier
fun TripJunkClassifier.reason(distanceMeters: Double, durationSeconds: Double, maxSpeedMS: Double): TripJunkClassifier.Reason? {
    if (distanceMeters < AutoTripPolicy.junkTripMinDistance && durationSeconds < AutoTripPolicy.junkTripMinDuration) {
        return TripJunkClassifier.Reason.tooShort
    }
    if (maxSpeedMS * 3.6 < AutoTripPolicy.junkTripWalkingSpeedKmh && durationSeconds > AutoTripPolicy.junkTripWalkingMinDuration) {
        return TripJunkClassifier.Reason.noDrivingSpeed
    }
    return null
}

fun TripJunkClassifier.isJunk(distanceMeters: Double, durationSeconds: Double, maxSpeedMS: Double): Boolean =
    reason(distanceMeters, durationSeconds, maxSpeedMS) != null

fun Trip.isJunk(now: Instant = Instant.now()): Boolean =
    TripJunkClassifier.isJunk(distance, (endDate ?: now).secondsSince(startDate), maxSpeed)

// iOS: RecordingFinishPreview.swift
val RecordingFinishPreview.discardReason: TripJunkClassifier.Reason?
    get() = TripJunkClassifier.reason(distance, duration, maxSpeed)

fun RecordingFinishPreview.confirmsSameOutcome(current: RecordingFinishPreview): Boolean =
    tripID == current.tripID && discardReason == current.discardReason

internal fun TrackPoint.distanceSample(): TripDistanceGate.Sample = TripDistanceGate.Sample(
    latitude, longitude, timestamp, recordingSegmentIndex, speed,
)
