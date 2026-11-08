// iOS: RecordingFinishPreviewTests.swift (unchanged boundary inputs and expected reasons).
package com.onezee.triptrack.location

import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import org.junit.Test

class RecordingFinishPreviewTests {
    private val start = Instant.ofEpochSecond(1_760_000_000)
    private fun reason(distance: Double, duration: Double, speed: Double) = TripJunkClassifier.reason(distance, duration, speed)
    private fun preview(id: UUID, distance: Double, duration: Double, speed: Double) =
        RecordingFinishPreview(id, start.plusNanos((duration * 1e9).toLong()), distance, duration, speed)

    @Test fun testMissingSpeedDoesNotTrimTheRecordedDrive() {
        val start = Instant.parse("2026-11-07T14:14:40Z")
        val lastDriveFix = Instant.parse("2026-11-07T15:18:55Z")
        val points = listOf(
            TrackPoint(latitude = 55.000000, longitude = 37.000000, speed = -1.0, timestamp = start),
            TrackPoint(latitude = 55.000100, longitude = 37.000000, speed = -1.0, timestamp = start.plusSeconds(10)),
            TrackPoint(latitude = 55.076000, longitude = 37.000000, speed = -1.0, timestamp = lastDriveFix),
            TrackPoint(latitude = 55.076000, longitude = 37.000000, speed = 0.0, timestamp = lastDriveFix.plusSeconds(70)),
        )
        assertEquals(lastDriveFix, com.onezee.triptrack.location.recording.RecordingEnd.trimmedEndDate(points))
    }

    @Test fun testJitterAtAStopWithAKnownSpeedStillTrimsTheTail() {
        val start = Instant.parse("2026-11-07T15:00:00Z")
        val points = listOf(
            TrackPoint(latitude = 45.0, longitude = 38.0, speed = 10.0, timestamp = start),
            TrackPoint(latitude = 45.001, longitude = 38.0, speed = 10.0, timestamp = start.plusSeconds(11)),
            // Parked: Doppler says 0 while the position jumps 8 m in a second.
            TrackPoint(latitude = 45.00107, longitude = 38.0, speed = 0.0, timestamp = start.plusSeconds(40)),
            TrackPoint(latitude = 45.001, longitude = 38.0, speed = 0.0, timestamp = start.plusSeconds(41)),
            TrackPoint(latitude = 45.00107, longitude = 38.0, speed = 0.0, timestamp = start.plusSeconds(100)),
        )
        assertEquals(start.plusSeconds(11), com.onezee.triptrack.location.recording.RecordingEnd.trimmedEndDate(points))
    }

    @Test fun testShortTripRequiresBothDistanceAndTimeBelowTheExistingThresholds() {
        assertEquals(TripJunkClassifier.Reason.tooShort, reason(499.999, 119.999, 10.0))
        assertNull(reason(500.0, 119.999, 10.0)); assertNull(reason(499.999, 120.0, 10.0)); assertNull(reason(500.0, 120.0, 10.0))
    }
    @Test fun testSlowTripUsesStrictSpeedAndDurationBoundaries() {
        assertNull(reason(1000.0, 180.0, 0.0))
        assertEquals(TripJunkClassifier.Reason.noDrivingSpeed, reason(1000.0, 180.001, 14.999 / 3.6))
        assertNull(reason(1000.0, 180.001, 15 / 3.6)); assertNull(reason(1000.0, 300.0, 20 / 3.6))
    }
    @Test fun testEveryReasonMatchesTheExistingFinalTripRoute() {
        val samples = listOf(Triple(0.0, 2.0, 0.0), Triple(499.0, 119.0, 10.0), Triple(500.0, 119.0, 10.0), Triple(499.0, 120.0, 10.0),
            Triple(0.0, 180.0, 0.0), Triple(1000.0, 181.0, 0.0), Triple(1000.0, 181.0, 15 / 3.6), Triple(10_000.0, 900.0, 25.0))
        samples.forEach { (distance, duration, speed) ->
            val trip = Trip(startDate = start, endDate = start.plusSeconds(duration.toLong()), distance = distance, maxSpeed = speed)
            assertEquals(trip.isJunk(), preview(trip.id, distance, duration, speed).discardReason != null)
        }
    }
    @Test fun testCrossingATimeBoundaryRequiresANewConfirmation() {
        val id = UUID.randomUUID()
        val short = preview(id, 100.0, 119.0, 0.0); val save = preview(id, 100.0, 120.0, 0.0); val slow = preview(id, 100.0, 181.0, 0.0)
        assertFalse(short.confirmsSameOutcome(save)); assertFalse(save.confirmsSameOutcome(slow)); assertFalse(short.confirmsSameOutcome(slow))
    }
    @Test fun testOrdinaryProgressDoesNotRequireRepeatedConfirmation() {
        val id = UUID.randomUUID()
        assertTrue(preview(id, 1000.0, 200.0, 10.0).confirmsSameOutcome(preview(id, 1100.0, 205.0, 12.0)))
        assertTrue(preview(id, 0.0, 2.0, 0.0).confirmsSameOutcome(preview(id, 0.0, 5.0, 0.0)))
        assertFalse(preview(UUID.randomUUID(), 0.0, 2.0, 0.0).confirmsSameOutcome(preview(UUID.randomUUID(), 0.0, 2.0, 0.0)))
    }
}
