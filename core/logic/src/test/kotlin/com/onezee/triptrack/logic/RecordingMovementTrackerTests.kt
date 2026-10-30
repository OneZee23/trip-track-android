package com.onezee.triptrack.logic

import com.onezee.triptrack.model.AutoTripPolicy
import kotlin.test.*

// iOS: RecordingMovementTrackerTests.swift
class RecordingMovementTrackerTests {
    private val start = TrackTestKit.epoch
    @Test fun testFirstSampleSeedsBaselineAndReturnsFalse() {
        val tracker = RecordingMovementTracker()
        assertFalse(tracker.record(0.0, start))
        assertEquals(0.0, tracker.lastDistanceMeters)
        assertEquals(start, tracker.lastChangeTime)
    }
    @Test fun testGrowthBelowThresholdDoesNotReset() {
        val tracker = RecordingMovementTracker()
        tracker.record(0.0, start)
        assertFalse(tracker.record(50.0, start.plusSeconds(60)))
        assertEquals(0.0, tracker.lastDistanceMeters)
        assertEquals(start, tracker.lastChangeTime)
    }
    @Test fun testGrowthAtOrAboveThresholdResetsBaseline() {
        val tracker = RecordingMovementTracker()
        tracker.record(0.0, start)
        val next = start.plusSeconds(30)
        assertTrue(tracker.record(AutoTripPolicy.meaningfulMovementDistance, next))
        assertEquals(AutoTripPolicy.meaningfulMovementDistance, tracker.lastDistanceMeters)
        assertEquals(next, tracker.lastChangeTime)
    }
    @Test fun testIsStaleReturnsFalseBeforeAnyRecord() { assertFalse(RecordingMovementTracker().isStale(60.0, start)) }
    @Test fun testIsStaleFalseInsideWindow() {
        val tracker = RecordingMovementTracker()
        tracker.record(0.0, start)
        assertFalse(tracker.isStale(600.0, start.plusSeconds(599)))
    }
    @Test fun testIsStaleTrueOutsideWindow() {
        val tracker = RecordingMovementTracker()
        tracker.record(0.0, start)
        assertTrue(tracker.isStale(600.0, start.plusSeconds(601)))
    }
    @Test fun testRecordingMovementDuringStaleWindowResetsStaleness() {
        val tracker = RecordingMovementTracker()
        tracker.record(0.0, start)
        val next = start.plusSeconds(570)
        assertFalse(tracker.isStale(600.0, next))
        tracker.record(AutoTripPolicy.meaningfulMovementDistance, next)
        assertFalse(tracker.isStale(600.0, next.plusSeconds(570)))
    }
    @Test fun testExtendWindowBumpsClockOnly() {
        val tracker = RecordingMovementTracker()
        tracker.record(250.0, start)
        tracker.extendWindow(start.plusSeconds(900))
        assertEquals(start.plusSeconds(900), tracker.lastChangeTime)
        assertEquals(250.0, tracker.lastDistanceMeters)
    }
    @Test fun testResetClearsBaselineAndTime() {
        val tracker = RecordingMovementTracker()
        tracker.record(500.0, start)
        tracker.reset()
        assertNull(tracker.lastDistanceMeters)
        assertNull(tracker.lastChangeTime)
        assertFalse(tracker.isStale(60.0, start))
    }
    @Test fun testRecordAfterResetSeedsFreshBaseline() {
        val tracker = RecordingMovementTracker()
        tracker.record(5000.0, start)
        tracker.reset()
        val next = start.plusSeconds(120)
        assertFalse(tracker.record(5000.0, next))
        assertEquals(next, tracker.lastChangeTime)
    }
    @Test fun shrinkingDistanceSeedsANewTripAndEqualityIsNotStale() {
        val tracker = RecordingMovementTracker()
        tracker.record(45_000.0, start)
        val next = start.plusSeconds(3600)
        assertFalse(tracker.record(0.0, next))
        assertEquals(0.0, tracker.lastDistanceMeters)
        assertFalse(tracker.isStale(600.0, next.plusSeconds(600)))
        assertTrue(tracker.record(100.0, next.plusSeconds(601)))
    }
}
