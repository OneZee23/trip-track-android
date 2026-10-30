package com.onezee.triptrack.map.fog

import com.onezee.triptrack.logic.MapRect
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.FogTestKit
import com.onezee.triptrack.map.MapPoints
import kotlin.test.*
import org.junit.AfterClass
import org.junit.Test

// iOS: FogMetalBudgetTests.swift, the parts that are not GL. The library is the scale of the
// `-seed-hang-stress` seed (400 trips × 2,000 points), previews only: the opened world is built from
// previews, never from raw points.
//
// The numbers guard against REGRESSION, as on iOS: a mesh build that doubles, a buffer per segment, a
// culling that no longer culls. Frame encoding is GL and is measured on the device by the native
// layer (FogStats.encodeMsPerFrame); see the report for the instrumented checks still to write.
class FogBudgetTests {
    companion object {
        // iOS spec §7 budgets.
        private const val MESH_BUILD_BUDGET_MS = 300.0
        private const val BYTE_BUDGET = 64 * 1024 * 1024
        private const val CHUNK_INDEX_THRESHOLD = 2000

        // The iPhone docs (CLAUDE.md, "Metal-туман на «Атласе» (0.8.0)"): 51,444 segments and 267 far
        // chunks of 32,768 map points on this library.
        private const val IOS_SEGMENTS = 51_444
        private const val IOS_FAR_CHUNKS = 267

        private const val VIEWPORT_WIDTH = 393.0
        private const val VIEWPORT_HEIGHT = 852.0

        // The middle of the stress library: trip 200 at its middle (iOS FogMetalBudgetTests.centre).
        private const val CENTRE_LAT = 47.03
        private const val CENTRE_LON = 40.92

        private var cached: RevealedLayer? = null

        /** Built once per class: four hundred previews through RevealBuilder. */
        fun stressLayer(): RevealedLayer = cached ?: FogTestKit.storedLayer(FogTestKit.iosStressPreviews()).also { cached = it }

        @JvmStatic
        @AfterClass
        fun release() {
            cached = null
        }
    }

    @Test
    fun theStressLibraryOpensTheSameWorldAsOnTheIPhone() {
        val layer = stressLayer()
        val segments = RevealedLayer.LOD.entries.map { lod -> layer.polylines(lod).sumOf { maxOf(0, it.size - 1) } }
        println("[fog] stress layer segments fine/mid/far ${segments.joinToString("/")}, total ${segments.sum()}")
        assertEquals(IOS_SEGMENTS, segments.sum(), "the reveal build no longer reproduces the iPhone's world")
        val iosChunks = FogMesh.build(layer) { FogRules.CHUNK_SIZE }
        assertEquals(IOS_FAR_CHUNKS, iosChunks.chunkCount(RevealedLayer.LOD.far), "far chunks at the iPhone's chunk size")
    }

    // Built off the main thread on the phone; here only the wall time is guarded.
    @Test
    fun testMeshBuildsWithinBudget() {
        val layer = stressLayer()
        FogMesh.build(layer) // warm up: the first run pays for class loading and JIT
        val runs = (0 until 5).map {
            val started = System.nanoTime()
            val mesh = FogMesh.build(layer)
            assertTrue(mesh.segmentCount > 0)
            (System.nanoTime() - started) / 1e6
        }.sorted()
        val median = runs[runs.size / 2]
        println("[fog] FogMesh.build median ${"%.1f".format(median)} ms (runs ${runs.joinToString { "%.1f".format(it) }})")
        assertTrue(median < MESH_BUILD_BUDGET_MS, "the mesh build left the iOS budget: $median ms")
    }

    @Test
    fun testMeshMemoryAndChunkCountFitTheBudget() {
        val mesh = FogMesh.build(stressLayer())
        val megabytes = mesh.byteSize / 1024.0 / 1024.0
        println(
            "[fog] buffers ${"%.2f".format(megabytes)} MB; chunks fine ${mesh.chunkCount(RevealedLayer.LOD.fine)}, " +
                "mid ${mesh.chunkCount(RevealedLayer.LOD.mid)}, far ${mesh.chunkCount(RevealedLayer.LOD.far)}",
        )
        assertTrue(mesh.byteSize < BYTE_BUDGET, "the revealed world's buffers passed 64 MB")
        // Culling is a linear pass over a level's chunks; past this count a coarse index is due.
        assertTrue(mesh.chunkCount(RevealedLayer.LOD.far) < CHUNK_INDEX_THRESHOLD, "time for a coarse chunk index")
        assertTrue(mesh.chunkCount(RevealedLayer.LOD.mid) < CHUNK_INDEX_THRESHOLD, "time for a coarse chunk index")
    }

    // The frames of the iOS budget: street (0.3 m/pt) and country (300 m/pt). Without this the encode
    // budget measured on the phone could measure an empty frame: move the centre and culling throws
    // everything away.
    @Test
    fun testStreetAndCountryFramesCullToWhatTheySee() {
        val mesh = FogMesh.build(stressLayer())
        val street = visibleChunks(mesh, metresPerPoint = 0.3)
        val country = visibleChunks(mesh, metresPerPoint = 300.0)
        println("[fog] visible chunks: street ${street.first} (${street.second}), country ${country.first} (${country.second})")
        assertTrue(street.first > 0, "the street frame must stand on a corridor")
        assertTrue(country.first > 0)
        assertEquals(RevealedLayer.LOD.fine, street.second)
        // 300 m/pt at 47° is 3.4e-4 points per map point: the mid level (far starts below 1.5e-4).
        assertEquals(RevealedLayer.LOD.mid, country.second)
        // At the iPhone's chunk size the country frame covers more chunks than the street frame.
        val ios = FogMesh.build(stressLayer()) { FogRules.CHUNK_SIZE }
        assertTrue(visibleChunks(ios, 300.0).first > visibleChunks(ios, 0.3).first)
    }

    /** Chunks that survive culling in a north-up frame, by the native layer's rule (fog_layer.cpp). */
    private fun visibleChunks(mesh: FogMesh, metresPerPoint: Double): Pair<Int, RevealedLayer.LOD> {
        val centreX = MapPoints.x(CENTRE_LON)
        val centreY = MapPoints.y(CENTRE_LAT)
        val mapPointsPerPoint = metresPerPoint * MapPoints.mapPointsPerMetre(CENTRE_LAT)
        val rect = MapRect(
            centreX - VIEWPORT_WIDTH * mapPointsPerPoint / 2,
            centreY - VIEWPORT_HEIGHT * mapPointsPerPoint / 2,
            VIEWPORT_WIDTH * mapPointsPerPoint,
            VIEWPORT_HEIGHT * mapPointsPerPoint,
        )
        val pointsPerMapPoint = 1 / mapPointsPerPoint
        val lod = com.onezee.triptrack.logic.FogVeilRenderer.lod(pointsPerMapPoint)
        val halfWidth = FogRules.halfWidthPoints(metresPerPoint)
        val pad = (halfWidth + halfWidth * FogRules.FEATHER_RATIO) * mapPointsPerPoint
        val data = mesh.lod(lod)
        var count = 0
        for (chunk in 0 until data.chunkCount) {
            if (data.maxX(chunk) < rect.minX - pad || data.minX(chunk) > rect.maxX + pad) continue
            if (data.maxY(chunk) < rect.minY - pad || data.minY(chunk) > rect.maxY + pad) continue
            count += 1
        }
        return count to lod
    }
}
