package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.*
import org.junit.Test

// iOS: RevealBuilderTests.swift. Half of these are about the SECOND drive: sixty copies of one street
// smeared by GPS noise are the blot the 0.7.0 map was rewritten to avoid.
class RevealBuilderTests {
    private val start = GeoPoint(45.0355, 38.9753)

    private fun line(northMetres: Double, points: Int): List<GeoPoint> = (0 until points).map { i ->
        val t = i.toDouble() / (points - 1)
        GeoPoint(start.latitude + northMetres * t / 111_320.0, start.longitude)
    }

    private fun metres(a: GeoPoint, b: GeoPoint) = a.distanceFrom(b)

    private fun allCells(patches: Map<String, TilePatch>) = patches.values.sumOf { it.cells.size }

    private fun allRuns(patches: Map<String, TilePatch>) = patches.values.flatMap { it.runs }

    private fun drawnMetres(patches: Map<String, TilePatch>) =
        allRuns(patches).sumOf { run -> run.zipWithNext().sumOf { (a, b) -> metres(a, b) } }

    private fun shifted(coords: List<GeoPoint>, eastMetres: Double): List<GeoPoint> {
        val degrees = eastMetres / (111_320.0 * cos(start.latitude * PI / 180))
        return coords.map { GeoPoint(it.latitude, it.longitude + degrees) }
    }

    // The same path in the NEIGHBOURING cell by longitude: what GPS scatter does to a second drive.
    // The shift is the grid step, not "plus 25 metres", which would land in the next cell only
    // sometimes (the step differs slightly between tiles).
    private fun oneCellAside(coords: List<GeoPoint>): List<GeoPoint> {
        val step = RevealGrid.lonDegrees(RevealGrid.tileIndex(start))
        return coords.map { GeoPoint(it.latitude, it.longitude + step) }
    }

    @Test
    fun testStraightTripBecomesOneRunWithSeventyFiveMetreStep() {
        val patches = RevealBuilder.patches(line(10_000.0, 3)) { emptySet() }
        val runs = allRuns(patches)
        assertTrue(allCells(patches) >= 125)
        // One meridian crosses several geohash-5 tiles, so there are as many runs, but each is
        // continuous and together they cover the ten kilometres with one thread.
        for (run in runs) {
            for (i in 1 until run.size) {
                val step = metres(run[i - 1], run[i])
                assertTrue(step > 30, "step $step")
                assertTrue(step < 130, "a run steps about one cell, got $step")
            }
        }
        assertEquals(10_000.0, drawnMetres(patches), 400.0)
        assertTrue(runs.flatten().size > 120)
    }

    @Test
    fun testTripLandsInItsOwnTiles() {
        val patches = RevealBuilder.patches(line(12_000.0, 5)) { emptySet() }
        assertTrue(patches.size > 1, "twelve kilometres do not fit one geohash-5")
        for ((key, patch) in patches) {
            // A tile's cells must have a local index in it, or they do not survive storage.
            val encoded = RevealGrid.encode(patch.cells.toList(), key)
            assertEquals(patch.cells, RevealGrid.decode(encoded, key).toSet(), "a cell landed in a foreign tile")
            for (run in patch.runs) for (point in run.dropLast(1)) assertEquals(key, RevealGrid.tileKey(point))
        }
    }

    @Test
    fun testSecondPassOverClaimedRoadGivesNothing() {
        val coords = line(10_000.0, 3)
        val first = RevealBuilder.patches(coords) { emptySet() }
        assertFalse(first.isEmpty())
        val second = RevealBuilder.patches(coords) { key -> first[key]?.cells ?: emptySet() }
        assertTrue(second.isEmpty(), "nothing opens over the opened")
    }

    @Test
    fun testThereAndBackDrawsOneGeometry() {
        val out = line(5_000.0, 3)
        val patches = RevealBuilder.patches(out + out.reversed()) { emptySet() }
        val onlyOut = RevealBuilder.patches(out) { emptySet() }
        assertEquals(allCells(onlyOut), allCells(patches))
        assertEquals(5_000.0, drawnMetres(patches), 400.0, "there and back is one line")
    }

    @Test
    fun testHalfOpenedRoadIsCutAtTheBorder() {
        val coords = line(10_000.0, 3)
        val half = line(5_000.0, 3)
        val opened = RevealBuilder.patches(half) { emptySet() }
        val patches = RevealBuilder.patches(coords) { key -> opened[key]?.cells ?: emptySet() }
        val runs = allRuns(patches)
        assertFalse(runs.isEmpty())

        val border = half.last().latitude
        // One step of overlap is allowed on purpose: without it a cell-wide hole would stay at the
        // seam with the opened part.
        val overshoot = 2 * RevealGrid.cellDegrees
        for (run in runs) for (point in run) assertTrue(point.latitude > border - overshoot, "a run went into the opened")
        val far = runs.flatten().maxOf { it.latitude }
        assertTrue(far > coords.last().latitude - 0.002, "the second half is opened")
    }

    // The blot of the 15 September frames: the second drive slid across a cell border, got fresh
    // cells and drew its own thread 25 m from the first. Cells must still be claimed: the opened
    // world GROWS, only the line is not drawn.
    @Test
    fun testSecondPassAsideClaimsCellsButDrawsNothing() {
        val first = line(5_000.0, 3)
        val opened = RevealBuilder.patches(first) { emptySet() }
        val aside = oneCellAside(first)
        assertTrue(metres(first[0], aside[0]) < 80, "the shift is one cell of GPS scatter, not the next street")

        val patches = RevealBuilder.patches(aside) { key -> opened[key]?.cells ?: emptySet() }
        assertTrue(allRuns(patches).isEmpty(), "the second drive drew a parallel thread")
        assertTrue(allCells(patches) > 50, "cells must be claimed on the second drive too")
    }

    // "There and back" in ONE trip: the way back also slides into the neighbouring cell, and the rule
    // must look at what this very trip claimed, or it doubles itself.
    @Test
    fun testThereAndBackAsideIsStillOneLine() {
        val out = line(5_000.0, 3)
        val back = oneCellAside(out).reversed()
        val patches = RevealBuilder.patches(out + back) { emptySet() }
        assertEquals(5_000.0, drawnMetres(patches), 900.0, "there and back is one line, not two 25 m apart")
    }

    // The other half of the rule: a street 200 m away is a REAL new road and must still be drawn.
    @Test
    fun testANewStreetTwoHundredMetresAwayIsStillDrawn() {
        val first = line(5_000.0, 3)
        val opened = RevealBuilder.patches(first) { emptySet() }
        val street = shifted(first, eastMetres = 200.0)
        val patches = RevealBuilder.patches(street) { key -> opened[key]?.cells ?: emptySet() }
        assertEquals(5_000.0, drawnMetres(patches), 400.0, "the new street is not drawn")
    }

    @Test
    fun testRecordingGapDoesNotDrawAcrossTheCountry() {
        val a = line(400.0, 3)
        val b = listOf(GeoPoint(55.75, 37.62), GeoPoint(55.754, 37.62))
        val patches = RevealBuilder.patches(a + b) { emptySet() }
        for (run in allRuns(patches)) {
            for (i in 1 until run.size) {
                assertTrue(metres(run[i - 1], run[i]) < RevealBuilder.runGapMetres, "a run stitched a recording gap")
            }
        }
    }

    @Test
    fun testTooShortLineGivesNoPatches() {
        assertTrue(RevealBuilder.patches(emptyList()) { emptySet() }.isEmpty())
        assertTrue(RevealBuilder.patches(listOf(start)) { emptySet() }.isEmpty())
    }

    // The in-memory branch of RevealedLayerStore.layer(before:): a second preview over the same road
    // adds cells to nothing and lines to nothing, so the layer equals the first trip's.
    @Test
    fun testInMemoryLayerDrawsARepeatedRoadOnce() {
        val road = line(5_000.0, 3)
        val once = RevealBuilder.layer(sequenceOf(road))
        val twice = RevealBuilder.layer(sequenceOf(road, road))
        assertEquals(once.cellCount, twice.cellCount)
        assertEquals(once.fine.sumOf { it.size }, twice.fine.sumOf { it.size })
        assertEquals(once.openedKm, twice.openedKm, 1e-9)
        assertTrue(once.openedKm > 4.5)
    }
}
