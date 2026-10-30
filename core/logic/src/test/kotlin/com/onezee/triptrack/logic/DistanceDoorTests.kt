package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: DistanceDoorTests.swift (recording/statistics parts).
class DistanceDoorTests {
    @Test fun testDoorTable() {
        assertTrue(TripDistanceGate.countsForDistance(5.0, false))
        assertTrue(TripDistanceGate.countsForDistance(65.0, false))
        assertFalse(TripDistanceGate.countsForDistance(65.1, false))
        assertFalse(TripDistanceGate.countsForDistance(5.0, true))
        assertFalse(TripDistanceGate.countsForDistance(-1.0, true))
        assertTrue(TripDistanceGate.countsForDistance(0.0, false))
    }
    @Test fun testPostProcessorCountsOnlyTrustedPoints() {
        val trip = TrackTestKit.trip(listOf(
            TrackTestKit.PointSpec(north = 0.0, seconds = 0.0), TrackTestKit.PointSpec(north = 100.0, seconds = 10.0),
            TrackTestKit.PointSpec(east = 60.0, north = 150.0, seconds = 15.0, accuracy = 140.0, speed = 40.0),
            TrackTestKit.PointSpec(north = 200.0, seconds = 20.0), TrackTestKit.PointSpec(north = 300.0, seconds = 30.0)))
        val processed = PostTripTrackProcessor.process(trip)
        assertNear(300.0, processed.distance, 1.0)
        assertNear(10.0, processed.maxSpeed, 0.001)
    }
    @Test fun testLegacyTripRecomputesToTheSameNumber() {
        val trip = TrackTestKit.trip((0..40).map { i -> TrackTestKit.PointSpec(
            east = (i % 7) * 3.0, north = i * 12.0, seconds = i.toDouble(), accuracy = (5 + i % 60).toDouble()) })
        val processed = PostTripTrackProcessor.process(trip)
        val legacy = TripDistanceGate.totalDistance(processed.trackPoints.filter { !it.isInterpolated }.sortedBy { it.timestamp }.map { it.distanceSample() })
        assertNear(legacy, processed.distance, 0.0001)
    }
    @Test fun testMeasuredPointsDropCoarseAndFilled() {
        val trip = TrackTestKit.trip(listOf(TrackTestKit.PointSpec(north = 0.0, seconds = 0.0),
            TrackTestKit.PointSpec(north = 10.0, seconds = 1.0, accuracy = 120.0),
            TrackTestKit.PointSpec(north = 20.0, seconds = 2.0, accuracy = -1.0, speed = -1.0, interpolated = true)))
        assertEquals(1, trip.measuredPoints.size)
    }
    @Test fun testSyncPayloadDoesNotCountAFilledStretchAsStopped() {
        val trip = TrackTestKit.trip(listOf(TrackTestKit.PointSpec(north = 0.0, seconds = 0.0),
            TrackTestKit.PointSpec(north = 100.0, seconds = 10.0)) + (12..18 step 2).map { seconds ->
            TrackTestKit.PointSpec(north = seconds * 10.0, seconds = seconds.toDouble(), speed = -1.0, accuracy = -1.0, interpolated = true)
        } + TrackTestKit.PointSpec(north = 200.0, seconds = 20.0))
        assertEquals(0.0, trip.stoppedTime)
        assertEquals(20.0, trip.drivingTime)
    }
}
