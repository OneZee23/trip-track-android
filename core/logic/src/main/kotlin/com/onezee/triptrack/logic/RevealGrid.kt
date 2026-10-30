package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import java.io.ByteArrayOutputStream
import kotlin.math.*

// iOS: RevealGrid.swift
object RevealGrid {
    const val cellDegrees = 0.000675
    const val tilePrecision = 5
    const val tileCells = 65
    const val tileLatSpan = 180.0 / 4096.0
    const val tileLonSpan = 360.0 / 8192.0
    const val indexStride = 128
    private const val minCosine = 0.05
    const val sampleStep = 0.33
    const val maxSegmentCells = 440.0

    // iOS: RevealGrid.swift
    data class Cell(val row: Int, val col: Int)

    // iOS: RevealGrid.swift
    data class TileIndex(val lat: Int, val lon: Int)

    // iOS: RevealGrid.swift (tileOrigin return value).
    data class TileOrigin(val row0: Int, val col0: Int, val lonStep: Double)

    fun tileIndex(coordinate: GeoPoint): TileIndex =
        TileIndex(
            floor((coordinate.latitude.coerceIn(-90.0, 90.0) + 90) / tileLatSpan)
                .toInt()
                .coerceIn(0, 4095),
            floor((coordinate.longitude.coerceIn(-180.0, 180.0) + 180) / tileLonSpan)
                .toInt()
                .coerceIn(0, 8191),
        )

    fun tileKey(coordinate: GeoPoint): String =
        GeohashEncoder.encode(coordinate.latitude, coordinate.longitude, tilePrecision)

    fun tileKey(index: TileIndex): String = tileKey(center(index))

    fun center(index: TileIndex): GeoPoint =
        GeoPoint(-90 + (index.lat + 0.5) * tileLatSpan, -180 + (index.lon + 0.5) * tileLonSpan)

    fun lonDegrees(tile: TileIndex): Double =
        cellDegrees / max(cos((-90 + (tile.lat + 0.5) * tileLatSpan) * PI / 180), minCosine)

    fun cell(coordinate: GeoPoint, tile: TileIndex = tileIndex(coordinate)): Cell =
        Cell(
            floor(coordinate.latitude / cellDegrees).toInt(),
            floor(coordinate.longitude / lonDegrees(tile)).toInt(),
        )

    fun center(cell: Cell, tile: String): GeoPoint =
        GeoPoint((cell.row + 0.5) * cellDegrees, (cell.col + 0.5) * tileOrigin(tile).lonStep)

    fun walkCells(line: List<GeoPoint>, body: (Cell, GeoPoint) -> Unit) {
        val first = line.firstOrNull() ?: return
        if (line.size == 1) {
            body(cell(first), first)
            return
        }
        var previous: Cell? = null
        for ((a, b) in line.zipWithNext()) {
            if (
                !a.latitude.isFinite() ||
                    !a.longitude.isFinite() ||
                    !b.latitude.isFinite() ||
                    !b.longitude.isFinite()
            )
                continue
            val span =
                max(
                    abs(b.latitude - a.latitude) / cellDegrees,
                    abs(b.longitude - a.longitude) / lonDegrees(tileIndex(a)),
                )
            if (!span.isFinite() || span > maxSegmentCells) continue
            val steps = ceil(span / sampleStep).toInt().coerceIn(1, 4096)
            for (step in 0..steps) {
                val t = step.toDouble() / steps
                val point =
                    GeoPoint(
                        a.latitude + (b.latitude - a.latitude) * t,
                        a.longitude + (b.longitude - a.longitude) * t,
                    )
                val here = cell(point)
                if (here == previous) continue
                previous = here
                body(here, point)
            }
        }
    }

    fun tileOrigin(tile: String): TileOrigin {
        val box = GeohashEncoder.decode(tile)
        val lonStep = cellDegrees / max(cos((box.minLat + box.maxLat) / 2 * PI / 180), minCosine)
        return TileOrigin(
            floor(box.minLat / cellDegrees).toInt(),
            floor(box.minLon / lonStep).toInt(),
            lonStep,
        )
    }

    fun encode(cells: List<Cell>, tile: String): ByteArray {
        val origin = tileOrigin(tile)
        val indices =
            cells
                .mapNotNull { cell ->
                    val dr = cell.row - origin.row0
                    val dc = cell.col - origin.col0
                    if (dr in 0 until indexStride && dc in 0 until indexStride)
                        dr * indexStride + dc
                    else null
                }
                .distinct()
                .sorted()
        val data = ByteArrayOutputStream()
        var previous = 0
        for (index in indices) {
            var rest = index - previous
            while (rest >= 0x80) {
                data.write((rest and 0x7f) or 0x80)
                rest = rest ushr 7
            }
            data.write(rest)
            previous = index
        }
        return data.toByteArray()
    }

    fun decode(data: ByteArray, tile: String): List<Cell> {
        val origin = tileOrigin(tile)
        val cells = mutableListOf<Cell>()
        var value = 0u
        var shift = 0
        var accumulated = 0u
        var first = true
        for (raw in data) {
            val byte = raw.toInt() and 0xff
            value = value or ((byte and 0x7f).toUInt() shl shift)
            if (byte and 0x80 != 0) {
                shift += 7
                if (shift > 28) return cells
                continue
            }
            accumulated = if (first) value else accumulated + value
            first = false
            cells +=
                Cell(
                    origin.row0 + (accumulated / indexStride.toUInt()).toInt(),
                    origin.col0 + (accumulated % indexStride.toUInt()).toInt(),
                )
            value = 0u
            shift = 0
        }
        return cells
    }
}
