package com.onezee.triptrack.logic

import com.onezee.triptrack.logic.JourneyTestKit.georgia
import com.onezee.triptrack.logic.JourneyTestKit.krd
import com.onezee.triptrack.logic.JourneyTestKit.t0
import com.onezee.triptrack.logic.JourneyTestKit.tbs
import com.onezee.triptrack.logic.JourneyTestKit.trip
import com.onezee.triptrack.logic.JourneyTestKit.vld
import com.onezee.triptrack.model.*
import kotlin.test.*
import org.junit.Test

// iOS: JourneyAggregateTests.swift
class JourneyAggregateTests {
    private fun aggregate() = JourneyAggregate.build(georgia())

    @Test
    fun testLocalTripsAroundTheStayFoldIntoOneItem() {
        val a = aggregate()
        assertEquals(4, a.legCount)
        assertEquals(6, a.calendarDays)
        assertEquals(1_410, (a.totalMetres / 1000).toInt())
        val local = a.days.flatMap { it.items }.filterIsInstance<JourneyAggregate.Item.Local>()
        assertEquals(1, local.size)
        assertEquals(2, local[0].value1.size)
    }

    @Test
    fun testDaysAreNumberedFromTheFirstLeg() {
        val a = aggregate()
        assertEquals(listOf(1, 2, 3, 5, 6), a.days.map { it.number })
        assertEquals(1, a.days[0].items.size)
    }

    @Test
    fun testDefaultTitleIsFirstStartToFarthestEnd() {
        val a = aggregate()
        assertEquals("Краснодар — Тбилиси", a.defaultTitle("Краснодар", "Тбилиси"))
        assertEquals(tbs.latitude, a.farthestEnd!!.latitude, 0.2)
    }

    @Test
    fun testTwoLocalGroupsCanShareOneDay() {
        val near = GeoPoint(45.06, 39.02)
        val a =
            JourneyAggregate.build(
                listOf(
                    trip(0, 8.0, krd, krd, 6.0, 0.3),
                    trip(0, 10.0, krd, near, 9.0, 0.4),
                    trip(0, 12.0, krd, vld, 480.0, 5.0),
                    trip(0, 19.0, vld, vld, 7.0, 0.3),
                    trip(0, 21.0, vld, vld, 5.0, 0.2),
                )
            )
        assertEquals(1, a.days.size)
        val groups = a.days[0].items.filterIsInstance<JourneyAggregate.Item.Local>()
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].value1.size)
        assertEquals(2, groups[1].value1.size)
        assertNotEquals(groups[0].value1.first().id, groups[1].value1.first().id)
    }

    @Test
    fun testLocalGroupCarriesTheDayNumberOfItsLastTrip() {
        val a = aggregate()
        assertEquals(
            listOf(4),
            a.days
                .flatMap { it.items }
                .filterIsInstance<JourneyAggregate.Item.Local>()
                .map { it.lastDayNumber },
        )
        assertEquals(2, a.localTripCount)
    }

    @Test
    fun testTripWithNeitherTrackNorPolylineIsStillALeg() {
        val start = t0.plusSeconds(9 * 3_600)
        val a =
            JourneyAggregate.build(
                listOf(
                    Trip(
                        startDate = start,
                        endDate = start.plusSeconds(3_600),
                        distance = 42_000.0,
                        maxSpeed = 30.0,
                        averageSpeed = 25.0,
                    )
                )
            )
        assertEquals(1, a.legCount)
        assertEquals(0, a.localTripCount)
        assertNull(a.firstStart)
        assertNull(a.farthestEnd)
        assertEquals(42_000, a.totalMetres.toInt())
    }

    @Test
    fun testFarthestEndIgnoresLocalTrips() {
        val a =
            JourneyAggregate.build(
                listOf(
                    trip(0, 9.0, krd, tbs, 690.0, 10.0),
                    trip(1, 11.0, tbs, GeoPoint(41.62, 44.90), 14.0, 0.5),
                )
            )
        assertEquals(tbs.latitude, a.farthestEnd!!.latitude, 0.001)
    }

    @Test
    fun testEmptyInputGivesEmptyAggregate() {
        val a = JourneyAggregate.build(emptyList())
        assertTrue(a.days.isEmpty())
        assertEquals(0, a.legCount)
    }

    @Test
    fun testOvernightLegCountsTheDayItFinishesOn() {
        val a = JourneyAggregate.build(listOf(trip(0, 22.0, krd, vld, 480.0, 4.0)))
        assertEquals(2, a.calendarDays)
        assertEquals(listOf(1), a.days.map { it.number })
    }
}
