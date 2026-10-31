// iOS: PlaceLastPassTests.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Test
import kotlin.test.*

class PlaceLastPassTests {
    private val place = UUID.randomUUID()
    private fun pass(minutesAgo: Long, elapsed: Int, course: Double = 90.0) = PlacePass(placeId = place,
        tripId = UUID.randomUUID(), timestamp = Instant.ofEpochSecond(1_760_000_000 - minutesAgo * 60),
        elapsedFromStart = elapsed.toDouble(), distanceFromStart = 1000.0, course = course)
    @Test fun fasterAndSlowerPassesAreReported() {
        val others = listOf(pass(60 * 24, 58 * 60), pass(60 * 48, 60 * 60), pass(60 * 72, 62 * 60))
        val faster = PlaceLastPass.reading(place, others + pass(0, 50 * 60))!!
        assertTrue(faster.isFaster); assertEquals(600.0, faster.delta)
        val slower = PlaceLastPass.reading(place, others + pass(0, 75 * 60))!!
        assertFalse(slower.isFaster); assertEquals(900.0, slower.delta)
    }
    @Test fun twoPassesAreNotEnough() { assertNull(PlaceLastPass.reading(place, listOf(pass(0, 30 * 60), pass(60 * 24, 60 * 60)))) }
    @Test fun tinyAbsoluteOrRelativeDeltaIsSilent() {
        assertNull(PlaceLastPass.reading(place, listOf(pass(0, 7 * 60), pass(60 * 24, 10 * 60), pass(60 * 48, 10 * 60), pass(60 * 72, 10 * 60))))
        assertNull(PlaceLastPass.reading(place, listOf(pass(0, 175 * 60), pass(60 * 24, 180 * 60), pass(60 * 48, 180 * 60), pass(60 * 72, 180 * 60))))
    }
    @Test fun oppositeOrUnknownDirectionIsNotCompared() {
        val opposite = listOf(pass(60 * 24, 20 * 60, 270.0), pass(60 * 48, 20 * 60, 270.0), pass(60 * 72, 20 * 60, 270.0))
        assertNull(PlaceLastPass.reading(place, opposite + pass(0, 50 * 60)))
        assertNull(PlaceLastPass.reading(place, opposite + pass(0, 50 * 60, PlacePass.unknownCourse)))
    }
    @Test fun emptyPlaceIsSilentAndLatestTimestampWins() {
        assertNull(PlaceLastPass.reading(place, emptyList()))
        val passes = listOf(pass(60 * 48, 60 * 60), pass(0, 50 * 60), pass(60 * 72, 62 * 60), pass(60 * 24, 58 * 60))
        assertEquals(600.0, PlaceLastPass.reading(place, passes)?.delta)
    }
}
