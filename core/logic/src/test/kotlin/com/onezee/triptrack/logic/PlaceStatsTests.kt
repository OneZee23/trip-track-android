package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: PlaceStatsTests.swift (the SwiftUI source guard belongs to the future place screen).
class PlaceStatsTests {
    private val now = Instant.ofEpochSecond(1_760_000_000)
    private val placeId = UUID.randomUUID()
    private fun pass(daysAgo: Double, course: Double, elapsed: Double) = PlacePass(placeId = placeId, tripId = UUID.randomUUID(),
        timestamp = now.addingSeconds(-daysAgo * 86400), elapsedFromStart = elapsed, distanceFromStart = 143_000.0, course = course)
    private fun hms(h: Int, m: Int, s: Int = 0) = (h * 3600 + m * 60 + s).toDouble()

    @Test fun testDirectionsSplitAtFortyFiveDegrees() {
        val stats = PlaceStats.build(listOf(pass(1.0, 0.0, hms(2, 14)), pass(8.0, 5.0, hms(2, 8)),
            pass(15.0, 350.0, hms(2, 31)), pass(2.0, 180.0, hms(1, 58)), pass(9.0, 175.0, hms(1, 49))), now)
        assertEquals(5, stats.passCount)
        assertEquals(2, stats.directions.size)
        val north = stats.directions[0]
        val south = stats.directions[1]
        assertEquals(3, north.count)
        assertEquals(hms(2, 14), north.median)
        assertEquals(hms(2, 8), north.best)
        assertEquals(hms(2, 31), north.worst)
        assertEquals(2, south.count)
        assertEquals(hms(1, 53, 30), south.median)
        assertNear(0.0, PlaceStats.angularDistance(north.course, 0.0), 4.0)
        assertNear(0.0, PlaceStats.angularDistance(south.course, 180.0), 4.0)
    }

    @Test fun testUnknownCourseCountsButHasNoDirection() {
        val stats = PlaceStats.build(listOf(pass(1.0, -1.0, hms(2, 0)), pass(2.0, 90.0, hms(2, 5))), now)
        assertEquals(2, stats.passCount)
        assertEquals(1, stats.directions.size)
        assertEquals(1, stats.directions[0].count)
    }

    @Test fun testAllUnknownCoursesGiveNoDirections() {
        val stats = PlaceStats.build(listOf(pass(1.0, -1.0, hms(2, 0)), pass(2.0, -1.0, hms(2, 5))), now)
        assertEquals(2, stats.passCount)
        assertTrue(stats.directions.isEmpty())
    }

    @Test fun testFirstAndLastAndFirstTime() {
        val one = PlaceStats.build(listOf(pass(3.0, 0.0, 100.0)), now)
        assertTrue(one.isFirstTime)
        assertEquals(one.firstAt, one.lastAt)
        val two = PlaceStats.build(listOf(pass(30.0, 0.0, 100.0), pass(3.0, 0.0, 100.0)), now)
        assertFalse(two.isFirstTime)
        assertEquals(now.minusSeconds(30 * 86400L), two.firstAt)
        assertEquals(now.minusSeconds(3 * 86400L), two.lastAt)
        val empty = PlaceStats.build(emptyList(), now)
        assertEquals(0, empty.passCount)
        assertNull(empty.firstAt)
        assertNull(empty.lastAt)
        assertTrue(empty.directions.isEmpty())
        assertFalse(empty.isFrequentGuest)
        assertFalse(empty.isFirstTime)
    }

    @Test fun testFrequentGuestNeedsFiveInNinetyDays() {
        val fresh = (0 until 5).map { pass((it * 10 + 1).toDouble(), 0.0, 100.0) }
        assertTrue(PlaceStats.build(fresh, now).isFrequentGuest)
        assertFalse(PlaceStats.build(fresh.take(4), now).isFrequentGuest)
        val stale = (0 until 5).map { pass((100 + it).toDouble(), 0.0, 100.0) }
        assertFalse(PlaceStats.build(stale, now).isFrequentGuest)
    }

    @Test fun testAngularDistanceWrapsAround() {
        assertEquals(20.0, PlaceStats.angularDistance(350.0, 10.0))
        assertEquals(20.0, PlaceStats.angularDistance(10.0, 350.0))
        assertEquals(180.0, PlaceStats.angularDistance(0.0, 180.0))
    }

    @Test fun testOverallMedianAndLatestTripPerDirection() {
        val a = pass(3.0, 0.0, hms(2, 14))
        val b = pass(1.0, 5.0, hms(2, 8))
        val c = pass(2.0, 180.0, hms(1, 58))
        val stats = PlaceStats.build(listOf(a, b, c), now)
        assertEquals(hms(2, 8), stats.medianElapsed)
        assertEquals(b.tripId, stats.directions[0].latestTripId)
        assertEquals(c.tripId, stats.directions[1].latestTripId)
        assertNull(PlaceStats.build(emptyList(), now).medianElapsed)
    }

    @Test fun testUsualNeedsMoreThanOnePass() {
        assertFalse(PlaceStats.build(emptyList(), now).hasUsual)
        val one = PlaceStats.build(listOf(pass(1.0, 0.0, hms(1, 8))), now)
        assertTrue(one.isFirstTime)
        assertFalse(one.hasUsual)
        val two = PlaceStats.build(listOf(pass(1.0, 0.0, hms(1, 8)), pass(3.0, 2.0, hms(1, 12))), now)
        assertTrue(two.hasUsual)
        assertFalse(two.isFirstTime)
    }

    @Test fun boundariesAndLatestAnchorStayFrozen() {
        val stats = PlaceStats.build(listOf(pass(0.0, 0.0, 1.0), pass(1.0, 45.0, 2.0), pass(2.0, 46.0, 3.0)), now)
        assertEquals(listOf(2, 1), stats.directions.map { it.count })
        assertTrue(PlaceStats.build((0 until 5).map { pass(90.0, 0.0, 100.0) }, now).isFrequentGuest)
        assertFalse(PlaceStats.build((0 until 5).map { pass(90.001, 0.0, 100.0) }, now).isFrequentGuest)
    }
}
