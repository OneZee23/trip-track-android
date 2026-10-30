package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*

// iOS: TripRouteLocatorTests.swift
class TripRouteLocatorTests {
    private val start = Instant.ofEpochSecond(1_780_000_000)
    private val metersPerDegree = 111_320.0
    private fun coordinate(metresNorth: Double) = GeoPoint(45 + metresNorth / metersPerDegree, 38.9)
    private fun straightTrack(seconds: Int) = (0..seconds).map { t ->
        TrackPoint(latitude = coordinate(t * 10.0).latitude, longitude = 38.9, speed = 10.0, timestamp = start.plusSeconds(t.toLong()))
    }
    private fun outAndBackTrack() = straightTrack(100) + (0..100).map { t ->
        TrackPoint(latitude = coordinate((100 - t) * 10.0).latitude, longitude = 38.9, speed = 10.0,
            timestamp = start.plusSeconds(1300 + t.toLong()))
    }

    @Test fun testDistanceToAPointAgreesWithTheOdometer() {
        val points = straightTrack(300)
        val prefix = TripRouteLocator.distancePrefix(points)
        val odometer = TripDistanceGate.totalDistance(points.map { TripDistanceGate.Sample(it.latitude, it.longitude, it.timestamp) })
        assertNear(odometer, prefix.last(), 0.01)
    }

    @Test fun testDistancePrefixResumesAfterPauseWithoutCountingTheMissingJourney() {
        val values = listOf(Triple(0.0, 0L, 8.0), Triple(10.0, 1L, 8.0), Triple(5000.0, 120L, 150.0),
            Triple(5010.0, 121L, 8.0), Triple(5020.0, 122L, 8.0))
        val points = values.map { (metres, seconds, accuracy) ->
            TrackPoint(latitude = coordinate(metres).latitude, longitude = 38.9, speed = 10.0,
                horizontalAccuracy = accuracy, timestamp = start.plusSeconds(seconds))
        }
        val segmented = RecordingBreaks.annotate(points, listOf(start.plusSeconds(120)))
        val prefix = TripRouteLocator.distancePrefix(segmented)
        assertNear(10.0, prefix[1], 0.1)
        assertNear(prefix[1], prefix[2], 1e-9)
        assertNear(prefix[1], prefix[3], 1e-9)
        assertNear(20.0, prefix[4], 0.1)
        val odometer = TripDistanceGate.totalDistance(segmented.filter { it.countsForDistance }.map { it.distanceSample() })
        assertNear(odometer, prefix[4], 1e-9)
    }

    @Test fun testAPointInTheMiddleReadsHalfTheTrip() {
        val fix = assertNotNull(TripRouteLocator.fix(start.plusSeconds(150), straightTrack(300)))
        assertNear(150.0, fix.elapsedFromStart, 1.0)
        assertNear(1500.0, fix.distanceFromStart, 20.0)
    }

    @Test fun testAMomentOutsideTheTripHasNoPlace() {
        val points = straightTrack(60)
        assertNull(TripRouteLocator.fix(start.minusSeconds(10), points))
        assertNull(TripRouteLocator.fix(start.plusSeconds(3600), points))
    }

    @Test fun testTheSameRoadDrivenTwiceOffersBothPasses() {
        val passes = TripRouteLocator.passes(coordinate(500.0), outAndBackTrack())
        assertEquals(2, passes.size)
        assertTrue(passes[0].elapsedFromStart < passes[1].elapsedFromStart)
        assertTrue(passes[1].elapsedFromStart - passes[0].elapsedFromStart > TripRouteLocator.distinctPassGap)
    }

    @Test fun testASinglePassOffersOneAnswer() {
        assertEquals(1, TripRouteLocator.passes(coordinate(500.0), straightTrack(300)).size)
    }

    @Test fun testATapAwayFromTheRouteFindsNothing() {
        assertTrue(TripRouteLocator.passes(GeoPoint(45.5, 39.5), straightTrack(300)).isEmpty())
    }

    @Test fun exactGapAndNearestTimeTieKeepIOSRules() {
        val first = straightTrack(1)
        assertEquals(600.0, TripRouteLocator.distinctPassGap)
        assertEquals(120.0, TripRouteLocator.tapRadius)
        assertEquals(1, TripRouteLocator.passes(first[0].coordinate,
            listOf(first[0], first[0].copy(timestamp = start.plusSeconds(600)))).size)
        assertEquals(2, TripRouteLocator.passes(first[0].coordinate,
            listOf(first[0], first[0].copy(timestamp = start.plusSeconds(601)))).size)
        assertEquals(1, TripRouteLocator.fix(start.plusMillis(500), first)?.index)
        assertTrue(TripRouteLocator.passes(first[0].coordinate, listOf(first[0])).isEmpty())
        assertEquals(listOf(0.0), TripRouteLocator.distancePrefix(listOf(first[0])))
        assertTrue(TripRouteLocator.distancePrefix(emptyList()).isEmpty())
    }
}
