package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: PostTripFillTests.swift (geometry and statistics).
class PostTripFillTests {
    private fun tunnelTrip(): Trip = TrackTestKit.trip(((0..20) + (80..100)).map { t ->
        TrackTestKit.PointSpec(north = t * 10.0, seconds = t.toDouble()) })

    @Test fun testGapGetsAStraightFillInTimeOrder() {
        val processed = PostTripTrackProcessor.process(tunnelTrip())
        val all = processed.trackPoints
        val fills = all.filter { it.isInterpolated }
        assertEquals(29, fills.size)
        assertEquals(all.map { it.timestamp }.sorted(), all.map { it.timestamp })
        assertTrue(fills.all { it.speed == -1.0 && it.horizontalAccuracy == -1.0 })
        assertEquals(RoadFillState.pending, processed.roadFillState)
        assertNear(1000.0, processed.distance, 2.0)
    }
    @Test fun testTripWithoutGapsIsDone() {
        val trip = TrackTestKit.trip((0..50).map { TrackTestKit.PointSpec(north = it * 10.0, seconds = it.toDouble()) })
        val processed = PostTripTrackProcessor.process(trip)
        assertEquals(RoadFillState.done, processed.roadFillState)
        assertFalse(processed.trackPoints.any { it.isInterpolated })
    }
    @Test fun testPauseStaysEmptyAndDoesNotReturnToDistanceDuringPostProcessing() {
        val trip = tunnelTrip().copy(distance = 400.0, recordingBreaks = listOf(TrackTestKit.epoch.plusSeconds(80)))
        val processed = PostTripTrackProcessor.process(trip)
        assertNear(400.0, processed.distance, 1.0)
        assertEquals(RoadFillState.done, processed.roadFillState)
        assertFalse(processed.trackPoints.any { it.isInterpolated })
    }
    @Test fun testSpikeRemovalDoesNotCompareGeometryAcrossPause() {
        val trip = TrackTestKit.trip(listOf(
            TrackTestKit.PointSpec(north = 0.0, seconds = 0.0),
            TrackTestKit.PointSpec(east = 200.0, north = 0.0, seconds = 20.0),
            TrackTestKit.PointSpec(north = 10.0, seconds = 120.0),
            TrackTestKit.PointSpec(north = 20.0, seconds = 121.0),
        )).copy(recordingBreaks = listOf(TrackTestKit.epoch.plusSeconds(120)))
        val ids = trip.trackPoints.map { it.id }.toSet()
        val processed = PostTripTrackProcessor.process(trip)
        assertEquals(ids, processed.trackPoints.filter { !it.isInterpolated }.map { it.id }.toSet())
        assertNear(210.0, processed.distance, 1.0)
    }
    @Test fun testTeleportGapStaysUnfilled() {
        val trip = TrackTestKit.trip(listOf(
            TrackTestKit.PointSpec(north = 0.0, seconds = 0.0), TrackTestKit.PointSpec(north = 10.0, seconds = 1.0),
            TrackTestKit.PointSpec(north = 5010.0, seconds = 12.0), TrackTestKit.PointSpec(north = 5020.0, seconds = 13.0)))
        assertFalse(PostTripTrackProcessor.process(trip).trackPoints.any { it.isInterpolated })
    }
    @Test fun testRemovedSpikeDoesNotComeBackWithTheFill() {
        val trip = TrackTestKit.trip(listOf(
            TrackTestKit.PointSpec(north = 0.0, seconds = 0.0), TrackTestKit.PointSpec(north = 10.0, seconds = 1.0),
            TrackTestKit.PointSpec(east = 900.0, north = 15.0, seconds = 2.0), TrackTestKit.PointSpec(north = 20.0, seconds = 3.0),
            TrackTestKit.PointSpec(north = 500.0, seconds = 60.0), TrackTestKit.PointSpec(north = 510.0, seconds = 61.0)))
        val processed = PostTripTrackProcessor.process(trip)
        assertEquals(5, processed.trackPoints.count { !it.isInterpolated })
        assertTrue(processed.trackPoints.any { it.isInterpolated })
        assertFalse(processed.trackPoints.any { it.id == trip.trackPoints[2].id })
    }
}
