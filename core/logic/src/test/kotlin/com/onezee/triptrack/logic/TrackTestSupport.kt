package com.onezee.triptrack.logic

import kotlin.math.abs
import kotlin.test.assertTrue

// iOS: TrackFidelityTests.swift, Noise (UInt64 overflow is intentional).
internal class Noise(seed: Long) {
    private var state = seed
    fun next(): Double {
        state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        return ((state ushr 33) % 10_000).toDouble() / 10_000.0 * 2 - 1
    }
}

internal fun assertNear(expected: Double, actual: Double, accuracy: Double) {
    assertTrue(abs(expected - actual) <= accuracy, "Expected $expected ± $accuracy, actual $actual")
}
