package com.onezee.triptrack.logic

import com.onezee.triptrack.model.FixGate
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripDistanceGate
import java.time.Instant

// iOS: TripManager.swift. In-memory recording math; persistence belongs to core:data.
class RecordingMath(initialTrip: Trip = Trip()) {
    var trip: Trip = initialTrip.withRecordingBreaks(initialTrip.recordingBreaks)
        private set
    val filter = KalmanLocationFilter()
    private val continuity = RecordingFixContinuity()
    private var lastShape: LocationFix? = null
    private var lastDistance: LocationFix? = null
    var isPaused: Boolean = false
        private set

    fun setPaused(paused: Boolean, at: Instant) {
        if (isPaused == paused) return
        isPaused = paused
        if (paused) {
            trip = trip.withRecordingBreaks(trip.recordingBreaks + at)
            lastShape = null
            lastDistance = null
            continuity.reset()
            filter.reset()
        }
    }

    /**
     * The points stored since the previous call, handed to storage and forgotten here: the recorder
     * saves them in batches, as iOS saves the managed object, and a whole drive kept in this
     * snapshot would be copied on every fix.
     */
    fun takeTrackPoints(): List<TrackPoint> {
        val points = trip.trackPoints
        trip = trip.copy(trackPoints = emptyList())
        return points
    }

    fun handleNewLocation(raw: LocationFix, now: Instant = Instant.now()): TrackPoint? {
        if (isPaused || trip.endDate != null) return null
        if (!(raw.horizontalAccuracy >= 0 && raw.horizontalAccuracy <= FixGate.recordingAccuracyLimit)) return null
        if (continuity.rejection(raw) != null) return null
        val filtered = filter.processGPSUpdate(raw)
        val decision = RecordingDecision.decide(raw, filtered, lastShape, lastDistance, filter.lastUpdateResetAfterGap)
        lastDistance = decision.distanceAnchor
        trip = trip.copy(distance = trip.distance + decision.distanceGain, maxSpeed = maxOf(trip.maxSpeed, decision.gapMaximum))
        if (!decision.storeShape) return null
        val point = TrackPoint(
            latitude = filtered.latitude, longitude = filtered.longitude, altitude = filtered.altitude,
            speed = decision.recordingSpeed, course = filtered.course, horizontalAccuracy = raw.horizontalAccuracy,
            timestamp = filtered.timestamp,
            recordingSegmentIndex = RecordingBreaks.segmentIndex(filtered.timestamp, trip.recordingBreaks),
        )
        lastShape = filtered
        val trusted = point.countsForDistance && TripDistanceGate.isPlausibleSpeed(point.speed)
        val elapsed = now.secondsSince(trip.startDate)
        trip = trip.copy(
            trackPoints = trip.trackPoints + point,
            maxSpeed = if (trusted) maxOf(trip.maxSpeed, point.speed) else trip.maxSpeed,
            averageSpeed = if (elapsed > 0) trip.distance / elapsed else trip.averageSpeed,
        )
        return point
    }
}
