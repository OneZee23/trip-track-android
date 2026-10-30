package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.*
import org.junit.Test

// iOS: ProfileHistoryFoldingTests.swift
class ProfileHistoryFoldingTests {
    private val zone = ZoneId.systemDefault()

    private fun date(day: Int, hour: Int = 10, month: Int = 9) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant()

    private fun trip(day: Int) =
        Trip(
            startDate = date(day),
            endDate = date(day).plusSeconds(3_600),
            distance = 120_000.0,
            maxSpeed = 30.0,
            averageSpeed = 25.0,
        )

    private fun journey() = Journey(startDate = date(12, 0), endDate = date(16, 23))

    @Test
    fun testLegsCollapseIntoOneRowAtTheJourneysPlace() {
        val t20 = trip(20)
        val t15 = trip(15)
        val t13 = trip(13)
        val t9 = trip(9)
        val j = journey()
        val rows = HistoryFolding.fold(listOf(t20, t15, t13, t9), listOf(j))
        assertEquals(3, rows.size)
        assertEquals(t20.id, assertIs<HistoryRow.Trip>(rows[0]).value.id)
        assertEquals(j.id, assertIs<HistoryRow.Journey>(rows[1]).value1.id)
        assertEquals(listOf(t15.id, t13.id), (rows[1] as HistoryRow.Journey).legs.map { it.id })
        assertEquals(t9.id, assertIs<HistoryRow.Trip>(rows[2]).value.id)
    }

    @Test
    fun testJourneyOutsideTheCalendarRangeIsHidden() {
        val august = Journey(startDate = date(1, 0, 8), endDate = date(10, 0, 8))
        val rows =
            HistoryFolding.fold(
                listOf(trip(20), trip(9)),
                listOf(august),
                HistoryFolding.dayRange(date(1), date(30)),
            )
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is HistoryRow.Trip })
    }

    @Test
    fun testJourneyWithNoLegsAtAllIsShown() {
        val j = journey()
        val rows = HistoryFolding.fold(listOf(trip(20), trip(9)), listOf(j))
        assertEquals(3, rows.size)
        val row = assertIs<HistoryRow.Journey>(rows[1])
        assertEquals(j.id, row.value1.id)
        assertTrue(row.legs.isEmpty())
    }

    @Test
    fun testJourneySortsByItsLatestLegNotItsEarliest() {
        val t15 = trip(15)
        val t14 = trip(14)
        val t13 = trip(13)
        val j = journey().copy(excludedTripIds = listOf(t14.id))
        val rows = HistoryFolding.fold(listOf(t15, t14, t13), listOf(j))
        assertEquals(2, rows.size)
        val row = assertIs<HistoryRow.Journey>(rows[0])
        assertEquals(j.id, row.value1.id)
        assertEquals(listOf(t15.id, t13.id), row.legs.map { it.id })
        assertEquals(t14.id, assertIs<HistoryRow.Trip>(rows[1]).value.id)
    }

    @Test
    fun testEmptyJourneyFollowsTheCalendarRange() {
        val trips = listOf(trip(20), trip(9))
        val j = journey()
        assertEquals(
            3,
            HistoryFolding.fold(trips, listOf(j), HistoryFolding.dayRange(date(12), date(16))).size,
        )
        val outside =
            HistoryFolding.fold(trips, listOf(j), HistoryFolding.dayRange(date(1), date(5)))
        assertEquals(2, outside.size)
        assertTrue(outside.all { it is HistoryRow.Trip })
    }

    @Test
    fun testOverlappingJourneysNeverShowATripTwice() {
        val t14 = trip(14)
        val rows =
            HistoryFolding.fold(
                listOf(t14),
                listOf(journey(), Journey(startDate = date(13, 0), endDate = date(18, 23))),
            )
        assertEquals(2, rows.size)
        assertEquals(
            listOf(t14.id),
            rows.filterIsInstance<HistoryRow.Journey>().flatMap { it.legs }.map { it.id },
        )
    }

    @Test
    fun testDayRangeUpperBoundSurvivesDSTFallBack() {
        val moscow = ZoneId.of("Europe/Moscow")
        val day = LocalDateTime.of(2014, 10, 26, 0, 0).atZone(moscow).toInstant()
        val late = LocalDateTime.of(2014, 10, 26, 23, 30).atZone(moscow).toInstant()
        val j = Journey(startDate = late, endDate = late.plusSeconds(600))
        val rows =
            HistoryFolding.fold(emptyList(), listOf(j), HistoryFolding.dayRange(day, day, moscow))
        assertEquals(1, rows.size)
        assertEquals(j.id, assertIs<HistoryRow.Journey>(rows[0]).value1.id)
    }

    @Test
    fun testRunsSplitTripsAroundJourneys() {
        val t20 = trip(20)
        val t9 = trip(9)
        val t8 = trip(8)
        val runs =
            HistoryFolding.runs(
                HistoryFolding.fold(listOf(t20, trip(15), t9, t8), listOf(journey()))
            )
        assertEquals(listOf(1, 1, 2), runs.map { it.size })
        assertIs<HistoryRow.Journey>(runs[1][0])
        assertEquals(listOf(t9.id, t8.id), runs[2].map { it.id })
    }

    // iOS: PreparedProfileHistoryTests.swift
    @Test
    fun testLongRunsPreserveEveryRowAndJourneyBoundary() {
        val trips = (0 until 1_000).map { Trip(startDate = date(1).plusSeconds(it.toLong())) }
        val rows =
            trips.take(500).map { HistoryRow.Trip(it) } +
                HistoryRow.Journey(Journey(startDate = date(1), endDate = date(2)), emptyList()) +
                trips.takeLast(500).map { HistoryRow.Trip(it) }
        val runs = HistoryFolding.runs(rows)
        assertEquals(listOf(500, 1, 500), runs.map { it.size })
        assertEquals(rows.map { it.id }, runs.flatten().map { it.id })
        assertTrue(HistoryFolding.runs(emptyList()).isEmpty())
    }
}
