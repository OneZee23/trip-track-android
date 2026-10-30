// iOS: TripRepositoryTrackPointsReplaceTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.TrackPointPayload
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The server's points REPLACE the local ones; an absent key leaves them; an explicit empty list is
 * «the other phone deleted the track». On iOS the delete branch once never ran (a cast that always
 * failed) and the points piled up next to the server's set.
 */
class TripRepositoryTrackPointsReplaceTests : RoomTestCase() {
    private val tripId = UUID.randomUUID()
    private val start = Instant.ofEpochSecond(1_700_000_000)

    @Before
    fun seed() = test {
        insert(TripEntity(id = tripId, startDate = start, endDate = start.plusSeconds(3_600), isPrivate = true, syncStatus = synced))
        db.tripDao().insertTrackPoints(
            (0 until 2).map { i ->
                TrackPointEntity(
                    id = UUID.randomUUID(), latitude = 45.0, longitude = 38.0 + i,
                    timestamp = start.plusSeconds(i * 10L), tripId = tripId,
                )
            },
        )
    }

    private fun payload(trackPoints: List<TrackPointPayload>?) = tripPayload(
        id = tripId, startDate = start, endDate = Instant.ofEpochSecond(1_700_003_600),
        trackPoints = trackPoints,
    )

    @Test
    fun testServerTrackPointsReplaceLocalOnes() = test {
        val newPoint = TrackPointPayload(
            id = UUID.randomUUID(), latitude = 46.0, longitude = 39.0, altitude = 0.0, speed = 0.0,
            course = -1.0, horizontalAccuracy = 5.0, timestamp = Instant.now(), isInterpolated = false,
        )

        repo.applyRemoteTrip(payload(listOf(newPoint)))

        val points = db.tripDao().orderedTrackPoints(tripId)
        assertEquals(1, points.size, "old points must be replaced, not accumulated")
        assertEquals(newPoint.id, points.first().id)
    }

    /** A pull delta omits points entirely: local ones survive. */
    @Test
    fun testMissingTrackPointsKeyKeepsLocalPoints() = test {
        repo.applyRemoteTrip(payload(null))

        assertEquals(2, db.tripDao().orderedTrackPoints(tripId).size, "an absent key must not touch local points")
    }

    @Test
    fun testEmptyServerTrackPointsDeleteAllLocalPoints() = test {
        repo.applyRemoteTrip(payload(emptyList()))

        assertEquals(0, db.tripDao().orderedTrackPoints(tripId).size, "an empty list replaces local points whole")
    }
}
