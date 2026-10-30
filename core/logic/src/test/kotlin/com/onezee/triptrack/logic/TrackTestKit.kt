package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.math.*
import kotlin.test.assertTrue

// iOS: Support/TrackTestKit.swift
internal object TrackTestKit {
    val origin = GeoPoint(45.035, 38.975)
    const val metersPerDegree = 111_320.0
    val epoch: Instant = Instant.ofEpochSecond(1_780_000_000)

    fun coordinate(east: Double, north: Double): GeoPoint = GeoPoint(
        origin.latitude + north / metersPerDegree,
        origin.longitude + east / (metersPerDegree * cos(origin.latitude * PI / 180)),
    )

    fun fix(east: Double = 0.0, north: Double = 0.0, speed: Double, course: Double = 0.0,
            after: Double, accuracy: Double = 8.0): LocationFix {
        val coordinate = coordinate(east, north)
        return LocationFix(coordinate.latitude, coordinate.longitude, altitude = 30.0,
            horizontalAccuracy = accuracy, verticalAccuracy = 3.0, course = course, speed = speed,
            timestamp = epoch.addingSeconds(after))
    }

    // iOS: Support/TrackTestKit.swift, PointSpec
    data class PointSpec(
        val east: Double = 0.0, val north: Double, val seconds: Double,
        val accuracy: Double = 8.0, val speed: Double = 10.0,
        val altitude: Double = 30.0, val interpolated: Boolean = false,
    )

    fun trip(points: List<PointSpec>, confirmation: TripConfirmation = TripConfirmation.confirmed): Trip = Trip(
        startDate = epoch.addingSeconds(points.firstOrNull()?.seconds ?: 0.0),
        endDate = epoch.addingSeconds(points.lastOrNull()?.seconds ?: 0.0), confirmation = confirmation,
        trackPoints = points.map { spec ->
            val coordinate = coordinate(spec.east, spec.north)
            TrackPoint(latitude = coordinate.latitude, longitude = coordinate.longitude,
                altitude = spec.altitude, speed = spec.speed, course = 0.0,
                horizontalAccuracy = spec.accuracy, timestamp = epoch.addingSeconds(spec.seconds),
                isInterpolated = spec.interpolated)
        },
    )
}

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
