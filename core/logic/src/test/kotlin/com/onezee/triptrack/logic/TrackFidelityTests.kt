package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import kotlin.math.*
import kotlin.test.*

// iOS: TrackFidelityTests.swift
class TrackFidelityTests {
    private fun fix(east: Double, north: Double, speed: Double, course: Double, after: Double): LocationFix =
        TrackTestKit.fix(east, north, speed, course, after).copy(altitude = 100.0)

    private fun record(route: List<LocationFix>): RecordingMath = RecordingMath().also { manager -> route.forEach(manager::handleNewLocation) }
    private fun thinnedTo5m(fixes: List<LocationFix>): List<LocationFix> {
        val kept = mutableListOf<LocationFix>()
        for (fix in fixes) if (kept.isEmpty() || fix.distanceFrom(kept.last()) >= 5) kept += fix
        return kept
    }
    private fun straightDrive(seconds: Int, amplitude: Double): List<LocationFix> {
        val rng = Noise(42)
        return (0..seconds).map { t -> fix(rng.next() * amplitude, t * 10.0 + rng.next() * amplitude, 10.0, 0.0, t.toDouble()) }
    }
    private fun parkingCrawl(amplitude: Double): List<LocationFix> {
        val rng = Noise(21)
        return (1..60).map { t -> fix(rng.next() * amplitude, t * 0.83 + rng.next() * amplitude, 0.83, 0.0, t.toDouble()) }
    }
    private fun courtyardManeuver(amplitude: Double): List<LocationFix> {
        val rng = Noise(7)
        var east = 0.0
        var north = 0.0
        var t = 0.0
        val result = mutableListOf<LocationFix>()
        val legs = listOf(Triple(0, 1, 0.0), Triple(1, 0, 90.0), Triple(0, -1, 180.0), Triple(-1, 0, 270.0))
        for ((dE, dN, course) in legs) repeat(20) {
            east += dE * 1.39
            north += dN * 1.39
            t += 1
            result += fix(east + rng.next() * amplitude, north + rng.next() * amplitude, 1.39, course, t)
        }
        return result
    }
    @Test fun testDenseRecordingDoesNotChangeTheOdometer() {
        val route = straightDrive(200, 2.0)
        val dense = record(route).trip.distance
        val sparse = record(thinnedTo5m(route)).trip.distance
        assertNear(sparse, dense, max(sparse * 0.02, 20.0))
        assertNear(2000.0, dense, 100.0)
    }
    @Test fun testTheOdometerHoldsAtCourtyardSpeed() {
        val ground = 4 * 20 * 1.39
        assertNear(ground, record(courtyardManeuver(1.5)).trip.distance, ground * 0.2)
    }
    @Test fun testAParkedCarWritesNothing() {
        val rng = Noise(99)
        val manager = RecordingMath()
        for (t in 0..120) manager.handleNewLocation(fix(rng.next() * 3, rng.next() * 3, 0.0, -1.0, t.toDouble()))
        assertNear(0.0, manager.trip.distance, 0.001)
        assertTrue(manager.trip.trackPoints.size <= 2)
    }
    @Test fun testTheCourtyardIsRecordedFarDenser() {
        val route = courtyardManeuver(1.0)
        assertTrue(record(route).trip.trackPoints.size > record(thinnedTo5m(route)).trip.trackPoints.size * 2.5)
    }
    @Test fun testTheSlowestParkingCrawlIsRecordedToo() {
        val route = parkingCrawl(1.0)
        assertTrue(record(route).trip.trackPoints.size > record(thinnedTo5m(route)).trip.trackPoints.size * 2)
    }
    @Test fun testTheMotorwayDoesNotGetHeavier() {
        val rng = Noise(5)
        val route = (0..120).map { t -> fix(rng.next(), t * 25.0, 25.0, 0.0, t.toDouble()) }
        assertTrue(record(route).trip.trackPoints.size <= record(thinnedTo5m(route)).trip.trackPoints.size + 2)
    }
    @Test fun testThePointRateIsCapped() {
        val manager = RecordingMath()
        var course = 0.0
        for (i in 0..<200) {
            val t = i / 10.0
            course = (course + 30) % 360
            manager.handleNewLocation(fix(sin(t) * 3, t * 3, 3.0, course, t))
        }
        val worst = manager.trip.trackPoints.groupingBy { it.timestamp.epochSecond }.eachCount().values.maxOrNull() ?: 0
        assertTrue(worst <= 3, "Recorded $worst points in one second")
    }
    @Test fun testCourseDeltaCrossesNorth() {
        assertNear(2.0, courseDelta(359.0, 1.0), 0.001)
        assertNear(2.0, courseDelta(1.0, 359.0), 0.001)
        assertNear(180.0, courseDelta(10.0, 190.0), 0.001)
        assertNear(0.0, courseDelta(-1.0, 90.0), 0.001)
    }
    @Test fun testDistanceIsCountedByTheFiveMetreStep() {
        val samples = (0..200).map { i -> TripDistanceGate.Sample(
            TrackTestKit.origin.latitude + i / TrackTestKit.metersPerDegree, TrackTestKit.origin.longitude,
            TrackTestKit.epoch.plusSeconds(i.toLong())) }
        val walked = TripDistanceGate.totalDistance(samples)
        val straight = GeoPoint(samples.first().latitude, samples.first().longitude)
            .distanceFrom(GeoPoint(samples.last().latitude, samples.last().longitude))
        assertNear(straight, walked, TripDistanceGate.minStep + 1)
        assertTrue(walked <= straight)
        val rng = Noise(3)
        val jitter = (0..60).map { i -> TripDistanceGate.Sample(
            TrackTestKit.origin.latitude + rng.next() * 2 / TrackTestKit.metersPerDegree,
            TrackTestKit.origin.longitude + rng.next() * 2 / TrackTestKit.metersPerDegree,
            TrackTestKit.epoch.plusSeconds(i.toLong())) }
        assertTrue(TripDistanceGate.totalDistance(jitter) < 15)
    }
}
