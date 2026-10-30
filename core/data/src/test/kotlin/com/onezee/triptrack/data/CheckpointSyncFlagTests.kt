// iOS: CheckpointSyncFlagTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.TripCheckpoint
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checkpoints travel inside their trip, so a checkpoint edit is a trip edit and must flip
 * `pendingUpload`; otherwise the next pull replaces the list with the server's and erases what the
 * person just set (review of 8 September 2026).
 */
class CheckpointSyncFlagTests : RoomTestCase() {
    private val tripId = UUID.randomUUID()

    @Before
    fun seed() = test {
        // Public: it leaves the phone even with Cloud Sync off.
        insert(TripEntity(id = tripId, startDate = Instant.now(), endDate = Instant.now(), isPrivate = false, syncStatus = synced))
    }

    private suspend fun status(): Short? = trip(tripId)?.syncStatus

    private fun checkpoint() = TripCheckpoint(
        timestamp = Instant.now(), latitude = 45.0, longitude = 38.9, distanceFromStart = 1_000.0, elapsedFromStart = 60.0,
    )

    @Test
    fun testAddingACheckpointMarksTheTripForUpload() = test {
        assertNotNull(repo.addCheckpoint(checkpoint(), tripId))
        assertEquals(pendingUpload, status())
    }

    @Test
    fun testRenamingACheckpointMarksTheTripForUpload() = test {
        val saved = repo.addCheckpoint(checkpoint(), tripId)!!
        update(tripId) { it.copy(syncStatus = synced) }
        assertEquals(tripId, repo.updateCheckpoint(saved.id, "Джубга", null, emptyList()))
        assertEquals(pendingUpload, status())
    }

    @Test
    fun testDeletingACheckpointMarksTheTripForUpload() = test {
        val saved = repo.addCheckpoint(checkpoint(), tripId)!!
        update(tripId) { it.copy(syncStatus = synced) }
        assertEquals(tripId, repo.deleteCheckpoint(saved.id))
        assertEquals(pendingUpload, status())
        assertEquals(0, db.tripDao().checkpoints(tripId).size)
    }

    /** No checkpoint, no trip: the queue must not receive garbage. */
    @Test
    fun testUnknownCheckpointReturnsNoTrip() = test {
        assertNull(repo.updateCheckpoint(UUID.randomUUID(), null, null, emptyList()))
        assertNull(repo.deleteCheckpoint(UUID.randomUUID()))
    }

    /** A private trip with Cloud Sync off cannot drain: flagging it would leave a phantom pending op. */
    @Test
    fun testPrivateTripWithCloudSyncOffIsStampedButNotFlagged() = test {
        update(tripId) { it.copy(isPrivate = true, lastModifiedAt = Instant.EPOCH) }
        repo.addCheckpoint(checkpoint(), tripId)
        assertEquals(synced, status())
        assertTrue(trip(tripId)!!.lastModifiedAt!! > Instant.EPOCH, "the edit is still stamped")

        cloudSyncEnabled = true
        repo.addCheckpoint(checkpoint(), tripId)
        assertEquals(pendingUpload, status(), "with Cloud Sync on the private edit can drain")
    }
}
