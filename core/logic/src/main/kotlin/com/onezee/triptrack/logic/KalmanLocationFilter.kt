package com.onezee.triptrack.logic

import java.time.Instant
import kotlin.math.*

// iOS: KalmanLocationFilter.swift
class KalmanLocationFilter(private val now: () -> Instant = Instant::now) {
    var lastUpdateResetAfterGap: Boolean = false
        private set
    val timeSinceLastGPS: Double get() = lastGPSTime?.let { now().secondsSince(it) } ?: Double.POSITIVE_INFINITY
    val isPredicting: Boolean get() = lastGPSTime != null && timeSinceLastGPS.let { it > 2 && it <= 10 }

    private var lastGPSTime: Instant? = null
    private var lastGPSMeasurement: LocationFix? = null
    private var lastFilteredLocation: LocationFix? = null
    private var originLat = 0.0
    private var originLon = 0.0
    private var cosOriginLat = 1.0
    private var x = DoubleArray(4)
    private var covariance = DoubleArray(16)
    private var lastAltitude = 0.0
    private var lastCourse = -1.0

    fun processGPSUpdate(location: LocationFix): LocationFix {
        lastUpdateResetAfterGap = false
        val previousTime = lastGPSTime
        if (previousTime == null) {
            initialize(location)
            return location
        }
        val dt = location.timestamp.secondsSince(previousTime)
        if (dt <= 0) return lastFilteredLocation ?: location
        val previous = lastGPSMeasurement
        if (dt < 1.0 / 3.0 && previous != null && location.latitude == previous.latitude &&
            location.longitude == previous.longitude && location.speed == previous.speed &&
            location.course == previous.course) return lastFilteredLocation ?: location
        if (dt > 10) {
            initialize(location)
            lastUpdateResetAfterGap = true
            return location
        }

        val (state, uncertainty) = predictedState(dt, maneuverAccel(location, dt))
        x = state
        covariance = uncertainty
        val east = (location.longitude - originLon) * 111_320.0 * cosOriginLat
        val north = (location.latitude - originLat) * 111_320.0
        val accuracy = max(location.horizontalAccuracy, 10.0)
        // The two position corrections precede the two velocity corrections.
        if (correct(0, east, accuracy * accuracy)) correct(1, north, accuracy * accuracy)
        if (location.speed >= 0 && location.course >= 0) {
            val angle = location.course * PI / 180.0
            val noise = max(accuracy * 0.5, 1.0).pow(2)
            if (correct(2, location.speed * sin(angle), noise)) {
                correct(3, location.speed * cos(angle), noise)
            }
        }
        lastGPSTime = location.timestamp
        lastGPSMeasurement = location
        lastAltitude = location.altitude
        if (location.course >= 0) lastCourse = location.course
        val scalarSpeed = location.speed.takeIf { it >= 0 && location.course < 0 }
        return filteredLocation(location.timestamp, x, covariance, scalarSpeed).also { lastFilteredLocation = it }
    }

    fun predictedLocation(at: Instant = now()): LocationFix? {
        val lastTime = lastGPSTime ?: return null
        val dt = at.secondsSince(lastTime)
        if (dt <= 2 || dt > 10) return null
        // Rendering projects a copy. Polling frequency must not alter recording.
        val (state, uncertainty) = predictedState(dt, 1.0)
        return filteredLocation(at, state, uncertainty)
    }

    fun reset() {
        lastUpdateResetAfterGap = false
        lastGPSTime = null
        lastGPSMeasurement = null
        lastFilteredLocation = null
        x = DoubleArray(4)
        covariance = DoubleArray(16)
        lastAltitude = 0.0
        lastCourse = -1.0
    }

    private fun initialize(location: LocationFix) {
        originLat = location.latitude
        originLon = location.longitude
        cosOriginLat = cos(originLat * PI / 180.0)
        x = DoubleArray(4)
        if (location.speed > 0 && location.course >= 0) {
            val angle = location.course * PI / 180.0
            x[2] = location.speed * sin(angle)
            x[3] = location.speed * cos(angle)
        }
        val positionVariance = max(location.horizontalAccuracy * location.horizontalAccuracy, 25.0)
        covariance = doubleArrayOf(
            positionVariance, 0.0, 0.0, 0.0,
            0.0, positionVariance, 0.0, 0.0,
            0.0, 0.0, 100.0, 0.0,
            0.0, 0.0, 0.0, 100.0,
        )
        lastAltitude = location.altitude
        lastCourse = location.course
        lastGPSTime = location.timestamp
        lastGPSMeasurement = location
        lastFilteredLocation = location
    }

    private fun maneuverAccel(location: LocationFix, dt: Double): Double {
        if (!(dt > 0 && location.speed >= 1.5 && location.course >= 0 && lastCourse >= 0)) return 1.0
        val turnRate = courseDelta(location.course, lastCourse) / dt
        if (turnRate <= 5) return 1.0
        val fraction = min(1.0, (turnRate - 5) / (25 - 5))
        return 1.0 + (4.0 - 1.0) * fraction
    }

    private fun predictedState(dt: Double, accel: Double): Pair<DoubleArray, DoubleArray> {
        val projected = x.copyOf()
        projected[0] += x[2] * dt
        projected[1] += x[3] * dt
        val q = accel * accel
        val dt2 = dt * dt
        val dt3 = dt2 * dt / 2.0
        val dt4 = dt2 * dt2 / 4.0
        val p = covariance
        val next = doubleArrayOf(
            p[0] + dt * (p[2] + p[8]) + dt2 * p[10],
            p[1] + dt * (p[3] + p[9]) + dt2 * p[11],
            p[2] + dt * p[10], p[3] + dt * p[11],
            p[4] + dt * (p[6] + p[12]) + dt2 * p[14],
            p[5] + dt * (p[7] + p[13]) + dt2 * p[15],
            p[6] + dt * p[14], p[7] + dt * p[15],
            p[8] + dt * p[10], p[9] + dt * p[11], p[10], p[11],
            p[12] + dt * p[14], p[13] + dt * p[15], p[14], p[15],
        )
        next[0] += q * dt4
        next[5] += q * dt4
        next[2] += q * dt3
        next[8] += q * dt3
        next[7] += q * dt3
        next[13] += q * dt3
        next[10] += q * dt2
        next[15] += q * dt2
        return projected to next
    }

    // Each observation selects one component of [east, north, vE, vN].
    private fun correct(component: Int, measurement: Double, noise: Double): Boolean {
        val innovationCovariance = covariance[component * 4 + component] + noise
        if (!(innovationCovariance > 1e-10)) return false
        val gains = DoubleArray(4) { covariance[it * 4 + component] / innovationCovariance }
        val innovation = measurement - x[component]
        for (i in 0..3) x[i] += gains[i] * innovation
        val next = covariance.copyOf()
        for (i in 0..3) for (j in 0..3) {
            next[i * 4 + j] -= gains[i] * covariance[component * 4 + j]
        }
        covariance = next
        return true
    }

    private fun filteredLocation(
        timestamp: Instant, state: DoubleArray, covariance: DoubleArray, speedOverride: Double? = null,
    ): LocationFix {
        val speed = hypot(state[2], state[3])
        val course = if (speed > 0.5) {
            val bearing = atan2(state[2], state[3]) * 180.0 / PI
            if (bearing < 0) bearing + 360 else bearing
        } else lastCourse
        return LocationFix(
            latitude = originLat + state[1] / 111_320.0,
            longitude = originLon + state[0] / (111_320.0 * cosOriginLat),
            altitude = lastAltitude,
            horizontalAccuracy = sqrt(max(max(covariance[0], covariance[5]), 0.0)),
            speed = speedOverride ?: speed,
            course = course,
            timestamp = timestamp,
        )
    }
}
