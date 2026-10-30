package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.*
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import org.junit.Test

// iOS: APISyncTransport, CheckpointSyncFlagTests, VehicleEnergyWireTests, VehicleSyncConflictTests.
class PushPullTests : SyncTestCase() {
    private suspend fun seedTrip() = tripPayload().also { repo.applyRemoteTrip(it) }
    private fun operation(id: UUID, type: SyncEntityType = SyncEntityType.trip, action: SyncAction = SyncAction.update) = SyncOperation(type, id, action)
    @Test fun tripPushIncludesCheckpointSegmentBreakAndEnergyEdits() = runBlocking<Unit> {
        val trip = seedTrip(); val a = TripCheckpoint(timestamp = t0, latitude = 45.0, longitude = 38.9, distanceFromStart = 1000.0, elapsedFromStart = 60.0)
        val b = a.copy(id = UUID.randomUUID(), timestamp = t0.plusSeconds(60), distanceFromStart = 2000.0, elapsedFromStart = 120.0)
        repo.addCheckpoint(a, trip.id); repo.addCheckpoint(b, trip.id); repo.addSegment(trip.id, a.id, b.id)
        repo.setEnergyMode(TripEnergyMode.electric, trip.id)
        server.enqueue(okValue(TripUpsertResponse(trip.id, 3, t0)))
        transport().execute(operation(trip.id))
        val request = server.takeRequest(); assertEquals("/trips/upsert", request.url.encodedPath); assertEquals("test-access", request.headers["x-access-token"])
        val p = NetworkJson.wire.decodeFromString<TripSyncPayload>(request.body!!.utf8())
        assertEquals(2, p.checkpoints!!.size); assertEquals(1, p.segments!!.size); assertEquals("electric", p.energyMode); assertEquals(emptyList(), p.recordingBreaks)
        assertEquals(SyncStatus.synced.rawValue, repo.fetchEntity(trip.id)!!.syncStatus); assertEquals(3, repo.fetchEntity(trip.id)!!.conflictVersion)
    }
    @Test fun tripConflictFetchesDetailAndKeepsTheVersionGuard() = runBlocking<Unit> {
        val trip = seedTrip(); repo.updateTitle(trip.id, "Местное имя")
        server.enqueue(error("CONFLICT_DETECTED")); server.enqueue(okValue(trip.copy(title = "Серверное имя", conflictVersion = 7)))
        transport().execute(operation(trip.id))
        assertEquals("Серверное имя", repo.fetchEntity(trip.id)!!.title)
        assertEquals("/trips/upsert", server.takeRequest().url.encodedPath); assertEquals("/trips/detail", server.takeRequest().url.encodedPath)
    }
    @Test fun missingTripOnUpsertPurgesLocalRow() = runBlocking<Unit> {
        val trip = seedTrip(); server.enqueue(error("TRIP_NOT_FOUND")); transport().execute(operation(trip.id)); assertNull(repo.fetchEntity(trip.id))
    }
    @Test fun batchAcknowledgesOnlyKnownSuccessfulStatusesAndResolvedConflicts() = runBlocking<Unit> {
        val trips = List(4) { seedTrip() }; val ids = trips.map { it.id }
        server.enqueue(okValue(SyncPushResponse(listOf(SyncPushResponse.TripResult(ids[0], 2, "updated", t0),
            SyncPushResponse.TripResult(ids[1], 4, "conflict"), SyncPushResponse.TripResult(ids[2], 4, "future"),
            SyncPushResponse.TripResult(UUID.randomUUID(), 2, "updated")))))
        server.enqueue(okValue(trips[1].copy(conflictVersion = 4)))
        val progress = mutableListOf<Int>(); val handled = transport().uploadTripsBatch(ids.map { operation(it, action = SyncAction.upload) }, progress::add)
        assertEquals(setOf(ids[0], ids[1]), handled); assertEquals(listOf(2), progress)
        assertEquals(2, repo.fetchEntity(ids[0])!!.conflictVersion); assertEquals(1, repo.fetchEntity(ids[2])!!.conflictVersion)
    }
    @Test fun failedChunkFallsBackToPerTripWithoutLosingOperations() = runBlocking<Unit> {
        val trips = List(2) { seedTrip() }; server.enqueue(error("TOO_MANY_REQUESTS"))
        for (trip in trips) server.enqueue(okValue(TripUpsertResponse(trip.id, 2, t0)))
        val queue = SyncQueue(isAuthorized = { true }); queue.configure(transport())
        trips.forEach { queue.enqueue(operation(it.id, action = SyncAction.upload)) }; queue.processQueue()
        assertEquals(listOf("/sync/push", "/trips/upsert", "/trips/upsert"), List(3) { server.takeRequest().url.encodedPath })
        assertEquals(0, queue.pendingCount)
    }
    @Test fun queuedPrivateTripCannotUploadAfterCloudIsSwitchedOff() = runBlocking<Unit> {
        val trip = seedTrip(); repo.updatePrivacy(trip.id, true); cloud = false
        server.enqueue(ok()); transport().execute(operation(trip.id))
        assertEquals("/trips/delete", server.takeRequest().url.encodedPath); assertNotNull(repo.fetchEntity(trip.id)); assertNull(repo.fetchEntity(trip.id)!!.serverCreatedAt)
    }
    @Test fun queuedDraftIsRecheckedAtExecutionIncludingBatch() = runBlocking<Unit> {
        val trip = seedTrip(); db.tripDao().upsertTrip(repo.fetchEntity(trip.id)!!.copy(confirmation = "draft"))
        val t = transport(); t.execute(operation(trip.id)); assertTrue(t.uploadTripsBatch(listOf(operation(trip.id))) {}.isEmpty()); assertEquals(0, server.requestCount)
    }
    @Test fun hybridAndGarageFieldsReachTheRealEncoder() = runBlocking<Unit> {
        val id = vehicles.addVehicle("Astra", "🚗")
        vehicles.setPowertrain(id, Powertrain.pluginHybrid)
        vehicles.updateVehicleEnergy(id, 18.0, 8.0, 50.0)
        vehicles.updateVehicleGarage(id, drive2URL = "https://www.drive2.ru/r/opel/astra/12345/", modTags = listOf(" touring "), catalogModelId = "astra")
        server.enqueue(okValue(VehicleUpsertResponse(id, 3))); transport().execute(operation(id, SyncEntityType.vehicle))
        val p = NetworkJson.wire.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("pluginHybrid", p["powertrain"]!!.jsonPrimitive.content); assertEquals(18.0, p["electricConsumption"]!!.jsonPrimitive.double)
        assertEquals(8.0, p["electricityPrice"]!!.jsonPrimitive.double); assertEquals(50.0, p["electricRangeKm"]!!.jsonPrimitive.double)
        assertEquals(JsonNull, p["manualOdometerKm"]); assertEquals(JsonNull, p["soldAt"]); assertEquals("astra", p["catalogModelId"]!!.jsonPrimitive.content)
        assertEquals(listOf("touring"), p["modTags"]!!.jsonArray.map { it.jsonPrimitive.content }); assertFalse("manualOdometerKnown" in p)
    }
    @Test fun vehicleConflictClearsPendingAndAppliesServerVisibility() = runBlocking<Unit> {
        val id = vehicles.addVehicle("Местное имя", "🚗")
        server.enqueue(error("CONFLICT_DETECTED"))
        val fresh = VehicleSyncPayload(id, "Серверное имя", "🚗", 1000.0, level = 1, cityConsumption = 10.0, highwayConsumption = 6.0,
            fuelPrice = 56.0, conflictVersion = 7, lastModifiedAt = t0, visibleToOthers = false, powertrain = Powertrain.electric, electricConsumption = 20.0)
        server.enqueue(okValue(VehicleListResponse(listOf(fresh)))); transport().execute(operation(id, SyncEntityType.vehicle))
        val after = db.vehicleDao().vehicle(id)!!; assertEquals("Серверное имя", after.name); assertFalse(after.visibleToOthers)
        assertEquals("electric", after.powertrain); assertEquals(SyncStatus.synced.rawValue, after.syncStatus)
    }
    @Test fun newerVehicleEditWhileUploadingStaysPending() = runBlocking<Unit> {
        val id = vehicles.addVehicle("Before", "🚗")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = runBlocking {
                db.vehicleDao().upsertVehicle(db.vehicleDao().vehicle(id)!!.copy(name = "After", lastModifiedAt = t0.plusSeconds(1)))
                okValue(VehicleUpsertResponse(id, 3))
            }
        }
        transport().execute(operation(id, SyncEntityType.vehicle)); val row = db.vehicleDao().vehicle(id)!!
        assertEquals("After", row.name); assertEquals(3, row.conflictVersion); assertEquals(SyncStatus.pendingUpload.rawValue, row.syncStatus)
    }
    @Test fun settingsPushCarriesUnitsButNoAccountCosmeticsOrHome() = runBlocking<Unit> {
        db.settingsDao().upsert(UserSettingsEntity(localUser, distanceUnit = "miles", volumeUnit = "gallons", avatarFrame = "frame_gold", lastModifiedAt = t0))
        server.enqueue(okValue(SettingsUpsertResponse(3))); transport().execute(operation(localUser, SyncEntityType.settings))
        val json = NetworkJson.wire.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("miles", json["distanceUnit"]!!.jsonPrimitive.content); assertEquals("gallons", json["volumeUnit"]!!.jsonPrimitive.content)
        assertFalse("avatarFrame" in json); assertFalse("bio" in json); assertFalse("pinnedTripIds" in json); assertFalse("home" in json)
    }
}
