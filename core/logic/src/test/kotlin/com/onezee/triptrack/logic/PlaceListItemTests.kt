package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: PlaceListItemTests.swift
class PlaceListItemTests {
    private val now = Instant.ofEpochSecond(1_760_000_000)
    private fun place(name: String?): Place = Place(UUID.randomUUID(), Place.cell(44.3196, 38.7089), 44.3196, 38.7089, name, now)
    private fun pass(p: Place, daysAgo: Double, course: Double, elapsed: Double) = PlacePass(placeId = p.id,
        tripId = UUID.randomUUID(), timestamp = now.addingSeconds(-daysAgo * 86400), elapsedFromStart = elapsed,
        distanceFromStart = 1000.0, course = course)
    @Test fun testUsualIsTheTopDirectionMedian() {
        val p = place("Джубга")
        val item = PlaceListItem.build(p, listOf(pass(p, 1.0, 0.0, 8040.0), pass(p, 2.0, 3.0, 7680.0),
            pass(p, 3.0, 358.0, 9060.0), pass(p, 4.0, 180.0, 7080.0)), now)
        assertEquals(8040.0, item.usual)
        assertEquals(now.minusSeconds(86400), item.lastAt)
        assertFalse(item.isFirstTime)
        assertFalse(item.isFrequentGuest)
    }
    @Test fun testUsualFallsBackToOverallMedianWithoutCourses() {
        val p = place("Пост")
        assertEquals(200.0, PlaceListItem.build(p, listOf(pass(p, 1.0, -1.0, 100.0), pass(p, 2.0, -1.0, 300.0)), now).usual)
        assertNull(PlaceListItem.build(p, emptyList(), now).usual)
    }
    @Test fun testSinglePassHasNoUsual() {
        val p = place("Гараж")
        assertNull(PlaceListItem.build(p, listOf(pass(p, 1.0, 0.0, 500.0)), now).usual)
    }
    @Test fun testChipsAndSorting() {
        val a = place("А")
        val b = place("Б")
        val c = place("В")
        val ia = PlaceListItem.build(a, listOf(pass(a, 10.0, 0.0, 1.0)), now)
        val ib = PlaceListItem.build(b, (0 until 5).map { pass(b, (it + 1).toDouble(), 0.0, 1.0) }, now)
        val ic = PlaceListItem.build(c, emptyList(), now)
        assertTrue(ia.isFirstTime)
        assertTrue(ib.isFrequentGuest)
        assertNull(ic.lastAt)
        assertEquals(listOf("Б", "А", "В"), PlaceListItem.sorted(listOf(ia, ic, ib)).map { it.place.name })
    }
    @Test fun testSortedStableForTwoNamelessEmptyPlaces() {
        val a = place(null)
        val b = place(null)
        val ia = PlaceListItem.build(a, emptyList(), now)
        val ib = PlaceListItem.build(b, emptyList(), now)
        assertEquals(listOf(a.id, b.id), PlaceListItem.sorted(listOf(ia, ib)).map { it.place.id })
    }
}
