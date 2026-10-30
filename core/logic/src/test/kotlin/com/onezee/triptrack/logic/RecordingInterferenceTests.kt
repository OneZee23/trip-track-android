package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.RecordingFixContinuity.Rejection
import kotlin.test.*

// iOS: RecordingInterferenceTests.swift (persistence assertions use snapshots).
class RecordingInterferenceTests {
    private fun fix(north: Double, at: Double, speed: Double = 15.0, accuracy: Double = 8.0, course: Double = 0.0) =
        TrackTestKit.fix(north = north, after = at, speed = speed, accuracy = accuracy, course = course)
    private fun pointLocation(point: TrackPoint) = LocationFix(point.latitude, point.longitude)
    private fun manager() = RecordingMath(Trip(startDate = TrackTestKit.epoch))

    @Test fun testTeleportNeverReachesShapeAndNextGoodFixRecovers() {
        val manager = manager()
        val drawn = mutableListOf<TrackPoint>()
        fun accept(fix: LocationFix) { manager.handleNewLocation(fix)?.let(drawn::add) }
        for (t in 0..30) accept(fix(t * 15.0, t.toDouble()))
        accept(fix(1050.0, 31.0, speed = 0.0, accuracy = 42.0))
        accept(fix(480.0, 32.0))
        val saved = manager.trip.trackPoints
        assertFalse(saved.any { it.timestamp == TrackTestKit.epoch.plusSeconds(31) })
        assertEquals(TrackTestKit.epoch.plusSeconds(32), saved.last().timestamp)
        assertTrue(pointLocation(saved.last()).distanceFrom(fix(480.0, 32.0)) < 15)
        assertTrue(manager.trip.maxSpeed <= 20)
        assertEquals(saved.size, drawn.size)
        assertFalse(drawn.any { it.timestamp == TrackTestKit.epoch.plusSeconds(31) })
    }
    @Test fun testCoarseTeleportCannotPoisonTheFollowingGoodMeasurement() {
        val gate = RecordingFixContinuity()
        assertNull(gate.rejection(fix(0.0, 0.0, accuracy = 120.0)))
        assertEquals(Rejection.displacement, gate.rejection(fix(240.0, 1.0, speed = 0.0, accuracy = 120.0)))
        assertNull(gate.rejection(fix(30.0, 2.0)))
    }
    @Test fun testDuplicateAndReorderedTimestampsDoNotMoveTheRawAnchor() {
        val gate = RecordingFixContinuity()
        assertNull(gate.rejection(fix(0.0, 10.0)))
        assertEquals(Rejection.timestamp, gate.rejection(fix(1000.0, 10.0)))
        assertEquals(Rejection.timestamp, gate.rejection(fix(1000.0, 9.0)))
        assertNull(gate.rejection(fix(15.0, 11.0)))
    }
    @Test fun testSparseRealDriveStillCountsAcrossTwoMinuteSignalGap() {
        val manager = manager()
        manager.handleNewLocation(fix(0.0, 0.0, speed = 42.0))
        manager.handleNewLocation(fix(5000.0, 120.0, speed = 42.0, course = -1.0))
        assertEquals(2, manager.trip.trackPoints.size)
        assertNear(5000.0, manager.trip.distance, 30.0)
        assertNear(42.0, manager.trip.maxSpeed, 0.01)
    }
    @Test fun testDenseGenuineHighwayMeasurementsAreNotThrottled() {
        val gate = RecordingFixContinuity()
        for (index in 0..100) assertNull(gate.rejection(fix(index * 4.0, index / 10.0, speed = 40.0)))
    }
    @Test fun testSparseUnknownSpeedDoesNotBecomeAStationaryFix() {
        val manager = manager()
        for (index in 0..2) manager.handleNewLocation(fix(index * 5000.0, index * 120.0, speed = -1.0, course = -1.0))
        assertEquals(3, manager.trip.trackPoints.size)
        assertNear(10_000.0, manager.trip.distance, 60.0)
        val preview = PostTripTrackProcessor.recalculateStats(manager.trip.copy(endDate = TrackTestKit.epoch.plusSeconds(240)))
        assertTrue(preview.maxSpeed > 40)
        assertFalse(preview.isJunk())
    }
    @Test fun testLongGapEndingStoppedKeepsDistanceAndDoesNotDiscardTheDrive() {
        val manager = manager()
        manager.handleNewLocation(fix(0.0, 0.0, speed = 0.0))
        manager.handleNewLocation(fix(5000.0, 240.0, speed = 0.0, course = -1.0))
        assertEquals(2, manager.trip.trackPoints.size)
        assertEquals(0.0, manager.trip.trackPoints.last().speed)
        val preview = PostTripTrackProcessor.recalculateStats(manager.trip.copy(endDate = TrackTestKit.epoch.plusSeconds(240)))
        assertNear(5000.0, preview.distance, 30.0)
        assertTrue(preview.maxSpeed > 20)
        assertFalse(preview.isJunk())
    }
    @Test fun testCorrelatedFalseFixBurstDoesNotDrawASpiral() {
        val manager = manager()
        for (t in 0..30) manager.handleNewLocation(fix(t * 10.0, t.toDouble(), speed = 10.0))
        for (index in 0..<53) manager.handleNewLocation(fix(530.0, 31 + index * 0.004, -1.0, 32.0, -1.0))
        manager.handleNewLocation(fix(320.0, 32.0, speed = 10.0))
        assertEquals(32, manager.trip.trackPoints.size)
        assertTrue(manager.trip.distance < 340)
        assertTrue(manager.trip.maxSpeed < 15)
    }
    @Test fun interruptedSparseDriveRetainsSpeedEvidenceAtThePauseBoundary() {
        // iOS: testInterruptedSparseDriveEndingStoppedSurvivesOrphanRecovery.
        // Durability/recovery belong to the repository; this checks its input.
        val pause = java.time.Instant.now().minusSeconds(5)
        val start = pause.minusSeconds(241)
        val manager = RecordingMath(Trip(startDate = start))
        manager.handleNewLocation(fix(0.0, 0.0, speed = 0.0, course = -1.0).copy(timestamp = start))
        manager.handleNewLocation(fix(5000.0, 240.0, speed = 0.0, course = -1.0).copy(timestamp = start.plusSeconds(240)))
        manager.setPaused(true, pause)
        assertEquals(listOf(pause), manager.trip.recordingBreaks)
        assertTrue(manager.trip.maxSpeed > 20)
    }
    @Test fun testResumeDoesNotInheritPrePauseVelocity() {
        val manager = manager()
        for (t in 0..30) manager.handleNewLocation(fix(t * 30.0, t.toDouble(), speed = 30.0))
        manager.setPaused(true, TrackTestKit.epoch.plusSeconds(40))
        manager.handleNewLocation(fix(10_000.0, 300.0))
        manager.setPaused(false, TrackTestKit.epoch.plusSeconds(600))
        manager.handleNewLocation(fix(910.0, 600.0, speed = 0.0, course = -1.0))
        val last = manager.trip.trackPoints.last()
        assertEquals(0.0, last.speed)
        assertTrue(pointLocation(last).distanceFrom(fix(910.0, 600.0)) < 0.1)
    }
    @Test fun testPausedMovementStaysExcludedAfterFinalRecalculationAndReload() {
        val manager = manager()
        val boundary = TrackTestKit.epoch.plusSeconds(40)
        for (t in 0..30) manager.handleNewLocation(fix(t * 15.0, t.toDouble()))
        manager.setPaused(true, boundary)
        manager.setPaused(true, boundary)
        manager.handleNewLocation(fix(5000.0, 300.0))
        manager.setPaused(false, TrackTestKit.epoch.plusSeconds(600))
        manager.handleNewLocation(fix(10_000.0, 600.0))
        manager.handleNewLocation(fix(10_015.0, 601.0))
        manager.handleNewLocation(fix(10_030.0, 602.0))
        val preview = PostTripTrackProcessor.recalculateStats(manager.trip)
        assertNear(480.0, preview.distance, 10.0)
        assertEquals(listOf(boundary), manager.trip.recordingBreaks)
        val restored = preview.copy()
        assertEquals(listOf(boundary), restored.recordingBreaks)
        assertEquals(setOf(0, 1), restored.measuredPoints.map { it.recordingSegmentIndex }.toSet())
    }
    @Test fun testStopPreviewDiscardsImpossibleRecordInsteadOfClampingIt() {
        val manager = manager()
        for (t in 0..3) manager.handleNewLocation(fix(t * 15.0, t.toDouble()))
        val saved = manager.trip.trackPoints
        val corrupt = manager.trip.copy(trackPoints = saved.dropLast(1) + saved.last().copy(speed = 1000.0))
        assertNear(15.0, PostTripTrackProcessor.recalculateStats(corrupt).maxSpeed, 0.5)
    }
    @Test fun testPostProcessingCannotRestoreImpossibleSpeedRecord() {
        val trip = TrackTestKit.trip(listOf(
            TrackTestKit.PointSpec(north = 0.0, seconds = 0.0, speed = 15.0),
            TrackTestKit.PointSpec(north = 150.0, seconds = 10.0, speed = 1000.0),
            TrackTestKit.PointSpec(north = 300.0, seconds = 20.0, speed = 20.0)))
        val processed = PostTripTrackProcessor.process(trip)
        assertEquals(20.0, processed.maxSpeed)
        assertNear(300.0, processed.distance, 3.0)
    }
    @Test fun testSpeedRecordRejectsNonFiniteAndUnknownValues() {
        for (speed in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 1000.0)) {
            assertFalse(TripDistanceGate.isPlausibleSpeed(speed))
        }
        assertTrue(TripDistanceGate.isPlausibleSpeed(0.0))
        assertTrue(TripDistanceGate.isPlausibleSpeed(80.0))
    }
}
