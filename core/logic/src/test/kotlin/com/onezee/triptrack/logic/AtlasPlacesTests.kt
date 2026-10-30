package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.test.*
import org.junit.Test

// iOS: AtlasPlacesTests.swift
class AtlasPlacesTests {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun day(y: Int, m: Int, d: Int) =
        LocalDateTime.of(y, m, d, 12, 0).atZone(zone).toInstant()

    private val now = day(2026, 12, 31)
    private val period = AtlasPeriod.Custom(day(2026, 6, 1), day(2026, 6, 30))

    private fun seed(name: String, dates: List<java.time.Instant>) =
        AtlasPlaces.Seed(UUID.randomUUID(), GeoPoint(45.0, 39.0), name, 600.0, dates)

    @Test
    fun testAPlaceVisitedAroundTheWindowIsNotInIt() {
        val pins =
            AtlasPlaces.build(
                listOf(seed("Дача", listOf(day(2026, 1, 10), day(2026, 12, 20)))),
                period,
                now,
                zone,
            )
        assertFalse(pins.first().inPeriod)
    }

    @Test
    fun testAPassInsideTheWindowPutsThePlaceInPeriod() {
        val pins =
            AtlasPlaces.build(
                listOf(seed("Пляж", listOf(day(2026, 1, 10), day(2026, 6, 15)))),
                period,
                now,
                zone,
            )
        assertTrue(pins.first().inPeriod)
        assertEquals(day(2026, 6, 15), pins.first().lastAt)
    }

    @Test
    fun testAPlaceOutOfPeriodKeepsItsOwnDate() {
        val pin =
            AtlasPlaces.build(listOf(seed("Дача", listOf(day(2026, 1, 10)))), period, now, zone)
                .first()
        assertEquals(day(2026, 1, 10), pin.lastAt)
        assertEquals(1, pin.passCount)
    }

    @Test
    fun testWithoutAPeriodEveryPlaceIsInIt() {
        val pins =
            AtlasPlaces.build(
                listOf(seed("A", listOf(day(2020, 1, 1))), seed("B", listOf(day(2026, 9, 1)))),
                AtlasPeriod.AllTime,
                now,
                zone,
            )
        assertEquals(2, pins.count { it.inPeriod })
        assertEquals(2, AtlasPlaces.inPeriodCount(pins))
    }

    @Test
    fun testNewestFirst() {
        val pins =
            AtlasPlaces.build(
                listOf(
                    seed("Старое", listOf(day(2020, 1, 1))),
                    seed("Свежее", listOf(day(2026, 9, 1))),
                ),
                AtlasPeriod.AllTime,
                now,
                zone,
            )
        assertEquals(listOf("Свежее", "Старое"), pins.map { it.name })
    }

    @Test
    fun testPlaceWithoutPassesSurvives() {
        val pins =
            AtlasPlaces.build(listOf(seed("Пустое", emptyList())), AtlasPeriod.AllTime, now, zone)
        assertEquals(1, pins.size)
        assertNull(pins.first().lastAt)
        assertEquals(0, pins.first().passCount)
    }
}

// iOS: AtlasHomeTests.swift
class AtlasHomeTests {
    private val home = GeoPoint(45.035, 38.975)

    private fun north(metres: Double) = home.copy(latitude = home.latitude + metres / 111_320)

    @Test
    fun testOnlyTripsThatStartedAtHomeCount() {
        assertEquals(
            2,
            AtlasHome.tripsStarted(home, listOf(10.0, 120.0, 400.0, 5_000.0).map(::north)),
        )
    }

    @Test
    fun testTheEdgeOfTheRadiusStillCounts() {
        assertEquals(1, AtlasHome.tripsStarted(home, listOf(north(150.0))))
        assertEquals(0, AtlasHome.tripsStarted(home, listOf(north(151.0))))
    }

    @Test
    fun testRadiusMatchesTheAppsIdeaOfTheSameSpot() {
        assertEquals(150.0, AtlasHome.radius)
    }

    @Test
    fun testNoTripsIsZero() {
        assertEquals(0, AtlasHome.tripsStarted(home, emptyList()))
    }
}

// iOS: AtlasPresentationTests.swift (period rules used by AtlasPlaces).
class AtlasPeriodTests {
    private val zone = ZoneId.of("Europe/Berlin")

    private fun date(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant()

    @Test
    fun testYearUsesLocalCalendarBoundaries() {
        val now = date(2026, 9, 25)
        assertTrue(AtlasPeriod.ThisYear.contains(date(2026, 1, 1, 0), now, zone))
        assertFalse(AtlasPeriod.ThisYear.contains(date(2025, 12, 31, 23), now, zone))
        assertFalse(AtlasPeriod.ThisYear.contains(date(2027, 1, 1, 0), now, zone))
    }

    @Test
    fun testLastThirtyDaysIncludesTodayAcrossDaylightSaving() {
        val now = date(2026, 4, 5)
        assertTrue(AtlasPeriod.Last30Days.contains(date(2026, 3, 7, 0), now, zone))
        assertFalse(AtlasPeriod.Last30Days.contains(date(2026, 3, 6, 23), now, zone))
        assertTrue(AtlasPeriod.Last30Days.contains(date(2026, 4, 5, 23), now, zone))
        assertFalse(AtlasPeriod.Last30Days.contains(date(2026, 4, 6, 0), now, zone))
    }

    @Test
    fun testCustomPeriodIncludesEntireLastDayAndNormalizesReversedDates() {
        val start = date(2026, 9, 3)
        val end = date(2026, 9, 5)
        for (period in listOf(AtlasPeriod.Custom(start, end), AtlasPeriod.Custom(end, start))) {
            assertTrue(period.contains(date(2026, 9, 3, 0), zone = zone))
            assertTrue(period.contains(date(2026, 9, 5, 23), zone = zone))
            assertFalse(period.contains(date(2026, 9, 6, 0), zone = zone))
        }
    }

    @Test
    fun testAllTimeDoesNotRestrictDates() {
        assertNull(AtlasPeriod.AllTime.interval())
        assertTrue(AtlasPeriod.AllTime.contains(java.time.Instant.MIN))
        assertTrue(AtlasPeriod.AllTime.contains(java.time.Instant.MAX))
    }
}
