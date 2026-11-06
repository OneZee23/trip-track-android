// iOS: ManualTripBuilder.swift, ManualTripRewardsTests.swift, SyncQueue.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.logic.ManualTripRules
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TripOrigin
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManualTripRepositoryTests : RoomTestCase() {
    @Test fun manualTripPersistsProcessedTrackAndUsesNormalTripUpload() = test {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val trip = assertNotNull(ManualTripRules.build(
            listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.01), GeoPoint(45.02, 39.02)),
            now.minusSeconds(3600), 600.0, now, null, "Rostov → Coast"))

        assertTrue(repo.insertManualTrip(trip))

        val stored = assertNotNull(repo.fetchEntity(trip.id))
        assertEquals(TripOrigin.manual.rawValue, stored.source)
        assertTrue(stored.isTrackProcessed)
        assertEquals(RoadFillState.done.rawValue, stored.roadFillState)
        assertEquals(3, repo.fetchTripDetail(trip.id)?.trackPoints?.size)
        assertTrue(sink.enqueued.contains(SyncRequest(SyncEntityType.trip, trip.id, SyncAction.upload)))
    }
}
