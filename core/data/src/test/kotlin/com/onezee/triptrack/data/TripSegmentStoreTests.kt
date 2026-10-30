// iOS: TripSegmentStoreTests.swift, TripSegmentSyncTests.swift (the pull half)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.TripCheckpoint
import com.onezee.triptrack.model.TripCheckpointPayload
import com.onezee.triptrack.model.TripSegment
import com.onezee.triptrack.model.TripSegmentPayload
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A segment lives in a JSON column of the trip, not as an entity, so everything the database does
 * for a checkpoint (cascade, order, uniqueness) the code does here, and only a test checks it.
 */
class TripSegmentStoreTests : RoomTestCase() {
    private val tripId = UUID.randomUUID()
    private val t0 = Instant.ofEpochSecond(1_700_000_000)

    @Before
    fun seed() = test {
        insert(
            TripEntity(
                id = tripId, startDate = t0, endDate = t0.plusSeconds(7_200), isPrivate = false,
                syncStatus = synced, serverCreatedAt = t0,
            ),
        )
    }

    private suspend fun addCheckpoint(elapsed: Long, distance: Double = 1_000.0): TripCheckpoint {
        val cp = TripCheckpoint(
            timestamp = t0.plusSeconds(elapsed), latitude = 45.0, longitude = 38.9,
            distanceFromStart = distance, elapsedFromStart = elapsed.toDouble(),
        )
        return assertNotNull(repo.addCheckpoint(cp, tripId), "the checkpoint was not saved")
    }

    private suspend fun status(): Short? = trip(tripId)?.syncStatus
    private suspend fun resetSyncStatus() = update(tripId) { it.copy(syncStatus = synced) }
    private suspend fun storedSegments(): List<TripSegment> = JsonColumns.decodeSegments(trip(tripId)?.segmentsJSON)

    // MARK: (a) order

    @Test
    fun testAddSegmentNormalisesOrderByElapsedTime() = test {
        val early = addCheckpoint(60)
        val late = addCheckpoint(3_600)

        // «Segment to…» was pointed at the EARLIER checkpoint.
        val segment = repo.addSegment(tripId, late.id, early.id)

        assertEquals(early.id, segment?.fromCheckpointId)
        assertEquals(late.id, segment?.toCheckpointId)
    }

    @Test
    fun testAddSegmentKeepsOrderWhenAlreadyForward() = test {
        val early = addCheckpoint(60)
        val late = addCheckpoint(3_600)

        val segment = repo.addSegment(tripId, early.id, late.id)

        assertEquals(early.id, segment?.fromCheckpointId)
        assertEquals(late.id, segment?.toCheckpointId)
    }

    // MARK: (b) a segment to itself

    @Test
    fun testSegmentToItselfIsRefused() = test {
        val cp = addCheckpoint(60)
        assertNull(repo.addSegment(tripId, cp.id, cp.id))
        assertTrue(storedSegments().isEmpty())
    }

    @Test
    fun testSegmentWithUnknownCheckpointIsRefused() = test {
        val cp = addCheckpoint(60)
        assertNull(repo.addSegment(tripId, cp.id, UUID.randomUUID()))
        assertNull(repo.addSegment(UUID.randomUUID(), cp.id, cp.id))
        assertTrue(storedSegments().isEmpty())
    }

    // MARK: (c) duplicate pair

    @Test
    fun testDuplicatePairInReverseOrderReturnsTheSameSegment() = test {
        val early = addCheckpoint(60)
        val late = addCheckpoint(3_600)

        val first = repo.addSegment(tripId, early.id, late.id)
        val again = repo.addSegment(tripId, late.id, early.id)

        assertNotNull(first)
        assertEquals(first.id, again?.id)
        assertEquals(1, storedSegments().size)
    }

    // MARK: (d) sync queue

    @Test
    fun testAddSegmentMarksTheTripForUpload() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        resetSyncStatus()
        assertNotNull(repo.addSegment(tripId, a.id, b.id))
        assertEquals(pendingUpload, status())
    }

    @Test
    fun testRenamingASegmentMarksTheTripForUpload() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        val segment = assertNotNull(repo.addSegment(tripId, a.id, b.id), "segment not created")
        resetSyncStatus()

        assertEquals(tripId, repo.updateSegment(segment.id, "Серпантин"))
        assertEquals(pendingUpload, status())
        assertEquals("Серпантин", storedSegments().first().name)
    }

    @Test
    fun testDeletingASegmentMarksTheTripForUpload() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        val segment = assertNotNull(repo.addSegment(tripId, a.id, b.id), "segment not created")
        resetSyncStatus()

        assertEquals(tripId, repo.deleteSegment(segment.id))
        assertEquals(pendingUpload, status())
        assertTrue(storedSegments().isEmpty())
    }

    /** A blank name is NULL, not an empty string: «A → B» is built from NULL at display time. */
    @Test
    fun testBlankNameBecomesNil() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        val segment = assertNotNull(repo.addSegment(tripId, a.id, b.id), "segment not created")
        repo.updateSegment(segment.id, "   ")
        assertNull(storedSegments().first().name)
    }

    @Test
    fun testUnknownSegmentReturnsNoTrip() = test {
        assertNull(repo.updateSegment(UUID.randomUUID(), "x"))
        assertNull(repo.deleteSegment(UUID.randomUUID()))
    }

    // MARK: (e) cascade from the checkpoint

    @Test
    fun testDeletingACheckpointRemovesItsSegments() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        val c = addCheckpoint(7_200)
        repo.addSegment(tripId, a.id, b.id)
        repo.addSegment(tripId, b.id, c.id)
        repo.addSegment(tripId, a.id, c.id)
        assertEquals(3, storedSegments().size)

        assertEquals(tripId, repo.deleteCheckpoint(b.id))

        // Only the pair that never touched the deleted checkpoint is left.
        assertEquals(1, storedSegments().size)
        assertEquals(a.id, storedSegments().first().fromCheckpointId)
        assertEquals(c.id, storedSegments().first().toCheckpointId)
    }

    // MARK: (f) a dangling segment is not read

    @Test
    fun testSegmentWithMissingCheckpointIsDroppedOnRead() = test {
        val a = addCheckpoint(60)
        val b = addCheckpoint(3_600)
        val alive = repo.addSegment(tripId, a.id, b.id)

        // The store after a pull that replaced the checkpoint list: the segment is intact, one of
        // its checkpoints is gone.
        val dangling = TripSegment(fromCheckpointId = a.id, toCheckpointId = UUID.randomUUID())
        update(tripId) { it.copy(segmentsJSON = JsonColumns.encodeSegments(JsonColumns.decodeSegments(it.segmentsJSON) + dangling)) }

        assertEquals(listOfNotNull(alive?.id), repo.fetchTripDetail(tripId)?.segments?.map { it.id })
    }

    // MARK: (g) the cap

    /** Eleven checkpoints give 55 pairs; the 51st segment is not created (the server's MAX_SEGMENTS). */
    @Test
    fun testSegmentBeyondTheCapIsNotCreated() = test {
        val checkpoints = (1..11).map { addCheckpoint(it * 60L) }
        val pairs = mutableListOf<Pair<TripCheckpoint, TripCheckpoint>>()
        for (i in checkpoints.indices) for (j in i + 1 until checkpoints.size) pairs.add(checkpoints[i] to checkpoints[j])
        assertTrue(pairs.size > TripSegment.maxPerTrip)

        for ((from, to) in pairs.take(TripSegment.maxPerTrip)) assertNotNull(repo.addSegment(tripId, from.id, to.id))
        assertEquals(TripSegment.maxPerTrip, storedSegments().size)

        val extra = pairs[TripSegment.maxPerTrip]
        assertNull(repo.addSegment(tripId, extra.first.id, extra.second.id))
        assertEquals(TripSegment.maxPerTrip, storedSegments().size)

        // A duplicate at the cap is still «already there», not a refusal: it creates nothing.
        val known = pairs[0]
        assertNotNull(repo.addSegment(tripId, known.second.id, known.first.id))
    }

    // MARK: Pull (TripSegmentSyncTests)

    private fun payload(checkpoints: List<TripCheckpoint>, segments: List<TripSegmentPayload>?) = tripPayload(
        id = tripId, startDate = t0, endDate = t0.plusSeconds(7_200), distance = 100_000.0,
        lastModifiedAt = t0.plusSeconds(10_000), serverCreatedAt = t0,
        checkpoints = checkpoints.mapIndexed { index, c ->
            TripCheckpointPayload(
                id = c.id, timestamp = c.timestamp, latitude = c.latitude, longitude = c.longitude,
                distanceFromStart = c.distanceFromStart, elapsedFromStart = c.elapsedFromStart,
                name = c.name, sortOrder = index,
            )
        },
        segments = segments,
    )

    /** No key: an old server, or a trip list without details. Local segments are left alone. */
    @Test
    fun testPullWithoutTheSegmentsKeyKeepsLocalSegments() = test {
        val a = addCheckpoint(600)
        val b = addCheckpoint(5_400)
        val local = repo.addSegment(tripId, a.id, b.id)

        repo.applyRemoteTrip(payload(listOf(a, b), null))

        assertEquals(listOfNotNull(local?.id), repo.fetchTripDetail(tripId)?.segments?.map { it.id })
    }

    /** A key: the server is the truth and the list is replaced whole. */
    @Test
    fun testPullWithTheSegmentsKeyReplacesLocalSegments() = test {
        val a = addCheckpoint(600)
        val b = addCheckpoint(5_400)
        repo.addSegment(tripId, a.id, b.id)

        val fromServer = TripSegmentPayload(id = UUID.randomUUID(), fromCheckpointId = a.id, toCheckpointId = b.id, name = "Перевал")
        repo.applyRemoteTrip(payload(listOf(a, b), listOf(fromServer)))

        val segments = repo.fetchTripDetail(tripId)?.segments.orEmpty()
        assertEquals(1, segments.size)
        assertEquals(fromServer.id, segments.first().id)
        assertEquals("Перевал", segments.first().name)
    }

    /** An empty list is an answer too: the segment was deleted on the other phone. */
    @Test
    fun testPullWithAnEmptySegmentsListClearsThem() = test {
        val a = addCheckpoint(600)
        val b = addCheckpoint(5_400)
        repo.addSegment(tripId, a.id, b.id)

        repo.applyRemoteTrip(payload(listOf(a, b), emptyList()))

        assertTrue(repo.fetchTripDetail(tripId)?.segments.orEmpty().isEmpty())
    }
}
