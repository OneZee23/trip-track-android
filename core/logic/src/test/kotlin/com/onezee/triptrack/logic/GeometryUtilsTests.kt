package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import kotlin.test.*
import org.junit.Test

// iOS: GeometryUtilsTests.swift (haversine, gap splitting, RDP).
class GeometryUtilsTests {
    @Test
    fun testHaversineDistance_samePoint() {
        val coord = GeoPoint(55.75, 37.62)
        assertEquals(0.0, GeometryUtils.haversineDistance(coord, coord), 0.01)
    }

    @Test
    fun testHaversineDistance_knownCities() {
        val moscow = GeoPoint(55.7558, 37.6173)
        val spb = GeoPoint(59.9343, 30.3351)
        assertEquals(634_000.0, GeometryUtils.haversineDistance(moscow, spb), 10_000.0)
    }

    @Test
    fun testHaversineDistance_shortDistance() {
        val dist = GeometryUtils.haversineDistance(GeoPoint(45.0350, 38.9750), GeoPoint(45.0360, 38.9760))
        assertTrue(dist > 100)
        assertTrue(dist < 200)
    }

    @Test
    fun testSplitByGaps_noGaps() {
        val coords = listOf(GeoPoint(45.035, 38.975), GeoPoint(45.036, 38.976), GeoPoint(45.037, 38.977))
        val segments = GeometryUtils.splitByGaps(coords, 1_000.0)
        assertEquals(1, segments.size)
        assertEquals(3, segments.first().size)
    }

    @Test
    fun testSplitByGaps_withGap() {
        val coords = listOf(
            GeoPoint(45.035, 38.975),
            GeoPoint(45.036, 38.976),
            // ~200 km gap
            GeoPoint(47.000, 39.000),
            GeoPoint(47.001, 39.001),
        )
        val segments = GeometryUtils.splitByGaps(coords, 1_000.0)
        assertEquals(2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)
    }

    @Test
    fun testSplitByGaps_singlePoint() {
        assertTrue(GeometryUtils.splitByGaps(listOf(GeoPoint(45.0, 38.0)), 1_000.0).isEmpty())
    }

    @Test
    fun testSplitByGaps_allGaps() {
        val coords = listOf(GeoPoint(45.0, 38.0), GeoPoint(46.0, 39.0), GeoPoint(47.0, 40.0))
        assertTrue(GeometryUtils.splitByGaps(coords, 1_000.0).isEmpty())
    }

    @Test
    fun testDefaultGapThreshold() {
        assertEquals(1_000.0, GeometryUtils.defaultGapThreshold)
    }

    @Test
    fun testSimplifyRDP_straightLine() {
        val coords = (0 until 10).map { GeoPoint(45.0 + it * 0.001, 38.0) }
        assertEquals(2, GeometryUtils.simplifyRDP(coords, 0.0001).size)
    }

    @Test
    fun testSimplifyRDP_preservesCurve() {
        val coords = listOf(GeoPoint(45.0, 38.0), GeoPoint(45.0, 38.01), GeoPoint(45.01, 38.01))
        assertEquals(3, GeometryUtils.simplifyRDP(coords, 0.0001).size)
    }

    // The iterative walk must keep exactly what the recursion keeps; a zigzag forces deep splits.
    @Test
    fun simplifyIndicesKeepsTheRecursiveSet() {
        val coords = (0 until 400).map { i -> GeoPoint(45.0 + i * 0.0001, 38.0 + (if (i % 7 < 3) 0.0003 else 0.0) + i * 0.00001) }
        fun recursive(start: Int, end: Int, epsilon: Double): Set<Int> {
            if (end - start <= 1) return setOf(start, end)
            var maxDist = 0.0
            var maxIndex = start
            for (i in start + 1 until end) {
                val d = GeometryUtils.perpendicularDistance(coords[i], coords[start], coords[end])
                if (d > maxDist) {
                    maxDist = d
                    maxIndex = i
                }
            }
            return if (maxDist > epsilon) recursive(start, maxIndex, epsilon) + recursive(maxIndex, end, epsilon) else setOf(start, end)
        }
        for (epsilon in listOf(0.0, 0.00002, 0.0001, 0.001)) {
            assertEquals(recursive(0, coords.lastIndex, epsilon), GeometryUtils.simplifyIndices(coords, 0, coords.lastIndex, epsilon))
        }
    }

    // A deep spiral would overflow a recursive walk on an Android worker thread.
    @Test
    fun simplifyIndicesSurvivesAnEightHourTrack() {
        val coords = (0 until 12_000).map { i -> GeoPoint(45.0 + i * 1e-6 + (i % 2) * 1e-5, 38.0 + i * 1e-6) }
        val kept = GeometryUtils.simplifyIndices(coords, 0, coords.lastIndex, 0.0)
        assertTrue(0 in kept && coords.lastIndex in kept)
    }

    @Test
    fun polylineLengthAddsHaversineSteps() {
        val coords = listOf(GeoPoint(45.0, 38.0), GeoPoint(45.001, 38.0), GeoPoint(45.002, 38.0))
        assertEquals(
            GeometryUtils.haversineDistance(coords[0], coords[1]) + GeometryUtils.haversineDistance(coords[1], coords[2]),
            GeometryUtils.polylineLength(coords),
            1e-9,
        )
        assertEquals(0.0, GeometryUtils.polylineLength(coords.take(1)))
    }
}
