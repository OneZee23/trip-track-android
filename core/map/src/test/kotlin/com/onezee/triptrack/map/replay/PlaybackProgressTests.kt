package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test

// iOS: PlaybackProgressTests.swift, unchanged equator fixtures.
class PlaybackProgressTests {
    private fun line(n: Int) = (0 until n).map { GeoPoint(0.0, it * .001) }
    private fun fraction(coords: List<GeoPoint>, car: GeoPoint, index: Int) = PlaybackProgress.fraction(PlaybackProgress.cumulative(coords), coords, car, index)
    @Test fun testStartIsZeroAndFinishIsOne() { val c = line(11); assertEquals(0.0, fraction(c,c[0],0),1e-9); assertEquals(1.0,fraction(c,c[10],10),1e-9) }
    @Test fun testMiddleIsHalf() { val c = line(11); assertEquals(.5,fraction(c,c[5],5),1e-6) }
    @Test fun testCarBetweenPointsCountsThePartialLeg() { val c = line(11); assertEquals(.45,fraction(c,GeoPoint(0.0,.0045),4),1e-6) }
    @Test fun testUnevenSpacingIsMeasuredInMetresNotPoints() { val c = (0 until 5).map { GeoPoint(0.0,it * .0001) } + (1..5).map { GeoPoint(0.0,.0004 + it * .01) }; assertTrue(fraction(c,c[4],4) < .02) }
    @Test fun testOutOfRangeIndexDoesNotEscapeTheRange() { val c = line(11); assertEquals(0.0,fraction(c,c[0],-5),1e-9); assertEquals(1.0,fraction(c,c[10],99),1e-9) }
    @Test fun testDegenerateInputsAreZeroNotCrash() { val one = listOf(GeoPoint(0.0,0.0)); assertEquals(0.0,PlaybackProgress.fraction(listOf(0.0),one,one[0],0)); assertEquals(0.0,PlaybackProgress.fraction(emptyList(),emptyList(),one[0],0)) }
}

// iOS: RoutePlaybackController.swift, fullscreen's crawl pacing and final-frame hold.
class RoutePlaybackControllerTests {
    private val coords = listOf(GeoPoint(0.0,0.0),GeoPoint(0.0,.01))
    @Test fun shortTripTakesSevenSecondsAndHoldsForOnePointTwo() { val c = RoutePlaybackController(); c.start(coords,0.0,0.0); c.tick(3.5); assertEquals(.5,c.progress); c.tick(7.0); assertEquals(1.0,c.progress); assertEquals(coords.last(),c.currentCoord); c.tick(8.19); assertNotNull(c.currentCoord); c.tick(8.2); assertNull(c.currentCoord); assertFalse(c.isPlaying) }
    @Test fun crawlUsesOnePointFourSecondsPerKilometreCappedAtForty() { val c = RoutePlaybackController(); c.start(coords,10_000.0,0.0); c.tick(7.0); assertEquals(.5,c.progress); c.start(coords,100_000.0,0.0); c.tick(20.0); assertEquals(.5,c.progress) }
    @Test fun stopThenStartDuringTheFinalHoldCannotClearTheNewRun() { val c = RoutePlaybackController(); c.start(coords,0.0,0.0); c.tick(7.0); c.stop(); c.start(coords,0.0,7.1); c.tick(8.3); assertTrue(c.isPlaying); assertNotNull(c.currentCoord); assertTrue(c.progress < 1) }
}
