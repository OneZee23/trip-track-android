package com.onezee.triptrack.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.*
import org.junit.Test

// iOS: JourneyEditWindowTests.swift (pure rules extracted from JourneyEditSheet).
class JourneyEditWindowTests {
    private val zone = ZoneId.systemDefault()
    private val now =
        Instant.ofEpochSecond(1_760_000_000)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .plusSeconds(12 * 3_600)

    private fun day(offset: Int, hour: Int = 0) = now.plusSeconds(offset * 86_400L + hour * 3_600L)

    @Test
    fun testFutureWindowClampsToNow() {
        assertEquals(
            JourneyWindow.Window(now, now),
            JourneyWindow.clampedWindow(day(5), day(20), now),
        )
    }

    @Test
    fun testFutureEndAloneClampsAndKeepsPastStart() {
        assertEquals(
            JourneyWindow.Window(day(-6), now),
            JourneyWindow.clampedWindow(day(-6), day(4), now),
        )
    }

    @Test
    fun testInvertedPairIsSwapped() {
        assertEquals(
            JourneyWindow.Window(day(-9), day(-2)),
            JourneyWindow.clampedWindow(day(-2), day(-9), now),
        )
    }

    @Test
    fun testOpenWindowClosesAtToday() {
        assertEquals(
            JourneyWindow.Window(day(-3), now),
            JourneyWindow.clampedWindow(day(-3), null, now),
        )
    }

    @Test
    fun testOpenWindowInFutureCollapsesToToday() {
        assertEquals(JourneyWindow.Window(now, now), JourneyWindow.clampedWindow(day(7), null, now))
    }

    @Test
    fun testPastWindowIsUntouchedToTheSecond() {
        assertEquals(
            JourneyWindow.Window(day(-30, 7), day(-24, 21)),
            JourneyWindow.clampedWindow(day(-30, 7), day(-24, 21), now),
        )
    }

    @Test
    fun testTodaysEveningEndIsKept() {
        val end =
            now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().plusSeconds(86_400 - 1)
        assertEquals(end, JourneyWindow.clampedWindow(day(-2), end, now).end)
    }

    @Test
    fun testRangesAreValidAndContainSelectionForAnyPair() {
        for (s in listOf(-40, -1, 0, 1, 40)) for (e in listOf(-40, -1, 0, 1, 40)) {
            val sr = JourneyWindow.startBounds(day(s), day(e), now)
            val er = JourneyWindow.endBounds(day(s), day(e), now)
            assertTrue(sr.start <= sr.endInclusive)
            assertTrue(er.start <= er.endInclusive)
            assertTrue(day(s) in sr)
            assertTrue(day(e) in er)
        }
    }

    @Test
    fun testRangesDoNotOpenTheFutureBeyondTheCurrentValue() {
        assertEquals(now, JourneyWindow.startBounds(day(-5), day(30), now).endInclusive)
        assertEquals(now, JourneyWindow.endBounds(day(-5), day(-5), now).endInclusive)
    }

    @Test
    fun testStartCeilingIsTheEndDateWhileBothAreInThePast() {
        assertEquals(day(-4), JourneyWindow.startBounds(day(-9), day(-4), now).endInclusive)
    }

    @Test
    fun testDatesUntouchedWhenNothingWasPicked() {
        val w = JourneyWindow.Window(day(-6), day(-2))
        assertFalse(JourneyWindow.datesMoved(w.start, w.end, w))
    }

    @Test
    fun testDatesUntouchedWhenOnlyTheTimeOfDayDiffers() {
        assertFalse(
            JourneyWindow.datesMoved(
                day(-6),
                day(-2),
                JourneyWindow.Window(day(-6, -5), day(-2, 9)),
            )
        )
    }

    @Test
    fun testEachBoundaryIsNoticedSeparately() {
        val w = JourneyWindow.Window(day(-6), day(-2))
        assertTrue(JourneyWindow.datesMoved(day(-7), w.end, w))
        assertTrue(JourneyWindow.datesMoved(w.start, day(-1), w))
    }

    @Test
    fun testSwappedPickersAreNotAMove() {
        val w = JourneyWindow.Window(day(-6), day(-2))
        assertFalse(JourneyWindow.datesMoved(w.end, w.start, w))
    }

    @Test
    fun testClampAtOpeningIsNotAMoveByTheUser() {
        val w = JourneyWindow.clampedWindow(day(5), day(20), now)
        assertFalse(JourneyWindow.datesMoved(w.start, w.end, w))
        assertNotEquals(w.start.atZone(zone).toLocalDate(), day(5).atZone(zone).toLocalDate())
    }

    @Test
    fun testOpenWindowClampIsNotAMoveByTheUser() {
        val w = JourneyWindow.clampedWindow(day(-10), null, now)
        assertFalse(JourneyWindow.datesMoved(w.start, w.end, w))
    }

    // iOS: PublicJourneyAdapterTests.swift
    @Test
    fun testDaysCoversClosedOpenAndInvertedWindows() {
        val utc = ZoneId.of("UTC")
        fun date(day: Int) = LocalDate.of(2026, 9, day).atStartOfDay(utc).toInstant()
        assertEquals(6, JourneyWindow.days(date(12), date(17), date(20), utc))
        assertEquals(3, JourneyWindow.days(date(12), null, date(14), utc))
        assertEquals(1, JourneyWindow.days(date(17), date(12), date(20), utc))
    }
}
