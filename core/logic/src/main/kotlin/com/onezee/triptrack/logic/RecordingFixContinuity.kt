package com.onezee.triptrack.logic

import com.onezee.triptrack.model.FixGate
import com.onezee.triptrack.model.RecordingFixContinuity.Rejection
import com.onezee.triptrack.model.TripDistanceGate

// iOS: RecordingFixContinuity.swift
class RecordingFixContinuity {
    private var lastAccepted: LocationFix? = null

    fun reset() { lastAccepted = null }

    fun rejection(fix: LocationFix): Rejection? {
        if (!fix.coordinate.isValid || !fix.horizontalAccuracy.isFinite() ||
            fix.horizontalAccuracy < 0 || fix.horizontalAccuracy > FixGate.recordingAccuracyLimit ||
            !fix.speed.isFinite()) return Rejection.invalid
        if (fix.speed > FixGate.maxSpeedMS) return Rejection.speed
        lastAccepted?.let { previous ->
            val dt = fix.timestamp.secondsSince(previous.timestamp)
            if (dt <= 0) return Rejection.timestamp
            val uncertainty = minOf(TripDistanceGate.odometerAccuracyLimit,
                previous.horizontalAccuracy + fix.horizontalAccuracy)
            if (fix.distanceFrom(previous) > TripDistanceGate.maxPlausibleSpeed * dt + uncertainty) {
                return Rejection.displacement
            }
        }
        // Rejected positions never become the next comparison's anchor.
        lastAccepted = fix
        return null
    }
}
