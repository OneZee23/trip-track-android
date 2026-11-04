package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripReplayInput
import java.time.Instant
import kotlin.test.*
import org.junit.Test

// iOS: FullscreenMapSheet.swift, TripReplayEngine. Deterministic display-clock regression cases.
class TripReplayTimingTests {
    private val t0 = Instant.ofEpochSecond(1_700_000_000)
    private fun engine(span: Long = 100) = TripReplayEngine().apply {
        configure(TripReplayInput(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, .01)), listOf(10.0, 20.0), listOf(t0, t0.plusSeconds(span))))
    }
    @Test fun oneTimesUsesTheTracksOwnDuration() { val e = engine(); e.play(0.0); e.tick(.5); assertEquals(.005, e.progress, 1e-9); assertEquals(.00005, e.headCoord!!.longitude, 1e-9); assertEquals(10.05, e.currentSpeedMS, 1e-9) }
    @Test fun ratesWalkTheSameLadderAndWrap() { val e = engine(); assertEquals(1.0, e.rate); for (rate in listOf(1.5, 2.0, 3.0, 5.0, 1.0)) { e.cycleRate(); assertEquals(rate, e.rate) } }
    @Test fun rateChangeMultipliesTheNextTickWithoutAJump() { val e = engine(); e.play(0.0); e.tick(.5); e.cycleRate(); e.tick(1.0); assertEquals(.0125, e.progress, 1e-9) }
    @Test fun backgroundDeltaIsCappedAtHalfASecond() { val e = engine(); e.play(0.0); e.tick(10_000.0); assertEquals(.005, e.progress, 1e-9) }
    @Test fun holdFiltersEndpointsAndSortsByTime() { val e = engine(); e.setHolds(listOf(t0.plusSeconds(80), t0, t0.plusSeconds(30), t0.plusSeconds(100), t0.minusSeconds(1))); assertEquals(listOf(.3, .8), e.holdFractions) }
    @Test fun holdLastsOnePointEightWallSecondsAtEveryRate() {
        for (rate in TripReplayEngine.rateLadder) {
            val e = engine(10); while (e.rate != rate) e.cycleRate()
            e.setHolds(listOf(t0.plusMillis(250))); e.play(0.0); e.tick(.5)
            assertEquals(.025, e.progress, 1e-9); assertEquals(0, e.holdingIndex)
            e.tick(2.299); assertEquals(.025, e.progress, 1e-9); assertEquals(0, e.holdingIndex)
            e.tick(2.3); assertNull(e.holdingIndex); assertTrue(e.progress > .025)
        }
    }
    @Test fun firstCrossedCheckpointWinsEvenAtFiveTimes() { val e = engine(10); repeat(4) { e.cycleRate() }; e.setHolds(listOf(t0.plusSeconds(1), t0.plusSeconds(2))); e.play(0.0); e.tick(.5); assertEquals(.1, e.progress, 1e-9); assertEquals(0, e.holdingIndex); e.tick(2.3); assertEquals(.2, e.progress, 1e-9); assertEquals(1, e.holdingIndex) }
    @Test fun pauseAndSeekClearTheHoldAndResumeDoesNotRepeatIt() { val e = engine(10); e.setHolds(listOf(t0.plusSeconds(1))); e.seek(.09); e.play(0.0); e.tick(.5); assertNotNull(e.holdingIndex); e.pause(); assertNull(e.holdingIndex); e.seek(.2); e.play(2.0); e.tick(2.5); assertNull(e.holdingIndex); assertEquals(.25, e.progress, 1e-9) }
    @Test fun endPausesOnFinalFrameAndPlayRestarts() { val e = engine(1); e.play(0.0); e.tick(.5); e.tick(1.0); assertEquals(ReplayBarState.paused, e.phase); assertEquals(1.0, e.progress); assertEquals(GeoPoint(0.0, .01), e.headCoord); e.play(2.0); assertEquals(0.0, e.progress); assertEquals(GeoPoint(0.0, 0.0), e.headCoord) }
    @Test fun malformedInputDoesNotOfferAFrame() { val e = TripReplayEngine(); e.configure(TripReplayInput(listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0)), emptyList(), listOf(t0))); e.play(0.0); assertEquals(ReplayBarState.idle, e.phase); assertNull(e.headCoord) }
    @Test fun crossingARecordingBreakFadesTheHeadWithoutDrawingAChord() {
        val points = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, .01), GeoPoint(.01, .01))
        val e = TripReplayEngine().apply {
            configure(TripReplayInput(points, emptyList(), listOf(t0, t0.plusSeconds(10), t0.plusSeconds(20)), breaks = listOf(1)))
        }
        e.seek(.125)
        assertEquals(.5, e.headOpacity, 1e-9)
        assertEquals(points.first(), e.headCoord)
        e.seek(.25)
        assertEquals(0.0, e.headOpacity, 1e-9)
        e.seek(.375)
        assertEquals(.5, e.headOpacity, 1e-9)
        assertEquals(points[1], e.headCoord)
        e.seek(.5)
        assertEquals(1.0, e.headOpacity, 1e-9)
        assertEquals(points[1], e.headCoord)
    }
    @Test fun degenerateSpanParksAtTheEndWithAOneSecondClockFloor() { val e = engine(0); e.play(0.0); assertEquals(GeoPoint(0.0, .01), e.headCoord); assertEquals(1.0, e.distanceFraction); e.tick(.5); assertEquals(.5, e.progress) }
    @Test fun distanceDoesNotAdvanceAtAStationaryCheckpoint() {
        val e = TripReplayEngine(); e.configure(TripReplayInput(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0), GeoPoint(0.0, .01)), emptyList(), listOf(t0, t0.plusSeconds(50), t0.plusSeconds(100))))
        e.seek(.4); assertEquals(0.0, e.distanceFraction); e.seek(.75); assertEquals(.5, e.distanceFraction, 1e-9); e.seek(.1); assertEquals(0, e.trailIndex)
    }
}
