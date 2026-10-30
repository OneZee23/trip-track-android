package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: TripDistanceGateTests.swift
class TripDistanceGateTests {
    @Test fun testHighwaySegmentCounts() { assertTrue(TripDistanceGate.isPlausibleSegment(28.0, 1.0)) }
    @Test fun testSparseGpsDeadZoneBridgeCounts() { assertTrue(TripDistanceGate.isPlausibleSegment(5000.0, 120.0)) }
    @Test fun testTeleportJumpRejected() { assertFalse(TripDistanceGate.isPlausibleSegment(5000.0, 1.0)) }
    @Test fun testSpeedCeilingBoundary() {
        assertTrue(TripDistanceGate.isPlausibleSegment(83.0, 1.0))
        assertFalse(TripDistanceGate.isPlausibleSegment(83.01, 1.0))
    }
    @Test fun testSlowStationaryDriftStillCounts() { assertTrue(TripDistanceGate.isPlausibleSegment(3.0, 2.0)) }
    @Test fun testNoTimestampFallbackUnderCapCounts() { assertTrue(TripDistanceGate.isPlausibleSegment(999.0, 0.0)) }
    @Test fun testNoTimestampFallbackAtOrOverCapRejected() {
        assertFalse(TripDistanceGate.isPlausibleSegment(1000.0, 0.0))
        assertFalse(TripDistanceGate.isPlausibleSegment(1500.0, 0.0))
    }
    @Test fun testNegativeDtUsesAbsoluteCap() {
        assertTrue(TripDistanceGate.isPlausibleSegment(500.0, -5.0))
        assertFalse(TripDistanceGate.isPlausibleSegment(1500.0, -5.0))
    }
    @Test fun testMovementSplitExcludesTeleportFromMovingAverage() {
        val start = java.time.Instant.parse("2001-01-01T00:00:00Z").plusSeconds(1_000_000)
        fun pt(lon: Double, sec: Double, speed: Double) = TrackPoint(latitude = 55.0, longitude = 37.0 + lon,
            speed = speed, timestamp = start.addingSeconds(sec))
        val trip = Trip(startDate = start, endDate = start.plusSeconds(210), trackPoints = listOf(
            pt(0.000, 0.0, 20.0), pt(0.001, 10.0, 20.0), pt(0.001, 20.0, 0.0),
            pt(0.001, 30.0, 0.0), pt(0.002, 200.0, 20.0), pt(0.100, 210.0, 20.0),
        ))
        assertNear(20.0, trip.drivingTime, 0.01)
        assertNear(10.0, trip.stoppedTime, 0.01)
        assertTrue(trip.movingAverageSpeedMS < 50 / 3.6)
    }
    @Test fun testExplicitPauseExcludesPlausibleMissingJourneyButKeepsBothRecordedLegs() {
        assertNear(20.0, TripDistanceGate.totalDistance(listOf(sample(0.0, 0.0), sample(10.0, 1.0),
            sample(5010.0, 121.0, 1), sample(5020.0, 122.0, 1))), 0.1)
    }
    @Test fun testSparseGPSWithoutPauseStillCountsTheBridge() {
        assertNear(5020.0, TripDistanceGate.totalDistance(listOf(sample(0.0, 0.0), sample(10.0, 1.0),
            sample(5010.0, 121.0), sample(5020.0, 122.0))), 10.0)
    }
    @Test fun testPauseResetsAnchorBeforeMinimumDistanceCheck() {
        assertNear(6.0, TripDistanceGate.totalDistance(listOf(sample(0.0, 0.0), sample(4.0, 1.0, 1),
            sample(8.0, 2.0, 1), sample(10.0, 3.0, 1))), 0.1)
    }
    @Test fun testShortExplicitPauseIsExcludedFromMovementTimeAndDistance() {
        fun point(north: Double, seconds: Double): TrackPoint {
            val c = TrackTestKit.coordinate(0.0, north)
            return TrackPoint(latitude = c.latitude, longitude = c.longitude, speed = 10.0,
                timestamp = TrackTestKit.epoch.addingSeconds(seconds))
        }
        val trip = Trip(startDate = TrackTestKit.epoch, endDate = TrackTestKit.epoch.plusSeconds(13),
            trackPoints = listOf(point(0.0, 0.0), point(10.0, 1.0), point(110.0, 12.0), point(120.0, 13.0)),
            recordingBreaks = listOf(TrackTestKit.epoch.plusSeconds(12)))
        assertEquals(listOf(0, 0, 1, 1), trip.measuredPoints.map { it.recordingSegmentIndex })
        assertNear(2.0, trip.drivingTime, 0.01)
        assertNear(0.0, trip.stoppedTime, 0.01)
        assertNear(10.0, trip.movingAverageSpeedMS, 0.1)
    }
    @Test fun testMaximumKeepsAValidSpeedFromTheFirstRecordedPoint() {
        assertEquals(25.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = 25.0), sample(100.0, 5.0, speed = 0.0))))
    }
    @Test fun testLongGPSGapWithStoppedArrivalStillProvesTheTripWasMoving() {
        val samples = listOf(sample(0.0, 0.0, speed = 0.0), sample(5000.0, 240.0, speed = 0.0))
        assertNear(5000 / 240.0, TripDistanceGate.maximumRecordedSpeed(samples), 0.1)
        assertEquals(0.0, samples.last().speed)
    }
    @Test fun testLongGPSGapUsesPlausibleDisplacementWhenSpeedsAreUnknownOrCorrupt() {
        assertNear(5000 / 240.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = -1.0),
            sample(5000.0, 240.0, speed = 1000.0))), 0.1)
    }
    @Test fun testMaximumDoesNotInferTravelDuringAnExplicitPause() {
        assertEquals(0.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = 0.0),
            sample(5000.0, 240.0, 1, 0.0))))
    }
    @Test fun testMaximumIgnoresImpossibleJumpRatherThanClampingToTheSpeedCeiling() {
        assertEquals(0.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = -1.0),
            sample(5000.0, 11.0, speed = 1000.0))))
    }
    @Test fun testDenseGeometryDoesNotInventAMaximumWhenSpeedWasNotMeasured() {
        assertEquals(0.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = -1.0),
            sample(100.0, 10.0, speed = -1.0))))
    }
    @Test fun testNonFiniteAndUnknownSpeedsAreNotRecords() {
        assertEquals(0.0, TripDistanceGate.maximumRecordedSpeed(listOf(sample(0.0, 0.0, speed = Double.NaN),
            sample(0.0, 1.0, speed = Double.POSITIVE_INFINITY), sample(0.0, 2.0, speed = -1.0))))
    }

    private fun sample(north: Double, seconds: Double, segment: Int = 0, speed: Double? = null): TripDistanceGate.Sample {
        val c = TrackTestKit.coordinate(0.0, north)
        return TripDistanceGate.Sample(c.latitude, c.longitude, TrackTestKit.epoch.addingSeconds(seconds), segment, speed)
    }
}
