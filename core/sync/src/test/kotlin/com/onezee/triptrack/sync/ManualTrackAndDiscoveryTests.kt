package com.onezee.triptrack.sync

import com.onezee.triptrack.data.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

// iOS: ManualTripHydrationBudgetTests.swift, DiscoveryRevealTests.swift.
class ManualTrackAndDiscoveryTests : SyncTestCase() {
    @Test fun privacyUploadHydratesTheManualRouteBeforeTrimming() = runBlocking<Unit> {
        val trip = tripPayload().copy(source = TripOrigin.manual)
        repo.applyRemoteTrip(trip)
        val points = List(2) { TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 0.0, -1.0, -1.0, -1.0, t0.plusSeconds(it * 30L), false) }
        server.enqueue(okValue(trip.copy(trackPoints = points)))
        ManualTripTrackLoader(repo, client, { account }, { cloud }).prepareUpload(trip.id, true)
        assertEquals(2, repo.fetchTripDetail(trip.id)!!.trackPoints.size); assertEquals("/trips/detail", server.takeRequest().url.encodedPath)
    }
    @Test fun failedHydrationCannotPublishAnUntrimmedServerRoute() = runBlocking<Unit> {
        val trip = tripPayload().copy(source = TripOrigin.manual); repo.applyRemoteTrip(trip)
        zone = PrivacyZoneSpec(GeoPoint(45.0, 38.9), 500.0)
        server.enqueue(okValue(trip.copy(trackPoints = emptyList())))
        assertFailsWith<ApiError.Transport> { transport().execute(SyncOperation(com.onezee.triptrack.data.sync.SyncEntityType.trip, trip.id, com.onezee.triptrack.data.sync.SyncAction.update)) }
        assertEquals(1, server.requestCount); assertTrue(repo.fetchTripDetail(trip.id)!!.trackPoints.isEmpty())
    }
    @Test fun invalidHydrationRejectsWrongVersionDuplicateIdsAndOversizedRoutes() {
        val trip = tripPayload().copy(source = TripOrigin.manual)
        val snapshot = ManualTrackSnapshot(trip.id, trip.conflictVersion, trip.lastModifiedAt)
        val a = TrackPointPayload(UUID.randomUUID(), 45.0, 38.9, 0.0, -1.0, -1.0, -1.0, t0, false)
        val b = a.copy(id = UUID.randomUUID(), timestamp = t0.plusSeconds(30))
        assertNotNull(ManualTripTrackLoader.validatedPoints(trip.copy(trackPoints = listOf(a, b)), snapshot))
        assertNull(ManualTripTrackLoader.validatedPoints(trip.copy(conflictVersion = 2, trackPoints = listOf(a, b)), snapshot))
        assertNull(ManualTripTrackLoader.validatedPoints(trip.copy(trackPoints = listOf(a, a)), snapshot))
        assertNull(ManualTripTrackLoader.validatedPoints(trip.copy(trackPoints = List(10_001) { a }), snapshot))
        assertNull(ManualTripTrackLoader.validatedPoints(trip.copy(trackPoints = listOf(a, b.copy(latitude = 91.0))), snapshot))
    }
    @Test fun discoveryRetriesUseCatalogKeyAndKeepTheFirstFind() = runBlocking<Unit> {
        val store = RoomDiscoveryStore(db); val tripId = UUID.randomUUID()
        val item = Discovery(kind = DiscoveryKind.riddle, key = "pass:ubcr4xk", tripId = tripId, coordinate = GeoPoint(45.0, 38.9), foundAt = t0, symbol = SealSymbol.generic)
        store.upsert(listOf(item))
        server.enqueue(okValue(SecretRevealResponse(item.key, item.kind.rawValue, title = "Гумбаши", verified = true, foundAt = t0.plusSeconds(86_400))))
        DiscoverySync(SecretsApi(client), store) { true }.retry(item.id)
        val request = NetworkJson.wire.decodeFromString<SecretRevealRequest>(server.takeRequest().body!!.utf8())
        assertEquals(item.key, request.id); assertEquals(tripId, request.tripId); assertTrue(store.discovery(item.id)!!.verified); assertEquals(t0, store.discovery(item.id)!!.foundAt)
    }
    @Test fun discoveryPermanentRejectionsDropButThrottleAndNetworkRetainDebt() = runBlocking<Unit> {
        assertTrue(DiscoverySync.isPermanent(ApiError.UnknownServer("SECRET_NOT_FOUND", "test")))
        assertTrue(DiscoverySync.isPermanent(ApiError.InvalidHttpStatus(403)))
        assertFalse(DiscoverySync.isPermanent(ApiError.InvalidHttpStatus(408))); assertFalse(DiscoverySync.isPermanent(ApiError.InvalidHttpStatus(429)))
        assertFalse(DiscoverySync.isPermanent(ApiError.InvalidHttpStatus(500))); assertFalse(DiscoverySync.isPermanent(ApiError.TooManyRequests))
    }
}
