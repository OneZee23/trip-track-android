// iOS: the editing half of CoreDataTripRepository (no single iOS test class: the rules live in the
// repository's comments, PrivacyFlowE2ETests and SyncHealTests); the inputs follow those tests.
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.GeocodeCacheEntity
import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.db.entity.VehicleEntity
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.PhotoUploadStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TripRepositoryEditTests : RoomTestCase() {
    private val start = Instant.ofEpochSecond(1_760_000_000)

    private suspend fun makeTrip(isPrivate: Boolean = true, serverCreatedAt: Instant? = null, userId: UUID? = localUserId, status: Short = synced): UUID =
        insert(
            TripEntity(
                id = UUID.randomUUID(), startDate = start, endDate = start.plusSeconds(3_600), isPrivate = isPrivate,
                serverCreatedAt = serverCreatedAt, userId = userId, syncStatus = status, lastModifiedAt = Instant.EPOCH,
            ),
        )

    // MARK: Title and notes

    @Test
    fun testTitleIsCustomOnlyWhileTyped() = test {
        val id = makeTrip(isPrivate = false)
        repo.updateTitle(id, "Краснодар → Джубга")
        assertEquals("Краснодар → Джубга", trip(id)?.title)
        assertEquals(true, trip(id)?.titleIsCustom)
        assertEquals(pendingUpload, trip(id)?.syncStatus)
        repo.updateTitle(id, "")
        assertNull(trip(id)?.title)
        assertEquals(false, trip(id)?.titleIsCustom, "clearing makes the trip unnamed again")
    }

    @Test
    fun testNotesAreStoredAsTypedAndStamped() = test {
        val id = makeTrip()
        repo.updateNotes(id, "")
        assertEquals("", trip(id)?.tripDescription)
        assertTrue(assertNotNull(trip(id)?.lastModifiedAt) > Instant.EPOCH)
        assertEquals(synced, trip(id)?.syncStatus, "private with Cloud Sync off: stamped, not flagged")
    }

    // MARK: Privacy (the four branches of updatePrivacy)

    @Test
    fun testGoingPublicUploadsTheTripAndItsUnsentPhotos() = test {
        val id = makeTrip(isPrivate = true)
        val unsent = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = unsent, timestamp = start, tripId = id))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = UUID.randomUUID(), timestamp = start, tripId = id, remoteURL = "photos/a", tripOrder = 1))

        repo.updatePrivacy(id, isPrivate = false)

        assertEquals(false, trip(id)?.isPrivate)
        assertEquals(pendingUpload, trip(id)?.syncStatus)
        assertEquals(listOf(id to SyncEntityType.trip), sink.cancelled)
        assertEquals(
            listOf(SyncRequest(SyncEntityType.trip, id, SyncAction.upload), SyncRequest(SyncEntityType.photo, unsent, SyncAction.upload)),
            sink.enqueued,
        )
    }

    @Test
    fun testRepublishingAMirroredTripIsAnUpdate() = test {
        val id = makeTrip(isPrivate = true, serverCreatedAt = start)
        repo.updatePrivacy(id, isPrivate = false)
        assertEquals(SyncRequest(SyncEntityType.trip, id, SyncAction.update), sink.enqueued.first())
    }

    /** Private, on the server, Cloud Sync off: the server copy goes and nothing of it may publish first. */
    @Test
    fun testHidingWithCloudSyncOffUnpublishesAndCancelsPhotoWork() = test {
        val id = makeTrip(isPrivate = false, serverCreatedAt = start)
        val photo = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = photo, timestamp = start, tripId = id))

        repo.updatePrivacy(id, isPrivate = true)

        assertEquals(pendingUpload, trip(id)?.syncStatus, "a concurrent pull must not flip it back")
        assertEquals(listOf(id to SyncEntityType.trip, photo to SyncEntityType.photo), sink.cancelled)
        assertEquals(listOf(SyncRequest(SyncEntityType.trip, id, SyncAction.unpublish)), sink.enqueued)
    }

    @Test
    fun testHidingWithCloudSyncOnIsAnUpdate() = test {
        cloudSyncEnabled = true
        val id = makeTrip(isPrivate = false, serverCreatedAt = start)
        repo.updatePrivacy(id, isPrivate = true)
        assertEquals(listOf(SyncRequest(SyncEntityType.trip, id, SyncAction.update)), sink.enqueued)
    }

    @Test
    fun testHidingANeverUploadedTripIsOnlyTheLocalFlip() = test {
        val id = makeTrip(isPrivate = false, status = pendingUpload)
        repo.updatePrivacy(id, isPrivate = true)
        assertEquals(true, trip(id)?.isPrivate)
        assertEquals(synced, trip(id)?.syncStatus)
        assertTrue(sink.enqueued.isEmpty())
        assertTrue(sink.cancelled.isEmpty())
    }

    @Test
    fun testMarkUnpublishedClearsTheServerBookkeepingButNotTheIntent() = test {
        val hidden = makeTrip(isPrivate = true, serverCreatedAt = start, status = pendingUpload)
        update(hidden) { it.copy(conflictVersion = 4) }
        repo.markUnpublished(hidden)
        assertNull(trip(hidden)?.serverCreatedAt)
        assertEquals(0, trip(hidden)?.conflictVersion)
        assertEquals(synced, trip(hidden)?.syncStatus)

        // Made public again while the unpublish was in flight: the queued upload must still drain.
        val republished = makeTrip(isPrivate = false, serverCreatedAt = start, status = pendingUpload)
        repo.markUnpublished(republished)
        assertEquals(false, trip(republished)?.isPrivate)
        assertEquals(pendingUpload, trip(republished)?.syncStatus)
    }

    @Test
    fun testPrivacyByDefaultMigrationReturnsOnlyServerCopies() = test {
        val local = makeTrip(isPrivate = false)
        val mirrored = makeTrip(isPrivate = false, serverCreatedAt = start)
        val already = makeTrip(isPrivate = true, serverCreatedAt = start)

        val toUnpublish = repo.migrateAllTripsToPrivate()

        assertEquals(listOf(mirrored), toUnpublish)
        for (id in listOf(local, mirrored, already)) assertEquals(true, trip(id)?.isPrivate)
        assertEquals(synced, trip(mirrored)?.syncStatus, "no phantom pending work for private trips at off")
    }

    // MARK: Deletion

    /** Never on the server: a hard delete at once, nothing queued (a private trip at off would hang forever). */
    @Test
    fun testDeletingAnUnsentTripIsHard() = test {
        val id = makeTrip()
        repo.deleteTrip(id)
        assertNull(trip(id))
        assertTrue(sink.enqueued.isEmpty())
    }

    @Test
    fun testDeletingAMirroredTripIsSoftAndHiddenAtOnce() = test {
        val id = makeTrip(serverCreatedAt = start)
        repo.deleteTrip(id)
        assertEquals(pendingDelete, trip(id)?.syncStatus)
        assertTrue(assertNotNull(trip(id)?.lastModifiedAt) > Instant.EPOCH)
        assertTrue(repo.fetchAllTrips().isEmpty(), "pendingDelete hides the trip from every list")
        assertEquals(1, repo.countLiveTrips(), "but it still counts as library: the server row is live")
        assertEquals(listOf(SyncRequest(SyncEntityType.trip, id, SyncAction.delete)), sink.enqueued)

        repo.purgeSoftDeletedTrips()
        assertNull(trip(id))
    }

    @Test
    fun testDeletingAPhotoDetachesItFromCheckpointsAndQueuesTheServerDelete() = test {
        val id = makeTrip()
        val photo = UUID.randomUUID()
        val other = UUID.randomUUID()
        val filename = assertNotNull(tripPhotos.save(byteArrayOf(1), id))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = photo, filename = filename, timestamp = start, tripId = id, thumbnailURL = "photos/t"))
        val cover = UUID.randomUUID()
        db.tripDao().upsertCheckpoint(
            TripCheckpointEntity(id = cover, timestamp = start, tripId = id, photoId = photo, photoIdsJSON = JsonColumns.encodeIds(listOf(other, photo))),
        )

        repo.deletePhoto(photo, id)

        val checkpoint = assertNotNull(db.tripDao().checkpoint(cover))
        assertNull(checkpoint.photoId)
        assertEquals(listOf(other), JsonColumns.decodeIds(checkpoint.photoIdsJSON))
        assertNull(db.tripDao().photo(photo))
        assertFalse(tripPhotos.exists(filename))
        assertEquals(
            listOf(
                SyncRequest(SyncEntityType.photo, photo, SyncAction.delete, hasServerCopy = true),
                SyncRequest(SyncEntityType.trip, id, SyncAction.update),
            ),
            sink.enqueued,
        )
    }

    @Test
    fun testServerOnlyPhotosAreTheOnesWithoutALocalFile() = test {
        val id = makeTrip()
        val here = assertNotNull(tripPhotos.save(byteArrayOf(1), id))
        val onDisk = UUID.randomUUID()
        val remoteOnly = UUID.randomUUID()
        val neverUploaded = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = onDisk, filename = here, timestamp = start, remoteURL = "a", tripId = id))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = remoteOnly, filename = "missing.jpg", timestamp = start, thumbnailURL = "t", tripId = id))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = neverUploaded, filename = "missing2.jpg", timestamp = start, tripId = id))

        assertEquals(listOf(remoteOnly), repo.serverOnlyPhotoIds(id))

        repo.adoptRescuedPhoto(remoteOnly, "rescued.jpg")
        val rescued = assertNotNull(db.tripDao().photo(remoteOnly))
        assertEquals("rescued.jpg", rescued.filename)
        assertNull(rescued.remoteURL)
        assertNull(rescued.thumbnailURL)
    }

    @Test
    fun testMarkPhotoUploadedKeepsAKnownOriginal() = test {
        val id = makeTrip()
        val photo = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = photo, timestamp = start, tripId = id, remoteURL = "photos/o"))
        repo.markPhotoUploaded(photo, remoteURL = null, thumbnailURL = "photos/t", uploadStatus = PhotoUploadStatus.uploaded)
        val row = assertNotNull(db.tripDao().photo(photo))
        assertEquals("photos/o", row.remoteURL)
        assertEquals("photos/t", row.thumbnailURL)
        assertEquals(PhotoUploadStatus.uploaded.rawValue, row.uploadStatus)
    }

    /** iOS: PhotoStorageService's three upload queries, read from the rows. */
    @Test
    fun testPhotoUploadBookkeeping() = test {
        val id = makeTrip()
        val fresh = UUID.randomUUID()
        val halfway = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = fresh, filename = "a.jpg", timestamp = start.plusSeconds(10), tripId = id))
        db.tripDao().upsertPhoto(
            TripPhotoEntity(id = halfway, filename = "b.jpg", timestamp = start, tripId = id, thumbnailURL = "t", uploadStatus = PhotoUploadStatus.uploading.rawValue),
        )
        assertEquals(listOf(fresh to "a.jpg"), repo.localOnlyPhotos())
        assertEquals(listOf(halfway), repo.photosAwaitingOriginal())

        repo.markPhotoOriginalUploaded(halfway, "photos/b")

        assertTrue(repo.photosAwaitingOriginal().isEmpty())
        assertEquals(PhotoUploadStatus.uploaded.rawValue, db.tripDao().photo(halfway)?.uploadStatus)
    }

    // MARK: Badges and Cloud Sync

    @Test
    fun testBadgesAreSavedFlaggedAndQueued() = test {
        val id = makeTrip(isPrivate = false)
        repo.saveBadgesJSON(id, emptyList())
        assertNull(trip(id)?.badgesJSON, "no badges, no write")
        repo.saveBadgesJSON(id, listOf("first_trip", "night_owl"))
        assertEquals(listOf("first_trip", "night_owl"), repo.fetchTripDetail(id)?.earnedBadgeIds)
        assertEquals(pendingUpload, trip(id)?.syncStatus)
        assertEquals(listOf(SyncRequest(SyncEntityType.trip, id, SyncAction.update)), sink.enqueued)
    }

    /** Switching Cloud Sync on flags THIS install's rows only: user B never uploads what A left behind. */
    @Test
    fun testMarkAllPendingUploadIsScopedToThisUser() = test {
        val mine = makeTrip(userId = localUserId)
        val theirs = makeTrip(userId = UUID.randomUUID())
        val myPhoto = UUID.randomUUID()
        val theirPhoto = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(id = myPhoto, timestamp = start, tripId = mine, syncStatus = synced))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = theirPhoto, timestamp = start, tripId = theirs, syncStatus = synced))
        val myCar = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = myCar, userId = localUserId, syncStatus = synced))
        db.settingsDao().upsert(UserSettingsEntity(id = localUserId, syncStatus = synced))

        repo.markAllPendingUpload()

        assertEquals(pendingUpload, trip(mine)?.syncStatus)
        assertEquals(synced, trip(theirs)?.syncStatus)
        assertEquals(pendingUpload, db.tripDao().photo(myPhoto)?.syncStatus)
        assertEquals(synced, db.tripDao().photo(theirPhoto)?.syncStatus)
        assertEquals(pendingUpload, db.vehicleDao().vehicle(myCar)?.syncStatus)
        assertEquals(pendingUpload, db.settingsDao().settings()?.syncStatus)
    }

    /** The queue dies with the process; the flags do not. Every pending row becomes an operation again. */
    @Test
    fun testPendingRowsComeBackAsOperationsAfterARestart() = test {
        val upload = makeTrip(status = pendingUpload)
        val delete = makeTrip(status = pendingDelete)
        makeTrip(status = synced)
        makeTrip(status = pendingUpload, userId = UUID.randomUUID())
        val car = UUID.randomUUID()
        db.vehicleDao().upsertVehicle(VehicleEntity(id = car, userId = localUserId, syncStatus = pendingUpload))
        val carPhoto = UUID.randomUUID()
        db.vehicleDao().upsertPhoto(com.onezee.triptrack.data.db.entity.VehiclePhotoEntity(id = carPhoto, vehicleId = car, timestamp = start, thumbnailURL = "t"))
        db.vehicleDao().upsertPhoto(com.onezee.triptrack.data.db.entity.VehiclePhotoEntity(id = UUID.randomUUID(), vehicleId = UUID.randomUUID(), timestamp = start))

        val ops = PendingSyncRecovery(db).pendingOperations(localUserId)

        assertEquals(
            setOf(
                SyncRequest(SyncEntityType.trip, upload, SyncAction.upload),
                SyncRequest(SyncEntityType.trip, delete, SyncAction.delete),
                SyncRequest(SyncEntityType.vehicle, car, SyncAction.upload),
                SyncRequest(SyncEntityType.vehiclePhoto, carPhoto, SyncAction.upload),
            ),
            ops.toSet(),
            "a dead car's photo and another account's trip stay out",
        )
    }

    // MARK: Geocoder cache

    @Test
    fun testGeocodeCacheExpiresAfterNinetyDays() = test {
        repo.saveGeocode("szgs0", "Джубга", "Краснодарский край")
        assertEquals(GeocodeCacheResult("Джубга", "Краснодарский край"), repo.cachedGeocode("szgs0"))
        assertEquals("Джубга", repo.cachedLocality("szgs0"))
        assertEquals(mapOf("szgs0" to "Джубга"), repo.cachedLocalities(setOf("szgs0", "ucfv0")))

        repo.saveGeocode("szgs0", "Архипо-Осиповка", null)
        assertEquals(1, countRows("GeocodeCacheEntity"), "one row per cell, refreshed in place")

        val old = Instant.now().minus(GeocodeCacheResult.ttl).minus(Duration.ofHours(1))
        db.mapDao().upsertGeocode(GeocodeCacheEntity(cachedAt = old, geohash5 = "ucfv0", locality = "Москва"))
        assertTrue(repo.cachedLocalities(setOf("ucfv0")).isEmpty(), "an expired row is skipped by the batch read")
        assertNull(repo.cachedGeocode("ucfv0"))
        assertEquals(1, countRows("GeocodeCacheEntity"), "and deleted by the single read")
    }

    @Test
    fun testMissingUserIdIsStampedOnce() = test {
        val orphan = makeTrip(userId = null)
        val flags = DataFlags.InMemory()
        RoomSettingsStore(db).migrateUserIdIfNeeded(localUserId, flags)
        assertEquals(localUserId, trip(orphan)?.userId)
        val later = makeTrip(userId = null)
        RoomSettingsStore(db).migrateUserIdIfNeeded(localUserId, flags)
        assertNull(trip(later)?.userId, "a one-shot migration: the latch holds")
    }
}
