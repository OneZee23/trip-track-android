package com.onezee.triptrack.map.replay

import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test

// iOS: CarHeadingPolicyTests.swift, replay heading/smoothing cases; MapCarMarkerTests.swift collapse cases.
class CarHeadingPolicyTests {
    private val frame = 1.0 / 60
    @Test fun testCrossingNorthGoesTheShortWayUp() { assertEquals(2.0,CarHeadingPolicy.shortestDelta(359.0,1.0),.0001) }
    @Test fun testCrossingNorthGoesTheShortWayDown() { assertEquals(-2.0,CarHeadingPolicy.shortestDelta(1.0,359.0),.0001) }
    @Test fun testSmoothingNeverSpinsThroughTheWholeCircle() { var current = 350.0; repeat(120) { current = CarHeadingPolicy.smoothed(current,10.0,frame); assertTrue(current >= 340 || current <= 20) }; assertEquals(0.0,CarHeadingPolicy.shortestDelta(current,10.0),2.5) }
    @Test fun testOppositeCourseTurnsTheWholeWayRound() { var current = 0.0; repeat(180) { current = CarHeadingPolicy.smoothed(current,180.0,frame) }; assertEquals(0.0,CarHeadingPolicy.shortestDelta(current,180.0),2.5) }
    @Test fun testFrozenHeadingSurvivesAThousandFrames() { var current = 123.0; repeat(1000) { current = CarHeadingPolicy.smoothed(current,null,frame) }; assertEquals(123.0,current,.0001) }
    @Test fun testTurnSpeedDoesNotDependOnFrameRate() { var at60 = 0.0; repeat(60) { at60 = CarHeadingPolicy.smoothed(at60,90.0,1.0/60) }; var at120 = 0.0; repeat(120) { at120 = CarHeadingPolicy.smoothed(at120,90.0,1.0/120) }; assertEquals(at60,at120,2.5) }
    @Test fun testReduceMotionTurnsInstantlyButStillTurns() { assertEquals(90.0,CarHeadingPolicy.smoothed(0.0,90.0,frame,true),.0001) }
    @Test fun testReduceMotionKeepsTheDeadZone() { assertEquals(10.0,CarHeadingPolicy.smoothed(10.0,11.0,frame,true),.0001) }
    @Test fun testReduceMotionStillFreezesWithoutACourse() { assertEquals(137.0,CarHeadingPolicy.smoothed(137.0,null,frame,true),.0001) }
    @Test fun testCourseAlongRouteLooksAhead() { val coords = (0 until 40).map { GeoPoint(45.0,39 + it * .00009) }; assertEquals(90.0,CarHeadingPolicy.courseAlongRoute(coords[0],0,coords) ?: -1.0,1.0) }
    @Test fun testQuantisationNoiseGivesNoCourse() { val here = GeoPoint(45.0,39.0); assertNull(CarHeadingPolicy.courseAlongRoute(here,0,listOf(here,GeoPoint(45.0,39.000001)))) }
    @Test fun testTinyWobbleIsIgnored() { assertEquals(100.0,CarHeadingPolicy.smoothed(100.0,101.5,frame),.0001) }
    @Test fun testTurnBeyondTheDeadZoneIsFollowed() { val after = CarHeadingPolicy.smoothed(100.0,130.0,frame); assertTrue(after > 100 && after < 130) }
    @Test fun testTurnRateIsCapped() { val after = CarHeadingPolicy.smoothed(0.0,179.0,.5); assertTrue(kotlin.math.abs(CarHeadingPolicy.shortestDelta(0.0,after)) <= 180 * .5 + .0001) }
    @Test fun testLookaheadWalksTheRouteAndNotTheStraightLine() {
        val start = GeoPoint(45.0,39.0)
        val coords = listOf(start,GeoPoint(45.000045,39.0),GeoPoint(45.000090,39.0),GeoPoint(45.000090,39.000025),GeoPoint(45.000045,39.000025),GeoPoint(45.0,39.000025))
        val course = assertNotNull(CarHeadingPolicy.courseAlongRoute(start,0,coords))
        assertTrue(kotlin.math.abs(CarHeadingPolicy.shortestDelta(course,0.0)) < 60)
    }
    @Test fun testEndOfRouteStillGivesTheLastDirection() { val coords = listOf(GeoPoint(45.0,39.0),GeoPoint(45.00005,39.0)); assertEquals(0.0,CarHeadingPolicy.courseAlongRoute(coords[0],0,coords) ?: -1.0,1.0) }
    @Test fun testPastTheLastPointThereIsNothingAhead() { val last = GeoPoint(45.0,39.0); assertNull(CarHeadingPolicy.courseAlongRoute(last,1,listOf(GeoPoint(44.999,39.0),last))) }
    @Test fun testAnglesComeBackInsideTheCircle() { assertEquals(350.0,CarHeadingPolicy.normalized(-10.0),.0001); assertEquals(10.0,CarHeadingPolicy.normalized(370.0),.0001); assertEquals(0.0,CarHeadingPolicy.normalized(360.0),.0001) }
    @Test fun collapseHasTheSameThresholdAndHysteresis() { assertFalse(CarHeadingPolicy.collapsed(23.0,false)); assertTrue(CarHeadingPolicy.collapsed(25.0,false)); assertTrue(CarHeadingPolicy.collapsed(21.0,true)); assertFalse(CarHeadingPolicy.collapsed(21.0,false)); assertFalse(CarHeadingPolicy.collapsed(18.0,true)); assertTrue(CarHeadingPolicy.collapsed(0.0,true)); assertFalse(CarHeadingPolicy.collapsed(0.0,false)) }
}
