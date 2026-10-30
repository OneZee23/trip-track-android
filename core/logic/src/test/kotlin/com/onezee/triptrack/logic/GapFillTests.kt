package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TrackGapFinder
import kotlin.test.*

// iOS: GapFillTests.swift
class GapFillTests {
    private fun gap(north: Double, seconds: Double): TrackGapFinder.Gap {
        val a = TrackTestKit.coordinate(0.0, 0.0)
        val b = TrackTestKit.coordinate(0.0, north)
        return TrackGapFinder.Gap(TrackGapFinder.Point(a.latitude, a.longitude, TrackTestKit.epoch, false),
            TrackGapFinder.Point(b.latitude, b.longitude, TrackTestKit.epoch.addingSeconds(seconds), false))
    }
    @Test fun testResampleLiesStrictlyInsideTheGap() {
        val g = gap(800.0, 60.0)
        val path = listOf(TrackTestKit.coordinate(0.0, 0.0), TrackTestKit.coordinate(0.0, 800.0))
        val points = GapFill.resample(path, g.from.timestamp, g.to.timestamp, 10.0, 50.0)
        assertEquals(39, points.size)
        assertTrue(points.all { it.isInterpolated && it.speed == -1.0 && it.horizontalAccuracy == -1.0 })
        assertTrue(points.all { it.timestamp > g.from.timestamp && it.timestamp < g.to.timestamp })
        assertEquals(points.map { it.timestamp }.sorted(), points.map { it.timestamp })
        assertTrue(points.all { it.altitude > 10 && it.altitude < 50 })
    }
    @Test fun testResampleIsCapped() {
        val points = GapFill.resample(listOf(TrackTestKit.coordinate(0.0, 0.0), TrackTestKit.coordinate(0.0, 50_000.0)),
            TrackTestKit.epoch, TrackTestKit.epoch.plusSeconds(2400), 0.0, 0.0)
        assertEquals(GapFill.maxPoints, points.size)
    }
    @Test fun testTeleportIsNotFillable() {
        assertFalse(GapFill.isFillable(gap(5000.0, 10.0)))
        assertTrue(GapFill.isFillable(gap(800.0, 60.0)))
    }
    @Test fun testPlausibilityTable() {
        assertTrue(GapFill.isPlausible(2400.0, 150.0, 1000.0, 120.0))
        assertFalse(GapFill.isPlausible(2600.0, 150.0, 1000.0, 120.0))
        assertTrue(GapFill.isPlausible(1200.0, 300.0, 1000.0, 20.0))
        assertFalse(GapFill.isPlausible(1200.0, 700.0, 1000.0, 60.0))
    }
    @Test fun testStraightnessTellsAStraightFillFromARoad() {
        val a = TrackTestKit.coordinate(0.0, 0.0)
        val b = TrackTestKit.coordinate(0.0, 800.0)
        val straight = GapFill.resample(listOf(a, b), TrackTestKit.epoch, TrackTestKit.epoch.plusSeconds(60), 0.0, 0.0).map { it.coordinate }
        assertTrue(GapFill.isStraight(straight, a, b))
        assertTrue(GapFill.isStraight(emptyList(), a, b))
        assertFalse(GapFill.isStraight(listOf(TrackTestKit.coordinate(200.0, 400.0)), a, b))
    }
}
