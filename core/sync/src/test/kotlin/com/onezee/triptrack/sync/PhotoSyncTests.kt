package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.*
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: VehiclePhotoUploadWireTests.swift, VehiclePhotoRecoveryTests.swift, APISyncTransport.swift.
class PhotoSyncTests : SyncTestCase() {
    private val encoder = object : PhotoEncoder {
        override suspend fun jpeg(data: ByteArray, maxDimension: Int, quality: Int): ByteArray = byteArrayOf(1, 2, 3)
    }
    private fun op(id: UUID, type: SyncEntityType = SyncEntityType.photo, action: SyncAction = SyncAction.upload) = SyncOperation(type, id, action)
    private fun ack(id: UUID, key: String, status: Short = 1) = okValue(PhotoUploadResponse(id, key, status))
    @Test fun testVehiclePhotoFormCarriesLowercaseIdentifiers() = runBlocking<Unit> {
        val vehicleId = UUID.fromString("FB701CDD-1111-4222-8333-444455556666"); val photoId = UUID.fromString("ABCDEF01-2345-4678-9ABC-DEF012345678")
        server.enqueue(ack(photoId, "object/key"))
        R2PhotoStorage(client).uploadVehiclePhotoPart(vehicleId, photoId, PhotoType.thumbnail, ByteArray(16) { 0xFF.toByte() }, true, t0)
        val request = server.takeRequest(); val body = request.body!!.utf8()
        assertTrue(body.contains("fb701cdd-1111-4222-8333-444455556666")); assertTrue(body.contains("abcdef01-2345-4678-9abc-def012345678"))
        assertFalse(body.contains("FB701CDD")); assertEquals("/vehicles/photos/upload", request.url.encodedPath)
        assertTrue(body.contains("image/jpeg")); assertTrue(body.contains("photo.jpg")); assertTrue(body.contains("name=\"isMain\"")); assertTrue(body.contains("true"))
    }
    @Test fun tripThumbnailIsCommittedBeforeOriginalAndOriginalDebtRetries() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip); val photo = repo.addPhoto(trip.id, byteArrayOf(1), "Caption")!!
        server.enqueue(ack(photo.id, "thumb/key")); server.enqueue(error("TOO_MANY_REQUESTS"))
        transport(encoder).execute(op(photo.id))
        val row = db.tripDao().photo(photo.id)!!; assertEquals("thumb/key", row.thumbnailURL); assertNull(row.remoteURL)
        assertEquals(PhotoUploadStatus.uploaded.rawValue, row.uploadStatus); assertEquals(SyncStatus.synced.rawValue, row.syncStatus)
        assertTrue(photo.id in repo.photosAwaitingOriginal()); assertTrue(server.takeRequest().body!!.utf8().contains("thumbnail")); assertTrue(server.takeRequest().body!!.utf8().contains("original"))
        server.enqueue(ack(photo.id, "original/key", 2)); transport(encoder).execute(op(photo.id))
        assertEquals("original/key", db.tripDao().photo(photo.id)!!.remoteURL); assertFalse(photo.id in repo.photosAwaitingOriginal())
        assertTrue(server.takeRequest().body!!.utf8().contains("original")); assertEquals(3, server.requestCount)
    }
    @Test fun vehiclePhotoFailureRetainsThumbnailForNextAttempt() = runBlocking<Unit> {
        val vehicleId = vehicles.addVehicle("Car", "🚗"); val photo = vehiclePhotos.add(byteArrayOf(1), vehicleId)!!
        server.enqueue(ack(photo.id, "thumb/key")); server.enqueue(error("TOO_MANY_REQUESTS"))
        assertFailsWith<ApiError.TooManyRequests> { transport(encoder).execute(op(photo.id, SyncEntityType.vehiclePhoto)) }
        assertEquals("thumb/key", db.vehicleDao().photo(photo.id)!!.thumbnailURL); assertNull(db.vehicleDao().photo(photo.id)!!.remoteURL)
        server.enqueue(ack(photo.id, "original/key", 2)); transport(encoder).execute(op(photo.id, SyncEntityType.vehiclePhoto))
        assertEquals("original/key", db.vehicleDao().photo(photo.id)!!.remoteURL); assertEquals(SyncStatus.synced.rawValue, db.vehicleDao().photo(photo.id)!!.syncStatus)
        assertEquals(3, server.requestCount)
    }
    @Test fun alreadyUploadedVehiclePhotoCanStillBecomeMain() = runBlocking<Unit> {
        val vehicleId = vehicles.addVehicle("Car", "🚗"); val photo = vehiclePhotos.add(byteArrayOf(1), vehicleId)!!
        db.vehicleDao().upsertPhoto(db.vehicleDao().photo(photo.id)!!.copy(thumbnailURL = "thumb", remoteURL = "original", isMain = true))
        server.enqueue(ok()); transport(encoder).execute(op(photo.id, SyncEntityType.vehiclePhoto, SyncAction.update))
        assertEquals("/vehicles/photos/main", server.takeRequest().url.encodedPath)
    }
    @Test fun privateParentAndCloudOffPreventPhotoUpload() = runBlocking<Unit> {
        val trip = tripPayload().copy(isPrivate = true); repo.applyRemoteTrip(trip); val photo = repo.addPhoto(trip.id, byteArrayOf(1), null)!!
        cloud = false; transport(encoder).execute(op(photo.id)); assertEquals(0, server.requestCount)
    }
    @Test fun queuedPhotoStaysLocalWhileHiddenAndUploadsAgainWhenVisible() = runBlocking<Unit> {
        val trip = tripPayload().copy(trackPoints = listOf(
            TrackPointPayload(UUID.randomUUID(), 45.0, 39.0, 10.0, 5.0, 0.0, 5.0, t0, false),
            TrackPointPayload(UUID.randomUUID(), 45.0, 39.001, 10.0, 5.0, 0.0, 5.0, t0.plusSeconds(30), false),
        ))
        repo.applyRemoteTrip(trip)
        val photo = repo.addPhoto(trip.id, byteArrayOf(1), null, capturedAt = t0.plusSeconds(10))!!
        repo.updateHiddenParts(trip.id, TripHiddenParts(sections = listOf(HiddenSection(UUID.randomUUID(), t0, t0.plusSeconds(20)))))
        transport(encoder).execute(op(photo.id))
        assertEquals(0, server.requestCount)
        assertNotNull(db.tripDao().photo(photo.id)); assertTrue(tripFiles.exists(db.tripDao().photo(photo.id)!!.filename))
        assertEquals(PhotoUploadStatus.localOnly.rawValue, db.tripDao().photo(photo.id)!!.uploadStatus)

        repo.updateHiddenParts(trip.id, null)
        server.enqueue(ack(photo.id, "thumb/key")); server.enqueue(ack(photo.id, "original/key", 2))
        transport(encoder).execute(op(photo.id))
        assertEquals(2, server.requestCount); assertEquals("original/key", db.tripDao().photo(photo.id)!!.remoteURL)
    }
    @Test fun unpublishRescuesPhotoBeforeDiscussionAndDeleteAndKeepsLocalTrip() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip); val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, tripId = trip.id, timestamp = t0, remoteURL = "remote/key", filename = "missing.jpg"))
        cloud = false
        server.enqueue(okValue(PhotoURLResponse("https://photos.trip-track.app/object", "2026-09-16T13:00:00Z"))); server.enqueue(ok())
        var archived = false
        val r2 = R2PhotoStorage(client) { byteArrayOf(1, 2) }
        transport(encoder, archive = {
            assertTrue(tripFiles.exists(db.tripDao().photo(photoId)!!.filename)); archived = true
        }, r2 = r2).execute(op(trip.id, SyncEntityType.trip, SyncAction.unpublish))
        assertTrue(archived); assertEquals(listOf("/photos/url", "/trips/delete"), List(2) { server.takeRequest().url.encodedPath })
        assertNotNull(repo.fetchEntity(trip.id)); assertNull(repo.fetchEntity(trip.id)!!.serverCreatedAt); assertNotNull(db.tripDao().photo(photoId))
        assertTrue(tripFiles.exists(db.tripDao().photo(photoId)!!.filename))
    }
    @Test fun failedRescueNeverDeletesTheLastServerCopy() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip); val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, tripId = trip.id, timestamp = t0, remoteURL = "remote/key", filename = "missing.jpg"))
        server.enqueue(okValue(PhotoURLResponse("https://photos.trip-track.app/object", "2026-09-16T13:00:00Z")))
        val brokenEncoder = object : PhotoEncoder { override suspend fun jpeg(data: ByteArray, maxDimension: Int, quality: Int): ByteArray? = null }
        assertFailsWith<ApiError.Transport> {
            transport(brokenEncoder, r2 = R2PhotoStorage(client) { byteArrayOf(1) }).execute(op(trip.id, SyncEntityType.trip, SyncAction.unpublish))
        }
        assertEquals(1, server.requestCount); assertNotNull(repo.fetchEntity(trip.id)!!.serverCreatedAt)
    }
    @Test fun originalDownloadFallsBackToThumbnailDuringRescue() = runBlocking<Unit> {
        val trip = tripPayload(); repo.applyRemoteTrip(trip); val photoId = UUID.randomUUID()
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, tripId = trip.id, timestamp = t0, thumbnailURL = "thumb", filename = "missing.jpg"))
        server.enqueue(error("PHOTO_NOT_FOUND")); server.enqueue(okValue(PhotoURLResponse("https://photos.trip-track.app/thumbnail", "2026-09-16T13:00:00Z"))); server.enqueue(ok())
        transport(encoder, r2 = R2PhotoStorage(client) { byteArrayOf(1) }).execute(op(trip.id, SyncEntityType.trip, SyncAction.unpublish))
        assertEquals(3, server.requestCount); assertTrue(tripFiles.exists(db.tripDao().photo(photoId)!!.filename))
    }
    @Test fun vehicleRestoreAddsMissingFilesWithoutDeletingLocalPhotosOrStoringSignedUrls() = runBlocking<Unit> {
        val vehicleId = vehicles.addVehicle("Car", "🚗"); val local = vehiclePhotos.add(byteArrayOf(1), vehicleId)!!; val remoteId = UUID.randomUUID()
        server.enqueue(okValue(VehiclePhotoListResponse(listOf(OwnedVehiclePhoto(remoteId, vehicleId, "https://photos.trip-track.app/signed", isMain = true, takenAt = t0)))))
        VehiclePhotoRestore(client, db, vehicleFiles, vehiclePhotos, { cloud }, { account }, { t0 }) { byteArrayOf(1, 2) }.run()
        val remote = db.vehicleDao().photo(remoteId)!!; assertTrue(vehicleFiles.exists(remote.filename)); assertNull(remote.remoteURL); assertNull(remote.thumbnailURL)
        assertNotNull(db.vehicleDao().photo(local.id))
    }
}
