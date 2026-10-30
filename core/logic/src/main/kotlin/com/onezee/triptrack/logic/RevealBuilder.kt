package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint

// iOS: RevealBuilder.swift (TilePatch). `cells` are only the cells this trip opened first; `runs`
// are the lines drawn through them, about one cell per step.
class TilePatch(
    val cells: Set<RevealGrid.Cell> = emptySet(),
    val runs: List<List<GeoPoint>> = emptyList(),
) {
    val isEmpty: Boolean
        get() = cells.isEmpty() && runs.isEmpty()
}

// iOS: RevealBuilder.swift. "Claim and cut": sixty drives down one street leave ONE line, not
// sixty smeared by GPS noise into a blot. The first trip through a cell owns its geometry; a later
// trip on already opened asphalt brings neither cells nor runs, and its tile is absent from the
// answer altogether.
//
// Ownership is checked over the cell's NEIGHBOURHOOD: a cell is 75 m, and GPS scatter on one street
// is tens of metres, so a claimed cell guards the 3×3 square around it. Cells are still added one
// by one: the opened world grows, only the line is not drawn twice.
//
// Pure: everything it knows about the opened world comes through `claimed`, so the same build serves
// the finish of a trip (claimed cells from storage) and the in-memory "world on a date".
object RevealBuilder {
    // Two neighbouring walk points further apart than this are a stitch across a recording gap, not
    // a step along a road: the run breaks. Three cells with room for the diagonal.
    const val runGapMetres = 300.0

    // A walk already lies one point per cell; a step just under a cell only removes two touches in a
    // row at a cell border, never the shape of the street.
    const val runStepDegrees = RevealGrid.cellDegrees * 0.6

    // After how many walk cells the trip's OWN cell counts as someone else's. "There and back" in
    // one track otherwise draws two threads 25 m apart, but cell i-1 is a neighbour of cell i by
    // definition. Six cells (~450 m) is where "still on this street" ends and "came back by it" starts.
    const val selfClaimGapCells = 6

    // How many steps a run reaches into the opened world. With the neighbour rule a new street starts
    // drawing two cells away from an old one, and the overlap covers exactly that; two steps (~150 m)
    // lie inside the ±50 m corridor of the opened road, so they add no new geometry to the map.
    const val runOverlapSteps = 2

    /**
     * New cells and runs of one line against the opened world. `claimed` is asked once per tile key,
     * so a caller may read storage there.
     */
    fun patches(coords: List<GeoPoint>, claimed: (String) -> Set<RevealGrid.Cell>): Map<String, TilePatch> {
        if (coords.size < 2) return emptyMap()

        // The tile goes by NUMBER and its geohash-5 string is built once per tile (iOS 0.9.0): the
        // number and the string of a tile map one to one, so the keys handed back are the same.
        val points = ArrayList<GeoPoint>()
        val cells = ArrayList<RevealGrid.Cell>()
        val tiles = ArrayList<RevealGrid.TileIndex>()
        val tileKeys = HashMap<RevealGrid.TileIndex, String>()
        RevealGrid.walkCells(coords) { cell, point ->
            points += point
            cells += cell
            val tile = RevealGrid.tileIndex(point)
            tiles += tile
            if (tile !in tileKeys) tileKeys[tile] = RevealGrid.tileKey(point)
        }
        if (points.size < 2) return emptyMap()

        // "Add the cell" and "draw the run" are DIFFERENT questions, and that is the decision of the
        // whole build: a cell is claimed when it is fresh, but drawn only when none of its eight
        // neighbours is taken, by earlier trips or by this one long ago. A second dedup for the line
        // would be a second source of geometry; the hole and the line share one.
        val claimedCache = HashMap<RevealGrid.TileIndex, Set<RevealGrid.Cell>>()
        val mine = HashMap<RevealGrid.TileIndex, HashMap<RevealGrid.Cell, Int>>()
        val paint = BooleanArray(points.size)
        val freshCells = HashMap<RevealGrid.TileIndex, LinkedHashSet<RevealGrid.Cell>>()

        for (i in points.indices) {
            val tile = tiles[i]
            val already = claimedCache.getOrPut(tile) { claimed(tileKeys[tile] ?: "") }
            if (cells[i] in already) continue
            if (!freshCells.getOrPut(tile) { LinkedHashSet() }.add(cells[i])) continue
            val own = mine.getOrPut(tile) { HashMap() }
            paint[i] = !neighbourClaimed(cells[i], i, already, own)
            own[cells[i]] = i
        }

        val steps = points.size - 1
        val broken = BooleanArray(steps)
        val draw = BooleanArray(steps)
        for (i in 0 until steps) {
            broken[i] = points[i].distanceFrom(points[i + 1]) > runGapMetres
            draw[i] = paint[i] || paint[i + 1]
        }
        // Steps around a drawn one are drawn too: otherwise a hole would stay where this trip takes
        // the road over from the one that claimed it first.
        val extended = draw.copyOf()
        for (i in 0 until steps) {
            if (!draw[i]) continue
            for (step in 1..runOverlapSteps) {
                if (i >= step) extended[i - step] = true
                if (i + step < steps) extended[i + step] = true
            }
        }
        for (i in 0 until steps) if (broken[i]) extended[i] = false

        val runsByTile = HashMap<RevealGrid.TileIndex, MutableList<List<GeoPoint>>>()
        var run = ArrayList<GeoPoint>()
        var runTile: RevealGrid.TileIndex? = null
        fun flush() {
            val tile = runTile
            if (run.size > 1 && tile != null) {
                val thinned = tileLine(run, runStepDegrees)
                if (thinned.size > 1) runsByTile.getOrPut(tile) { mutableListOf() } += thinned
            }
            run = ArrayList()
            runTile = null
        }
        for (i in 0 until steps) {
            if (!extended[i]) {
                flush()
                continue
            }
            if (tiles[i] != runTile) {
                flush()
                run.add(points[i])
                runTile = tiles[i]
            }
            run.add(points[i + 1])
        }
        flush()

        // Merged, not assigned: should two indices ever name one geohash, the string-keyed code of
        // older builds pooled them, and so does this.
        val cellsByKey = LinkedHashMap<String, MutableSet<RevealGrid.Cell>>()
        val runsByKey = LinkedHashMap<String, MutableList<List<GeoPoint>>>()
        for (tile in tileKeys.keys) {
            val key = tileKeys.getValue(tile)
            val fresh = freshCells[tile].orEmpty()
            val runs = runsByTile[tile].orEmpty()
            if (fresh.isEmpty() && runs.isEmpty()) continue
            cellsByKey.getOrPut(key) { LinkedHashSet() }.addAll(fresh)
            runsByKey.getOrPut(key) { mutableListOf() }.addAll(runs)
        }
        return cellsByKey.keys.associateWith { key ->
            TilePatch(cellsByKey.getValue(key), runsByKey[key].orEmpty())
        }
    }

    /**
     * Whether any of the eight neighbours is taken, by earlier trips or by this one long enough ago
     * (`selfClaimGapCells`). Neighbours are looked up in the SAME tile: the longitude step of a cell
     * follows the cosine of its tile's centre, so cells of two tiles cannot be compared. At a tile
     * seam one cell does not see the neighbours across it; the error is under a cell and invisible
     * in the fog.
     */
    fun neighbourClaimed(
        cell: RevealGrid.Cell,
        index: Int,
        claimed: Set<RevealGrid.Cell>,
        mine: Map<RevealGrid.Cell, Int>,
    ): Boolean {
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val neighbour = RevealGrid.Cell(cell.row + dr, cell.col + dc)
                if (neighbour in claimed) return true
                val since = mine[neighbour]
                if (since != null && index - since > selfClaimGapCells) return true
            }
        }
        return false
    }

    // Radial thinning, like RoadFog.decimate and for the same reason: for a mask and for the zoom
    // where half the points are under a pixel, RDP costs more than its result is worth.
    fun tileLine(coords: List<GeoPoint>, stepDegrees: Double): List<GeoPoint> =
        RevealedLayer.decimate(coords, stepDegrees)

    /**
     * iOS: RevealedLayerStore.layer(before:), the in-memory branch. Previews oldest first (who drove
     * first owns the geometry), each patched against what the earlier ones claimed; nothing is
     * written anywhere. Iterates the whole library: call it off the main thread.
     */
    fun layer(previews: Sequence<List<GeoPoint>>, atlas: RegionAtlas? = null): RevealedLayer {
        val runs = ArrayList<List<GeoPoint>>()
        val claimedCells = HashMap<String, MutableSet<RevealGrid.Cell>>()
        for (coords in previews) {
            if (coords.size < 2) continue
            val patches = patches(coords) { key -> claimedCells[key] ?: emptySet() }
            for ((key, patch) in patches) {
                claimedCells.getOrPut(key) { HashSet() }.addAll(patch.cells)
                runs.addAll(patch.runs)
            }
        }
        return RevealedLayer.build(runs, claimedCells.values.sumOf { it.size }, atlas)
    }
}
