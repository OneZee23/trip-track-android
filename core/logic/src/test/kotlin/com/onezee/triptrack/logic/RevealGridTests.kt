package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.*
import kotlin.random.Random
import kotlin.test.*
import org.junit.Test

// iOS: RevealGridTests.swift
class RevealGridTests {
    private val krasnodar = GeoPoint(45.0355, 38.9753)

    private fun centre(c: GeoPoint) = RevealGrid.center(RevealGrid.cell(c), RevealGrid.tileKey(c))

    private fun offset(c: GeoPoint, north: Double = 0.0, east: Double = 0.0) =
        GeoPoint(
            c.latitude + north / 111_320,
            c.longitude + east / (111_320 * cos(c.latitude * PI / 180)),
        )

    @Test
    fun testCellIsStableForOneCoordinate() {
        val first = RevealGrid.cell(krasnodar)
        repeat(10) { assertEquals(first, RevealGrid.cell(krasnodar)) }
    }

    @Test
    fun testThirtyMetresStaysInTheSameCell() {
        val c = centre(krasnodar)
        val cell = RevealGrid.cell(c)
        for (point in
            listOf(
                offset(c, north = 30.0),
                offset(c, north = -30.0),
                offset(c, east = 30.0),
                offset(c, east = -30.0),
            )) assertEquals(cell, RevealGrid.cell(point))
    }

    @Test
    fun testHundredAndTwentyMetresIsAnotherCell() {
        val c = centre(krasnodar)
        assertNotEquals(RevealGrid.cell(c), RevealGrid.cell(offset(c, north = 120.0)))
        assertNotEquals(RevealGrid.cell(c), RevealGrid.cell(offset(c, east = 120.0)))
    }

    @Test
    fun testCellIsNearSquareFarNorth() {
        val c = GeoPoint(68.97, 33.07)
        val width =
            RevealGrid.lonDegrees(RevealGrid.tileIndex(c)) * 111_320 * cos(c.latitude * PI / 180)
        assertEquals(RevealGrid.cellDegrees * 111_320, width, 4.0)
    }

    @Test
    fun testTileArithmeticMatchesGeohash() {
        val random = Random(42)
        repeat(200) {
            val c = GeoPoint(random.nextDouble(-80.0, 80.0), random.nextDouble(-179.0, 179.0))
            assertEquals(RevealGrid.tileKey(c), RevealGrid.tileKey(RevealGrid.tileIndex(c)))
        }
    }

    @Test
    fun testEncodeDecodeRoundTripEmpty() {
        val tile = RevealGrid.tileKey(krasnodar)
        val data = RevealGrid.encode(emptyList(), tile)
        assertTrue(data.isEmpty())
        assertEquals(emptyList(), RevealGrid.decode(data, tile))
    }

    @Test
    fun testEncodeDecodeRoundTripOneCell() {
        val tile = RevealGrid.tileKey(krasnodar)
        val cell = RevealGrid.cell(krasnodar)
        assertEquals(listOf(cell), RevealGrid.decode(RevealGrid.encode(listOf(cell), tile), tile))
    }

    @Test
    fun testEncodeDecodeRoundTripWholeTile() {
        val tile = RevealGrid.tileKey(krasnodar)
        val origin = RevealGrid.tileOrigin(tile)
        val cells =
            (0 until 64).flatMap { row ->
                (0 until 63).map { col -> RevealGrid.Cell(origin.row0 + row, origin.col0 + col) }
            }
        assertEquals(4_032, cells.size)
        assertEquals(cells.toSet(), RevealGrid.decode(RevealGrid.encode(cells, tile), tile).toSet())
    }

    @Test
    fun testDenseRowCostsAtMostTwoBytesPerCell() {
        val tile = RevealGrid.tileKey(krasnodar)
        val origin = RevealGrid.tileOrigin(tile)
        val cells = (0 until 64).map { RevealGrid.Cell(origin.row0 + 7, origin.col0 + it) }
        assertTrue(RevealGrid.encode(cells, tile).size.toDouble() / cells.size <= 2)
    }

    @Test
    fun testForeignCellIsDropped() {
        val tile = RevealGrid.tileKey(krasnodar)
        val origin = RevealGrid.tileOrigin(tile)
        assertTrue(
            RevealGrid.encode(listOf(RevealGrid.Cell(origin.row0 + 5_000, origin.col0)), tile)
                .isEmpty()
        )
    }

    @Test
    fun testWalkAlongOneKilometreOpensFourteenCellsWithoutGaps() {
        val start = centre(krasnodar)
        val cells = mutableListOf<RevealGrid.Cell>()
        RevealGrid.walkCells(listOf(start, offset(start, north = 1_000.0))) { cell, _ ->
            cells += cell
        }
        val unique = cells.map { it.row }.toSet().sorted()
        assertTrue(unique.size in 13..15)
        unique.zipWithNext().forEach { (a, b) -> assertEquals(1, b - a) }
        assertTrue(cells.size <= unique.size + 1)
        assertTrue(cells.map { it.col }.toSet().size <= 2)
    }

    @Test
    fun testWalkDoesNotSkipCellsOnADiagonal() {
        val start = centre(krasnodar)
        val cells = mutableListOf<RevealGrid.Cell>()
        RevealGrid.walkCells(listOf(start, offset(start, north = 700.0, east = 700.0))) { cell, _ ->
            cells += cell
        }
        cells.zipWithNext().forEach { (a, b) ->
            val dr = abs(b.row - a.row)
            val dc = abs(b.col - a.col)
            assertTrue(max(dr, dc) <= 1)
            assertTrue(dr + dc > 0)
        }
        assertTrue(cells.size > 9)
    }

    @Test
    fun testWalkSkipsATeleport() {
        var count = 0
        RevealGrid.walkCells(listOf(krasnodar, GeoPoint(55.75, 37.62))) { _, _ -> count++ }
        assertEquals(0, count)
    }
}
