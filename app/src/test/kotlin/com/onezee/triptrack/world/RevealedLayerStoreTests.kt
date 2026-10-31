// iOS: RevealedLayerStoreTests.swift (the cases of ingest, regions, layer and the rebuild latch) and
// RevealedLayerSyncTests.swift (ReconcileRequest.merged).
package com.onezee.triptrack.world

import android.content.Context
import androidx.room.Room
import com.onezee.triptrack.data.DataFlags
import com.onezee.triptrack.data.RevealedTileStore
import com.onezee.triptrack.data.RoomRevealedTileStore
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.feature.atlas.RevealedLayerStore
import com.onezee.triptrack.feature.atlas.RevealedLayerStore.ReconcileRequest
import com.onezee.triptrack.logic.RevealGrid
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RevealedLayerStoreTests {
    private lateinit var db: TripTrackDatabase
    private lateinit var tiles: RoomRevealedTileStore
    private val flags = DataFlags.InMemory()
    private val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("reveal-tests", Context.MODE_PRIVATE)
    private lateinit var store: RevealedLayerStore
    private val origin = GeoPoint(45.0355, 38.9753)
    private val now = Instant.ofEpochSecond(1_760_000_000)

    @Before fun open() {
        preferences.edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TripTrackDatabase::class.java).allowMainThreadQueries().build()
        tiles = RoomRevealedTileStore(db)
        store = RevealedLayerStore(tiles, flags, preferences, Dispatchers.Unconfined, regionId = { null })
    }

    @After fun close() { db.close() }

    private fun makeTrip(northMetres: Double, from: GeoPoint = origin, withPreview: Boolean = true, end: Instant = now): UUID = runBlocking {
        val coords = (0 until 3).map { i -> GeoPoint(from.latitude + northMetres * (i / 2.0) / 111_320.0, from.longitude) }
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = end.minusSeconds(1_800), endDate = end, distance = northMetres,
            previewPolyline = if (withPreview) Trip.encodePolyline(coords) else null))
        id
    }

    private fun makeDiagonalTrip(metres: Double): UUID = runBlocking {
        val leg = metres / sqrt(2.0)
        val lonScale = cos(origin.latitude * PI / 180)
        val steps = maxOf(2, (metres / 200).toInt())
        val coords = (0..steps).map { i ->
            val t = i.toDouble() / steps
            GeoPoint(origin.latitude + leg * t / 111_320.0, origin.longitude + leg * t / (111_320.0 * lonScale))
        }
        val id = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id = id, startDate = now.minusSeconds(3_600), endDate = now, previewPolyline = Trip.encodePolyline(coords)))
        id
    }

    private fun storedTileCount() = runBlocking { tiles.count() }
    private fun storedCells() = runBlocking { tiles.tiles().sumOf { tile -> RevealGrid.decode(tile.cells ?: byteArrayOf(), tile.key!!).size } }

    @Test fun ingestWritesTilesAndReturnsNewCells() = runBlocking {
        val added = store.ingestDelta(makeTrip(3_000.0)).openedCells
        assertTrue(added > 30, "three kilometres are dozens of 75 m cells")
        assertTrue(storedTileCount() > 0)
        assertEquals(added, storedCells())
    }

    @Test fun secondIngestOfTheSameTripOpensNothing() = runBlocking {
        val id = makeTrip(3_000.0)
        val first = store.ingestDelta(id).openedCells
        val tilesAfterFirst = storedTileCount()
        assertEquals(0, store.ingestDelta(id).openedCells)
        assertEquals(tilesAfterFirst, storedTileCount())
        assertEquals(first, storedCells(), "a second finish does not double the opened world")
    }

    @Test fun tripWithoutPreviewIsSkipped() = runBlocking {
        assertEquals(0, store.ingestDelta(makeTrip(3_000.0, withPreview = false)).openedCells)
        assertEquals(0, storedTileCount())
    }

    @Test fun twoTripsInOneTileMerge() = runBlocking {
        val first = store.ingestDelta(makeTrip(600.0)).openedCells
        val second = store.ingestDelta(makeTrip(600.0, from = GeoPoint(45.0355, 38.9773))).openedCells
        assertTrue(second > 0, "the next street is an opening too")
        val stored = tiles.tiles()
        assertEquals(1, stored.size, "two streets of one block are one tile")
        assertEquals(first + second, storedCells())
    }

    @Test fun deltaCountsKilometresAlongTheRunsItPainted() = runBlocking {
        val id = makeTrip(3_000.0)
        val delta = store.ingestDelta(id)
        assertTrue(delta.openedCells > 30)
        assertEquals(3.0, delta.openedKm, 0.09, "3 km of straight road are 3 km of new road")
        assertEquals(RevealedLayerStore.IngestDelta.none, store.ingestDelta(id), "a drive over opened ground opens nothing")
    }

    @Test fun diagonalStreetAgreesWithTheAtlasHeader() = runBlocking {
        val before = store.layer().openedKm
        val delta = store.ingestDelta(makeDiagonalTrip(10_000.0))
        val after = store.layer().openedKm
        assertEquals(10.0, delta.openedKm, 0.3, "ten kilometres along the diagonal")
        assertEquals(delta.openedKm, after - before, 0.3, "the Atlas header and the finish delta count the same")
    }

    @Test fun newRegionIsReportedOnceAndOnlyForFreshCells() = runBlocking {
        val counted = RevealedLayerStore(tiles, flags, preferences, Dispatchers.Unconfined, regionId = { "RU-KDA" })
        assertEquals(listOf("RU-KDA"), counted.ingestDelta(makeTrip(3_000.0)).newRegionIds)
        val second = counted.ingestDelta(makeTrip(600.0, from = GeoPoint(45.0355, 38.9773)))
        assertTrue(second.openedCells > 0, "the next street opened")
        assertTrue(second.newRegionIds.isEmpty(), "and the krai was not new any more")
    }

    @Test fun aRegionOpenedByAPulledTripIsNotNewLater() = runBlocking {
        val counted = RevealedLayerStore(tiles, flags, preferences, Dispatchers.Unconfined, regionId = { "RU-KDA" })
        // A pulled trip: its rows came in past the finish.
        makeTrip(3_000.0)
        val outcome = counted.reconcile(ReconcileRequest.Full)
        assertIs<RevealedLayerStore.ReconcileOutcome.Done>(outcome)
        assertTrue(outcome.cells > 0, "the pull opened something")
        val delta = counted.ingestDelta(makeTrip(600.0, from = GeoPoint(45.0355, 38.9773)))
        assertTrue(delta.openedCells > 0)
        assertTrue(delta.newRegionIds.isEmpty(), "the pull had opened the krai already")
    }

    @Test fun forgettingTheRegionsAfterAWipe() = runBlocking {
        val counted = RevealedLayerStore(tiles, flags, preferences, Dispatchers.Unconfined, regionId = { "RU-KDA" })
        counted.ingestDelta(makeTrip(3_000.0))
        assertTrue(preferences.contains(RevealedLayerStore.REGIONS_KEY))
        counted.forgetRegions()
        assertNull(preferences.getStringSet(RevealedLayerStore.REGIONS_KEY, null))
    }

    @Test fun layerReadsWhatIngestWrote() = runBlocking {
        store.ingestDelta(makeTrip(3_000.0))
        val layer = store.layer()
        assertFalse(layer.fine.isEmpty())
        assertFalse(layer.mid.isEmpty())
        assertTrue(layer.mid.size <= layer.fine.size)
        assertEquals(3.0, layer.openedKm, 0.4)
        assertTrue(layer.cellCount > 30)
    }

    @Test fun rebuildDoesNotLatchOnAnEmptyLibrary() = runBlocking {
        store.rebuildIfNeeded()
        assertFalse(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
        assertEquals(0, storedTileCount())
    }

    @Test fun rebuildLatchesAfterRealWork() = runBlocking {
        makeTrip(3_000.0)
        store.rebuildIfNeeded()
        assertTrue(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
        assertTrue(storedTileCount() > 0)
    }

    @Test fun armedLatchOverAnEmptyStoreReopensItself() = runBlocking {
        makeTrip(3_000.0)
        store.rebuildIfNeeded()
        tiles.deleteAll()
        flags.set(RevealedTileStore.REBUILD_FLAG_KEY, true)
        store.rebuildIfNeeded()
        assertTrue(storedTileCount() > 0, "an empty store under an armed latch has to rebuild")
        assertTrue(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
    }

    @Test fun armedLatchOverAnEmptyStoreWithNoTripsStaysQuiet() = runBlocking {
        flags.set(RevealedTileStore.REBUILD_FLAG_KEY, true)
        store.rebuildIfNeeded()
        assertEquals(0, storedTileCount())
        assertTrue(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
    }

    @Test fun rebuildRunsAfterTheLibraryComesBack() = runBlocking {
        store.rebuildIfNeeded()
        makeTrip(3_000.0)
        store.rebuildIfNeeded()
        assertTrue(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
        assertTrue(storedTileCount() > 0)
    }

    @Test fun rebuildIsSkippedOnceLatched() = runBlocking {
        makeTrip(3_000.0)
        store.rebuildIfNeeded()
        val cells = storedCells()
        makeTrip(3_000.0, from = GeoPoint(46.0, 39.5))
        store.rebuildIfNeeded()
        assertEquals(cells, storedCells(), "the build after an update runs once; new trips come by the finish")
    }

    @Test fun rebuildDoesNotLatchWhenPreviewsAreMissing() = runBlocking {
        makeTrip(3_000.0, withPreview = false)
        makeTrip(3_000.0, withPreview = false)
        store.rebuildIfNeeded()
        assertFalse(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
        assertEquals(0, storedTileCount())
    }

    @Test fun rebuildKeepsOldFogWhenPreviewsAreMissing() = runBlocking {
        val id = makeTrip(3_000.0)
        store.ingestDelta(id)
        val before = storedTileCount()
        db.tripDao().upsertTrip(db.tripDao().completedTrips().single().copy(previewPolyline = null))
        store.rebuildIfNeeded()
        assertEquals(before, storedTileCount(), "nothing to build means nothing to wipe either")
        assertFalse(flags.get(RevealedTileStore.REBUILD_FLAG_KEY))
    }

    @Test fun reconcileOfThePullsIdsOpensOnlyThem() = runBlocking {
        val pulled = makeTrip(3_000.0)
        makeTrip(3_000.0, from = GeoPoint(46.0, 39.5))
        val outcome = store.reconcile(ReconcileRequest.Ids(setOf(pulled)))
        assertEquals(RevealedLayerStore.ReconcileOutcome.Done(1, storedCells()), outcome)
        // An empty list is «the pull brought nothing»: zero work, not a full pass.
        assertEquals(RevealedLayerStore.ReconcileOutcome.Done(0, 0), store.reconcile(ReconcileRequest.Ids(emptySet())))
        // Idempotent: reading opened ground again adds no cell.
        val again = store.reconcile(ReconcileRequest.Full)
        assertIs<RevealedLayerStore.ReconcileOutcome.Done>(again)
        assertTrue(again.cells > 0, "the second trip was not pulled before")
        assertEquals(RevealedLayerStore.ReconcileOutcome.Done(2, 0), store.reconcile(ReconcileRequest.Full))
    }

    @Test fun mergedRequestsFoldIntoOne() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(ReconcileRequest.Ids(setOf(a)), ReconcileRequest.merged(null, ReconcileRequest.Ids(setOf(a))))
        assertEquals(ReconcileRequest.Full, ReconcileRequest.merged(ReconcileRequest.Ids(setOf(a)), ReconcileRequest.Full))
        assertEquals(ReconcileRequest.Full, ReconcileRequest.merged(ReconcileRequest.Full, ReconcileRequest.Ids(setOf(b))))
        assertEquals(ReconcileRequest.Ids(setOf(a, b)), ReconcileRequest.merged(ReconcileRequest.Ids(setOf(a)), ReconcileRequest.Ids(setOf(b))))
        assertTrue(ReconcileRequest.Ids(emptySet()).isEmpty)
        assertFalse(ReconcileRequest.Full.isEmpty)
    }

    @Test fun theLatchKeyIsV18() {
        assertEquals("reveal_rebuild_v18_done", RevealedTileStore.REBUILD_FLAG_KEY)
        assertEquals("reveal.regions.v2", RevealedLayerStore.REGIONS_KEY)
    }
}
