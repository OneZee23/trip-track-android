package com.onezee.triptrack.logic

import com.onezee.triptrack.model.FixGate

// iOS: FixGate.swift
val FixGate.recordingAccuracyLimit: Double get() = 200.0
val FixGate.idleAccuracyLimit: Double get() = recordingAccuracyLimit
val FixGate.maxLocationAge: Double get() = 10.0
val FixGate.maxSpeedMS: Double get() = 83.3

fun FixGate.decide(
    horizontalAccuracy: Double,
    ageSeconds: Double,
    speedMS: Double,
    isRecording: Boolean,
    recordingLimit: Double = recordingAccuracyLimit,
): FixGate.Decision {
    if (!(horizontalAccuracy >= 0)) return FixGate.Decision.Reject(FixGate.Reason.invalid)
    val limit = if (isRecording) recordingLimit else idleAccuracyLimit
    if (!(horizontalAccuracy <= limit)) return FixGate.Decision.Reject(FixGate.Reason.accuracy)
    if (!(ageSeconds < maxLocationAge)) return FixGate.Decision.Reject(FixGate.Reason.stale)
    if (maxOf(0.0, speedMS) > maxSpeedMS) return FixGate.Decision.Reject(FixGate.Reason.speed)
    return FixGate.Decision.Accept
}
