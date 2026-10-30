// iOS: SyncChunkBudgetTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.encodeToString
import org.junit.Test

class SyncChunkBudgetTests {
    private fun payload(points: Int): TripSyncPayload = TripSyncPayload(
        id = UUID.randomUUID(), title = "Краснодар → Ростов-на-Дону ".repeat(3), description = "заметка ".repeat(40),
        startDate = Instant.ofEpochSecond(1_700_000_000), endDate = Instant.ofEpochSecond(1_700_040_000),
        distance = 250_123.4, maxSpeed = 31.1, averageSpeed = 16.6, fuelUsed = 18.2, elevation = 320.0,
        maxAltitude = 410.0, drivingTime = 14_950, stoppedTime = 1_200, region = "Rostov Oblast", isPrivate = true,
        vehicleId = UUID.randomUUID(), fuelCurrency = "RUB", previewPolyline = Base64.getEncoder().encodeToString(ByteArray(1_500) { 0xAB.toByte() }),
        badgesJson = "[\"night_owl\",\"long_haul\"]", xpEarned = 120, conflictVersion = 3,
        lastModifiedAt = Instant.ofEpochSecond(1_700_050_000), trackPoints = List(points) { i ->
            TrackPointPayload(UUID.randomUUID(), 45.035_123_456_789 + i * 1e-6, 38.975_987_654_321 - i * 1e-6,
                40.5 + i % 7, 16.666_666_666, 123.456_789, 4.999_999, Instant.ofEpochSecond(1_700_000_000L + i), i % 5 == 0)
        },
    )

    @Test fun testEstimateIsAnUpperBoundWithinAThird() {
        val p = payload(5_000)
        val actual = NetworkJson.wire.encodeToString(p).toByteArray().size
        assertTrue(p.estimatedWireBytes >= actual)
        assertTrue(p.estimatedWireBytes < actual * 1.34)
    }

    @Test fun testAnOversizedSingleTripStillFormsItsOwnChunk() {
        val huge = SyncChunkBudget.estimate(89_000, 0, 0, 0)
        assertTrue(huge > SyncChunkBudget.maxBytes)
        assertFalse(SyncChunkBudget.shouldFlush(0, 0, huge))
        assertTrue(SyncChunkBudget.shouldFlush(1, huge, 1))
    }

    @Test fun testPackingByBytes() {
        val big = SyncChunkBudget.estimate(30_000, 0, 0, 0)
        assertTrue(SyncChunkBudget.shouldFlush(1, big, big))
        val mid = SyncChunkBudget.estimate(20_000, 5, 3, 1)
        assertFalse(SyncChunkBudget.shouldFlush(1, mid, mid))
        assertFalse(SyncChunkBudget.shouldFlush(1, 1, SyncChunkBudget.maxBytes - 1L))
    }

    @Test fun testMaxTripsStillCapsTheChunk() {
        val tiny = SyncChunkBudget.estimate(10, 0, 0, 0)
        assertFalse(SyncChunkBudget.shouldFlush(24, tiny * 24, tiny))
        assertTrue(SyncChunkBudget.shouldFlush(25, tiny * 25, tiny))
        val trips = List(26) { payload(0) }
        assertEquals(listOf(25, 1), SyncChunkBudget.pack(trips).map { it.size })
        assertEquals(trips, SyncChunkBudget.pack(trips).flatten())
    }
}
