// iOS: RevealedLayerStore.swift, RevealedLayerSync.swift
package com.onezee.triptrack.feature.atlas

import android.content.SharedPreferences
import androidx.core.content.edit
import com.onezee.triptrack.data.DataFlags
import com.onezee.triptrack.data.RevealedTileStore
import com.onezee.triptrack.data.db.entity.RevealedCellEntity
import com.onezee.triptrack.location.world.RevealIngest
import com.onezee.triptrack.logic.RegionAtlas
import com.onezee.triptrack.logic.RevealBuilder
import com.onezee.triptrack.logic.RevealGrid
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.logic.RevealedTile
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.TilePatch
import com.onezee.triptrack.logic.decodePolyline
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS: RevealedLayerStore.swift. The opened world in storage: a geohash-5 tile per row, cells
 * delta-coded, corridors as polylines. It grows incrementally: a finished trip adds only what was not
 * open before, and the Atlas reads what is ready instead of rebuilding the library. The source is
 * ALWAYS a trip's preview polyline; raw points never come to the map.
 *
 * The opened world is CUMULATIVE: deleting a trip never shrinks the fog's holes («где был, там был»).
 *
 * Four doors write here, as on iOS: the finish ([ingest]), the one-off build after an update
 * ([rebuildIfNeeded]), the reconcile after a pull ([reconcile]) and the wipe ([forgetRegions] after
 * LocalDataWipe). Room invalidations of the tile table tell the Atlas to reload.
 */
class RevealedLayerStore(
    private val tiles: RevealedTileStore,
    private val flags: DataFlags,
    /** Where the regions the opened world touched are remembered; iOS UserDefaults `reveal.regions.v2`. */
    private val preferences: SharedPreferences,
    private val worker: CoroutineDispatcher,
    private val regionId: (GeoPoint) -> String? = { RegionAtlas.shared.region(containing = it)?.id },
    private val clock: () -> Instant = Instant::now,
) : RevealIngest {
    // iOS: MapViewModel.attachDiscoveries. The finish result uses this delta, never a second track count.
    private val mutableLastIngest = MutableStateFlow<Pair<UUID, IngestDelta>?>(null)
    val lastIngest = mutableLastIngest.asStateFlow()
    /** iOS ReconcileRequest: what a reconcile pass reads. No time window, see the iOS file. */
    sealed interface ReconcileRequest {
        data object Full : ReconcileRequest
        data class Ids(val ids: Set<UUID>) : ReconcileRequest

        val isEmpty: Boolean get() = this is Ids && ids.isEmpty()

        companion object {
            /** A full pass swallows everything; id lists are joined. */
            fun merged(queued: ReconcileRequest?, incoming: ReconcileRequest): ReconcileRequest = when {
                queued == null -> incoming
                queued is Full || incoming is Full -> Full
                else -> Ids((queued as Ids).ids + (incoming as Ids).ids)
            }
        }
    }

    sealed interface ReconcileOutcome {
        /** Not lost: the running pass does it right after itself. */
        data object Queued : ReconcileOutcome
        data class Done(val trips: Int, val cells: Int) : ReconcileOutcome
    }

    /** iOS IngestDelta: what ONE trip opened. Kilometres are the length of the runs it drew. */
    data class IngestDelta(val openedCells: Int, val openedKm: Double, val newRegionIds: List<String>) {
        companion object {
            val none = IngestDelta(0, 0.0, emptyList())
        }
    }

    // One writer at a time: the iOS store runs everything on its one background context.
    private val writer = Mutex()
    private val queueLock = Any()
    private var reconciling = false
    private var queued: ReconcileRequest? = null

    suspend fun layer(atlas: RegionAtlas? = null): RevealedLayer = withContext(worker) {
        RevealedLayer.build(tiles.tiles().mapNotNull(::tile), atlas)
    }

    override suspend fun ingest(tripId: UUID) {
        mutableLastIngest.value = tripId to ingestDelta(tripId)
    }

    /**
     * Counted by the cells INSERTED, not by the track: a region reached a second time is not new, and
     * a second lap of the yard adds no kilometres.
     */
    suspend fun ingestDelta(tripId: UUID): IngestDelta = withContext(worker) {
        writer.withLock {
            val preview = tiles.previews(ids = setOf(tripId)).firstOrNull()?.previewPolyline ?: return@withLock IngestDelta.none
            val coords = Trip.decodePolyline(preview)
            if (coords.size < 2) return@withLock IngestDelta.none
            // Regions «before» are read BEFORE the merge: afterwards the new cells are already stored.
            val before = knownRegionIds()
            val patches = patches(coords, HashMap())
            val merged = merge(patches)
            val fresh = regionIds(merged.samples) - before
            if (fresh.isNotEmpty()) rememberRegionIds(before + fresh)
            val metres = RevealedLayer.openedMetres(patches.values.flatMap { it.runs })
            IngestDelta(merged.added, metres / 1000, fresh.sorted())
        }
    }

    /**
     * The build from the whole library, once after an update. From ZERO: the flag's number is the
     * SHAPE of what is stored, and a new rule does not reach runs already claimed. Wiping comes AFTER
     * checking there is something to build, and the latch is set only after real work: a launch that
     * lost its library («nothing yet», a pull still to come) must not lock the map without fog.
     */
    suspend fun rebuildIfNeeded(): Unit = withContext(worker) {
        writer.withLock {
            if (flags.get(RevealedTileStore.REBUILD_FLAG_KEY)) {
                // An EMPTY store under an armed latch reopens it: cells live in Room and the latch in
                // preferences, and the two can part (a recreated store, a manual cleanup).
                if (tiles.count() != 0) return@withLock
            }
            val usable = tiles.previews().filter { (it.previewPolyline?.size ?: 0) >= USABLE_BYTES }
            // «Usable by length» is not yet «opens something»: ask BEFORE wiping.
            if (usable.none { opensSomething(it.previewPolyline) }) return@withLock
            tiles.deleteAll()
            var total = 0
            for (batch in usable.chunked(REBUILD_BATCH)) {
                total += mergeBatch(batch.mapNotNull { it.previewPolyline })
                yield()
            }
            // Zero opened is not «the build ran»: latching it would lock the phone without fog.
            if (total > 0) flags.set(RevealedTileStore.REBUILD_FLAG_KEY, true)
        }
    }

    /**
     * After a pull: trips from another phone arrive straight in the database, past the finish and the
     * one-off build. [request] is WHAT to read: the pull's own ids, or everything when unknown.
     * Idempotent: reading opened ground again adds no cell. A request arriving during a pass is
     * folded into the queue and run right after it: that pass picked its previews before these rows
     * were written.
     */
    suspend fun reconcile(request: ReconcileRequest = ReconcileRequest.Full): ReconcileOutcome {
        synchronized(queueLock) {
            queued = ReconcileRequest.merged(queued, request)
            if (reconciling) return ReconcileOutcome.Queued
            reconciling = true
        }
        var trips = 0
        var cells = 0
        while (true) {
            val next = synchronized(queueLock) {
                val value = queued
                if (value == null) reconciling = false
                queued = null
                value
            } ?: break
            val step = runPass(next)
            trips += step.first
            cells += step.second
        }
        return ReconcileOutcome.Done(trips, cells)
    }

    private suspend fun runPass(request: ReconcileRequest): Pair<Int, Int> = withContext(worker) {
        if (request.isEmpty) return@withContext 0 to 0
        val ids = (request as? ReconcileRequest.Ids)?.ids
        val usable = tiles.previews(ids = ids).filter { (it.previewPolyline?.size ?: 0) >= USABLE_BYTES }
        if (usable.isEmpty()) return@withContext 0 to 0
        var total = 0
        writer.withLock {
            for (batch in usable.chunked(REBUILD_BATCH)) {
                total += mergeBatch(batch.mapNotNull { it.previewPolyline })
                yield()
            }
        }
        usable.size to total
    }

    /**
     * After LocalDataWipe (which empties the table and clears the rebuild latch): the regions are
     * derived from the cells, and keeping them would greet no region as new after «delete everywhere».
     */
    fun forgetRegions() {
        preferences.edit { remove(REGIONS_KEY) }
    }

    private suspend fun mergeBatch(previews: List<ByteArray>): Int {
        var opened = 0
        val samples = ArrayList<Pair<String, RevealGrid.Cell>>()
        val cache = HashMap<String, MutableSet<RevealGrid.Cell>>()
        for (polyline in previews) {
            val coords = Trip.decodePolyline(polyline)
            if (coords.size < 2) continue
            val patches = patches(coords, cache)
            for ((key, patch) in patches) cache.getOrPut(key) { mutableSetOf() }.addAll(patch.cells)
            val merged = merge(patches)
            opened += merged.added
            samples += merged.samples
        }
        noteRegions(samples)
        return opened
    }

    /**
     * RevealBuilder asks for the claimed cells of every tile its walk touches, and the walk does not
     * depend on the answers: a first pass names the tiles, storage is read for the ones not cached,
     * the second pass builds. [cache] holds what this batch already merged.
     */
    private suspend fun patches(coords: List<GeoPoint>, cache: MutableMap<String, MutableSet<RevealGrid.Cell>>): Map<String, TilePatch> {
        val keys = LinkedHashSet<String>()
        RevealBuilder.patches(coords) { key -> keys += key; emptySet() }
        for (key in keys) if (key !in cache) cache[key] = claimed(key).toMutableSet()
        return RevealBuilder.patches(coords) { key -> cache[key].orEmpty() }
    }

    private class MergeResult(val added: Int, val samples: List<Pair<String, RevealGrid.Cell>>)

    /** One sample per tile: a real new cell on the driven road names the region right. */
    private suspend fun merge(patches: Map<String, TilePatch>): MergeResult {
        var added = 0
        val samples = ArrayList<Pair<String, RevealGrid.Cell>>()
        val now = clock()
        val rows = ArrayList<RevealedCellEntity>()
        for ((key, patch) in patches) {
            val existing = tiles.tile(key)
            val cells = LinkedHashSet(existing?.cells?.let { RevealGrid.decode(it, key) }.orEmpty())
            val before = cells.size
            for (cell in patch.cells.sortedWith(compareBy({ it.row }, { it.col }))) {
                if (cells.add(cell) && samples.lastOrNull()?.first != key) samples += key to cell
            }
            added += cells.size - before
            val runs = existing?.geometry?.let { RevealedTile.decodeRuns(it) }.orEmpty() + patch.runs
            rows += RevealedCellEntity(
                pk = existing?.pk ?: 0,
                cellCount = cells.size,
                cells = RevealGrid.encode(cells.toList(), key),
                geometry = RevealedTile.encodeRuns(runs),
                key = key,
                updatedAt = now,
                version = existing?.version ?: 1,
            )
        }
        tiles.upsert(rows)
        return MergeResult(added, samples)
    }

    private suspend fun claimed(key: String): Set<RevealGrid.Cell> =
        tiles.tile(key)?.cells?.let { RevealGrid.decode(it, key).toSet() }.orEmpty()

    /**
     * The regions the opened world already touched. No key means a phone updated from a version that
     * did not count them: the set is SEEDED from the stored tiles, one atlas question per tile, so a
     * person with years of driving is not greeted with all their regions as new.
     */
    private suspend fun knownRegionIds(): Set<String> {
        preferences.getStringSet(REGIONS_KEY, null)?.let { return it.toSet() }
        val seeded = tiles.keys().mapNotNull { regionId(GeohashEncoder.centerCoordinate(it)) }.toSet()
        rememberRegionIds(seeded)
        return seeded
    }

    private fun rememberRegionIds(ids: Set<String>) {
        preferences.edit { putStringSet(REGIONS_KEY, ids.toSortedSet()) }
    }

    private fun regionIds(samples: List<Pair<String, RevealGrid.Cell>>): Set<String> =
        samples.mapNotNull { (tile, cell) -> regionId(RevealGrid.center(cell, tile)) }.toSet()

    private suspend fun noteRegions(samples: List<Pair<String, RevealGrid.Cell>>) {
        if (samples.isEmpty()) return
        val found = regionIds(samples)
        if (found.isEmpty()) return
        val known = knownRegionIds()
        if (known.containsAll(found)) return
        rememberRegionIds(known + found)
    }

    private fun tile(entity: RevealedCellEntity): RevealedTile? {
        val key = entity.key ?: return null
        return RevealedTile(key, entity.cells ?: byteArrayOf(), entity.geometry ?: byteArrayOf(), entity.updatedAt ?: Instant.EPOCH)
    }

    companion object {
        /** iOS rebuildBatch: trips per save of the build. */
        const val REBUILD_BATCH = 40

        /** iOS `(polyline?.count ?? 0) >= 16`: shorter is not a line. */
        const val USABLE_BYTES = 16

        /** iOS regionsKey. `v2`: the enclave geometry fix of 16 September re-seeds the set once. */
        const val REGIONS_KEY = "reveal.regions.v2"

        /** Opens anything at all: pure, against an empty world. */
        fun opensSomething(polyline: ByteArray?): Boolean {
            polyline ?: return false
            val coords = Trip.decodePolyline(polyline)
            if (coords.size < 2) return false
            return RevealBuilder.patches(coords) { emptySet() }.isNotEmpty()
        }
    }
}
