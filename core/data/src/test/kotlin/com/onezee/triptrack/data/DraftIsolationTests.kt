// iOS: DraftIsolationTests.swift (the store half), TripConfirmationSchemaTests.swift (the store half)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.dao.WorldSql
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** A draft does not enter the world (spec 0.8.1 §3.2); the history reads drafts on their own. */
class DraftIsolationTests : RoomTestCase() {
    private val epoch = Instant.ofEpochSecond(1_760_000_000)

    /** A trip of 31 points 300 m to the north, shifted `eastDegrees` to the east. */
    private suspend fun makeTrip(confirmation: TripConfirmation, eastDegrees: Double = 0.0): UUID {
        val id = UUID.randomUUID()
        insert(
            TripEntity(
                id = id, startDate = epoch, endDate = epoch.plusSeconds(30), isTrackProcessed = true,
                confirmation = confirmation.rawValue, syncStatus = synced,
            ),
        )
        db.tripDao().insertTrackPoints(
            (0..30).map {
                TrackPointEntity(
                    id = UUID.randomUUID(), latitude = 45.0 + it * 0.00009, longitude = 38.9 + eastDegrees,
                    timestamp = epoch.plusSeconds(it.toLong()), tripId = id,
                )
            },
        )
        return id
    }

    @Test
    fun testWorldReadsNeverSeeADraft() = test {
        val draft = makeTrip(TripConfirmation.draft)
        val real = makeTrip(TripConfirmation.confirmed)
        assertEquals(listOf(real), repo.fetchAllTrips().map { it.id })
        assertFalse(repo.fetchTrips(Instant.MIN, Instant.MAX).any { it.id == draft })
        assertFalse(repo.tripPreviews(needingPlaceMatch = false).any { it.id == draft })
        assertEquals(1, repo.fetchTripStats().count)
        assertEquals(1, repo.countLiveTrips(), "the server never has a draft: library size is without it")
        assertFalse(repo.fetchTripsForMap().any { it.id == draft })
        assertEquals(1, repo.fetchTripCount())
    }

    @Test
    fun testHistoryAndTheTripScreenStillSeeIt() = test {
        val draft = makeTrip(TripConfirmation.draft)
        assertEquals(listOf(draft), repo.fetchDraftTrips().map { it.id })
        assertEquals(TripConfirmation.draft, repo.fetchTripDetail(draft)?.confirmation)
    }

    @Test
    fun testRevealLayerNeverReadsADraftPreview() = test {
        val draft = makeTrip(TripConfirmation.draft)
        val real = makeTrip(TripConfirmation.confirmed)
        for (id in listOf(draft, real)) update(id) { it.copy(previewPolyline = byteArrayOf(1, 2, 3)) }
        assertEquals(listOf(real), RevealedTileStore(db).previews().map { it.id })
        assertEquals(listOf(real), RevealedTileStore(db).previews(ids = setOf(draft, real)).map { it.id })
    }

    @Test
    fun testDraftCheckpointIsNotAnOrphanForPlaces() = test {
        val draft = makeTrip(TripConfirmation.draft)
        repo.addCheckpoint(
            TripCheckpoint(timestamp = epoch.plusSeconds(5), latitude = 45.0, longitude = 38.9, distanceFromStart = 50.0, elapsedFromStart = 5.0),
            draft,
        )
        assertTrue(repo.checkpointsWithoutPlace().isEmpty())

        // «Mine» brings it into the world; then it waits for its place like any checkpoint.
        assertTrue(repo.setConfirmation(TripConfirmation.confirmed, draft))
        assertEquals(1, repo.checkpointsWithoutPlace().size)
    }

    /** Territory is «cities and regions» in the statistics: a draft does not paint it. */
    @Test
    fun testTerritoryBackfillSkipsDraftPoints() = test {
        makeTrip(TripConfirmation.confirmed)
        makeTrip(TripConfirmation.draft, eastDegrees = 0.4) // thirty kilometres to the side: its own cells
        val flags = DataFlags.InMemory()
        val added = TerritoryStore(db).backfillIfNeeded(flags, coarseGeohash).orEmpty()
        val draftCell = coarseGeohash.encode(45.0, 38.9 + 0.4)
        assertTrue(added.isNotEmpty())
        assertFalse(draftCell in added)
        assertFalse(draftCell in TerritoryStore(db).visitedHashes())
    }

    @Test
    fun testSetConfirmationOnlyTurnsADraftIntoAConfirmedTripOnce() = test {
        val draft = makeTrip(TripConfirmation.draft)
        val real = makeTrip(TripConfirmation.confirmed)
        assertFalse(repo.setConfirmation(TripConfirmation.draft, draft), "nothing turns into a draft")
        assertFalse(repo.setConfirmation(TripConfirmation.confirmed, real), "entering the world twice is refused")
        assertTrue(repo.setConfirmation(TripConfirmation.confirmed, draft))
        assertFalse(repo.setConfirmation(TripConfirmation.confirmed, draft))
        assertEquals(listOf(real, draft).toSet(), repo.fetchAllTrips().map { it.id }.toSet())
    }

    @Test
    fun testDiscardDraftDeletesOnlyADraft() = test {
        val draft = makeTrip(TripConfirmation.draft)
        val real = makeTrip(TripConfirmation.confirmed)
        assertFalse(repo.discardDraft(real))
        assertTrue(repo.discardDraft(draft))
        assertEquals(null, repo.fetchEntity(draft), "a draft was never on the server: no tombstone")
        assertTrue(repo.fetchEntity(real) != null)
        assertTrue(sink.enqueued.isEmpty(), "nothing to tell the server about a draft")
    }

    // MARK: TripConfirmationSchemaTests

    @Test
    fun testNewTripEntityDefaultsToConfirmedAndUnchecked() {
        val entity = TripEntity(id = UUID.randomUUID(), startDate = Instant.now())
        assertEquals("confirmed", entity.confirmation)
        assertEquals("unchecked", entity.roadFillState)
        assertEquals("recorded", entity.source)
        assertEquals(pendingUpload, entity.syncStatus, "a fresh row is pendingUpload, as in Core Data")
    }

    @Test
    fun testRepositoryMapsUnknownStringsToSafeDefaults() = test {
        val id = insert(
            TripEntity(id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(), confirmation = "somethingFromTheFuture", roadFillState = "later", source = "teleport"),
        )
        val trip = repo.fetchTripDetail(id)
        assertEquals(TripConfirmation.confirmed, trip?.confirmation)
        assertEquals(RoadFillState.unchecked, trip?.roadFillState)
        assertEquals(com.onezee.triptrack.model.TripOrigin.recorded, trip?.source)
    }

    /** An empty `confirmation` column is a confirmed trip: every trip before 0.8.1 reads so. */
    @Test
    fun testANullConfirmationColumnIsInTheWorld() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(), confirmation = null))
        assertEquals(listOf(id), repo.fetchAllTrips().map { it.id })
        assertTrue(WorldSql.NOT_DRAFT.contains(TripConfirmation.draft.rawValue))
    }

    private companion object {
        /** A stand-in geohash of ~0.1°: the latch and the draft gate do not care about precision. */
        val coarseGeohash = GeohashOf { lat, lon -> "%.1f,%.1f".format(java.util.Locale.ROOT, lat, lon) }
    }
}
