package com.onezee.triptrack.logic

import com.onezee.triptrack.model.AutoTripPolicy
import java.time.Instant

// iOS: RecordingMovementTracker.swift
class RecordingMovementTracker {
    var lastDistanceMeters: Double? = null
        private set
    var lastChangeTime: Instant? = null
        private set

    fun reset() {
        lastDistanceMeters = null
        lastChangeTime = null
    }

    fun record(currentDistance: Double, now: Instant = Instant.now()): Boolean {
        val lastDistance = lastDistanceMeters
        if (lastDistance == null || lastChangeTime == null || currentDistance < lastDistance) {
            lastDistanceMeters = currentDistance
            lastChangeTime = now
            return false
        }
        if (currentDistance - lastDistance < AutoTripPolicy.meaningfulMovementDistance) return false
        lastDistanceMeters = currentDistance
        lastChangeTime = now
        return true
    }

    fun isStale(threshold: Double, now: Instant = Instant.now()): Boolean =
        lastChangeTime?.let { now.secondsSince(it) > threshold } ?: false

    fun extendWindow(to: Instant = Instant.now()) { lastChangeTime = to }
}
