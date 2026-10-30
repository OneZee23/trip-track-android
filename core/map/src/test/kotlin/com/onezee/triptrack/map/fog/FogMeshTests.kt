package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.FogTestKit
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.*
import org.junit.Test

// iOS: FogMesh.build. What the GPU receives: Float32 offsets from a chunk origin, the chunk picked by
// the segment's midpoint, true chunk bounds for culling, one range per chunk.
class FogMeshTests {
    private val krasnodar = GeoPoint(45.0355, 38.9753)

    private fun line(vararg points: GeoPoint) = points.toList()

    private fun absolute(lod: FogMeshLod, chunk: Int, segment: Int): DoubleArray {
        val base = 4 * segment
        return doubleArrayOf(
            lod.originX(chunk) + lod.segments[base],
            lod.originY(chunk) + lod.segments[base + 1],
            lod.originX(chunk) + lod.segments[base + 2],
            lod.originY(chunk) + lod.segments[base + 3],
        )
    }

    @Test
    fun emptyLayerBuildsAnEmptyMesh() {
        val mesh = FogMesh.build(RevealedLayer.empty)
        assertTrue(mesh.isEmpty)
        assertEquals(0, mesh.byteSize)
        for (lod in RevealedLayer.LOD.entries) assertEquals(0, mesh.chunkCount(lod))
    }

    @Test
    fun aPolylineOfNPointsGivesNMinusOneSegments() {
        val road = (0 until 50).map { GeoPoint(krasnodar.latitude + it * 0.0005, krasnodar.longitude + it * 0.0002) }
        val mesh = FogMesh.build(FogTestKit.layer(listOf(road, line(krasnodar))))
        for (lod in RevealedLayer.LOD.entries) assertEquals(49, mesh.lod(lod).segmentCount)
        assertEquals(3 * 49 * 16, mesh.byteSize)
    }

    // The fine level keeps the iPhone's 32,768 map point chunks; mid and far use 2^20 (Android only).
    @Test
    fun levelsUseTheirChunkSizes() {
        val road = (0 until 400).map { GeoPoint(krasnodar.latitude + it * 0.002, krasnodar.longitude) }
        val mesh = FogMesh.build(FogTestKit.layer(listOf(road)))
        assertEquals(FogRules.CHUNK_SIZE, mesh.fine.chunkSize)
        assertEquals(FogRules.COARSE_CHUNK_SIZE, mesh.mid.chunkSize)
        assertEquals(FogRules.COARSE_CHUNK_SIZE, mesh.far.chunkSize)
        // About 89 km of road: several fine chunks, a single coarse one or two.
        assertTrue(mesh.fine.chunkCount > 10, "fine chunks ${mesh.fine.chunkCount}")
        assertTrue(mesh.far.chunkCount <= 2, "far chunks ${mesh.far.chunkCount}")
    }

    @Test
    fun everySegmentLivesInTheChunkOfItsMidpoint() {
        val road = (0 until 300).map { GeoPoint(krasnodar.latitude + it * 0.0009, krasnodar.longitude + it * 0.0013) }
        val mesh = FogMesh.build(FogTestKit.layer(listOf(road)))
        for (lod in RevealedLayer.LOD.entries) {
            val data = mesh.lod(lod)
            for (chunk in 0 until data.chunkCount) {
                for (s in data.first(chunk) until data.first(chunk) + data.count(chunk)) {
                    val p = absolute(data, chunk, s)
                    assertEquals(floor((p[0] + p[2]) / 2 / data.chunkSize), data.originX(chunk) / data.chunkSize, "x chunk of segment $s")
                    assertEquals(floor((p[1] + p[3]) / 2 / data.chunkSize), data.originY(chunk) / data.chunkSize, "y chunk of segment $s")
                }
            }
        }
    }

    @Test
    fun rangesTileTheSegmentsWithoutGapsOrOverlap() {
        val roads = (0 until 20).map { r ->
            (0 until 100).map { GeoPoint(44.0 + r * 0.05 + it * 0.001, 38.0 + it * 0.002) }
        }
        val mesh = FogMesh.build(FogTestKit.layer(roads))
        for (lod in RevealedLayer.LOD.entries) {
            val data = mesh.lod(lod)
            var expected = 0
            for (chunk in 0 until data.chunkCount) {
                assertEquals(expected, data.first(chunk))
                assertTrue(data.count(chunk) > 0)
                expected += data.count(chunk)
            }
            assertEquals(data.segmentCount, expected)
        }
    }

    // Chunk bounds are the true bounds of its segments, at least one map point wide (iOS MKMapRect):
    // culling decides by them.
    @Test
    fun chunkBoundsContainTheirSegments() {
        val road = (0 until 200).map { GeoPoint(krasnodar.latitude, krasnodar.longitude + it * 0.0004) }
        val mesh = FogMesh.build(FogTestKit.layer(listOf(road)))
        val data = mesh.fine
        for (chunk in 0 until data.chunkCount) {
            assertTrue(data.maxY(chunk) - data.minY(chunk) >= 1.0, "a horizontal road still has a 1 map point tall box")
            for (s in data.first(chunk) until data.first(chunk) + data.count(chunk)) {
                val p = absolute(data, chunk, s)
                for (i in 0..1) {
                    assertTrue(p[2 * i] >= data.minX(chunk) - 0.01 && p[2 * i] <= data.maxX(chunk) + 0.01)
                    assertTrue(p[2 * i + 1] >= data.minY(chunk) - 0.01 && p[2 * i + 1] <= data.maxY(chunk) + 0.01)
                }
            }
        }
    }

    // Krasnodar lies near map point (1.6e8, 9.6e7): absolute Float32 there is ~16 map points coarse,
    // metres at street zoom. Offsets from a chunk origin are millimetre precise, which is the whole
    // reason a chunk has one (iOS: FogMetalPixelTests.testStreetZoomHasNoFloat32Jitter, CPU half).
    @Test
    fun offsetsKeepStreetPrecisionFarFromTheOrigin() {
        val road = (0 until 100).map { GeoPoint(krasnodar.latitude + it * 0.00003, krasnodar.longitude + it * 0.00002) }
        val mesh = FogMesh.build(FogTestKit.layer(listOf(road)))
        val data = mesh.fine
        var worst = 0.0
        var s = 0
        for (chunk in 0 until data.chunkCount) {
            for (i in data.first(chunk) until data.first(chunk) + data.count(chunk)) {
                val p = absolute(data, chunk, i)
                // Points are not listed per segment, so compare the start of each segment with the road.
                val match = road.minOf { abs(MapPoints.x(it.longitude) - p[0]) + abs(MapPoints.y(it.latitude) - p[1]) }
                worst = maxOf(worst, match)
                s += 1
            }
        }
        assertEquals(99, s)
        assertTrue(worst < 0.01, "worst offset error $worst map points")
        val absoluteFloatError = abs(MapPoints.x(krasnodar.longitude).toFloat().toDouble() - MapPoints.x(krasnodar.longitude))
        assertTrue(absoluteFloatError > 1.0, "the fixture must be where absolute Float32 is coarse ($absoluteFloatError)")
    }

    // A segment lying across a chunk border goes by its middle, so its start's rounding cannot move it.
    @Test
    fun aSegmentOnABorderGoesByItsMiddle() {
        val border = 5_000 * FogRules.CHUNK_SIZE
        val a = GeoPoint(krasnodar.latitude, MapPoints.longitude(border - 10.0))
        val b = GeoPoint(krasnodar.latitude, MapPoints.longitude(border + 30.0))
        val mesh = FogMesh.build(FogTestKit.layer(listOf(listOf(a, b))))
        assertEquals(1, mesh.fine.chunkCount)
        assertEquals(border, mesh.fine.originX(0), 1e-6)
    }
}
