package com.onezee.triptrack.logic

import com.onezee.triptrack.model.RoadSceneTime
import java.time.*
import kotlin.test.*

// iOS: RoadSceneTimeTests.swift (hours, boundaries, time zones and DST).
class RoadSceneTimeTests {
    private val utc = ZoneOffset.UTC
    private fun date(hour: Int) = LocalDate.of(2026, 10, 26).atTime(hour, 0).toInstant(utc)
    @Test fun testEveryHour() {
        val expected = listOf("night", "night", "night", "night", "night", "dawn", "dawn", "dawn",
            "day", "day", "day", "day", "day", "day", "day", "day", "day", "sunset", "sunset", "sunset", "night", "night", "night", "night")
        for ((hour, slot) in expected.withIndex()) assertEquals(slot, RoadSceneTime.slot(date(hour), utc).rawValue)
    }
    @Test fun testBoundariesToTheSecond() {
        for ((hour, before, after) in listOf(Triple(5, RoadSceneTime.night, RoadSceneTime.dawn), Triple(8, RoadSceneTime.dawn, RoadSceneTime.day),
            Triple(17, RoadSceneTime.day, RoadSceneTime.sunset), Triple(20, RoadSceneTime.sunset, RoadSceneTime.night))) {
            val boundary = date(hour)
            assertEquals(before, RoadSceneTime.slot(boundary.minusSeconds(1), utc))
            assertEquals(after, RoadSceneTime.slot(boundary, utc)); assertEquals(after, RoadSceneTime.slot(boundary.plusSeconds(1), utc))
        }
        assertEquals(RoadSceneTime.night, RoadSceneTime.slot(date(0).minusSeconds(1), utc))
        assertEquals(RoadSceneTime.night, RoadSceneTime.slot(date(0), utc))
    }
    @Test fun testCalendarTimeZoneDeterminesTheSlot() {
        assertEquals(RoadSceneTime.night, RoadSceneTime.slot(date(4), utc))
        assertEquals(RoadSceneTime.dawn, RoadSceneTime.slot(date(4), ZoneOffset.ofHours(3)))
        assertEquals(RoadSceneTime.night, RoadSceneTime.slot(date(4), ZoneOffset.ofHours(-8)))
    }
    @Test fun testDaylightSavingUsesLocalHours() {
        val berlin = ZoneId.of("Europe/Berlin")
        for ((month, day) in listOf(3 to 29, 10 to 25)) {
            val dawn = LocalDate.of(2026, month, day).atTime(5, 0).atZone(berlin).toInstant()
            assertEquals(RoadSceneTime.night, RoadSceneTime.slot(dawn.minusSeconds(1), berlin))
            assertEquals(RoadSceneTime.dawn, RoadSceneTime.slot(dawn, berlin))
        }
    }
}
