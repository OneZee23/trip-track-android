// iOS: TombstoneGuardTests.swift, TripHardDeleteFilesTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * A tombstone deletes only a MIRRORED trip, and a pulled trip remembers the server has it. The two
 * halves land together: the guard alone would make pulled trips undeletable from other devices.
 */
class TombstoneGuardTests : RoomTestCase() {
    private suspend fun insertTrip(serverCreatedAt: Instant?): UUID = insert(
        TripEntity(
            id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(),
            serverCreatedAt = serverCreatedAt, syncStatus = synced,
        ),
    )

    private fun payload(id: UUID, serverCreatedAt: Instant?, conflictVersion: Int = 1) = tripPayload(
        id = id, startDate = Instant.now(), endDate = Instant.now(), conflictVersion = conflictVersion,
        serverCreatedAt = serverCreatedAt,
    )

    /** A trip that lives only here, including one the person unpublished (markUnpublished clears the column). */
    @Test
    fun testATombstoneDoesNotDeleteATripTheServerNeverHad() = test {
        val id = insertTrip(serverCreatedAt = null)

        assertFalse(repo.deleteTripHardIfMirrored(id))
        assertNotNull(repo.fetchEntity(id), "an unmirrored trip must survive a tombstone")
    }

    @Test
    fun testATombstoneDeletesAMirroredTrip() = test {
        val id = insertTrip(serverCreatedAt = Instant.now())

        assertTrue(repo.deleteTripHardIfMirrored(id))
        assertNull(repo.fetchEntity(id))
    }

    @Test
    fun testAPulledTripRemembersThatTheServerHasIt() = test {
        val p = payload(UUID.randomUUID(), serverCreatedAt = Instant.now())
        repo.applyRemoteTrip(p)
        assertNotNull(repo.fetchEntity(p.id)?.serverCreatedAt)
    }

    /** An older server omits the field, but sending the trip at all says the server has it. */
    @Test
    fun testAPayloadWithoutServerCreatedAtStillStamps() = test {
        val p = payload(UUID.randomUUID(), serverCreatedAt = null)
        repo.applyRemoteTrip(p)
        assertEquals(p.lastModifiedAt, repo.fetchEntity(p.id)?.serverCreatedAt)
    }

    @Test
    fun testApplyRemoteTripDoesNotOverwriteAnExistingServerCreatedAt() = test {
        val known = Instant.ofEpochSecond(1_700_000_000)
        val id = insertTrip(serverCreatedAt = known)

        repo.applyRemoteTrip(payload(id, serverCreatedAt = Instant.now()))

        assertEquals(known, repo.fetchEntity(id)?.serverCreatedAt)
    }

    /** On a full pull a soft-deleted trip is still a live server row: it must not come back as synced. */
    @Test
    fun testAFullPullDoesNotResurrectASoftDeletedTrip() = test {
        val id = insertTrip(serverCreatedAt = Instant.now())
        update(id) { it.copy(syncStatus = pendingDelete, conflictVersion = 5) }

        repo.applyRemoteTrip(payload(id, serverCreatedAt = Instant.now(), conflictVersion = 5))

        assertEquals(pendingDelete, repo.fetchEntity(id)?.syncStatus, "a deliberately deleted trip must not come back as synced")
    }

    /** `/trips/delete` cascades onto photo rows; a trip we kept must keep its gallery. */
    @Test
    fun testTripIdForPhotoResolvesTheParent() = test {
        val id = insertTrip(serverCreatedAt = null)
        val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = photoId, filename = "p.jpg", timestamp = Instant.now(), tripId = id))

        assertEquals(id, repo.tripId(photoId))
    }

    // MARK: TripHardDeleteFilesTests: a hard delete takes the photo FILES

    private fun photoFile(tripId: UUID): File {
        val dir = File(File(filesRoot, "TripPhotos"), tripId.toString().uppercase())
        dir.mkdirs()
        return File(dir, "kadr.jpg").also { it.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) }
    }

    @Test
    fun testHardDeleteTakesThePhotoFilesWithIt() = test {
        val id = insertTrip(serverCreatedAt = null)
        val file = photoFile(id)

        repo.deleteTripHard(id)

        assertFalse(file.exists())
    }

    @Test
    fun testTombstoneTakesThePhotoFilesWithIt() = test {
        val id = insertTrip(serverCreatedAt = Instant.ofEpochSecond(1_760_000_000))
        val file = photoFile(id)

        assertTrue(repo.deleteTripHardIfMirrored(id))

        assertFalse(file.exists())
    }

    /** A refused tombstone keeps the trip AND its frames. */
    @Test
    fun testRefusedTombstoneKeepsThePhotoFiles() = test {
        val id = insertTrip(serverCreatedAt = null)
        val file = photoFile(id)

        assertFalse(repo.deleteTripHardIfMirrored(id))

        assertTrue(file.exists())
    }

    /** The cascade: points, photos and checkpoints go with the trip row (Core Data deletion rule Cascade). */
    @Test
    fun testHardDeleteCascadesToEveryChildTable() = test {
        val id = insertTrip(serverCreatedAt = Instant.now())
        db.tripDao().insertTrackPoints(listOf(com.onezee.triptrack.data.db.entity.TrackPointEntity(id = UUID.randomUUID(), timestamp = Instant.now(), tripId = id)))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = UUID.randomUUID(), timestamp = Instant.now(), tripId = id))
        db.tripDao().upsertCheckpoint(com.onezee.triptrack.data.db.entity.TripCheckpointEntity(id = UUID.randomUUID(), timestamp = Instant.now(), tripId = id))

        repo.deleteTripHard(id)

        assertEquals(0, db.tripDao().countTrackPoints(id))
        assertEquals(0, db.tripDao().countPhotos(id))
        assertTrue(db.tripDao().checkpoints(id).isEmpty())
    }
}
