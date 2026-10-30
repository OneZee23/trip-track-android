package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import mockwebserver3.Dispatcher
import mockwebserver3.RecordedRequest
import org.junit.Test

// iOS: SyncHealTests.swift, LastSyncedAtStoreTests, RevealedLayerSyncTests, SyncCoordinator.swift.
class SyncCoordinatorTests : SyncTestCase() {
    private lateinit var queue: SyncQueue
    private lateinit var gate: SyncEnqueuer
    private lateinit var cursor: LastSyncedAtStore
    private var recording = false
    private var profileCalls = 0
    private var restoreCalls = 0
    private fun coordinator() : SyncCoordinator {
        queue = SyncQueue(isAuthorized = { authorized }); queue.configure(transport())
        gate = SyncEnqueuer(db, queue, { authorized }, { cloud }, scope, {})
        cursor = LastSyncedAtStore(context.getSharedPreferences(UUID.randomUUID().toString(), 0), "test")
        return SyncCoordinator(client, db, repo, queue, gate, applier(), cursor, { "A" }, { account }, { authorized }, { false },
            { cloud }, { localUser }, { recording }, { restoreCalls++ }, { profileCalls++ })
    }
    @Test fun fullThenDeltaPullApplyRoomRowsAdvanceCursorAndEmitIds() = runBlocking<Unit> {
        val coordinator = coordinator(); val trip = tripPayload(); val events = mutableListOf<SyncPullCompleted>()
        val collect = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.completed.take(2).toList(events) }
        server.enqueue(okValue(response(listOf(trip)))); assertTrue(coordinator.runFullSync())
        server.enqueue(okValue(response())); assertTrue(coordinator.runFullSync()); collect.join()
        val first = NetworkJson.wire.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val second = NetworkJson.wire.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertFalse("lastSyncedAt" in first); assertEquals("2026-09-16T12:00:00.000Z", second["lastSyncedAt"]!!.jsonPrimitive.content)
        assertEquals(listOf(SyncPullCompleted(listOf(trip.id)), SyncPullCompleted(emptyList())), events)
        assertNotNull(repo.fetchEntity(trip.id)); assertEquals(2, profileCalls); assertEquals(2, restoreCalls); gate.close()
    }
    @Test fun failedPullDoesNotAdvanceCursor() = runBlocking<Unit> {
        val c = coordinator(); cursor.set(t0, account!!, "A"); server.enqueue(error("TOO_MANY_REQUESTS"))
        assertFalse(c.runFullSync()); assertEquals(t0, cursor.get(account!!, "A")); gate.close()
    }
    @Test fun invalidServerClockDoesNotAdvanceCursor() = runBlocking<Unit> {
        val c = coordinator(); cursor.set(t0, account!!, "A"); server.enqueue(okValue(response().copy(serverTime = "bad date")))
        c.runFullSync(); assertEquals(t0, cursor.get(account!!, "A")); gate.close()
    }
    @Test fun cloudOffAndRecordingBothSkipPullButSyncProfile() = runBlocking<Unit> {
        val c = coordinator(); cloud = false; assertTrue(c.runFullSync()); cloud = true; recording = true; assertTrue(c.runFullSync())
        assertEquals(0, server.requestCount); assertEquals(2, profileCalls); assertEquals(0, restoreCalls); gate.close()
    }
    @Test fun healRequestsEveryLibraryTypeOncePerStore() = runBlocking<Unit> {
        val c = coordinator(); cursor.set(t0, account!!, "A")
        server.enqueue(okValue(response(counts = SyncPullResponse.OwnedCounts(107, 0, 0))))
        server.enqueue(okValue(response()))
        assertTrue(c.runFullSync()); server.takeRequest()
        val body = NetworkJson.wire.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertFalse("lastSyncedAt" in body); assertEquals(listOf("trip", "vehicle", "photo", "journey", "discovery"), body["entityTypes"]!!.jsonArray.map { it.jsonPrimitive.content })
        server.enqueue(okValue(response(counts = SyncPullResponse.OwnedCounts(107, 0, 0))))
        assertTrue(c.runFullSync()); assertEquals(3, server.requestCount); gate.close()
    }
    @Test fun truncatedManifestNeverReuploadsRows() = runBlocking<Unit> {
        val c = coordinator(); repo.applyRemoteTrip(tripPayload())
        server.enqueue(okValue(response(counts = SyncPullResponse.OwnedCounts(0, 0, 0))))
        server.enqueue(okValue(SyncManifestResponse(emptyList(), emptyList(), emptyList(), true)))
        assertTrue(c.runFullSync()); assertEquals(2, server.requestCount); assertEquals(0, queue.pendingCount); gate.close()
    }
    @Test fun missingManifestTripIsRepushed() = runBlocking<Unit> {
        val c = coordinator(); val trip = tripPayload(); repo.applyRemoteTrip(trip)
        server.enqueue(okValue(response(counts = SyncPullResponse.OwnedCounts(0, 0, 0))))
        server.enqueue(okValue(SyncManifestResponse(emptyList(), emptyList(), emptyList(), false)))
        server.enqueue(okValue(TripUpsertResponse(trip.id, 2, t0)))
        assertTrue(c.runFullSync()); assertEquals(listOf("/sync/pull", "/sync/manifest", "/trips/upsert"), List(3) { server.takeRequest().url.encodedPath }); gate.close()
    }
    @Test fun accountChangedDuringPullCannotApplyOrAdvanceCursor() = runBlocking<Unit> {
        val c = coordinator(); val owner = account!!; val trip = tripPayload()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse { account = UUID.randomUUID(); return okValue(response(listOf(trip))) }
        }
        assertFalse(c.runFullSync()); assertNull(repo.fetchEntity(trip.id)); assertNull(cursor.get(owner, "A")); gate.close()
    }
    @Test fun recordingStartedDuringPullCannotApplyIt() = runBlocking<Unit> {
        val c = coordinator(); val trip = tripPayload()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): mockwebserver3.MockResponse { recording = true; return okValue(response(listOf(trip))) }
        }
        assertFalse(c.runFullSync()); assertNull(repo.fetchEntity(trip.id)); gate.close()
    }
    @Test fun fullPullRequestResetsCursorAndZoneChangeQueuesOnlyPublicConfirmedTrips() = runBlocking<Unit> {
        val c = coordinator(); cursor.set(t0, account!!, "A"); c.requestFullPull(); assertNull(cursor.get(account!!, "A"))
        val public = tripPayload(); repo.applyRemoteTrip(public); repo.applyRemoteTrip(tripPayload().copy(isPrivate = true))
        val draft = UUID.randomUUID(); db.tripDao().upsertTrip(TripEntity(draft, startDate = t0, isPrivate = false, confirmation = "draft"))
        c.privacyZoneChanged(); assertEquals(listOf(public.id), queue.state.value.pending.map { it.entityId }); gate.close()
    }
    @Test fun failedPullStillDrainsPublicWritesAndSyncsProfile() = runBlocking<Unit> {
        val c = coordinator(); val trip = tripPayload(); repo.applyRemoteTrip(trip)
        queue.enqueue(SyncOperation(SyncEntityType.trip, trip.id, SyncAction.update))
        server.enqueue(error("TOO_MANY_REQUESTS")); server.enqueue(okValue(TripUpsertResponse(trip.id, 2, t0)))
        assertFalse(c.runFullSync()); assertEquals(1, profileCalls); assertEquals(0, queue.pendingCount)
        assertEquals(listOf("/sync/pull", "/trips/upsert"), List(2) { server.takeRequest().url.encodedPath }); gate.close()
    }
    @Test fun failedManifestStillEmitsTheAppliedPullIds() = runBlocking<Unit> {
        val c = coordinator(); val local = tripPayload(); repo.applyRemoteTrip(local)
        val incoming = tripPayload(); val event = async(start = CoroutineStart.UNDISPATCHED) { c.completed.first() }
        server.enqueue(okValue(response(listOf(incoming), counts = SyncPullResponse.OwnedCounts(0, 0, 0))))
        server.enqueue(error("TOO_MANY_REQUESTS"))
        assertFalse(c.runFullSync()); assertEquals(listOf(incoming.id), event.await().appliedTripIds); assertNotNull(repo.fetchEntity(incoming.id)); gate.close()
    }
    @Test fun healDecisionKeepsIosInputsAndExpectedValues() {
        assertTrue(SyncCoordinator.shouldHeal(107, 0, null, "A")); assertFalse(SyncCoordinator.shouldHeal(107, 0, "A", "A"))
        assertTrue(SyncCoordinator.shouldHeal(107, 0, "A", "B")); assertFalse(SyncCoordinator.shouldHeal(107, 107, null, "A"))
        assertFalse(SyncCoordinator.shouldHeal(100, 107, null, "A")); assertFalse(SyncCoordinator.shouldHeal(107, 0, null, null))
        assertTrue(SyncCoordinator.shouldHeal(42, 0, null, "fresh")); assertFalse(SyncCoordinator.shouldHeal(42, 0, "fresh", "fresh"))
    }
}
