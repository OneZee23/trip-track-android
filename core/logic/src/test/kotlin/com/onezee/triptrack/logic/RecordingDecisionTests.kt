package com.onezee.triptrack.logic

import com.onezee.triptrack.model.RecordingFixContinuity.Rejection
import kotlin.test.*

// iOS: TripManager.swift, shouldStoreShapePoint / handleNewLocation (boundary checks).
class RecordingDecisionTests {
    private fun fix(north: Double, time: Double, speed: Double = 1.0, course: Double = 0.0) =
        TrackTestKit.fix(north = north, after = time, speed = speed, course = course)
    private val last = fix(0.0, 0.0)

    @Test fun shapeRequiresOneSecondOrTwelveDegreesAndOneMetre() {
        assertTrue(RecordingDecision.shouldStoreShapePoint(fix(1.1, 1.0), last, 0.5))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(0.9, 1.0), last, 0.5))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 0.9), last, 0.5))
        // 1/3 s lies between these two representable Instant values.
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 0.333333333, course = 12.0), last, 0.5))
        assertTrue(RecordingDecision.shouldStoreShapePoint(fix(1.1, 0.333333334, course = 12.0), last, 0.5))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 0.333333334, course = 11.999), last, 0.5))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 0.3, course = 90.0), last, 0.5))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 1.0), last, 0.4999))
        assertFalse(RecordingDecision.shouldStoreShapePoint(fix(1.1, 1.0), last, -1.0))
    }
    @Test fun fiveMetresPreservesTheOldRuleBeforeDenseCadenceAndRawSpeedChecks() {
        assertTrue(RecordingDecision.shouldStoreShapePoint(fix(5.1, 0.1), last, -1.0))
    }
    @Test fun distanceAnchorMovesIndependentlyOfShapeAndCoarseShapeDoesNotMoveIt() {
        val candidate = fix(2.0, 1.0)
        val dense = RecordingDecision.decide(candidate, candidate, last, last, false)
        assertTrue(dense.storeShape)
        assertEquals(last, dense.distanceAnchor)
        assertEquals(0.0, dense.distanceGain)
        val coarse = fix(10.0, 2.0).copy(horizontalAccuracy = 120.0)
        val shapeOnly = RecordingDecision.decide(coarse, coarse, last, last, false)
        assertTrue(shapeOnly.storeShape)
        assertEquals(last, shapeOnly.distanceAnchor)
        assertEquals(0.0, shapeOnly.distanceGain)
        assertFalse(RecordingDecision.decide(coarse.copy(speed = 0.0), coarse, last, last, false).storeShape)
    }
    @Test fun driftRejectsBothAnchorsButGapReacquisitionBypassesDrift() {
        val candidate = fix(20.0, 1.0, speed = 0.0)
        val drift = RecordingDecision.decide(candidate, candidate, last, last, false)
        assertTrue(drift.rejectedAsDrift)
        assertFalse(drift.storeShape)
        assertEquals(last, drift.distanceAnchor)
        val reacquired = RecordingDecision.decide(candidate, candidate, last, last, true)
        assertTrue(reacquired.storeShape)
        assertEquals(candidate, reacquired.distanceAnchor)
        assertTrue(reacquired.distanceGain > 19)
    }
    @Test fun rejectedPlausibilityMovesDistanceAnchorAndSpeedFallbackNeverInventsARecord() {
        val candidate = fix(5000.0, 1.0, speed = 1000.0)
        val rejected = RecordingDecision.decide(candidate.copy(speed = -1.0), candidate, last, last, false)
        assertEquals(candidate, rejected.distanceAnchor)
        assertEquals(0.0, rejected.distanceGain)
        assertEquals(0.0, rejected.recordingSpeed)
        val longGap = fix(5000.0, 120.0, speed = -1.0)
        val fallback = RecordingDecision.decide(longGap, longGap, last, last, true)
        assertNear(5000 / 120.0, fallback.recordingSpeed, 0.1)
    }
    @Test fun continuityRejectsInvalidCoordinatesAccuracyAndSpeedWithoutPoisoningAnchor() {
        val gate = RecordingFixContinuity()
        assertNull(gate.rejection(last))
        for (invalid in listOf(last.copy(latitude = Double.NaN), last.copy(longitude = 181.0),
            last.copy(horizontalAccuracy = -1.0), last.copy(horizontalAccuracy = 201.0),
            last.copy(horizontalAccuracy = Double.POSITIVE_INFINITY), last.copy(speed = Double.NaN))) {
            assertEquals(Rejection.invalid, gate.rejection(invalid))
        }
        assertEquals(Rejection.speed, gate.rejection(fix(10.0, 1.0, speed = 83.31)))
        assertNull(gate.rejection(fix(10.0, 1.0)))
        gate.reset()
        assertNull(gate.rejection(last))
    }
    @Test fun locationFixKeepsEveryUnknownProviderField() {
        val fix = LocationFix(45.0, 39.0)
        assertEquals(-1.0, fix.horizontalAccuracy)
        assertEquals(-1.0, fix.verticalAccuracy)
        assertEquals(-1.0, fix.speed)
        assertEquals(-1.0, fix.speedAccuracy)
        assertEquals(-1.0, fix.course)
        assertEquals(-1.0, fix.courseAccuracy)
        val measured = fix.copy(speed = 14.4, speedAccuracy = 0.3, course = 120.0, courseAccuracy = 5.0)
        assertEquals(14.4, measured.speed)
        assertEquals(0.3, measured.speedAccuracy)
        assertEquals(120.0, measured.course)
        assertEquals(5.0, measured.courseAccuracy)
    }
}
