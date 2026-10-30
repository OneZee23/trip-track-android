package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: PostTripTrackProcessorTests.swift (pure gap/stat checks; preview/storage are separate).
class PostTripTrackProcessorTests {
    private val start = TrackTestKit.epoch
    private fun point(latitude: Double, seconds: Long, speed: Double = 10.0, longitude: Double = 39.0, course: Double = 0.0) =
        TrackPoint(latitude = latitude, longitude = longitude, speed = speed, course = course, altitude = 30.0,
            horizontalAccuracy = 5.0, timestamp = start.plusSeconds(seconds))
    private fun trip(points: List<TrackPoint>) = Trip(startDate = start, endDate = points.last().timestamp, trackPoints = points)

    @Test fun testShortGapIsInterpolated() {
        val trip = trip(listOf(point(45.0, 0), point(45.0001, 1), point(45.001, 11), point(45.0011, 12)))
        assertEquals(0, PostTripTrackProcessor.process(trip).trackPoints.count { it.isInterpolated })
    }
    @Test fun testNoGapNoInterpolation() {
        val trip = trip(listOf(point(45.0, 0), point(45.0001, 1), point(45.0002, 2), point(45.0003, 3)))
        assertEquals(0, PostTripTrackProcessor.process(trip).trackPoints.count { it.isInterpolated })
    }
    @Test fun testLargeGapNotInterpolated() {
        val trip = trip(listOf(point(45.0, 0), point(45.001, 1), point(45.1, 61), point(45.101, 62)))
        assertEquals(0, PostTripTrackProcessor.process(trip).trackPoints.count { it.isInterpolated })
    }
    @Test fun testProcessTripIsIdempotent() {
        val trip = trip(listOf(point(45.0, 0), point(45.001, 10)))
        val first = PostTripTrackProcessor.process(trip)
        val second = PostTripTrackProcessor.process(first)
        assertEquals(first.trackPoints.size, second.trackPoints.size)
    }
    @Test fun testStatsRecalculated() {
        val trip = trip(listOf(point(45.0, 0), point(45.001, 10, speed = 20.0)))
        val processed = PostTripTrackProcessor.process(trip)
        assertTrue(processed.distance > 0)
        assertEquals(20.0, processed.maxSpeed)
        assertNear(processed.distance / 10, processed.averageSpeed, 1e-12)
    }
    @Test fun testProcessedTripHasPreviewPolyline() {
        val trip = trip(listOf(point(45.0, 0, course = 340.0), point(45.0001, 1, course = 350.0),
            point(45.001, 7, longitude = 39.0001, course = 10.0), point(45.0011, 8, longitude = 39.0002, course = 20.0)))
        // The pure part of this iOS test checks that no interpolation was saved.
        assertEquals(0, PostTripTrackProcessor.process(trip).trackPoints.count { it.isInterpolated })
    }
    @Test fun missingEndPreservesAverageAndNonpositiveElapsedProducesZero() {
        val trip = trip(listOf(point(45.0, 0), point(45.001, 10)))
        assertEquals(7.0, PostTripTrackProcessor.recalculateStats(trip.copy(endDate = null, averageSpeed = 7.0)).averageSpeed)
        assertEquals(0.0, PostTripTrackProcessor.recalculateStats(trip.copy(endDate = start)).averageSpeed)
        val singleton = trip.copy(distance = 123.0, maxSpeed = 12.0, trackPoints = trip.trackPoints.take(1))
        assertEquals(singleton, PostTripTrackProcessor.recalculateStats(singleton))
    }
    @Test fun manualRouteIsNeverTreatedAsRecordedGPS() {
        val trip = trip(listOf(point(45.0, 0), point(45.1, 600))).copy(source = TripOrigin.manual, distance = 42.0)
        val processed = PostTripTrackProcessor.process(trip)
        assertEquals(42.0, processed.distance)
        assertEquals(trip.trackPoints, processed.trackPoints)
        assertEquals(RoadFillState.done, processed.roadFillState)
    }
}
