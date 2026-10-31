package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripReplayInput
import java.time.Instant
import kotlin.test.*
import org.junit.Test

// iOS: ReplayBarStateTests.swift. Same equator line, timestamps and expected states.
class ReplayBarStateTests {
    private val t0 = Instant.ofEpochSecond(1_700_000_000)
    private fun configured(n: Int = 11) = TripReplayEngine().apply {
        configure(TripReplayInput((0 until n).map { GeoPoint(0.0, it * .001) }, emptyList(), (0 until n).map { t0.plusSeconds(it * 10L) }))
    }
    @Test fun testEntryIsIdleAndHidesStop() { assertFalse(ReplayBarState.idle.showsStop); assertFalse(ReplayBarState.idle.showsPauseIcon); assertFalse(ReplayBarState.idle.showsHead) }
    @Test fun testTapPlayFromIdleStartsPlaying() { assertEquals(ReplayBarState.playing, ReplayBarState.idle.reduce(ReplayBarState.Event.tapPlayPause)); assertTrue(ReplayBarState.playing.showsStop); assertTrue(ReplayBarState.playing.showsPauseIcon) }
    @Test fun testTapPlayTogglesBetweenPlayingAndPaused() { assertEquals(ReplayBarState.paused, ReplayBarState.playing.reduce(ReplayBarState.Event.tapPlayPause)); assertEquals(ReplayBarState.playing, ReplayBarState.paused.reduce(ReplayBarState.Event.tapPlayPause)); assertTrue(ReplayBarState.paused.showsStop); assertFalse(ReplayBarState.paused.showsPauseIcon) }
    @Test fun testStopFromAnyStateReturnsToIdle() { ReplayBarState.entries.forEach { assertEquals(ReplayBarState.idle, it.reduce(ReplayBarState.Event.tapStop)) } }
    @Test fun testScrubFromIdleEntersPaused() { assertEquals(ReplayBarState.paused, ReplayBarState.idle.reduce(ReplayBarState.Event.scrub)) }
    @Test fun testScrubLeavesRunningStatesAlone() { assertEquals(ReplayBarState.playing, ReplayBarState.playing.reduce(ReplayBarState.Event.scrub)); assertEquals(ReplayBarState.paused, ReplayBarState.paused.reduce(ReplayBarState.Event.scrub)) }
    @Test fun testReachingTheEndPausesOnTheLastFrame() { assertEquals(ReplayBarState.paused, ReplayBarState.playing.reduce(ReplayBarState.Event.reachedEnd)); assertEquals(ReplayBarState.paused, ReplayBarState.paused.reduce(ReplayBarState.Event.reachedEnd)); assertEquals(ReplayBarState.idle, ReplayBarState.idle.reduce(ReplayBarState.Event.reachedEnd)) }
    @Test fun testConfigureLeavesTheEngineIdleWithNoHead() { val e = configured(); assertEquals(ReplayBarState.idle, e.phase); assertFalse(e.phase.showsStop); assertNull(e.headCoord); assertEquals(-1, e.trailIndex); assertEquals(0.0, e.progress, 1e-9) }
    @Test fun testPlayShowsTheHeadAndTheStopButton() { val e = configured(); e.play(0.0); assertEquals(ReplayBarState.playing, e.phase); assertTrue(e.isPlaying); assertTrue(e.phase.showsStop); assertNotNull(e.headCoord); e.stop() }
    @Test fun testStopFromPlayingReturnsToIdleAndClearsTheMap() { val e = configured(); e.play(0.0); e.stop(); assertEquals(ReplayBarState.idle, e.phase); assertFalse(e.phase.showsStop); assertNull(e.headCoord); assertEquals(-1, e.trailIndex); assertEquals(0.0, e.progress, 1e-9); assertEquals(0.0, e.distanceFraction, 1e-9) }
    @Test fun testStopFromPausedReturnsToIdle() { val e = configured(); e.seek(.5); e.pause(); assertEquals(ReplayBarState.paused, e.phase); e.stop(); assertEquals(ReplayBarState.idle, e.phase); assertNull(e.headCoord) }
    @Test fun testScrubFromIdlePausesAtThatPosition() { val e = configured(); e.seek(.4); assertEquals(ReplayBarState.paused, e.phase); assertTrue(e.phase.showsStop); assertEquals(.4, e.progress, 1e-9); assertNotNull(e.headCoord) }
    @Test fun testPauseFromIdleStaysIdle() { val e = configured(); e.pause(); assertEquals(ReplayBarState.idle, e.phase); assertNull(e.headCoord) }
    @Test fun testStopKeepsHoldMarkersOnTheScrubber() { val e = configured(); e.setHolds(listOf(t0.plusSeconds(40))); assertEquals(1, e.holdFractions.size); e.play(0.0); e.stop(); assertEquals(1, e.holdFractions.size); assertNull(e.holdingIndex) }
    @Test fun testTogglePlayWalksTheSamePathAsTheReducer() { val e = configured(); e.togglePlay(0.0); assertEquals(ReplayBarState.playing, e.phase); e.togglePlay(0.0); assertEquals(ReplayBarState.paused, e.phase); e.togglePlay(0.0); assertEquals(ReplayBarState.playing, e.phase); e.stop() }
}

// iOS: ReplayUnknownSpeedTests.swift
class ReplayUnknownSpeedTests {
    @Test fun testTheBubbleKnowsWhenSpeedIsUnknown() {
        val t0 = Instant.ofEpochSecond(1_700_000_000)
        val e = TripReplayEngine()
        e.configure(TripReplayInput((0 until 5).map { GeoPoint(0.0, it * .001) }, listOf(10.0, 10.0, -1.0, -1.0, 10.0), (0 until 5).map { t0.plusSeconds(it * 10L) }))
        e.seek(.55); assertFalse(e.currentSpeedKnown)
        e.seek(.05); assertTrue(e.currentSpeedKnown); assertEquals(10.0, e.currentSpeedMS, .001)
    }
}
