package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.test.*

// iOS: TripConfirmationSchemaTests.swift, DraftIsolationTests.swift, RecordingFinishPreviewTests.swift.
class TripConfirmationTests {
    @Test fun testNewTripDefaultsToConfirmedAndUnchecked() {
        val trip = Trip()
        assertEquals(TripConfirmation.confirmed, trip.confirmation)
        assertEquals(RoadFillState.unchecked, trip.roadFillState)
    }
    @Test fun testStartTripCanCreateADraft() {
        val manager = RecordingMath(Trip(confirmation = TripConfirmation.draft))
        assertTrue(manager.trip.isDraft)
    }
    @Test fun testDraftSurvivesTheRebuildOnEveryFix() {
        val manager = RecordingMath(Trip(confirmation = TripConfirmation.draft))
        manager.handleNewLocation(TrackTestKit.fix(north = 0.0, speed = 10.0, after = 0.0))
        manager.handleNewLocation(TrackTestKit.fix(north = 10.0, speed = 10.0, after = 1.0))
        assertTrue(manager.trip.isDraft)
    }
    @Test fun draftStaysOutsideTheWorldAndLegacyValuesAreAllowed() {
        assertFalse(TripConfirmation.permitsWorldEntry("draft"))
        for (value in listOf(null, "confirmed", "somethingFromTheFuture")) assertTrue(TripConfirmation.permitsWorldEntry(value))
    }
    @Test fun testShortTripRequiresBothDistanceAndTimeBelowTheExistingThresholds() {
        assertEquals(TripJunkClassifier.Reason.tooShort, TripJunkClassifier.reason(499.999, 119.999, 10.0))
        assertNull(TripJunkClassifier.reason(500.0, 119.999, 10.0))
        assertNull(TripJunkClassifier.reason(499.999, 120.0, 10.0))
        assertNull(TripJunkClassifier.reason(500.0, 120.0, 10.0))
    }
    @Test fun testSlowTripUsesStrictSpeedAndDurationBoundaries() {
        assertNull(TripJunkClassifier.reason(1000.0, 180.0, 0.0))
        assertEquals(TripJunkClassifier.Reason.noDrivingSpeed, TripJunkClassifier.reason(1000.0, 180.001, 14.999 / 3.6))
        assertNull(TripJunkClassifier.reason(1000.0, 180.001, 15 / 3.6))
        assertNull(TripJunkClassifier.reason(1000.0, 300.0, 20 / 3.6))
    }
    @Test fun testEveryReasonMatchesTheExistingFinalTripRoute() {
        val start = java.time.Instant.ofEpochSecond(1_760_000_000)
        val samples = listOf(Triple(0.0, 2.0, 0.0), Triple(499.0, 119.0, 10.0), Triple(500.0, 119.0, 10.0),
            Triple(499.0, 120.0, 10.0), Triple(0.0, 180.0, 0.0), Triple(1000.0, 181.0, 0.0),
            Triple(1000.0, 181.0, 15 / 3.6), Triple(10_000.0, 900.0, 25.0))
        for ((distance, duration, speed) in samples) {
            val trip = Trip(startDate = start, endDate = start.addingSeconds(duration), distance = distance, maxSpeed = speed)
            assertEquals(TripJunkClassifier.reason(distance, duration, speed) != null, trip.isJunk())
        }
    }
}
