package com.onezee.triptrack.location.recording

import java.time.Instant

/**
 * iOS: MapViewModel.swift, the speedometer (`smoothedSpeed`, `speedDecayTimer`). What the ongoing
 * notification and the record screen show; it never reaches storage.
 *
 * Metres per second throughout. Below one metre per second a sample reads as a stop.
 */
class LiveSpeed {
    var speed: Double = 0.0
        private set
    private var lastFix: Instant? = null
    private var consecutiveZeros = 0

    fun reset(now: Instant) {
        speed = 0.0
        consecutiveZeros = 0
        // A fresh stamp keeps a new recording from reading a stale gap as «GPS lost».
        lastFix = now
    }

    /** An accepted fix; `rawSpeed` below zero means unknown, not stopped. */
    fun onFix(rawSpeed: Double, now: Instant) {
        val sample = maxOf(0.0, rawSpeed).let { if (it < 1.0) 0.0 else it }
        val gap = lastFix?.let { secondsBetween(it, now) } ?: Double.POSITIVE_INFINITY
        if (sample == 0.0) {
            // A lone unknown-speed fix among real ones must not flash 60 → 0 → 60, while a real stop
            // still reads zero within about two seconds: snap only on the second sub-floor sample.
            consecutiveZeros++
            speed = if (consecutiveZeros >= 2) 0.0 else (1 - ALPHA) * speed
        } else {
            consecutiveZeros = 0
            // After a background or GPS gap, jump straight to the real value.
            speed = if (gap > 3.0) sample else ALPHA * sample + (1 - ALPHA) * speed
        }
        lastFix = now
    }

    /** The half-second tick: without a fix for two seconds the needle falls to zero. */
    fun tick(now: Instant) {
        if (speed <= 0) return
        val since = lastFix?.let { secondsBetween(it, now) } ?: return
        if (since > 2.0) {
            val decayed = speed * 0.4
            speed = if (decayed < 1 / 3.6) 0.0 else decayed
        }
    }

    /** No accepted fix for more than ten seconds: the screen says the signal is lost. */
    fun isStale(now: Instant): Boolean = lastFix?.let { secondsBetween(it, now) > 10 } ?: true

    private companion object {
        const val ALPHA = 0.3
    }
}
