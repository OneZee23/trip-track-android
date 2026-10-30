package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TrackGapFinder
import kotlin.test.*

// iOS: TrackGapFinderTests.swift
class TrackGapFinderTests {
    private fun p(north: Double, seconds: Double, filled: Boolean = false, segment: Int = 0): TrackGapFinder.Point {
        val c = TrackTestKit.coordinate(0.0, north)
        return TrackGapFinder.Point(c.latitude, c.longitude, TrackTestKit.epoch.addingSeconds(seconds), filled, segment)
    }
    @Test fun testLongAndSlowIsAGap() { assertEquals(1, TrackGapFinder.openGaps(listOf(p(0.0, 0.0), p(200.0, 12.0))).size) }
    @Test fun testShortDistanceIsNotAGap() { assertTrue(TrackGapFinder.openGaps(listOf(p(0.0, 0.0), p(100.0, 12.0))).isEmpty()) }
    @Test fun testShortTimeIsNotAGap() { assertTrue(TrackGapFinder.openGaps(listOf(p(0.0, 0.0), p(300.0, 5.0))).isEmpty()) }
    @Test fun testFilledGapIsNotOpen() {
        val points = listOf(p(0.0, 0.0), p(100.0, 6.0, filled = true), p(200.0, 12.0))
        assertTrue(TrackGapFinder.openGaps(points).isEmpty())
        assertEquals(1, TrackGapFinder.gaps(points, true).size)
    }
    @Test fun testUnsortedInputIsHandled() { assertEquals(1, TrackGapFinder.openGaps(listOf(p(200.0, 12.0), p(0.0, 0.0))).size) }
    @Test fun testExplicitPauseIsNotAnOpenOrPreviouslyFilledGPSGap() {
        val paused = listOf(p(0.0, 0.0), p(200.0, 60.0, segment = 1))
        assertTrue(TrackGapFinder.openGaps(paused).isEmpty())
        assertTrue(TrackGapFinder.gaps(paused, true).isEmpty())
    }
    @Test fun testGPSGapInsideResumedSectionStillFills() {
        val points = listOf(p(0.0, 0.0), p(200.0, 60.0, segment = 1), p(400.0, 80.0, segment = 1))
        val gaps = TrackGapFinder.openGaps(points)
        assertEquals(1, gaps.size)
        assertEquals(1, gaps.first().from.recordingSegmentIndex)
        assertEquals(20.0, gaps.first().seconds)
    }
}
