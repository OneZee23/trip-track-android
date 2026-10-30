// iOS: LocalDataWipe.swift (no iOS test class; the rules are its comments), BackfillLatchTests.swift
// (territory half), RevealedLayerStoreTests.swift (store half: latch key, previews, wipe)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.DiscoveryEntity
import com.onezee.triptrack.data.db.entity.GeocodeCacheEntity
import com.onezee.triptrack.data.db.entity.JourneyEntity
import com.onezee.triptrack.data.db.entity.PlaceEntity
import com.onezee.triptrack.data.db.entity.PlacePassEntity
import com.onezee.triptrack.data.db.entity.RevealedCellEntity
import com.onezee.triptrack.data.db.entity.RoadEntity
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.db.entity.VehiclePhotoEntity
import com.onezee.triptrack.data.db.entity.VisitedGeohashEntity
import java.time.Instant
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class LocalDataWipeTests : RoomTestCase() {
    private val now = Instant.ofEpochSecond(1_760_000_000)

    /** «Delete account — irreversibly, everywhere»: every table of the person's data, relationless ones by name. */
    @Test
    fun testWipeErasesEveryTableExceptThePreferences() = test {
        val tripId = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now))
        db.tripDao().insertTrackPoints(listOf(TrackPointEntity(id = UUID.randomUUID(), timestamp = now, tripId = tripId)))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = UUID.randomUUID(), timestamp = now, tripId = tripId))
        db.tripDao().upsertCheckpoint(TripCheckpointEntity(id = UUID.randomUUID(), timestamp = now, tripId = tripId))
        val car = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = car))
        db.vehicleDao().upsertPhoto(VehiclePhotoEntity(id = UUID.randomUUID(), vehicleId = car, timestamp = now))
        db.journeyDao().upsert(JourneyEntity(id = UUID.randomUUID(), startDate = now))
        db.placeDao().upsertPlace(PlaceEntity(id = UUID.randomUUID(), cell = "szgs0u4", createdAt = now))
        db.placeDao().insertPasses(listOf(PlacePassEntity(id = UUID.randomUUID(), placeId = UUID.randomUUID(), tripId = tripId, timestamp = now)))
        db.mapDao().insertVisited(listOf(VisitedGeohashEntity(hash6 = "szgs0u")))
        db.mapDao().insertRoad(RoadEntity(id = UUID.randomUUID()))
        db.mapDao().upsertRevealedTiles(listOf(RevealedCellEntity(key = "szgs0")))
        db.discoveryDao().insertIfAbsent(listOf(DiscoveryEntity(id = UUID.randomUUID(), foundAt = now, key = "k", kind = "riddle", symbol = "seal", tripId = tripId)))
        db.mapDao().upsertGeocode(GeocodeCacheEntity(cachedAt = now, geohash5 = "szgs0", locality = "Джубга"))
        db.settingsDao().upsert(UserSettingsEntity(id = localUserId, themeMode = "light", language = "en"))
        val tripFile = assertNotNull(tripPhotos.save(byteArrayOf(1), tripId))
        val carFile = assertNotNull(vehiclePhotos.save(byteArrayOf(2)))
        val flags = DataFlags.InMemory().apply { set(RevealedTileStore.REBUILD_FLAG_KEY, true) }
        var hooks = 0

        LocalDataWipe(db, tripPhotos, vehiclePhotos, flags, listOf(suspend { hooks += 1 })).run()

        for (table in listOf(
            "TripEntity", "TrackPointEntity", "TripPhotoEntity", "TripCheckpointEntity", "VehicleEntity", "VehiclePhotoEntity",
            "JourneyEntity", "PlaceEntity", "PlacePassEntity", "VisitedGeohashEntity", "RoadEntity", "RevealedCellEntity",
            "DiscoveryEntity", "GeocodeCacheEntity",
        )) {
            assertEquals(0, countRows(table), "$table survived the wipe")
        }
        val prefs = assertNotNull(db.settingsDao().settings(), "theme, language and units are the app's, not the person's")
        assertEquals("light", prefs.themeMode)
        assertFalse(tripPhotos.exists(tripFile))
        assertFalse(vehiclePhotos.exists(carFile))
        assertFalse(flags.get(RevealedTileStore.REBUILD_FLAG_KEY), "trips returning by sync must get their fog again")
        assertEquals(1, hooks)
    }

    // MARK: BackfillLatchTests, territory half

    private val geohash = GeohashOf { lat, lon -> "%.2f,%.2f".format(java.util.Locale.ROOT, lat, lon) }

    /** No points is «not yet», not «nothing to do»: latching here keeps the territory empty forever. */
    @Test
    fun testTerritoryBackfillDoesNotLatchWithoutTrackPoints() = test {
        val flags = DataFlags.InMemory()
        assertNull(RoomTerritoryStore(db).backfillIfNeeded(flags, geohash))
        assertFalse(flags.get(TerritoryStore.BACKFILL_KEY), "no points yet is not the same as no territory")
    }

    @Test
    fun testTerritoryBackfillLatchesOncePointsExist() = test {
        val tripId = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now))
        db.tripDao().insertTrackPoints(
            (0 until 3).map { TrackPointEntity(id = UUID.randomUUID(), latitude = 55.75 + it * 0.01, longitude = 37.61 + it * 0.01, timestamp = now, tripId = tripId) },
        )
        val flags = DataFlags.InMemory()

        val added = RoomTerritoryStore(db).backfillIfNeeded(flags, geohash)

        assertEquals(3, added?.size)
        assertTrue(flags.get(TerritoryStore.BACKFILL_KEY))
        assertNull(RoomTerritoryStore(db).backfillIfNeeded(flags, geohash), "latched work does not run twice")
        assertEquals(3, RoomTerritoryStore(db).visitedHashes().size)
    }

    @Test
    fun testTerritoryRebuildUsesOnlyLiveConfirmedFinishedTrips() = test {
        val live = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now))
        val deleted = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now, syncStatus = pendingDelete))
        val recording = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = null))
        for ((index, id) in listOf(live, deleted, recording).withIndex()) {
            db.tripDao().insertTrackPoints(listOf(TrackPointEntity(id = UUID.randomUUID(), latitude = 50.0 + index, longitude = 30.0, timestamp = now, tripId = id)))
        }
        db.mapDao().insertVisited(listOf(VisitedGeohashEntity(hash6 = "stale")))

        assertEquals(setOf("50.00,30.00"), RoomTerritoryStore(db).rebuildFromTrips(geohash))
        assertEquals(setOf("50.00,30.00"), RoomTerritoryStore(db).visitedHashes())
    }

    // MARK: RevealedLayerStoreTests, store half

    @Test
    fun testRebuildLatchKeyIsV18() {
        assertEquals("reveal_rebuild_v18_done", RevealedTileStore.REBUILD_FLAG_KEY)
    }

    @Test
    fun testTilesAreUniqueByKeyAndWipedWithTheirLatch() = test {
        val tiles = RoomRevealedTileStore(db)
        tiles.upsert(listOf(RevealedCellEntity(key = "szgs0", cells = byteArrayOf(1), cellCount = 1, updatedAt = now)))
        tiles.upsert(listOf(RevealedCellEntity(key = "szgs0", cells = byteArrayOf(1, 2), cellCount = 2, updatedAt = now)))

        assertEquals(1, tiles.count(), "one row per tile key")
        val stored = assertNotNull(tiles.tile("szgs0"))
        assertContentEquals(byteArrayOf(1, 2), stored.cells)
        assertEquals(2, stored.cellCount)
        assertEquals(1.toShort(), stored.version)

        tiles.deleteAll()
        assertEquals(0, tiles.count())
    }

    /** Oldest first (who drove first owns the geometry), finished and live only, cut by date or by the pull's ids. */
    @Test
    fun testPreviewsAreOrderedAndFiltered() = test {
        val late = insert(TripEntity(id = UUID.randomUUID(), startDate = now.plusSeconds(200), endDate = now.plusSeconds(300), previewPolyline = byteArrayOf(2)))
        val early = insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now.plusSeconds(100), previewPolyline = byteArrayOf(1)))
        insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = null, previewPolyline = byteArrayOf(3)))
        insert(TripEntity(id = UUID.randomUUID(), startDate = now, endDate = now, syncStatus = pendingDelete, previewPolyline = byteArrayOf(4)))
        val tiles = RoomRevealedTileStore(db)

        assertEquals(listOf(early, late), tiles.previews().map { it.id })
        assertEquals(listOf(early), tiles.previews(endedBefore = now.plusSeconds(150)).map { it.id })
        assertEquals(listOf(early, late), tiles.previews(ids = setOf(late, early)).map { it.id })
        assertEquals(listOf(early), tiles.previews(endedBefore = now.plusSeconds(150), ids = setOf(late, early)).map { it.id })
        assertTrue(tiles.previews(ids = emptySet()).isEmpty())
    }
}
