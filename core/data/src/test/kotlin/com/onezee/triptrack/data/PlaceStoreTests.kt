// iOS: PlaceStoreTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlacePass
import com.onezee.triptrack.model.TripCheckpointPayload
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

/** One cell, one place; passes are replaced per (place, trip) pair; a deleted trip takes its passes. */
class PlaceStoreTests : RoomTestCase() {
    private lateinit var store: RoomPlaceStore
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val jubga = GeoPoint(44.3196, 38.7089)
    // Place.cell (geohash-7) of Jubga; the encoder belongs to core:logic, the cell is just text here.
    private val jubgaCell = "szgs0u4"

    @Before
    fun makeStore() {
        store = RoomPlaceStore(db)
    }

    private fun pass(place: UUID, trip: UUID, t: Long, course: Double = 0.0) = PlacePass(
        placeId = place, tripId = trip, timestamp = t0.plusSeconds(t),
        elapsedFromStart = t.toDouble(), distanceFromStart = t * 14.0, course = course,
    )

    @Test
    fun testSameCellIsOnePlaceAndKeepsFirstName() = test {
        val first = store.upsertPlace(jubgaCell, jubga, null)
        assertTrue(first.second)
        assertEquals(Place.id(jubgaCell), first.first.id)
        val second = store.upsertPlace(jubgaCell, jubga, "Джубга")
        assertFalse(second.second)
        assertEquals(first.first.id, second.first.id)
        assertEquals("Джубга", second.first.name, "the place takes a name while it has none")
        val third = store.upsertPlace(jubgaCell, jubga, "Поворот")
        assertEquals("Джубга", third.first.name, "the first name stays")
        assertEquals(1, store.fetchPlaces().size)
    }

    @Test
    fun testRenameOverwritesButAdoptNameDoesNot() = test {
        val p = store.upsertPlace(jubgaCell, jubga, "Джубга").first
        store.rename(p.id, "Поворот к морю")
        assertEquals("Поворот к морю", store.fetchPlace(p.id)?.name)
        store.adoptName("Джубга", p.id)
        assertEquals("Поворот к морю", store.fetchPlace(p.id)?.name)
        store.rename(p.id, "   ")
        assertNull(store.fetchPlace(p.id)?.name, "a blank name is unnamed again")
    }

    @Test
    fun testCentroidIsMeanOfCheckpoints() = test {
        val p = store.upsertPlace(jubgaCell, jubga, null).first
        store.recomputeCentroid(p.id, listOf(GeoPoint(44.3190, 38.7080), GeoPoint(44.3200, 38.7100)))
        val stored = store.fetchPlace(p.id)
        assertEquals(44.3195, stored?.latitude ?: 0.0, 1e-6)
        assertEquals(38.7090, stored?.longitude ?: 0.0, 1e-6)
    }

    @Test
    fun testReplacePassesIsIdempotentPerPlaceAndTrip() = test {
        val place = UUID.randomUUID()
        val tripA = UUID.randomUUID()
        val tripB = UUID.randomUUID()
        store.replacePasses(place, tripA, listOf(pass(place, tripA, 100), pass(place, tripA, 900, course = 180.0)))
        store.replacePasses(place, tripB, listOf(pass(place, tripB, 300)))
        assertEquals(3, store.passes(place).size)
        store.replacePasses(place, tripA, listOf(pass(place, tripA, 100)))
        assertEquals(2, store.passes(place).size, "a repeated match does not double the passes")
        assertEquals(1, store.passesOfTrip(tripB).size)
        assertEquals(2, store.passCount(place))
    }

    @Test
    fun testBatchPassesKeepsPlacesSeparateAndOrdersEachNewestFirst() = test {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val outside = UUID.randomUUID()
        val empty = UUID.randomUUID()
        val trip = UUID.randomUUID()
        val aOld = pass(a, trip, 100)
        val aNew = pass(a, trip, 900)
        val bOld = pass(b, trip, 200)
        val bNew = pass(b, trip, 700, course = 180.0)
        store.replacePasses(a, trip, listOf(aOld, aNew))
        store.replacePasses(b, trip, listOf(bNew, bOld))
        store.replacePasses(outside, trip, listOf(pass(outside, trip, 1_000)))

        val result = store.passes(listOf(b, a, empty, a))

        assertEquals(setOf(a, b), result.keys, "a list snapshot must not include other places")
        assertEquals(listOf(aNew.id, aOld.id), result[a]?.map { it.id })
        assertEquals(listOf(bNew.id, bOld.id), result[b]?.map { it.id })
        assertTrue(result[empty].orEmpty().isEmpty())
        assertEquals(store.passes(a), result[a], "the batch must preserve the detail screen's history")
        assertEquals(store.passes(b), result[b])
    }

    @Test
    fun testEmptyBatchDoesNotReadUnrequestedHistory() = test {
        val place = UUID.randomUUID()
        val trip = UUID.randomUUID()
        store.replacePasses(place, trip, listOf(pass(place, trip, 100)))
        assertTrue(store.passes(emptyList()).isEmpty())
    }

    @Test
    fun testDeletingPassesOfATripAndDeletingAPlace() = test {
        val place = UUID.randomUUID()
        val tripA = UUID.randomUUID()
        val tripB = UUID.randomUUID()
        store.replacePasses(place, tripA, listOf(pass(place, tripA, 100)))
        store.replacePasses(place, tripB, listOf(pass(place, tripB, 200)))
        store.deletePasses(tripA)
        assertEquals(listOf(tripB), store.passes(place).map { it.tripId })
        val p = store.upsertPlace(jubgaCell, jubga, null).first
        store.replacePasses(p.id, tripB, listOf(pass(p.id, tripB, 200)))
        store.deletePlace(p.id)
        assertNull(store.fetchPlace(p.id))
        assertTrue(store.passes(p.id).isEmpty(), "passes go with the place")
    }

    // MARK: Repository

    private suspend fun makeTrip(withCheckpointAt: GeoPoint?, ended: Boolean = true, mirrored: Boolean = false): UUID {
        val id = UUID.randomUUID()
        // `mirrored`: a trip with a server copy; without it deleteTrip would itself short-circuit
        // into deleteTripHard, and the transport test must call deleteTripHard directly.
        insert(
            TripEntity(
                id = id, startDate = t0, endDate = if (ended) t0.plusSeconds(3_600) else null, distance = 100_000.0,
                isPrivate = true, serverCreatedAt = if (mirrored) t0 else null,
            ),
        )
        if (withCheckpointAt != null) {
            db.tripDao().upsertCheckpoint(
                TripCheckpointEntity(
                    id = UUID.randomUUID(), timestamp = t0.plusSeconds(600), latitude = withCheckpointAt.latitude,
                    longitude = withCheckpointAt.longitude, tripId = id,
                ),
            )
        }
        return id
    }

    @Test
    fun testCheckpointsWithoutPlaceAndSettingPlaceIdDoesNotFlipSync() = test {
        val tripId = makeTrip(jubga)
        val pending = repo.checkpointsWithoutPlace()
        assertEquals(1, pending.size)
        assertEquals(tripId, pending[0].second)
        val statusBefore = trip(tripId)?.syncStatus
        repo.setPlaceId(pending[0].first.id, Place.id("szgs0u4"))
        assertTrue(repo.checkpointsWithoutPlace().isEmpty())
        assertEquals(Place.id("szgs0u4"), repo.fetchTripDetail(tripId)?.checkpoints?.first()?.placeId)
        assertEquals(statusBefore, trip(tripId)?.syncStatus, "a derived placeId does not flip pendingUpload")
        assertEquals(1, repo.checkpointCoordinates(Place.id("szgs0u4")).size)
        assertEquals(setOf(Place.id("szgs0u4")), repo.checkpointPlaceIds())
    }

    @Test
    fun testTripPreviewsNeedingMatchAndMarking() = test {
        val done = makeTrip(null)
        makeTrip(null, ended = false)
        assertEquals(setOf(done), repo.tripPreviews(needingPlaceMatch = true).map { it.id }.toSet(), "an unfinished trip is not matched")
        repo.markPlacesMatched(listOf(done))
        assertTrue(repo.tripPreviews(needingPlaceMatch = true).isEmpty())
        assertEquals(listOf(done), repo.tripPreviews(needingPlaceMatch = false).map { it.id })
    }

    /** The library pass marks trips in a batch: exactly the named ones. */
    @Test
    fun testMarkPlacesMatchedBatch() = test {
        val a = makeTrip(null)
        val b = makeTrip(null)
        val c = makeTrip(null)
        repo.markPlacesMatched(listOf(a, b))
        assertEquals(listOf(c), repo.tripPreviews(needingPlaceMatch = true).map { it.id })
        repo.markPlacesMatched(emptyList())
        assertEquals(listOf(c), repo.tripPreviews(needingPlaceMatch = true).map { it.id }, "an empty batch touches nothing")
    }

    // MARK: Pull

    private fun payload(tripId: UUID, checkpointId: UUID, placeId: UUID?) = tripPayload(
        id = tripId, startDate = t0, endDate = t0.plusSeconds(3_600), distance = 100_000.0, lastModifiedAt = t0, serverCreatedAt = t0,
        checkpoints = listOf(
            TripCheckpointPayload(
                id = checkpointId, timestamp = t0.plusSeconds(600), latitude = jubga.latitude, longitude = jubga.longitude,
                distanceFromStart = 1_000.0, elapsedFromStart = 600.0, placeId = placeId, sortOrder = 0,
            ),
        ),
    )

    /** The place is derived locally; wiping it on a pull would orphan the checkpoint and resurrect a deleted place. */
    @Test
    fun testPullKeepsTheLocalPlaceIdWhenTheServerHasNone() = test {
        val tripId = makeTrip(jubga)
        val cpId = repo.fetchTripDetail(tripId)!!.checkpoints[0].id
        val placeId = Place.id(jubgaCell)
        repo.setPlaceId(cpId, placeId)
        repo.applyRemoteTrip(payload(tripId, cpId, null))
        assertEquals(placeId, repo.fetchTripDetail(tripId)?.checkpoints?.first()?.placeId)
    }

    /** But a place the server DID send wins: that is not «no answer». */
    @Test
    fun testPullTakesTheServerPlaceIdWhenItHasOne() = test {
        val tripId = makeTrip(jubga)
        val cpId = repo.fetchTripDetail(tripId)!!.checkpoints[0].id
        repo.setPlaceId(cpId, Place.id("szgs0u4"))
        val fromServer = Place.id("szgs0u5")
        repo.applyRemoteTrip(payload(tripId, cpId, fromServer))
        assertEquals(fromServer, repo.fetchTripDetail(tripId)?.checkpoints?.first()?.placeId)
    }

    @Test
    fun testDeletingATripForgetsItsPasses() = test {
        val tripId = makeTrip(null)
        val place = UUID.randomUUID()
        store.replacePasses(place, tripId, listOf(pass(place, tripId, 100)))
        repo.deleteTrip(tripId) // serverCreatedAt == null: hard delete at once
        assertTrue(store.passesOfTrip(tripId).isEmpty())
    }

    /** The soft delete (a mirrored trip) takes the passes at once, too: the history forgets it now. */
    @Test
    fun testSoftDeletingATripForgetsItsPasses() = test {
        val tripId = makeTrip(null, mirrored = true)
        val place = UUID.randomUUID()
        store.replacePasses(place, tripId, listOf(pass(place, tripId, 100)))
        repo.deleteTrip(tripId)
        assertEquals(pendingDelete, trip(tripId)?.syncStatus)
        assertTrue(store.passesOfTrip(tripId).isEmpty())
    }

    /** The transport calls deleteTripHard DIRECTLY (tripNotFound, confirmed delete): it forgets passes too. */
    @Test
    fun testHardDeleteFromTransportForgetsPasses() = test {
        val tripId = makeTrip(null, mirrored = true)
        val place = UUID.randomUUID()
        store.replacePasses(place, tripId, listOf(pass(place, tripId, 100)))
        repo.deleteTripHard(tripId)
        assertTrue(store.passesOfTrip(tripId).isEmpty())
    }

    /** A tombstone from the other phone forgets the passes as well. */
    @Test
    fun testTombstoneForgetsPasses() = test {
        val tripId = makeTrip(null, mirrored = true)
        val place = UUID.randomUUID()
        store.replacePasses(place, tripId, listOf(pass(place, tripId, 100)))
        assertTrue(repo.deleteTripHardIfMirrored(tripId))
        assertTrue(store.passesOfTrip(tripId).isEmpty())
    }
}
