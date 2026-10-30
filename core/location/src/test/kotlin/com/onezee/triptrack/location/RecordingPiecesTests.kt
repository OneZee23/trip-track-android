package com.onezee.triptrack.location

import com.onezee.triptrack.data.RecordingOrphan
import com.onezee.triptrack.location.recording.LiveSpeed
import com.onezee.triptrack.location.recording.OrphanTriage
import com.onezee.triptrack.location.recording.RecordingEnd
import com.onezee.triptrack.logic.TrackTestKit
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

// iOS: RecoveryWindowTests.swift, TripManager.trimmedEndDate, MapViewModel's speedometer.
class RecordingPiecesTests {
    private val epoch = TrackTestKit.epoch

    private fun orphan(start: Double, last: Double, distance: Double = 5_000.0, maxSpeed: Double = 20.0, points: Int = 100) =
        RecordingOrphan(
            id = UUID.randomUUID(), startDate = TrackTestKit.at(start), distance = distance, maxSpeed = maxSpeed,
            averageSpeed = 10.0, vehicleId = null, isTransfer = false, confirmation = TripConfirmation.confirmed,
            recordingBreaks = emptyList(), pointCount = points, lastPointAt = TrackTestKit.at(last),
        )

    private fun isFresh(secondsAgo: Double): Boolean {
        val verdict = OrphanTriage.triage(listOf(orphan(0.0, 600.0)), TrackTestKit.at(600.0 + secondsAgo))
        return verdict.recoverable?.isFresh == true
    }

    // iOS: testCrashSecondsAgoResumesWithoutAsking / testCrashMinutesAgoStillResumesWithoutAsking.
    @Test
    fun aCrashWithinFifteenMinutesResumesWithoutAsking() {
        listOf(5.0, 60.0, 5 * 60.0, 14 * 60.0 + 59).forEach { assertTrue(isFresh(it), "$it s") }
    }

    @Test
    fun olderThanTheWindowAsks() {
        listOf(15 * 60.0 + 1, 3_600.0, 5 * 3_600.0).forEach { assertFalse(isFresh(it), "$it s") }
    }

    @Test
    fun theWindowStaysInsideTheRestorableAge() {
        assertTrue(OrphanTriage.silentResumeWindowSeconds in 5 * 60.0..30 * 60.0)
        assertTrue(OrphanTriage.silentResumeWindowSeconds < OrphanTriage.maxRestorableAgeSeconds)
    }

    @Test
    fun trimmedTailNeedsAMinuteOfStillness() {
        val moving = (0..100).map { TrackTestKit.PointSpec(north = it * 10.0, seconds = it.toDouble(), speed = 10.0) }
        val longStop = (101..170).map { TrackTestKit.PointSpec(north = 1_000.0, seconds = it.toDouble(), speed = 0.5) }
        val shortStop = (101..150).map { TrackTestKit.PointSpec(north = 1_000.0, seconds = it.toDouble(), speed = 0.5) }
        assertEquals(TrackTestKit.at(100.0), RecordingEnd.trimmedEndDate(TrackTestKit.trip(moving + longStop).trackPoints))
        assertNull(RecordingEnd.trimmedEndDate(TrackTestKit.trip(moving + shortStop).trackPoints))
    }

    @Test
    fun theEndFallsBackToTheHintThenNowAndNeverPrecedesTheStart() {
        val moving = TrackTestKit.trip((0..10).map { TrackTestKit.PointSpec(north = it * 10.0, seconds = it.toDouble()) }).trackPoints
        val start = TrackTestKit.at(0.0)
        assertEquals(TrackTestKit.at(50.0), RecordingEnd.endDate(moving, start, TrackTestKit.at(50.0), TrackTestKit.at(90.0)))
        assertEquals(TrackTestKit.at(90.0), RecordingEnd.endDate(moving, start, null, TrackTestKit.at(90.0)))
        assertEquals(start, RecordingEnd.endDate(moving, start, TrackTestKit.at(-60.0), TrackTestKit.at(90.0)))
    }

    @Test
    fun finalStatsKeepTheLiveNumbersForASinglePoint() {
        val one = TrackTestKit.trip(listOf(TrackTestKit.PointSpec(north = 0.0, seconds = 0.0))).trackPoints
        assertEquals(RecordingEnd.Stats(42.0, 7.0), RecordingEnd.finalStats(one, emptyList(), 42.0, 7.0))
        assertNull(RecordingEnd.previewPolyline(one))
    }

    @Test
    fun theSpeedometerSnapsToZeroOnlyOnTheSecondStill() {
        val speed = LiveSpeed()
        var t: Instant = epoch
        speed.reset(t)
        repeat(10) {
            t = t.plusSeconds(1)
            speed.onFix(20.0, t)
        }
        assertTrue(speed.speed > 19)
        t = t.plusSeconds(1)
        speed.onFix(-1.0, t)
        assertTrue(speed.speed > 10, "one unknown fix is smoothed, not snapped")
        t = t.plusSeconds(1)
        speed.onFix(0.0, t)
        assertEquals(0.0, speed.speed)
    }

    @Test
    fun theSpeedometerJumpsAfterAGapAndFallsWithoutFixes() {
        val speed = LiveSpeed()
        speed.reset(epoch)
        speed.onFix(25.0, epoch.plusSeconds(10))
        assertEquals(25.0, speed.speed, "after a gap the real value, not an average")
        speed.tick(epoch.plusSeconds(11))
        assertEquals(25.0, speed.speed)
        speed.tick(epoch.plusSeconds(13))
        assertEquals(10.0, speed.speed, 1e-9)
        repeat(10) { speed.tick(epoch.plusSeconds(14L + it)) }
        assertEquals(0.0, speed.speed)
        assertTrue(speed.isStale(epoch.plusSeconds(21)))
    }
}
