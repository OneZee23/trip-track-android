package com.onezee.triptrack.map.route

import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test

// iOS: RouteMapView.swift, relight/runMetres; ReplayRelightShotTests.swift's immediate stop restoration.
class ReplayRouteTests {
    private val c = (0..4).map { GeoPoint(0.0,it * .0001) }
    @Test fun speedRunsUseMetresRatherThanPointCounts() {
        val lines = RouteGeometry.replayFractions(listOf(RouteLine(c.take(2),10.0),RouteLine(listOf(c[1],c.last()),-1.0)))
        assertEquals(.25,lines[0].endFraction,1e-9); assertEquals(1.0,lines[1].endFraction,1e-9)
        assertEquals(.3,ReplayRoute.opacity(lines[0],.24)); assertEquals(1.0,ReplayRoute.opacity(lines[0],.25)); assertEquals(.3,ReplayRoute.opacity(lines[1],.99))
    }
    @Test fun stopImmediatelyRestoresBothKnownAndUnknownRuns() { val lines = RouteGeometry.replayFractions(listOf(RouteLine(c.take(2),10.0),RouteLine(c.drop(1),-1.0))); lines.forEach { assertEquals(.3,ReplayRoute.opacity(it,0.0)); assertEquals(1.0,ReplayRoute.opacity(it,null)); assertEquals(1.0,ReplayRoute.opacity(it,1.0)) } }
    @Test fun routeWithoutSpeedsStaysBrightLikeTheIosPlainPolyline() { assertEquals(1.0,ReplayRoute.opacity(RouteLine(c,null),0.0)) }
    @Test fun stationaryAndEmptyRoutesHaveNoDivisionByZero() { assertEquals(emptyList(),RouteGeometry.replayFractions(emptyList())); assertEquals(1.0,RouteGeometry.replayFractions(listOf(RouteLine(listOf(c[0],c[0]),0.0))).single().endFraction) }
    @Test fun relightThresholdNeverDelaysStopOrRewindToStart() { assertFalse(ReplayRoute.shouldRelight(.5001,.5)); assertTrue(ReplayRoute.shouldRelight(.501,.5)); assertTrue(ReplayRoute.shouldRelight(1.0,.9999)); assertTrue(ReplayRoute.shouldRelight(0.0,.0001)) }
}
