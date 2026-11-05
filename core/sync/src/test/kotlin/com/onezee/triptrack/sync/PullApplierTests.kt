package com.onezee.triptrack.sync

import com.onezee.triptrack.data.*
import com.onezee.triptrack.data.db.entity.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: PullApplierStatsCacheTests, TombstoneGuardTests, TripSegmentSyncTests,
// JourneySyncConflictTests, RecordingBreaksPersistenceTests, PullApplierDiscoveryTests.
class PullApplierTests : SyncTestCase() {
    @Test fun appliesAllRelationsAndReannotatesBreaks() = runBlocking<Unit> {
        val base = tripPayload(); val a = TripCheckpointPayload(UUID.randomUUID(), t0, 45.0, 38.9, 1000.0, 60.0, sortOrder = 0)
        val b = a.copy(id = UUID.randomUUID(), timestamp = t0.plusSeconds(30), sortOrder = 1)
        val photoId = UUID.randomUUID()
        val points = List(3) { TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 100.0, 10.0, 0.0, 5.0, t0.plusSeconds(it * 30L), false) }
        val trip = base.copy(trackPoints = points, checkpoints = listOf(a, b), segments = listOf(TripSegmentPayload(UUID.randomUUID(), a.id, b.id)), recordingBreaks = listOf(t0.plusSeconds(30)))
        val photo = PhotoSyncPayload(photoId, trip.id, "photo.jpg", timestamp = t0, remoteUrl = "r2/key", thumbnailUrl = "r2/thumb", sortOrder = 0, uploadStatus = 2, lastModifiedAt = t0)
        val ids = applier().apply(response(listOf(trip), photos = listOf(photo)))
        assertEquals(listOf(trip.id), ids)
        val stored = repo.fetchTripDetail(trip.id)!!
        assertEquals(listOf(0, 1, 1), stored.trackPoints.map { it.recordingSegmentIndex }); assertEquals(2, stored.checkpoints.size)
        assertEquals(1, stored.segments.size); assertEquals(photoId, stored.photos.single().id); assertEquals(t0, repo.fetchEntity(trip.id)!!.serverCreatedAt)
    }
    @Test fun absentCollectionsPreserveButExplicitEmptyCollectionsClear() = runBlocking<Unit> {
        val base = tripPayload(); val a = TripCheckpointPayload(UUID.randomUUID(), t0, 45.0, 38.9, 1000.0, 60.0, sortOrder = 0)
        val b = a.copy(id = UUID.randomUUID(), sortOrder = 1)
        val points = listOf(TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 100.0, 10.0, 0.0, 5.0, t0, false))
        applier().apply(response(listOf(base.copy(trackPoints = points, checkpoints = listOf(a, b), segments = listOf(TripSegmentPayload(UUID.randomUUID(), a.id, b.id)), recordingBreaks = listOf(t0)))))
        applier().apply(response(listOf(base.copy(conflictVersion = 2))))
        var after = repo.fetchTripDetail(base.id)!!; assertEquals(1, after.trackPoints.size); assertEquals(2, after.checkpoints.size); assertEquals(1, after.segments.size); assertEquals(listOf(t0), after.recordingBreaks)
        applier().apply(response(listOf(base.copy(conflictVersion = 3, trackPoints = emptyList(), checkpoints = emptyList(), segments = emptyList(), recordingBreaks = emptyList()))))
        after = repo.fetchTripDetail(base.id)!!; assertTrue(after.trackPoints.isEmpty()); assertTrue(after.checkpoints.isEmpty()); assertTrue(after.segments.isEmpty()); assertTrue(after.recordingBreaks.isEmpty())
    }
    @Test fun pendingCheckpointEditIsNotErasedBySameVersionPull() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip)
        val checkpoint = TripCheckpoint(timestamp = t0, latitude = 45.0, longitude = 38.9, distanceFromStart = 1000.0, elapsedFromStart = 60.0)
        repo.addCheckpoint(checkpoint, trip.id)
        applier().apply(response(listOf(trip.copy(checkpoints = emptyList()))))
        assertEquals(listOf(checkpoint.id), repo.fetchTripDetail(trip.id)!!.checkpoints.map { it.id })
        assertEquals(SyncStatus.pendingUpload.rawValue, repo.fetchEntity(trip.id)!!.syncStatus)
    }
    @Test fun localOnlyTripAndItsGallerySurviveCascadedTombstones() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip); val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, filename = "local.jpg", timestamp = t0, tripId = trip.id, remoteURL = "key"))
        repo.markUnpublished(trip.id)
        applier().apply(response(deleted = listOf(trip.id), deletedPhotos = listOf(photoId)))
        assertNotNull(repo.fetchEntity(trip.id)); assertNotNull(db.tripDao().photo(photoId))
        repo.markSynced(trip.id, 2, t0); applier().apply(response(deleted = listOf(trip.id), deletedPhotos = listOf(photoId)))
        assertNull(repo.fetchEntity(trip.id)); assertNull(db.tripDao().photo(photoId))
    }
    @Test fun deletedServerPhotoCopyKeepsItsLocallyHiddenFileAndRow() = runBlocking<Unit> {
        val points = List(3) { index -> TrackPointPayload(UUID.randomUUID(), 45.0, 39.0 + index * .001, 10.0, 5.0, 0.0, 5.0, t0.plusSeconds(index * 10L), false) }
        val trip = tripPayload().copy(trackPoints = points, hiddenParts = TripHiddenParts(
            sections = listOf(HiddenSection(UUID.randomUUID(), t0.plusSeconds(5), t0.plusSeconds(15))),
        ))
        repo.applyRemoteTrip(trip)
        val filename = tripFiles.save(byteArrayOf(1, 2, 3), trip.id)!!
        val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, filename = filename, timestamp = t0.plusSeconds(10),
            capturedAt = t0.plusSeconds(10), tripId = trip.id, remoteURL = "server/key", thumbnailURL = "server/thumb", uploadStatus = 2))

        applier().apply(response(deletedPhotos = listOf(photoId)))

        assertNotNull(db.tripDao().photo(photoId)); assertTrue(tripFiles.exists(filename))
    }
    @Test fun pulledEmptyHiddenPartsObjectClearsTheLocalOverride() = runBlocking<Unit> {
        val trip = tripPayload().copy(hiddenParts = TripHiddenParts(startMetres = 500))
        repo.applyRemoteTrip(trip)
        assertEquals(500, repo.fetchTripDetail(trip.id)!!.hiddenParts!!.startMetres)

        applier().apply(response(listOf(trip.copy(conflictVersion = 2, hiddenParts = TripHiddenParts()))))

        assertNull(repo.fetchTripDetail(trip.id)!!.hiddenParts)
    }
    @Test fun photoAbsenceKeepsTheDocumentedIosAsymmetry() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip)
        val remote = TripPhotoEntity(UUID.randomUUID(), timestamp = t0, tripId = trip.id, uploadStatus = 2, syncStatus = 2)
        val local = TripPhotoEntity(UUID.randomUUID(), timestamp = t0, tripId = trip.id, uploadStatus = 0)
        db.tripDao().upsertPhoto(remote); db.tripDao().upsertPhoto(local)
        applier().apply(response(listOf(trip.copy(conflictVersion = 2))))
        assertNull(db.tripDao().photo(remote.id)); assertNotNull(db.tripDao().photo(local.id))
    }
    @Test fun pendingJourneyAndDeletedJourneyCannotBeResurrectedByUpserts() = runBlocking<Unit> {
        val journey = repo.saveJourney(Journey(title = "Локальное", startDate = t0))
        val remote = JourneySyncPayload(journey.id, "Серверное", t0, excludedTripIds = emptyList(), isPrivate = false, conflictVersion = 7, lastModifiedAt = t0)
        applier().apply(response(journeys = listOf(remote))); assertEquals("Локальное", repo.fetchJourney(journey.id)?.title)
        repo.markJourneyDeleted(journey.id); applier().apply(response(journeys = listOf(remote))); assertEquals(SyncStatus.pendingDelete.rawValue, repo.journeySyncStatus(journey.id))
    }
    @Test fun manualOdometersRecomputeAfterVehiclesIncludingTheOldAssignment() = runBlocking<Unit> {
        val oldId = vehicles.addVehicle("Old", "🚗"); val newId = vehicles.addVehicle("New", "🚗")
        val trip = tripPayload().copy(source = TripOrigin.manual, distance = 480_000.0, vehicleId = oldId)
        applier().apply(response(listOf(trip)))
        assertEquals(480.0, db.vehicleDao().vehicle(oldId)!!.odometerKm)
        applier().apply(response(listOf(trip.copy(vehicleId = newId, conflictVersion = 2))))
        assertEquals(0.0, db.vehicleDao().vehicle(oldId)!!.odometerKm); assertEquals(480.0, db.vehicleDao().vehicle(newId)!!.odometerKm)
        assertEquals(1, db.vehicleDao().vehicle(newId)!!.vehicleLevel)
    }
    @Test fun deletedVehiclePurgesFilesRowsAndPreferenceBindings() = runBlocking<Unit> {
        val id = vehicles.addVehicle("Car", "🚗"); val filename = vehicleFiles.save(byteArrayOf(1, 2))!!
        db.vehicleDao().upsertPhoto(VehiclePhotoEntity(UUID.randomUUID(), id, filename, timestamp = t0))
        val purged = mutableListOf<UUID>(); applier(purge = { purged.add(it) }).apply(response(deletedCars = listOf(id)))
        assertFalse(vehicleFiles.exists(filename)); assertTrue(db.vehicleDao().photos(id).isEmpty()); assertNull(db.vehicleDao().vehicle(id)); assertEquals(listOf(id), purged)
    }
    @Test fun tripAndPhotoChangesInvalidateSnapshotsEvenWithUnchangedCountAndDate() = runBlocking<Unit> {
        val trip = tripPayload(); var invalidations = 0; val applier = applier(invalidate = { invalidations++ })
        applier.apply(response(listOf(trip))); applier.apply(response(listOf(trip.copy(title = "From the other phone", conflictVersion = 2))))
        assertEquals(2, invalidations); assertEquals(1, repo.fetchTripCount()); assertEquals(trip.startDate, repo.fetchLastTripDate())
        applier.apply(response()); assertEquals(2, invalidations)
    }
    @Test fun discoveryCoordinatesAndFirstFindRulesMatchIos() = runBlocking<Unit> {
        val store = RoomDiscoveryStore(db); val id = UUID.randomUUID()
        val row = DiscoverySyncPayload("pass:ubcr4xk", "riddle", "light.beacon.max", t0, tripId = id, title = "Гумбаши")
        applier().apply(response(discoveries = listOf(row))); val first = store.all().single()
        assertEquals(GeohashEncoder.centerCoordinate("ubcr4xk"), first.coordinate)
        applier().apply(response(discoveries = listOf(row.copy(foundAt = t0.plusSeconds(86_400), verified = true, latitude = 43.5, longitude = 42.5, tripId = null, story = "История"))))
        val after = store.all().single(); assertEquals(first.foundAt, after.foundAt); assertEquals(first.tripId, after.tripId); assertEquals(first.coordinate, after.coordinate)
        assertTrue(after.verified); assertEquals("История", after.story)
        val secret = DiscoverySyncPayload("komsomolsky", "secret", foundAt = t0, verified = true, tripId = id, latitude = 45.035, longitude = 38.975)
        applier().apply(response(discoveries = listOf(secret))); assertEquals(2, store.all().size)
    }
    @Test fun unplaceableDiscoveriesAreNotInvented() = runBlocking<Unit> {
        val rows = listOf(
            DiscoverySyncPayload("x", "constellation", foundAt = t0), DiscoverySyncPayload("brokenkey", "riddle", foundAt = t0),
            DiscoverySyncPayload("pass:ubcr", "riddle", foundAt = t0), DiscoverySyncPayload("komsomolsky", "secret", foundAt = t0, verified = true, latitude = 0.0, longitude = 0.0),
            DiscoverySyncPayload("komsomolsky", "secret", foundAt = t0, verified = false, latitude = 45.035, longitude = 38.975),
        )
        applier().apply(response(discoveries = rows)); assertTrue(RoomDiscoveryStore(db).all().isEmpty())
        assertEquals(SealSymbol.generic, PullApplier.remote(DiscoverySyncPayload("pass:ubcr4xk", "riddle", "future", t0))!!.symbol)
    }
}
