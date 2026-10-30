package com.onezee.triptrack.logic

import java.time.Instant
import kotlin.math.*
import kotlin.random.Random
import kotlin.test.*

// iOS: KalmanLocationFilterTests.swift
class KalmanLocationFilterTests {
    private val fixtureStart = Instant.ofEpochSecond(1_700_000_000)
    private val filter = KalmanLocationFilter()

    private fun fix(east: Double = 0.0, north: Double = 0.0, speed: Double, course: Double,
                    accuracy: Double = 10.0, time: Double) = LocationFix(
        latitude = 45 + north / 111_320,
        longitude = 39 + east / (111_320 * cos(45 * PI / 180)), altitude = 30.0,
        horizontalAccuracy = accuracy, verticalAccuracy = 0.0, course = course, speed = speed,
        timestamp = fixtureStart.addingSeconds(time),
    )

    private fun assertSameLocation(expected: LocationFix, actual: LocationFix) {
        assertNear(expected.latitude, actual.latitude, 1e-12)
        assertNear(expected.longitude, actual.longitude, 1e-12)
        assertEquals(expected.timestamp, actual.timestamp)
        assertNear(expected.speed, actual.speed, 1e-9)
        assertNear(expected.course, actual.course, 1e-9)
        assertNear(expected.horizontalAccuracy, actual.horizontalAccuracy, 1e-9)
        assertNear(expected.altitude, actual.altitude, 1e-9)
    }

    @Test fun testFirstPointPassesThrough() {
        val input = LocationFix(45.0, 39.0)
        val output = filter.processGPSUpdate(input)
        assertNear(45.0, output.latitude, 0.0001)
        assertNear(39.0, output.longitude, 0.0001)
    }
    @Test fun testSmoothingReducesNoise() {
        val random = Random(42)
        val inputs = (0..<20).map { i ->
            LocationFix(45 + 10.0 * i / 111_320 + random.nextDouble(-0.0001, 0.0001),
                39 + random.nextDouble(-0.0001, 0.0001), altitude = 30.0, horizontalAccuracy = 10.0,
                course = 0.0, speed = 10.0, timestamp = fixtureStart.plusSeconds(i.toLong()))
        }
        val outputs = inputs.map(filter::processGPSUpdate)
        val inputVariance = inputs.sumOf { ((it.longitude - 39) * 111_320).pow(2) }
        val outputVariance = outputs.sumOf { ((it.longitude - 39) * 111_320).pow(2) }
        assertTrue(outputVariance < inputVariance)
    }
    @Test fun testPredictionDuringGap() {
        val now = Instant.now()
        for (i in 0..<5) filter.processGPSUpdate(fix(north = i * 10.0, speed = 10.0, course = 0.0,
            time = i.toDouble()).copy(horizontalAccuracy = 5.0, verticalAccuracy = -1.0, timestamp = now.plusSeconds(i.toLong())))
        assertNull(filter.predictedLocation())
    }
    @Test fun testPredictionReturnsNilWhenNotInitialized() {
        assertNull(filter.predictedLocation())
        assertFalse(filter.isPredicting)
    }
    @Test fun testIsPredictingFalseInitially() { assertFalse(filter.isPredicting) }
    @Test fun testSmoothTransitionAfterJump() {
        for (i in 0..<3) filter.processGPSUpdate(fix(speed = 0.0, course = 0.0, accuracy = 5.0, time = i.toDouble()))
        val output = filter.processGPSUpdate(fix(north = 100.0, speed = 0.0, course = 0.0, accuracy = 50.0, time = 10.0))
        val distance = abs(output.latitude - 45.0) * 111_320
        assertTrue(distance < 100)
        assertTrue(distance > 0)
    }
    @Test fun testENURoundtrip() {
        val origin = LocationFix(45.035, 38.975, altitude = 30.0, horizontalAccuracy = 5.0,
            course = -1.0, speed = 0.0, timestamp = fixtureStart)
        val result = filter.processGPSUpdate(origin)
        assertTrue(abs(result.latitude - origin.latitude) * 111_320 < 0.01)
        assertTrue(abs(result.longitude - origin.longitude) * 111_320 * cos(45.035 * PI / 180) < 0.01)
    }
    @Test fun testResetClearsState() {
        filter.processGPSUpdate(LocationFix(45.0, 39.0))
        filter.reset()
        assertFalse(filter.isPredicting)
        assertNull(filter.predictedLocation())
    }
    @Test fun testPredictionDoesNotChangeTheNextRecordedFix() {
        val withoutRendering = KalmanLocationFilter()
        val withRendering = KalmanLocationFilter()
        for (t in 0..<5) {
            val measurement = fix(north = t * 10.0, speed = 10.0, course = 0.0, time = t.toDouble())
            withoutRendering.processGPSUpdate(measurement)
            withRendering.processGPSUpdate(measurement)
        }
        for (time in listOf(6.5, 7.0, 7.5, 8.0)) assertNotNull(withRendering.predictedLocation(fixtureStart.addingSeconds(time)))
        val next = fix(east = 3.0, north = 90.0, speed = 10.0, course = 0.0, time = 9.0)
        assertSameLocation(withoutRendering.processGPSUpdate(next), withRendering.processGPSUpdate(next))
        val expected = assertNotNull(withoutRendering.predictedLocation(fixtureStart.plusSeconds(12)))
        val actual = assertNotNull(withRendering.predictedLocation(fixtureStart.plusSeconds(12)))
        assertSameLocation(expected, actual)
    }
    @Test fun testPredictionIsIndependentOfPollingCadenceAndOrder() {
        filter.processGPSUpdate(fix(speed = 10.0, course = 0.0, time = 0.0))
        val first = assertNotNull(filter.predictedLocation(fixtureStart.plusSeconds(5)))
        filter.predictedLocation(fixtureStart.plusSeconds(8))
        filter.predictedLocation(fixtureStart.plusSeconds(3))
        assertSameLocation(first, assertNotNull(filter.predictedLocation(fixtureStart.plusSeconds(5))))
        assertNear(45 + 50 / 111_320.0, first.latitude, 1e-10)
        assertNull(filter.predictedLocation(fixtureStart.plusSeconds(11)))
    }
    @Test fun testOutOfOrderFixDoesNotRewindEstimatorClockOrChangeItsState() {
        val reference = KalmanLocationFilter()
        for (t in 0..<5) {
            val measurement = fix(north = t * 10.0, speed = 10.0, course = 0.0, time = t.toDouble())
            reference.processGPSUpdate(measurement)
            filter.processGPSUpdate(measurement)
        }
        val stale = filter.processGPSUpdate(fix(east = 500.0, speed = 0.0, course = 90.0, time = 1.0))
        assertEquals(fixtureStart.plusSeconds(4), stale.timestamp)
        val next = fix(east = 2.0, north = 50.0, speed = 10.0, course = 0.0, time = 5.0)
        assertSameLocation(reference.processGPSUpdate(next), filter.processGPSUpdate(next))
    }
    @Test fun testReturningFixAfterPredictionHorizonStartsFreshEstimate() {
        filter.processGPSUpdate(fix(speed = 25.0, course = 90.0, time = 0.0))
        assertFalse(filter.lastUpdateResetAfterGap)
        filter.processGPSUpdate(fix(east = 25.0, speed = 25.0, course = 90.0, time = 1.0))
        val returning = fix(north = 200.0, speed = -1.0, course = -1.0, accuracy = 40.0, time = 13.0)
        assertSameLocation(returning, filter.processGPSUpdate(returning))
        assertTrue(filter.lastUpdateResetAfterGap)
        val next = filter.processGPSUpdate(fix(north = 200.0, speed = -1.0, course = -1.0, time = 14.0))
        assertFalse(filter.lastUpdateResetAfterGap)
        assertNear(0.0, next.speed, 1e-9)
        assertEquals(-1.0, next.course)
    }
    @Test fun testGapResetSignalOnlyDescribesTheCurrentUpdate() {
        filter.processGPSUpdate(fix(speed = 0.0, course = -1.0, time = 0.0))
        filter.processGPSUpdate(fix(north = 5000.0, speed = 0.0, course = -1.0, time = 240.0))
        assertTrue(filter.lastUpdateResetAfterGap)
        filter.processGPSUpdate(fix(north = 5000.0, speed = 0.0, course = -1.0, time = 240.1))
        assertFalse(filter.lastUpdateResetAfterGap)
        filter.processGPSUpdate(fix(north = 5010.0, speed = 0.0, course = -1.0, time = 300.0))
        assertTrue(filter.lastUpdateResetAfterGap)
        filter.reset()
        assertFalse(filter.lastUpdateResetAfterGap)
    }
    @Test fun testLongOutageAfterCoarseTeleportCannotRetainRunawayVelocity() {
        for (t in 0..<30) filter.processGPSUpdate(fix(north = t * 14.4, speed = 14.4, course = 0.0, time = t.toDouble()))
        filter.processGPSUpdate(fix(east = 80_000.0, north = 1_000.0, speed = -1.0, course = -1.0, accuracy = 125.0, time = 84.0))
        filter.processGPSUpdate(fix(east = 81_000.0, north = 1_000.0, speed = -1.0, course = -1.0, accuracy = 190.0, time = 92.0))
        val returning = fix(north = 18_000.0, speed = 14.4, course = -1.0, accuracy = 42.0, time = 870.0)
        assertSameLocation(returning, filter.processGPSUpdate(returning))
        val next = filter.processGPSUpdate(fix(north = 18_014.4, speed = 14.4, course = 0.0, time = 871.0))
        assertTrue(next.speed.isFinite())
        assertTrue(next.speed > 5)
        assertTrue(next.speed < 20)
        assertTrue(next.distanceFrom(returning) < 30)
    }
    @Test fun testRepeatedCoordinateBurstDoesNotCreateTrackOrExtraConfidence() {
        val singleMeasurement = KalmanLocationFilter()
        for (t in 0..<30) {
            val measurement = fix(north = t * 10.0, speed = 10.0, course = 0.0, time = t.toDouble())
            singleMeasurement.processGPSUpdate(measurement)
            filter.processGPSUpdate(measurement)
        }
        val first = fix(east = 200.0, north = 300.0, speed = -1.0, course = -1.0, accuracy = 45.0, time = 30.0)
        val expected = singleMeasurement.processGPSUpdate(first)
        filter.processGPSUpdate(first)
        for (index in 1..52) assertSameLocation(expected, filter.processGPSUpdate(fix(
            east = 200.0, north = 300.0, speed = -1.0, course = -1.0,
            accuracy = 32 + (index % 4) * 0.01, time = 30 + index * 0.209 / 52)))
        val next = fix(north = 310.0, speed = 10.0, course = 0.0, time = 31.0)
        assertSameLocation(singleMeasurement.processGPSUpdate(next), filter.processGPSUpdate(next))
    }
    @Test fun testSameCoordinateWithNewStopSpeedIsNotSuppressedAsDuplicate() {
        filter.processGPSUpdate(fix(speed = 10.0, course = -1.0, time = 0.0))
        val stop = fix(speed = 0.0, course = -1.0, time = 0.1)
        val output = filter.processGPSUpdate(stop)
        assertEquals(stop.timestamp, output.timestamp)
        assertEquals(0.0, output.speed)
    }
    @Test fun testValidScalarSpeedSurvivesMissingCourse() {
        filter.processGPSUpdate(fix(speed = 10.0, course = 0.0, time = 0.0))
        val output = filter.processGPSUpdate(fix(east = 30.0, north = 10.0, speed = 14.4, course = -1.0, time = 1.0))
        assertNear(14.4, output.speed, 1e-9)
    }
}
