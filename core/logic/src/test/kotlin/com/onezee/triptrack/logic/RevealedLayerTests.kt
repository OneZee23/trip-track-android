package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.*
import org.junit.Test

// iOS: RevealedLayerStitchTests.swift
class RevealedLayerStitchTests {
    private fun c(lat: Double, lon: Double) = GeoPoint(lat, lon)

    private fun segments(runs: List<List<GeoPoint>>) = runs.flatMap { it.zipWithNext() }

    @Test
    fun testTileCutRoadBecomesOneLine() {
        val runs =
            (0 until 10).map { i -> listOf(c(55.0, 37 + i * 0.1), c(55.0, 37 + (i + 1) * 0.1)) }
        val joined = RevealedLayer.stitch(runs.shuffled(Random(42)))
        assertEquals(1, joined.size)
        assertEquals(37.0, joined[0].first().longitude, 1e-12)
        assertEquals(38.0, joined[0].last().longitude, 1e-12)
        assertEquals(segments(runs).toSet(), segments(joined).toSet())
    }

    @Test
    fun testSeparateRoadsStaySeparate() {
        assertEquals(
            2,
            RevealedLayer.stitch(
                    listOf(
                        listOf(c(55.0, 37.0), c(55.0, 37.1)),
                        listOf(c(56.0, 40.0), c(56.0, 40.1)),
                    )
                )
                .size,
        )
    }

    @Test
    fun testBranchKeepsEverySegment() {
        val runs =
            listOf(
                listOf(c(55.0, 37.0), c(55.0, 37.1)),
                listOf(c(55.0, 37.1), c(55.0, 37.2)),
                listOf(c(55.0, 37.1), c(55.1, 37.1)),
            )
        val joined = RevealedLayer.stitch(runs)
        assertEquals(2, joined.size)
        assertEquals(3, segments(joined).size)
        assertEquals(segments(runs).toSet(), segments(joined).toSet())
    }

    @Test
    fun testLoopIsNotLost() {
        val runs =
            listOf(
                listOf(c(55.0, 37.0), c(55.0, 37.1)),
                listOf(c(55.0, 37.1), c(55.1, 37.1)),
                listOf(c(55.1, 37.1), c(55.0, 37.0)),
            )
        val joined = RevealedLayer.stitch(runs)
        assertEquals(segments(runs).toSet(), segments(joined).toSet())
        assertEquals(3, segments(joined).size)
    }

    @Test
    fun testLayerKeepsKilometresAndFineRunsWhileCoarseLinesShrink() {
        val road = (0..800).map { i -> c(55 + sin(i * 0.05) * 0.01, 37 + i * 0.004) }
        val runs = (0 until 800 step 20).map { road.subList(it, it + 21) }
        assertEquals(40, runs.size)
        val layer = RevealedLayer.build(runs, 0, null)
        assertEquals(40, layer.fine.size)
        assertTrue(layer.far.size < 40)
        assertTrue(layer.mid.size < 40)
        assertEquals(RevealedLayer.openedMetres(runs) / 1000, layer.openedKm, 1e-9)
    }
}

// iOS: RevealedLayer.swift. Additional regression cases for the shared anchor and stored bytes.
class RevealedLayerTests {
    private val origin = GeoPoint(45.0355, 38.9753)

    private fun north(metres: Double) = origin.copy(latitude = origin.latitude + metres / 111_320)

    @Test
    fun stitchingTreatsSignedZeroLikeSwift() {
        val runs =
            listOf(
                listOf(GeoPoint(-1.0, 1.0), GeoPoint(-0.0, 1.0)),
                listOf(GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0)),
            )
        assertEquals(1, RevealedLayer.stitch(runs).size)
        assertEquals(3, RevealedLayer.stitch(runs).single().size)
    }

    @Test
    fun subFiveMetreStepsAccumulateFromTheSameAnchor() {
        val run = listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0).map(::north)
        val layer = RevealedLayer.build(listOf(run), 17, null)
        assertEquals(12.0, layer.openedKm * 1000, 0.1)
        assertEquals(17, layer.cellCount)
        assertEquals(RevealedLayer.openedMetres(listOf(run)), layer.openedKm * 1000, 1e-9)
    }

    @Test
    fun rejectedTeleportAdvancesTheAnchor() {
        val run = listOf(north(0.0), north(2_000.0), north(2_020.0))
        assertEquals(20.0, RevealedLayer.openedMetres(listOf(run)), 0.1)
    }

    @Test
    fun centroidIsSnappedToAnOpenedSample() {
        val run = (0..10).map { north(it * 75.0) }
        val layer = RevealedLayer.build(listOf(run), 0, RegionAtlas.shared)
        assertTrue(layer.regionCentroids.isNotEmpty())
        for (centroid in layer.regionCentroids.values) assertTrue(centroid in run)
        assertEquals(origin, RevealedLayer.nearest(origin, emptyList()))
    }

    @Test
    fun separateRunsNeverInventConnectingDistance() {
        val runs = listOf(listOf(north(0.0), north(50.0)), listOf(north(5_000.0), north(5_050.0)))
        assertEquals(100.0, RevealedLayer.openedMetres(runs), 0.5)
    }

    @Test
    fun tileGeometryRoundTripsWithTheSharedFloatCodec() {
        val runs = listOf(listOf(north(0.0), north(75.0)), listOf(north(500.0), north(575.0)))
        val data = RevealedTile.encodeRuns(runs)
        val expected =
            runs.map {
                com.onezee.triptrack.model.Trip.decodePolyline(
                    com.onezee.triptrack.model.Trip.encodePolyline(it)
                )
            }
        assertEquals(expected, RevealedTile.decodeRuns(data))
        assertEquals(2, ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).int)
        val key = RevealGrid.tileKey(origin)
        val cells = listOf(RevealGrid.cell(origin))
        val tile = RevealedTile(key, RevealGrid.encode(cells, key), data, Instant.EPOCH)
        val layer = RevealedLayer.build(listOf(tile), null)
        assertEquals(1, layer.cellCount)
        assertEquals(expected, layer.fine)
    }

    @Test
    fun incompleteTileBlocksGiveNoPartialGeometry() {
        val data = RevealedTile.encodeRuns(listOf(listOf(north(0.0), north(75.0))))
        for (length in 0 until data.size) assertTrue(
            RevealedTile.decodeRuns(data.copyOf(length)).isEmpty()
        )
        assertTrue(RevealedTile.decodeRuns(byteArrayOf(-1, -1, -1, -1)).isEmpty())
    }
}
