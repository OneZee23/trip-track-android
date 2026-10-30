package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: TripSegmentMetricsTests.swift
class TripSegmentMetricsTests {
    private val start = Instant.ofEpochSecond(1_760_000_000)
    private fun checkpoint(elapsed: Double, distance: Double, name: String? = null) = TripCheckpoint(
        timestamp = start.addingSeconds(elapsed), latitude = 45.0, longitude = 38.0,
        distanceFromStart = distance, elapsedFromStart = elapsed, name = name)
    private fun text(segment: TripSegment, checkpoints: List<TripCheckpoint>) =
        TripSegmentName.text(segment, checkpoints) { number -> "Отметка $number" }
    @Test fun testResolveComputesDelta() {
        val a = checkpoint(1000.0, 10000.0)
        val b = checkpoint(4600.0, 55000.0)
        val resolved = assertNotNull(TripSegmentMetrics.resolve(TripSegment(fromCheckpointId = a.id, toCheckpointId = b.id), listOf(a, b)))
        assertEquals(3600.0, resolved.elapsed)
        assertEquals(45000.0, resolved.metres)
        assertEquals(a.id, resolved.from.id)
        assertEquals(b.id, resolved.to.id)
    }
    @Test fun testResolveReordersByElapsedRegardlessOfFieldOrder() {
        val earlier = checkpoint(1000.0, 10000.0)
        val later = checkpoint(4600.0, 55000.0)
        val resolved = assertNotNull(TripSegmentMetrics.resolve(TripSegment(fromCheckpointId = later.id, toCheckpointId = earlier.id), listOf(earlier, later)))
        assertEquals(earlier.id, resolved.from.id)
        assertEquals(later.id, resolved.to.id)
        assertEquals(3600.0, resolved.elapsed)
        assertTrue(resolved.elapsed >= 0)
    }
    @Test fun testResolveReturnsNilWhenCheckpointMissing() {
        val a = checkpoint(1000.0, 10000.0)
        assertNull(TripSegmentMetrics.resolve(TripSegment(fromCheckpointId = a.id, toCheckpointId = UUID.randomUUID()), listOf(a)))
    }
    @Test fun testManualNameWins() {
        val a = checkpoint(1000.0, 10000.0, "Джубга")
        val b = checkpoint(4600.0, 55000.0, "Сочи")
        assertEquals("Серпантин", text(TripSegment(fromCheckpointId = a.id, toCheckpointId = b.id, name = "Серпантин"), listOf(a, b)))
    }
    @Test fun testUnnamedSegmentBuildsArrowFromCheckpointNames() {
        val a = checkpoint(1000.0, 10000.0, "Джубга")
        val b = checkpoint(4600.0, 55000.0, "Сочи")
        assertEquals("Джубга → Сочи", text(TripSegment(fromCheckpointId = a.id, toCheckpointId = b.id), listOf(a, b)))
    }
    @Test fun testUnnamedCheckpointGetsDefaultNameByTimeOrder() {
        val first = checkpoint(500.0, 5000.0, "Старт у заправки")
        val second = checkpoint(1000.0, 10000.0)
        val third = checkpoint(4600.0, 55000.0, "Сочи")
        assertEquals("Отметка 2 → Сочи", text(TripSegment(fromCheckpointId = second.id, toCheckpointId = third.id), listOf(first, second, third)))
    }
    @Test fun reorderedSegmentNamesUseTheSameTimelineNumbers() {
        val a = checkpoint(1000.0, 10000.0)
        val b = checkpoint(4600.0, 55000.0)
        assertEquals("Отметка 1 → Отметка 2", text(TripSegment(fromCheckpointId = b.id, toCheckpointId = a.id), listOf(b, a)))
        assertEquals("", text(TripSegment(fromCheckpointId = a.id, toCheckpointId = b.id), emptyList()))
    }
}

// iOS: SegmentHistoryTests.swift
class SegmentHistoryTests {
    private val start = Instant.ofEpochSecond(1_760_000_000)
    private val fromPlace = UUID.randomUUID()
    private val toPlace = UUID.randomUUID()
    private fun pass(place: UUID, tripId: UUID, elapsed: Double) = PlacePass(placeId = place, tripId = tripId,
        timestamp = start.addingSeconds(elapsed), elapsedFromStart = elapsed, distanceFromStart = elapsed * 20, course = 0.0)
    private fun hms(h: Int, m: Int, s: Int = 0) = (h * 3600 + m * 60 + s).toDouble()
    @Test fun testThreeTripsGiveThreeTimesAscending() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val from = listOf(pass(fromPlace, a, 1000.0), pass(fromPlace, b, 2000.0), pass(fromPlace, c, 500.0))
        val to = listOf(pass(toPlace, a, 1000 + hms(5, 12)), pass(toPlace, b, 2000 + hms(4, 58)), pass(toPlace, c, 500 + hms(5, 40)))
        assertEquals(listOf(hms(4, 58), hms(5, 12), hms(5, 40)), SegmentHistory.times(from, to))
    }
    @Test fun testTripWhereToPrecedesFromIsExcluded() {
        val trip = UUID.randomUUID()
        assertEquals(emptyList(), SegmentHistory.times(listOf(pass(fromPlace, trip, 5000.0)), listOf(pass(toPlace, trip, 1000.0))))
    }
    @Test fun testThereAndBackWithinOneTripGivesOneMinimalDelta() {
        val trip = UUID.randomUUID()
        val from = listOf(pass(fromPlace, trip, 1000.0), pass(fromPlace, trip, 2000.0))
        val to = listOf(pass(toPlace, trip, 1000 + hms(0, 5)), pass(toPlace, trip, 2000 - hms(0, 5)))
        assertEquals(listOf(hms(0, 5)), SegmentHistory.times(from, to))
    }
    @Test fun testClockFormatsHoursAndMinutes() {
        assertEquals("4:58", SegmentHistory.clock(17880.0))
        assertEquals("0:42", SegmentHistory.clock(42 * 60.0))
        assertEquals("12:05", SegmentHistory.clock(hms(12, 5)))
    }
    @Test fun testClockDropsIncompleteMinute() {
        assertEquals("0:01", SegmentHistory.clock(90.0))
        assertEquals("0:00", SegmentHistory.clock(59.0))
        assertEquals("0:59", SegmentHistory.clock(hms(1, 0) - 1))
    }
    @Test fun noCrossTripOrZeroDeltaPairs() {
        val trip = UUID.randomUUID()
        val otherTrip = UUID.randomUUID()
        assertTrue(SegmentHistory.times(listOf(pass(fromPlace, trip, 100.0)), listOf(pass(toPlace, otherTrip, 200.0))).isEmpty())
        assertTrue(SegmentHistory.times(listOf(pass(fromPlace, trip, 100.0)), listOf(pass(toPlace, trip, 100.0))).isEmpty())
        assertEquals("0:00", SegmentHistory.clock(-1.0))
    }
}

// iOS: CheckpointDefaultNameTests.swift. Words are fixtures from generated checkpointWord resources.
class CheckpointDefaultNameTests {
    private val words = listOf("Отметка", "Checkpoint", "Markierung", "Marca", "Repère", "Penanda", "Segnalibro",
        "Белгі", "Znacznik", "Marcação", "Marka", "İşaret", "Позначка")
    private fun isDefault(name: String) = CheckpointDefaultName.looksLikeDefaultCheckpointName(name, words)
    @Test fun testRussianDefaultNameMatches() { assertTrue(isDefault("Отметка 7")) }
    @Test fun testEnglishDefaultNameMatches() { assertTrue(isDefault("Checkpoint 12")) }
    @Test fun testAHumanGivenNameDoesNotMatch() { assertFalse(isDefault("Отметка у моря")) }
    @Test fun testEmptyNameCountsAsDefault() { assertTrue(isDefault("")) }
    @Test fun testBareNumberDoesNotMatch() { assertFalse(isDefault("7")) }
    @Test fun testTrailingPunctuationDoesNotMatch() { assertFalse(isDefault("Отметка 7.")) }
    @Test fun allThirteenLanguagesAndEscapedWords() {
        assertEquals(13, words.size)
        for (word in words) assertTrue(isDefault("$word 2"))
        assertTrue(CheckpointDefaultName.looksLikeDefaultCheckpointName("A.(B) 12", listOf("A.(B)")))
        assertFalse(CheckpointDefaultName.looksLikeDefaultCheckpointName("AxB 12", listOf("A.(B)")))
    }
}
