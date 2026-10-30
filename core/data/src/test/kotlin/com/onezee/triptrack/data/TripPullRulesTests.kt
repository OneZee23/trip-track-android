// iOS: RecordingBreaksPersistenceTests.swift, TripOriginTests.swift, VehicleEnergyWireTests.swift (trip mode),
// ManualTripSyncImportTests.swift (store half), PhotoMetadataTests.swift (store half),
// CompanionsCachePersistenceTests.swift, CompanionsCacheSignOutTests.swift (store half)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TripCheckpointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.model.CompanionStatus
import com.onezee.triptrack.model.PhotoUploadStatus
import com.onezee.triptrack.model.TrackPointPayload
import com.onezee.triptrack.model.TripCheckpointPayload
import com.onezee.triptrack.model.TripCompanion
import com.onezee.triptrack.model.TripEnergyMode
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.TripPhotoMetadataPayload
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** What a pull may and may not overwrite on a trip. */
class TripPullRulesTests : RoomTestCase() {
    private val start = Instant.ofEpochSecond(1_700_000_000)

    // MARK: RecordingBreaksPersistenceTests

    /** An absent key keeps the pauses (an older peer knows only points); an explicit [] clears them. */
    @Test
    fun testPersistenceAndSyncPreserveMissingFieldButAllowExplicitClear() = test {
        val id = UUID.randomUUID()
        val boundary = start.plusSeconds(10)
        insert(
            TripEntity(
                id = id, startDate = start, endDate = start.plusSeconds(100), lastModifiedAt = start, syncStatus = synced,
                recordingBreaksJSON = JsonColumns.encodeBreaks(listOf(boundary, boundary)),
            ),
        )
        val payload = tripPayload(id = id, startDate = start, endDate = start.plusSeconds(100), conflictVersion = 0, recordingBreaks = null)

        repo.applyRemoteTrip(payload)
        assertEquals(listOf(boundary), repo.fetchTripDetail(id)?.recordingBreaks, "normalized: one boundary, not two")

        repo.applyRemoteTrip(payload.copy(recordingBreaks = emptyList()))
        assertEquals(emptyList(), repo.fetchTripDetail(id)?.recordingBreaks)
        assertNull(trip(id)?.recordingBreaksJSON, "an empty list is stored as NULL")
    }

    /** A fresh row is pendingUpload by default but carries no edit: its first pull takes the pauses. */
    @Test
    fun testFirstPullRetainsBoundariesDespiteNewEntityDefaultSyncStatus() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, endDate = start.plusSeconds(30), recordingBreaks = listOf(start.plusSeconds(10))))
        assertEquals(listOf(start.plusSeconds(10)), repo.fetchTripDetail(id)?.recordingBreaks)
    }

    @Test
    fun testPendingLocalBoundaryIsNotErasedByRemoteMetadata() = test {
        val id = UUID.randomUUID()
        insert(
            TripEntity(
                id = id, startDate = start, endDate = start.plusSeconds(100), lastModifiedAt = start, syncStatus = pendingUpload,
                recordingBreaksJSON = JsonColumns.encodeBreaks(listOf(start.plusSeconds(10))),
            ),
        )
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, conflictVersion = 0, recordingBreaks = emptyList()))
        assertEquals(listOf(start.plusSeconds(10)), JsonColumns.decodeBreaks(trip(id)?.recordingBreaksJSON))
    }

    /** The column keeps iOS's shape: seconds since 2001 as JSON numbers, sorted, distinct. */
    @Test
    fun testBreaksColumnUsesTheFoundationDateShape() {
        val json = JsonColumns.encodeBreaks(listOf(Instant.ofEpochSecond(978_307_210), Instant.ofEpochSecond(978_307_200)))
        assertEquals("[0.0,10.0]", json)
        assertEquals(listOf(Instant.ofEpochSecond(978_307_200), Instant.ofEpochSecond(978_307_210)), JsonColumns.decodeBreaks(json))
        assertEquals(emptyList(), JsonColumns.decodeBreaks("not json"))
    }

    // MARK: TripOriginTests

    private suspend fun makeLocal(id: UUID, source: String, synced: Boolean): TripEntity {
        val e = TripEntity(
            id = id, startDate = Instant.now(), source = source, conflictVersion = 5,
            syncStatus = if (synced) this.synced else pendingUpload,
        )
        insert(e)
        return e
    }

    @Test
    fun testNilSourceLeavesLocalUntouched() = test {
        val id = UUID.randomUUID()
        makeLocal(id, "manual", synced = true)
        repo.applyRemoteTrip(tripPayload(id = id, startDate = Instant.now(), endDate = null, conflictVersion = 6, source = null))
        assertEquals("manual", trip(id)?.source, "the server's silence must not erase the source")
    }

    @Test
    fun testValueIsAppliedWhenSynced() = test {
        val id = UUID.randomUUID()
        makeLocal(id, "recorded", synced = true)
        repo.applyRemoteTrip(tripPayload(id = id, startDate = Instant.now(), endDate = null, conflictVersion = 6, source = TripOrigin.manual))
        assertEquals("manual", trip(id)?.source)
    }

    /**
     * The payload's version is HIGHER on purpose, so the early guard does not fire: the source is
     * protected by ITS OWN gate while the other fields apply.
     */
    @Test
    fun testValueIsNotAppliedOverAnUnsyncedLocalEdit() = test {
        val id = UUID.randomUUID()
        makeLocal(id, "manual", synced = false)
        repo.applyRemoteTrip(tripPayload(id = id, startDate = Instant.now(), endDate = null, conflictVersion = 99, source = TripOrigin.recorded))
        assertEquals("manual", trip(id)?.source, "an unsent local edit must not be overwritten by a pull")
        assertEquals(99, trip(id)?.conflictVersion, "the other fields applied: it was the source's own gate")
    }

    // MARK: VehicleEnergyWireTests, trip mode

    @Test
    fun testUnknownModeLeavesTheLocalChoiceAlone() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(), energyMode = "electric", syncStatus = synced))
        repo.applyRemoteTrip(tripPayload(id = id, startDate = Instant.now(), conflictVersion = 0, energyMode = "regenerative"))
        assertEquals("electric", trip(id)?.energyMode, "an unknown mode wiped the local choice")
    }

    @Test
    fun testAutoFromTheServerClearsTheChoice() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(), energyMode = "electric", syncStatus = synced))
        repo.applyRemoteTrip(tripPayload(id = id, startDate = Instant.now(), conflictVersion = 0, energyMode = "auto"))
        assertNull(trip(id)?.energyMode, "«Auto» from the second phone did not arrive")
    }

    @Test
    fun testSetEnergyModeStoresAutoAsNullAndFlagsTheTrip() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.now(), endDate = Instant.now(), isPrivate = false, syncStatus = synced))
        repo.setEnergyMode(TripEnergyMode.electric, id)
        assertEquals("electric", trip(id)?.energyMode)
        assertEquals(pendingUpload, trip(id)?.syncStatus)

        update(id) { it.copy(syncStatus = synced) }
        repo.setEnergyMode(TripEnergyMode.electric, id)
        assertEquals(synced, trip(id)?.syncStatus, "the same mode again is no edit")

        repo.setEnergyMode(TripEnergyMode.auto, id)
        assertNull(trip(id)?.energyMode)
        assertEquals(TripEnergyMode.auto, repo.fetchTripDetail(id)?.energyMode)
    }

    // MARK: Transfer and preview

    /** Absence is not «not a transfer»: an old backend's pull must not undo the passenger mark. */
    @Test
    fun testAbsentTransferKeyIsNotAnExplicitFalse() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, isTransfer = true, syncStatus = synced))
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, conflictVersion = 0, isTransfer = null))
        assertEquals(true, trip(id)?.isTransfer)
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, conflictVersion = 0, isTransfer = false))
        assertEquals(false, trip(id)?.isTransfer)
    }

    /** Under the home zone the server echoes a TRIMMED preview: it must not cut the local one. */
    @Test
    fun testTrimmedEchoDoesNotReplaceTheLocalPreviewWhileTheZoneIsOn() = test {
        val local = byteArrayOf(1, 2, 3, 4)
        val echo = Base64.getEncoder().encodeToString(byteArrayOf(9))
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, previewPolyline = local, syncStatus = synced))

        homeZoneActive = true
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, conflictVersion = 0, previewPolyline = echo))
        assertContentEquals(local, trip(id)?.previewPolyline)

        homeZoneActive = false
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, conflictVersion = 0, previewPolyline = echo))
        assertContentEquals(byteArrayOf(9), trip(id)?.previewPolyline)
    }

    /** A trip from the other phone has no preview here: it needs the server's, zone or not. */
    @Test
    fun testAFreshTripTakesTheServerPreviewEvenUnderTheZone() = test {
        homeZoneActive = true
        val id = UUID.randomUUID()
        repo.applyRemoteTrip(tripPayload(id = id, startDate = start, previewPolyline = Base64.getEncoder().encodeToString(byteArrayOf(7, 7))))
        assertContentEquals(byteArrayOf(7, 7), trip(id)?.previewPolyline)
    }

    // MARK: Checkpoints from the pull

    /** Inside the privacy zone a checkpoint travels without a point: NULL is silence, our copy stays. */
    @Test
    fun testCheckpointWithoutCoordinateKeepsTheLocalPointOrBecomesAnHonestZero() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start.plusSeconds(3_600), syncStatus = synced))
        val known = UUID.randomUUID()
        db.tripDao().upsertCheckpoint(TripCheckpointEntity(id = known, timestamp = start, latitude = 44.5, longitude = 38.1, tripId = id))
        val fresh = UUID.randomUUID()

        repo.applyRemoteTrip(
            tripPayload(
                id = id, startDate = start, conflictVersion = 0,
                checkpoints = listOf(
                    TripCheckpointPayload(id = fresh, timestamp = start.plusSeconds(60), distanceFromStart = 1.0, elapsedFromStart = 60.0, sortOrder = 1),
                    TripCheckpointPayload(id = known, timestamp = start, distanceFromStart = 0.0, elapsedFromStart = 0.0, sortOrder = 0, name = "Двор"),
                ),
            ),
        )

        val checkpoints = assertNotNull(repo.fetchTripDetail(id)).checkpoints
        assertEquals(listOf(known, fresh), checkpoints.map { it.id }, "sorted by elapsed time")
        assertEquals(44.5, checkpoints[0].latitude)
        assertEquals("Двор", checkpoints[0].name)
        assertEquals(0.0, checkpoints[1].latitude, "no key and no own copy: an honest zero")
        assertEquals(localUserId, db.tripDao().checkpoint(fresh)?.userId)
    }

    // MARK: PhotoMetadataTests and the photo half of a pull

    @Test
    fun testAPhotoKeepsItsCaptureTimeAndPlaceThroughTheStore() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, isPrivate = false, syncStatus = synced))
        val shot = Instant.parse("2026-09-12T10:15:00Z")

        val photo = assertNotNull(repo.addPhoto(id, byteArrayOf(1, 2), caption = null, capturedAt = shot, latitude = 44.7239, longitude = 37.7683))

        val stored = assertNotNull(repo.fetchTripDetail(id)?.photos?.firstOrNull())
        assertEquals(photo.id, stored.id)
        assertEquals(shot, stored.capturedAt)
        assertEquals(44.7239, stored.exifLatitude)
        assertTrue(tripPhotos.exists(stored.filename), "the JPEG is on disk under its relative name")
        assertEquals(synced, trip(id)?.syncStatus, "adding a photo queues it, it does not flag the trip")
        assertEquals(
            listOf(
                com.onezee.triptrack.data.sync.SyncRequest(com.onezee.triptrack.data.sync.SyncEntityType.photo, photo.id, com.onezee.triptrack.data.sync.SyncAction.upload),
                com.onezee.triptrack.data.sync.SyncRequest(com.onezee.triptrack.data.sync.SyncEntityType.trip, id, com.onezee.triptrack.data.sync.SyncAction.update),
            ),
            sink.enqueued,
        )
    }

    /**
     * The local capture metadata is more precise and is never overwritten; the server's is taken
     * only where this phone has none. A local-only photo is never touched, and an uploaded row the
     * pull does not name goes (the iOS asymmetry the photo section of the same pull restores).
     */
    @Test
    fun testPullMergesPhotoMetadataAndKeepsLocalOnlyPhotos() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, syncStatus = synced))
        val local = Instant.parse("2026-01-01T10:00:00Z")
        val known = UUID.randomUUID()
        val bare = UUID.randomUUID()
        val localOnly = UUID.randomUUID()
        val gone = UUID.randomUUID()
        val uploaded = PhotoUploadStatus.uploaded.rawValue
        db.tripDao().upsertPhoto(TripPhotoEntity(id = known, timestamp = start, capturedAt = local, exifLatitude = 1.0, exifLongitude = 2.0, uploadStatus = uploaded, tripId = id))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = bare, timestamp = start, uploadStatus = uploaded, tripId = id, tripOrder = 1))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = localOnly, timestamp = start, tripId = id, tripOrder = 2))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = gone, timestamp = start, uploadStatus = uploaded, tripId = id, tripOrder = 3))

        val serverTime = Instant.parse("2026-05-05T05:05:00Z")
        repo.applyRemoteTrip(
            tripPayload(
                id = id, startDate = start, conflictVersion = 0,
                photos = listOf(
                    TripPhotoMetadataPayload(id = known, filename = "a.jpg", timestamp = start, sortOrder = 0, capturedAt = serverTime, exifLatitude = 9.0, exifLongitude = 9.0),
                    TripPhotoMetadataPayload(id = bare, filename = "b.jpg", timestamp = start, sortOrder = 1, capturedAt = serverTime, exifLatitude = 5.0, exifLongitude = 6.0),
                ),
            ),
        )

        val known2 = assertNotNull(db.tripDao().photo(known))
        assertEquals(local, known2.capturedAt)
        assertEquals(1.0, known2.exifLatitude)
        val bare2 = assertNotNull(db.tripDao().photo(bare))
        assertEquals(serverTime, bare2.capturedAt)
        assertEquals(5.0, bare2.exifLatitude)
        assertEquals(6.0, bare2.exifLongitude)
        assertNotNull(db.tripDao().photo(localOnly), "a local-only photo is never deleted by a pull")
        assertNull(db.tripDao().photo(gone))
    }

    @Test
    fun testApplyRemotePhotoJoinsItsTripAtTheEnd() = test {
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, syncStatus = synced))
        db.tripDao().upsertPhoto(TripPhotoEntity(id = UUID.randomUUID(), timestamp = start, tripId = id, tripOrder = 0))
        val pulled = UUID.randomUUID()

        repo.applyRemotePhoto(
            com.onezee.triptrack.model.PhotoSyncPayload(
                id = pulled, tripId = id, filename = "${id.toString().uppercase()}/X.jpg", caption = "Море", timestamp = start,
                remoteUrl = "photos/x", thumbnailUrl = "photos/x_t", sortOrder = 3, uploadStatus = PhotoUploadStatus.uploaded.rawValue,
                lastModifiedAt = start,
            ),
        )

        val photos = repo.fetchTripDetail(id)!!.photos
        assertEquals(pulled, photos.last().id)
        assertEquals("Море", photos.last().caption)
        assertEquals(synced, db.tripDao().photo(pulled)?.syncStatus)
    }

    // MARK: ManualTripSyncImportTests, store half

    private fun manualPayload(points: List<TrackPointPayload>? = null, version: Int = 1, id: UUID) = tripPayload(
        id = id, startDate = start, endDate = start.plusSeconds(600), title = "Web trip", distance = 1_111.0, badgesJson = "[]",
        conflictVersion = version, lastModifiedAt = start.plusSeconds(900), serverCreatedAt = start.plusSeconds(900),
        trackPoints = points, source = TripOrigin.manual, energyMode = "electric",
    )

    private fun points() = listOf(
        TrackPointPayload(UUID.randomUUID(), 45.0, 39.0, 0.0, 1.8517, 0.0, -1.0, start, false),
        TrackPointPayload(UUID.randomUUID(), 45.01, 39.0, 0.0, 1.8517, 0.0, -1.0, start.plusSeconds(600), false),
    )

    @Test
    fun testFirstPullClassifiesManualSourceAndPreservesNoRewardRule() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteTrip(manualPayload(id = id))
        val trip = assertNotNull(repo.fetchTripDetail(id))
        assertEquals(TripOrigin.manual, trip.source)
        assertEquals(TripEnergyMode.electric, trip.energyMode)
        assertEquals(synced, trip(id)?.syncStatus)
    }

    @Test
    fun testExistingUnsyncedOriginAndEnergyChoiceSurviveAConflictPull() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteTrip(manualPayload(id = id))
        update(id) { it.copy(source = TripOrigin.recorded.rawValue, energyMode = TripEnergyMode.fuel.rawValue, syncStatus = pendingUpload) }
        repo.applyRemoteTrip(manualPayload(id = id, version = 2))
        assertEquals(TripOrigin.recorded.rawValue, trip(id)?.source)
        assertEquals(TripEnergyMode.fuel.rawValue, trip(id)?.energyMode)
    }

    @Test
    fun testHydratingAdoptsOnlyThePoints() = test {
        val id = UUID.randomUUID()
        repo.applyRemoteTrip(manualPayload(id = id))
        update(id) { it.copy(title = "Local display title") }
        db.tripDao().upsertPhoto(TripPhotoEntity(id = UUID.randomUUID(), filename = "retained.jpg", timestamp = start, uploadStatus = 2, tripId = id))
        db.tripDao().upsertCheckpoint(TripCheckpointEntity(id = UUID.randomUUID(), timestamp = start, name = "Keep me", tripId = id))
        val snapshot = assertNotNull(repo.missingManualTrack(id))

        assertTrue(repo.applyMissingManualTrack(points(), snapshot))

        val trip = assertNotNull(repo.fetchTripDetail(id))
        assertEquals(listOf(-1.0, -1.0), trip.trackPoints.map { it.horizontalAccuracy })
        assertEquals(45.01, trip.trackPoints.last().latitude)
        assertEquals("Local display title", trip.title)
        assertEquals(1, trip.photos.size)
        assertEquals(1, trip.checkpoints.size)
        assertEquals(synced, trip(id)?.syncStatus)
        assertEquals(start.plusSeconds(900), trip(id)?.lastModifiedAt)
        assertEquals(true, trip(id)?.isTrackProcessed, "a constructed route never runs through GPS reconstruction")
        assertNull(repo.missingManualTrack(id), "never fetched again once the route is here")
    }

    @Test
    fun testRefusesLateResponseAfterLocalEditDeletionOrNewerPull() = test {
        for (change in 0 until 3) {
            val id = UUID.randomUUID()
            repo.applyRemoteTrip(manualPayload(id = id))
            val snapshot = assertNotNull(repo.missingManualTrack(id))
            when (change) {
                0 -> update(id) { it.copy(syncStatus = pendingUpload, title = "Changed while fetching") }
                1 -> update(id) { it.copy(syncStatus = pendingDelete) }
                else -> update(id) { it.copy(conflictVersion = 2) }
            }
            assertFalse(repo.applyMissingManualTrack(points(), snapshot))
            assertEquals(0, db.tripDao().countTrackPoints(id))
        }
    }

    @Test
    fun testRecordedOrLoadedTripsAreNotMissingAManualTrack() = test {
        val recorded = UUID.randomUUID()
        repo.applyRemoteTrip(manualPayload(id = recorded).copy(source = TripOrigin.recorded))
        assertNull(repo.missingManualTrack(recorded))
        val loaded = UUID.randomUUID()
        repo.applyRemoteTrip(manualPayload(points = points(), id = loaded))
        assertNull(repo.missingManualTrack(loaded))
    }

    // MARK: CompanionsCachePersistenceTests and the sign-out cleanup

    @Test
    fun testUpdateCompanionsDoesNotChangeLastModifiedAtNorFlagTheTrip() = test {
        val stamp = Instant.ofEpochSecond(1_760_000_000)
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start, isPrivate = false, lastModifiedAt = stamp, syncStatus = synced))

        repo.updateCompanions(id, listOf(TripCompanion(accountId = UUID.randomUUID(), displayName = "Аня", avatarEmoji = "🙂", status = CompanionStatus.accepted)))

        assertEquals(stamp, trip(id)?.lastModifiedAt, "the companions cache write must not advance the sync clock")
        assertEquals(synced, trip(id)?.syncStatus)
        assertEquals(1, repo.fetchTripDetail(id)?.companions?.size)
    }

    /** A foreign trip has no row here, and the cache write must not create one. */
    @Test
    fun testCompanionsOfAForeignTripCreateNoRow() = test {
        repo.updateCompanions(UUID.randomUUID(), listOf(TripCompanion(accountId = UUID.randomUUID(), status = CompanionStatus.pending)))
        assertEquals(0, countRows("TripEntity"))
    }

    @Test
    fun testEmptyRosterClearsTheColumnAndSignOutClearsEveryCache() = test {
        val a = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start))
        val b = insert(TripEntity(id = UUID.randomUUID(), startDate = start, endDate = start))
        val roster = listOf(TripCompanion(accountId = UUID.randomUUID(), status = CompanionStatus.pending))
        repo.updateCompanions(a, roster)
        repo.updateCompanions(b, roster)
        repo.updateCompanions(a, emptyList())
        assertNull(trip(a)?.companionsJSON)

        repo.clearCompanionsCache()

        assertNull(trip(b)?.companionsJSON)
    }
}
