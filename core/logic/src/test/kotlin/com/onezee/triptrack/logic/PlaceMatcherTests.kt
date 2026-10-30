package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: PlaceMatcherTests.swift
class PlaceMatcherTests {
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val lat0 = 44.30
    private val lon0 = 38.70
    private val stepLat = 0.00045
    private val tripId = UUID.randomUUID()
    private fun point(lat: Double, course: Double, t: Long) = TrackPoint(latitude = lat, longitude = lon0,
        speed = 5.0, course = course, horizontalAccuracy = 5.0, timestamp = t0.plusSeconds(t))
    private fun northLine(count: Int, course: Double = 0.0) = (0 until count).map { point(lat0 + it * stepLat, course, it * 10L) }
    private fun outAndBack(count: Int) = northLine(count) + (0 until count).map { k ->
        point(lat0 + (count - 1 - k) * stepLat, 180.0, (count + k) * 10L)
    }
    private fun place(lat: Double, lon: Double): Place {
        val cell = Place.cell(lat, lon)
        return Place(Place.id(cell), cell, lat, lon, createdAt = t0)
    }

    @Test fun testOnePassStraightThrough() {
        val place = place(lat0 + 30 * stepLat, lon0)
        val passes = PlaceMatcher.passes(place, tripId, northLine(60), t0)
        assertEquals(1, passes.size)
        assertNear(300.0, passes[0].elapsedFromStart, 0.5)
        assertNear(1500.0, passes[0].distanceFromStart, 75.0)
        assertNear(0.0, passes[0].course, 0.5)
        assertEquals(place.id, passes[0].placeId)
        assertEquals(tripId, passes[0].tripId)
    }

    @Test fun testOutAndBackGivesTwoPassesWithOppositeCourses() {
        val passes = PlaceMatcher.passes(place(lat0 + 30 * stepLat, lon0), tripId, outAndBack(120), t0).sortedBy { it.timestamp }
        assertEquals(2, passes.size)
        assertNear(0.0, passes[0].course, 0.5)
        assertNear(180.0, passes[1].course, 0.5)
        assertTrue(passes[1].elapsedFromStart > passes[0].elapsedFromStart + 600)
    }

    @Test fun testRadiusIsOneHundredMetres() {
        val points = northLine(60)
        assertEquals(100.0, PlaceMatcher.passRadius)
        assertEquals(TripRouteLocator.distinctPassGap, PlaceMatcher.passGap)
        assertEquals(1, PlaceMatcher.passes(place(lat0 + 30 * stepLat, lon0 + 0.0010), tripId, points, t0).size)
        assertEquals(0, PlaceMatcher.passes(place(lat0 + 30 * stepLat, lon0 + 0.00188), tripId, points, t0).size)
    }

    @Test fun testCourseFallsBackToNeighbours() {
        val points = northLine(60, -1.0)
        for (i in listOf(30, 0, 59)) assertNear(0.0, PlaceMatcher.course(i, points), 1.0)
        assertEquals(PlacePass.unknownCourse, PlaceMatcher.course(0, listOf(points[0])))
    }

    @Test fun testPrefilterByGeohash5() {
        val jubga = place(44.3196, 38.7089)
        assertFalse(PlaceMatcher.isCandidate(jubga, PlaceMatcher.cells(listOf(GeoPoint(55.75, 37.61)))))
        assertTrue(PlaceMatcher.isCandidate(jubga, PlaceMatcher.cells(listOf(jubga.coordinate))))
        assertTrue(PlaceMatcher.isCandidate(jubga, PlaceMatcher.cells(listOf(GeoPoint(44.3196, 38.7350)))))
        assertTrue(PlaceMatcher.isCandidate(jubga, emptySet()))
    }

    @Test fun testTooFewPointsGiveNoPasses() {
        assertEquals(0, PlaceMatcher.passes(place(lat0, lon0), tripId, northLine(1), t0).size)
    }
}
