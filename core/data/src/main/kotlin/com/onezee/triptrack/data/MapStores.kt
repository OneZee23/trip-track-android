// iOS: TerritoryManager.swift (storage half), RoadCollectionManager.swift (storage half),
// RevealedLayerStore.swift (storage half)
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.dao.TripPolylineRow
import com.onezee.triptrack.data.db.entity.RevealedCellEntity
import com.onezee.triptrack.data.db.entity.RoadEntity
import com.onezee.triptrack.data.db.entity.VisitedGeohashEntity
import com.onezee.triptrack.model.RoadCard
import com.onezee.triptrack.model.RoadLevel
import com.onezee.triptrack.model.RoadRarity
import java.time.Instant
import java.util.UUID

/** Encodes a coordinate as a geohash of the store's precision; the encoder itself is `core:logic`'s. */
fun interface GeohashOf {
    fun encode(latitude: Double, longitude: Double): String
}

/** iOS: TerritoryManager, storage half. */
interface TerritoryStore {
    suspend fun visitedHashes(): Set<String>
    /** False for a tile already known. */
    suspend fun recordVisit(hash6: String): Boolean
    /** The whole library once; null when it did not run (latched, or no points yet). */
    suspend fun backfillIfNeeded(flags: DataFlags, geohash6: GeohashOf): List<String>?
    suspend fun rebuildFromTrips(geohash6: GeohashOf): Set<String>

    companion object {
        const val BACKFILL_KEY = "territory_backfill_done"
    }
}

/**
 * iOS: TerritoryManager, storage half. Geohash-6 tiles the person has driven through.
 *
 * Drafts never paint territory: the backfill reads points through `tripNotDraftPredicate` only, the
 * rebuild through the full completed-trip predicate, exactly as on iOS.
 */
class RoomTerritoryStore(
    private val db: TripTrackDatabase,
    private val clock: () -> Instant = Instant::now,
) : TerritoryStore {
    private val map = db.mapDao()

    override suspend fun visitedHashes(): Set<String> = map.visitedHashes().toSet()

    /** iOS: recordVisit, minus the in-memory cache the caller keeps. Returns false for a known tile. */
    override suspend fun recordVisit(hash6: String): Boolean = db.withTransaction {
        if (hash6 in map.visitedHashes()) return@withTransaction false
        val stamp = clock()
        map.insertVisited(listOf(VisitedGeohashEntity(hash6 = hash6, firstVisited = stamp, lastVisited = stamp, visitCount = 1)))
        true
    }

    /**
     * iOS: backfillIfNeeded. The whole library of points, once. No points is NOT «nothing to do»: on
     * the launch that lost a person's store it meant «the data has not come back yet», and latching
     * there kept the territory empty forever. Returns the tiles added, or null when it did not run.
     */
    override suspend fun backfillIfNeeded(flags: DataFlags, geohash6: GeohashOf): List<String>? {
        if (flags.get(TerritoryStore.BACKFILL_KEY)) return null
        val points = db.tripDao().territoryBackfillPoints()
        if (points.isEmpty()) return null
        val seen = visitedHashes().toMutableSet()
        val found = mutableListOf<Pair<String, Instant>>()
        for (point in points) {
            val hash = geohash6.encode(point.latitude, point.longitude)
            if (seen.add(hash)) found.add(hash to point.timestamp)
        }
        db.withTransaction {
            for (batch in found.chunked(BATCH)) {
                map.insertVisited(batch.map { (hash, at) -> VisitedGeohashEntity(hash6 = hash, firstVisited = at, lastVisited = at, visitCount = 1) })
            }
        }
        flags.set(TerritoryStore.BACKFILL_KEY, true)
        return found.map { it.first }
    }

    /** iOS: rebuildFromTrips, after a deletion: every tile again from live, confirmed, finished trips. */
    override suspend fun rebuildFromTrips(geohash6: GeohashOf): Set<String> {
        val hashes = db.tripDao().territoryRebuildPoints().mapTo(LinkedHashSet()) { geohash6.encode(it.latitude, it.longitude) }
        val stamp = clock()
        db.withTransaction {
            map.deleteAllVisited()
            for (batch in hashes.chunked(BATCH)) {
                map.insertVisited(batch.map { VisitedGeohashEntity(hash6 = it, firstVisited = stamp, lastVisited = stamp, visitCount = 1) })
            }
        }
        return hashes
    }

    private companion object {
        const val BATCH = 500
    }
}

/** iOS: RoadCollectionManager, storage half. */
interface RoadStore {
    /** Newest driven first; a row without an id is not a card. */
    suspend fun roads(): List<RoadCard>
    suspend fun roadsTouching(hashes: Collection<String>): List<RoadEntity>
    suspend fun insert(road: RoadEntity): RoadEntity
    suspend fun update(road: RoadEntity)
}

/**
 * iOS: RoadCollectionManager, storage half. A local collection, never restored by sync. Matching a
 * trip to a road (fingerprints, neighbours, similarity) is gamification logic and lives elsewhere.
 */
class RoomRoadStore(private val db: TripTrackDatabase) : RoadStore {
    private val map = db.mapDao()

    /** iOS: fetchRoads. Newest driven first; a row without an id is not a card. */
    override suspend fun roads(): List<RoadCard> = map.roads().mapNotNull { e ->
        val id = e.id ?: return@mapNotNull null
        RoadCard(
            id = id,
            name = e.name ?: "Road",
            rarity = RoadRarity.fromRawValue(e.rarity ?: "common") ?: RoadRarity.common,
            level = RoadLevel.fromRawValue(e.level) ?: RoadLevel.discovered,
            timesDriven = e.timesDriven,
            distanceKm = e.distanceKm,
            geohashSequence = (e.geohashSequence ?: "").split(",").filter { it.isNotEmpty() },
            firstDriven = e.firstDriven ?: Instant.now(),
            lastDriven = e.lastDriven ?: Instant.now(),
        )
    }

    /** Roads that start or end in one of the cells, filtered in the database before matching. */
    override suspend fun roadsTouching(hashes: Collection<String>): List<RoadEntity> =
        if (hashes.isEmpty()) emptyList() else hashes.toList().chunked(250).flatMap { map.roadsTouching(it) }.distinctBy { it.pk }

    override suspend fun insert(road: RoadEntity): RoadEntity = road.copy(pk = map.insertRoad(road))

    override suspend fun update(road: RoadEntity) {
        map.updateRoad(road)
    }
}

/** iOS: RevealedLayerStore, storage half. */
interface RevealedTileStore {
    suspend fun tiles(): List<RevealedCellEntity>
    suspend fun tile(key: String): RevealedCellEntity?
    suspend fun keys(): List<String>
    suspend fun upsert(rows: List<RevealedCellEntity>)
    suspend fun count(): Int
    suspend fun deleteAll()
    /** Completed, live, confirmed trips, oldest first; optionally cut by end date and by the pull's ids. */
    suspend fun previews(endedBefore: Instant? = null, ids: Set<UUID>? = null): List<TripPolylineRow>

    companion object {
        /** Version of the SHAPE of what is stored, not of the schema (see RevealedLayerStore). */
        const val REBUILD_FLAG_KEY = "reveal_rebuild_v18_done"
    }
}

/**
 * iOS: RevealedLayerStore, storage half: one row per geohash-5 tile of the opened world, and the
 * preview read the fog is built from. Building and merging patches (RevealBuilder, RevealGrid) is
 * the fog module's. The opened world is CUMULATIVE: deleting a trip never shrinks it.
 */
class RoomRevealedTileStore(private val db: TripTrackDatabase) : RevealedTileStore {
    private val map = db.mapDao()

    override suspend fun tiles(): List<RevealedCellEntity> = map.revealedTiles()

    override suspend fun tile(key: String): RevealedCellEntity? = map.revealedTile(key)

    override suspend fun keys(): List<String> = map.revealedKeys()

    /** One unique row per key: an existing tile is rewritten in place, never duplicated. */
    override suspend fun upsert(rows: List<RevealedCellEntity>) {
        if (rows.isEmpty()) return
        db.withTransaction {
            map.upsertRevealedTiles(rows.map { row ->
                val key = row.key ?: return@map row
                val existing = map.revealedTile(key)
                if (existing != null) row.copy(pk = existing.pk) else row.copy(pk = 0)
            })
        }
    }

    override suspend fun count(): Int = map.countRevealedTiles()

    override suspend fun deleteAll() {
        map.deleteAllRevealed()
    }

    /**
     * iOS: RevealedLayerStore.previewRequest. Completed, live, confirmed trips only (a draft never
     * opens fog, neither by the build nor by «the world on a date»), oldest first, because who drove
     * first owns the geometry. `ids` limits the read to what a pull brought.
     */
    override suspend fun previews(endedBefore: Instant?, ids: Set<UUID>?): List<TripPolylineRow> {
        val dao = db.tripDao()
        if (ids == null) return if (endedBefore == null) dao.revealPreviews() else dao.revealPreviews(endedBefore)
        // Chunked reads lose the global order and the window; both are restored from the rows.
        return ids.toList().chunked(500).flatMap { dao.revealRows(it) }
            .filter { row -> endedBefore == null || row.endDate?.let { it <= endedBefore } == true }
            .sortedBy { it.startDate }
            .map { TripPolylineRow(it.id, it.previewPolyline) }
    }
}
