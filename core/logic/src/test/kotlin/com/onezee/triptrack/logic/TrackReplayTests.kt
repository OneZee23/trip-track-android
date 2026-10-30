package com.onezee.triptrack.logic

import kotlin.math.abs
import kotlin.test.*

// iOS: TrackReplayTests.swift
class TrackReplayTests {
    @Test fun testCityEveningNoLongerTearsTheTrack() {
        val old = TrackReplay.run(SyntheticDrives.cityEvening(), 65.0)
        val new = TrackReplay.run(SyntheticDrives.cityEvening())
        assertTrue(old.recordedGaps >= 5)
        assertEquals(0, new.recordedGaps)
        assertEquals(0, new.openGaps)
        assertTrue(new.coarsePoints > 0)
        assertNear(old.distance, new.distance, old.distance * 0.01)
        val truth = SyntheticDrives.trueDistance()
        val old400 = TrackReplay.run(SyntheticDrives.cityEvening(side = 400.0), 65.0)
        val new400 = TrackReplay.run(SyntheticDrives.cityEvening(side = 400.0))
        assertTrue(abs(new400.distance - truth) <= abs(old400.distance - truth))
    }
    @Test fun testTunnelIsFilledAndNotCounted() {
        val old = TrackReplay.run(SyntheticDrives.tunnel(), 65.0)
        val new = TrackReplay.run(SyntheticDrives.tunnel())
        assertEquals(1, new.recordedGaps)
        assertEquals(0, new.openGaps)
        assertTrue(new.filledPoints > 0)
        assertNear(old.distance, new.distance, old.distance * 0.01)
    }
    @Test fun testSuspensionIsFilledAndNotCounted() {
        val new = TrackReplay.run(SyntheticDrives.suspension())
        assertEquals(1, new.recordedGaps)
        assertEquals(0, new.openGaps)
        assertTrue(new.filledPoints > 0)
    }
    @Test fun testEachGapIsClosedByExactlyOneFill() {
        // The iOS router stub returns the same straight line as the pure fill.
        val tunnel = TrackReplay.run(SyntheticDrives.tunnel())
        assertEquals(0, tunnel.openGaps)
        assertEquals(1, tunnel.fillRuns)
        assertEquals(1, TrackReplay.run(SyntheticDrives.suspension()).fillRuns)
    }
}
