package com.onezee.triptrack.ui

import com.onezee.triptrack.model.GeoPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class MapSnapshotRulesTests {
    @Test
    fun regionUsesBoundsCentreAndIOSMinimumSpan() {
        val region = SnapshotRules.region(listOf(GeoPoint(10.0, 20.0), GeoPoint(10.01, 20.02)))

        assertEquals(10.005, region.centreLat, 0.000001)
        assertEquals(20.01, region.centreLon, 0.000001)
        assertEquals(0.014, region.latitudeSpan, 0.000001)
        assertEquals(0.028, region.longitudeSpan, 0.000001)
        assertEquals(0.005, SnapshotRules.region(listOf(GeoPoint(1.0, 2.0), GeoPoint(1.0, 2.0))).latitudeSpan, 0.0)
    }

    @Test
    fun finishFlagFlipsAtTheIOSThreshold() {
        assertFalse(SnapshotRules.finishFlagPointsUp(11f))
        assertTrue(SnapshotRules.finishFlagPointsUp(11.01f))
    }

    @Test
    fun retriesLandAtZeroOneAndFourSeconds() {
        assertEquals(listOf(0L, 1_500L, 2_500L), SnapshotRules.retryDelaysMillis)
    }

    @Test
    fun cacheKeyTracksThemePixelSizeAndCoordinateEdits() {
        val points = listOf(GeoPoint(1.0, 2.0), GeoPoint(3.0, 4.0))
        val first = SnapshotRules.key("trip", false, 300, 178, points, "trip")

        assertEquals(first, SnapshotRules.key("trip", false, 300, 178, points, "trip"))
        assertNotEquals(first, SnapshotRules.key("trip", true, 300, 178, points, "trip"))
        assertNotEquals(first, SnapshotRules.key("trip", false, 301, 178, points, "trip"))
        assertNotEquals(first, SnapshotRules.key("trip", false, 300, 178, points + GeoPoint(5.0, 6.0), "trip"))
    }

    @Test
    fun weightedCacheEvictsTheLeastRecentlyUsedEntry() {
        val cache = WeightedLruCache<String, Int>(10) { it }
        cache.put("a", 5)
        cache.put("b", 5)
        assertEquals(5, cache.get("a"))
        cache.put("c", 5)

        assertEquals(5, cache.get("a"))
        assertEquals(null, cache.get("b"))
        assertEquals(5, cache.get("c"))
    }

    @Test
    fun renderGateAllowsAtMostTwoConcurrentSnapshots() = runBlocking {
        val inFlight = AtomicInteger()
        val peak = AtomicInteger()
        List(8) {
            async {
                SnapshotEngine.withRenderPermit {
                    val active = inFlight.incrementAndGet()
                    peak.updateAndGet { old -> maxOf(old, active) }
                    delay(5)
                    inFlight.decrementAndGet()
                }
            }
        }.forEach { it.await() }

        assertEquals(2, peak.get())
    }
}
