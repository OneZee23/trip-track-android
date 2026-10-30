package com.onezee.triptrack.fogspike.data

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One line of revealed road: latitude and longitude interleaved. */
typealias Run = DoubleArray

/** The revealed world at three levels of detail, as `RevealedLayer` on iOS. */
class RevealedLayerLite(
    val fine: List<Run>,
    val mid: List<Run>,
    val far: List<Run>,
    val cellCount: Int,
) {
    fun lod(index: Int): List<Run> = when (index) {
        0 -> fine
        1 -> mid
        else -> far
    }

    fun segmentCount(index: Int): Int = lod(index).sumOf { it.size / 2 - 1 }
}

// A trimmed port of RevealGrid.walkCells, RevealBuilder.patches, RoadFog.decimate and
// RevealedLayer.stitch (iOS: Services/Reveal). It gives the fog candidates the geometry the
// iPhone app would hand them: one line per street, however many times it was driven. Regions,
// persistence and tile keys are left out; the full port with its tests belongs to wave 2.
class RevealLite {
    private val claimed = HashMap<Long, HashSet<Long>>()
    private val tileRuns = LinkedHashMap<Long, MutableList<Run>>()

    fun add(trip: DoubleArray) {
        val points = DoubleList()
        val cells = LongList()
        val tiles = LongList()
        walkCells(trip) { cell, lat, lon ->
            points.add(lat)
            points.add(lon)
            cells.add(cell)
            tiles.add(tileIndex(lat, lon))
        }
        val count = cells.size
        if (count < 2) return

        // "Add the cell" and "draw the run" are different questions: a cell is claimed when it is
        // new, but drawn only when none of its eight neighbours is taken, so a second drive down
        // the same street does not leave a parallel line 25 m away.
        val fresh = HashMap<Long, HashSet<Long>>()
        val mine = HashMap<Long, HashMap<Long, Int>>()
        val paint = BooleanArray(count)
        for (i in 0 until count) {
            val tile = tiles[i]
            val already = claimed[tile] ?: EMPTY
            val cell = cells[i]
            if (cell in already) continue
            if (!fresh.getOrPut(tile) { HashSet() }.add(cell)) continue
            val own = mine.getOrPut(tile) { HashMap() }
            paint[i] = !neighbourClaimed(cell, i, already, own)
            own[cell] = i
        }

        val steps = count - 1
        val broken = BooleanArray(steps)
        val draw = BooleanArray(steps)
        for (i in 0 until steps) {
            broken[i] = metres(points[2 * i], points[2 * i + 1], points[2 * i + 2], points[2 * i + 3]) > RUN_GAP_METRES
            draw[i] = paint[i] || paint[i + 1]
        }
        val extended = draw.copyOf()
        for (i in 0 until steps) {
            if (!draw[i]) continue
            for (step in 1..RUN_OVERLAP_STEPS) {
                if (i >= step) extended[i - step] = true
                if (i + step < steps) extended[i + step] = true
            }
        }
        for (i in 0 until steps) if (broken[i]) extended[i] = false

        val run = DoubleList()
        var runTile = Long.MIN_VALUE
        fun flush() {
            if (run.size >= 4 && runTile != Long.MIN_VALUE) {
                val thinned = decimate(run.toArray(), RUN_STEP_DEGREES)
                if (thinned.size >= 4) tileRuns.getOrPut(runTile) { mutableListOf() }.add(thinned)
            }
            run.clear()
            runTile = Long.MIN_VALUE
        }
        for (i in 0 until steps) {
            if (!extended[i]) {
                flush()
                continue
            }
            if (tiles[i] != runTile) {
                flush()
                run.add(points[2 * i])
                run.add(points[2 * i + 1])
                runTile = tiles[i]
            }
            run.add(points[2 * i + 2])
            run.add(points[2 * i + 3])
        }
        flush()

        for ((tile, set) in fresh) claimed.getOrPut(tile) { HashSet() }.addAll(set)
    }

    fun build(): RevealedLayerLite {
        val fine = tileRuns.values.flatten()
        val mid = stitch(fine.map { decimate(it, MID_DEGREES) }.filter { it.size >= 4 })
        val far = stitch(fine.map { decimate(it, FAR_DEGREES) }.filter { it.size >= 4 })
        return RevealedLayerLite(fine, mid, far, claimed.values.sumOf { it.size })
    }

    private fun neighbourClaimed(cell: Long, index: Int, claimed: Set<Long>, mine: Map<Long, Int>): Boolean {
        val row = (cell shr 32).toInt()
        val col = cell.toInt()
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val neighbour = cellKey(row + dr, col + dc)
                if (neighbour in claimed) return true
                val since = mine[neighbour]
                if (since != null && index - since > SELF_CLAIM_GAP_CELLS) return true
            }
        }
        return false
    }

    companion object {
        const val CELL_DEGREES = 0.000675
        internal const val TILE_LAT_SPAN = 180.0 / 4096.0
        internal const val TILE_LON_SPAN = 360.0 / 8192.0
        internal const val SAMPLE_STEP = 0.33
        internal const val MAX_SEGMENT_CELLS = 440.0
        internal const val MIN_COSINE = 0.05
        private const val RUN_GAP_METRES = 300.0
        private const val RUN_STEP_DEGREES = CELL_DEGREES * 0.6
        private const val SELF_CLAIM_GAP_CELLS = 6
        private const val RUN_OVERLAP_STEPS = 2
        const val MID_DEGREES = 250.0 / 111_320.0
        const val FAR_DEGREES = 1_000.0 / 111_320.0
        private val EMPTY = HashSet<Long>()

        fun build(trips: List<DoubleArray>): RevealedLayerLite {
            val builder = RevealLite()
            trips.forEach(builder::add)
            return builder.build()
        }

        internal fun tileIndex(lat: Double, lon: Double): Long {
            val latIndex = floor((lat.coerceIn(-90.0, 90.0) + 90) / TILE_LAT_SPAN).toLong().coerceIn(0, 4095)
            val lonIndex = floor((lon.coerceIn(-180.0, 180.0) + 180) / TILE_LON_SPAN).toLong().coerceIn(0, 8191)
            return (latIndex shl 32) or lonIndex
        }

        internal fun lonStep(tile: Long): Double {
            val centreLat = -90 + ((tile shr 32).toDouble() + 0.5) * TILE_LAT_SPAN
            return CELL_DEGREES / max(cos(centreLat * PI / 180), MIN_COSINE)
        }

        internal fun cellKey(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)

        internal fun cellOf(lat: Double, lon: Double): Long {
            val step = lonStep(tileIndex(lat, lon))
            return cellKey(floor(lat / CELL_DEGREES).toInt(), floor(lon / step).toInt())
        }

        internal inline fun walkCells(line: DoubleArray, body: (Long, Double, Double) -> Unit) {
            val n = line.size / 2
            if (n < 2) return
            var previous = Long.MIN_VALUE
            for (i in 1 until n) {
                val aLat = line[2 * i - 2]
                val aLon = line[2 * i - 1]
                val bLat = line[2 * i]
                val bLon = line[2 * i + 1]
                val step = lonStep(tileIndex(aLat, aLon))
                val span = max(abs(bLat - aLat) / CELL_DEGREES, abs(bLon - aLon) / step)
                if (!span.isFinite() || span > MAX_SEGMENT_CELLS) continue
                val steps = max(1, min(4096, ceil(span / SAMPLE_STEP).toInt()))
                for (s in 0..steps) {
                    val t = s.toDouble() / steps
                    val lat = aLat + (bLat - aLat) * t
                    val lon = aLon + (bLon - aLon) * t
                    val here = cellOf(lat, lon)
                    if (here == previous) continue
                    previous = here
                    body(here, lat, lon)
                }
            }
        }

        // iOS: RoadFog.decimate. Radial thinning keeps a point once it is minDegrees away from the
        // last kept one, and always keeps the end of the road.
        fun decimate(run: DoubleArray, minDegrees: Double): DoubleArray {
            val n = run.size / 2
            if (n <= 2) return run
            val threshold = minDegrees * minDegrees
            val out = DoubleList()
            out.add(run[0])
            out.add(run[1])
            for (i in 1 until n) {
                val dLat = run[2 * i] - out[out.size - 2]
                val dLon = run[2 * i + 1] - out[out.size - 1]
                if (dLat * dLat + dLon * dLon >= threshold) {
                    out.add(run[2 * i])
                    out.add(run[2 * i + 1])
                }
            }
            val endLat = run[run.size - 2]
            val endLon = run[run.size - 1]
            if (endLat != out[out.size - 2] || endLon != out[out.size - 1]) {
                out.add(endLat)
                out.add(endLon)
            }
            return if (out.size >= 4) out.toArray() else run
        }

        // iOS: RevealedLayer.stitch. Runs are cut at tile borders and the next run starts exactly
        // where the last one ended; joined, the mid and far levels draw one line instead of
        // thousands of two-point stubs.
        fun stitch(runs: List<Run>): List<Run> {
            if (runs.size <= 1) return runs
            val startsAt = HashMap<Pair<Double, Double>, MutableList<Int>>()
            runs.forEachIndexed { i, run -> startsAt.getOrPut(run[0] to run[1]) { mutableListOf() }.add(i) }
            val used = BooleanArray(runs.size)
            val hasPredecessor = BooleanArray(runs.size)
            for (run in runs) {
                startsAt[run[run.size - 2] to run[run.size - 1]]?.forEach { hasPredecessor[it] = true }
            }
            val out = ArrayList<Run>(runs.size)
            fun chain(first: Int) {
                used[first] = true
                val line = DoubleList()
                runs[first].forEach(line::add)
                while (true) {
                    val next = startsAt[line[line.size - 2] to line[line.size - 1]]?.firstOrNull { !used[it] } ?: break
                    used[next] = true
                    val run = runs[next]
                    for (k in 2 until run.size) line.add(run[k])
                }
                out.add(line.toArray())
            }
            for (i in runs.indices) if (!used[i] && !hasPredecessor[i]) chain(i)
            for (i in runs.indices) if (!used[i]) chain(i)
            return out
        }

        private fun metres(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
            val dy = (bLat - aLat) * 111_320.0
            val dx = (bLon - aLon) * 111_320.0 * cos((aLat + bLat) / 2 * PI / 180)
            return sqrt(dx * dx + dy * dy)
        }
    }
}

class DoubleList(capacity: Int = 64) {
    private var data = DoubleArray(capacity)
    var size = 0
        private set

    fun add(value: Double) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Double = data[index]

    fun clear() {
        size = 0
    }

    fun toArray(): DoubleArray = data.copyOf(size)
}

class LongList(capacity: Int = 64) {
    private var data = LongArray(capacity)
    var size = 0
        private set

    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    operator fun get(index: Int): Long = data[index]
}

class FloatList(capacity: Int = 64) {
    private var data = FloatArray(capacity)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
