package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.*
import com.onezee.triptrack.data.sync.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: DraftSyncGateTests.swift, SyncEnqueuerJourneyTests.swift, SyncEnqueuer.allowsPhotoDelete.
class SyncEnqueuerTests : SyncTestCase() {
    private fun gate(queue: SyncQueue = SyncQueue(isAuthorized = { false })) = SyncEnqueuer(db, queue, { authorized }, { cloud }, scope, {})
    private fun request(type: SyncEntityType, id: UUID, action: SyncAction = SyncAction.upload) = SyncRequest(type, id, action)
    @Test fun tripAndJourneyPrivacyGatesMatchForEveryAction() = runBlocking<Unit> {
        cloud = false
        val gate = gate()
        for (type in listOf(SyncEntityType.trip, SyncEntityType.journey)) for (private in listOf(true, false)) for (mirrored in listOf(true, false)) {
            val id = UUID.randomUUID()
            if (type == SyncEntityType.trip) db.tripDao().upsertTrip(TripEntity(id, startDate = t0, isPrivate = private, serverCreatedAt = t0.takeIf { mirrored }))
            else db.journeyDao().upsert(JourneyEntity(id, startDate = t0, isPrivate = private, serverCreatedAt = t0.takeIf { mirrored }))
            for (action in SyncAction.entries) {
                val expected = when (action) { SyncAction.unpublish -> true; SyncAction.delete -> mirrored; else -> !private }
                assertEquals(expected, gate.shouldEnqueue(request(type, id, action)), "$type $private $mirrored $action")
            }
        }
        gate.close()
    }
    @Test fun draftAndDraftPhotoNeverLeaveWithEitherCloudSetting() = runBlocking<Unit> {
        val id = UUID.randomUUID(); val photoId = UUID.randomUUID()
        db.tripDao().upsertTrip(TripEntity(id, startDate = t0, isPrivate = false, confirmation = "draft"))
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, timestamp = t0, tripId = id))
        val gate = gate()
        for (cloudOn in listOf(true, false)) {
            cloud = cloudOn
            for ((type, entityId) in listOf(SyncEntityType.trip to id, SyncEntityType.photo to photoId)) {
                assertFalse(gate.enqueueIfAllowed(request(type, entityId)))
                assertFalse(gate.isDraftTrip(request(type, entityId, SyncAction.delete)))
            }
        }
        db.tripDao().upsertTrip(db.tripDao().trip(id)!!.copy(confirmation = "confirmed"))
        assertFalse(gate.isDraftTrip(request(SyncEntityType.trip, id))); gate.close()
    }
    @Test fun missingRowsFailClosedWhileUnpublishAlwaysPasses() = runBlocking<Unit> {
        cloud = false; val gate = gate()
        for (type in SyncEntityType.entries) assertFalse(gate.shouldEnqueue(request(type, UUID.randomUUID())))
        for (type in listOf(SyncEntityType.trip, SyncEntityType.journey)) assertTrue(gate.shouldEnqueue(request(type, UUID.randomUUID(), SyncAction.unpublish)))
        gate.close()
    }
    @Test fun vehiclesSettingsAndDiscoveriesStayLocalWithoutCloud() = runBlocking<Unit> {
        cloud = false; val gate = gate()
        for (type in listOf(SyncEntityType.vehicle, SyncEntityType.vehiclePhoto, SyncEntityType.settings, SyncEntityType.discovery)) {
            for (action in SyncAction.entries) assertFalse(gate.shouldEnqueue(request(type, UUID.randomUUID(), action)))
        }
        cloud = true
        for (type in SyncEntityType.entries) assertTrue(gate.shouldEnqueue(request(type, UUID.randomUUID())))
        gate.close()
    }
    @Test fun photoRidesParentPrivacyAndDeletedRowUsesCallersServerStamp() = runBlocking<Unit> {
        cloud = false; val id = UUID.randomUUID(); val photoId = UUID.randomUUID(); val gate = gate()
        db.tripDao().upsertTrip(TripEntity(id, startDate = t0, isPrivate = false))
        db.tripDao().upsertPhoto(TripPhotoEntity(photoId, timestamp = t0, tripId = id, remoteURL = "object/key"))
        assertTrue(gate.shouldEnqueue(request(SyncEntityType.photo, photoId)))
        db.tripDao().upsertTrip(db.tripDao().trip(id)!!.copy(isPrivate = true))
        assertFalse(gate.shouldEnqueue(request(SyncEntityType.photo, photoId)))
        db.tripDao().deletePhoto(photoId)
        assertTrue(gate.shouldEnqueue(SyncRequest(SyncEntityType.photo, photoId, SyncAction.delete, true)))
        assertFalse(gate.shouldEnqueue(SyncRequest(SyncEntityType.photo, photoId, SyncAction.delete, false)))
        assertFalse(gate.shouldEnqueue(request(SyncEntityType.photo, photoId, SyncAction.delete))); gate.close()
    }
    @Test fun unauthorizedCannotEnqueue() = runBlocking<Unit> {
        authorized = false; val queue = SyncQueue(isAuthorized = { true }); val gate = gate(queue)
        assertFalse(gate.enqueueIfAllowed(request(SyncEntityType.trip, UUID.randomUUID(), SyncAction.unpublish))); assertEquals(0, queue.pendingCount); gate.close()
    }
}
