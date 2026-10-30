package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadLevel
import com.onezee.triptrack.model.RoadRarity
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripOrigin
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// iOS: RoadHistoryCountTests.swift (the SimilarRecordedTrips cases), SimilarRecordedTripsBudgetTests.swift
class SimilarRecordedTripsTests {
    private val date = Instant.ofEpochSecond(1_700_000_000)
    private val route = listOf(GeoPoint(40.123, 20.123), GeoPoint(40.243, 20.243))

    private fun trip(coordinates: List<GeoPoint> = route, previewOnly: Boolean = true) = Trip(
        startDate = date, endDate = date.plusSeconds(1200), distance = 15_000.0, maxSpeed = 25.0,
        trackPoints = if (previewOnly) emptyList() else coordinates.mapIndexed { index, c ->
            TrackPoint(latitude = c.latitude, longitude = c.longitude, speed = 15.0, timestamp = date.plusSeconds(index.toLong()))
        },
        previewPolyline = if (previewOnly) Trip.encodePolyline(coordinates) else null,
    )

    @Test
    fun restoredHistoryIsRecognizedWithoutAnyLocalRoadCollection() {
        assertEquals(3, SimilarRecordedTrips.count(trip(previewOnly = false), listOf(trip(), trip())))
    }

    @Test
    fun currentTripAndPublicPrivateCopiesCountOnceById() {
        val current = trip()
        val prior = trip()
        val publicCopy = prior.copy(isPrivate = false, isOnServer = true)
        val history = listOf(current, prior, publicCopy, current)
        assertEquals(2, SimilarRecordedTrips.count(current, history))
        assertEquals(2, SimilarRecordedTrips.count(current, history.reversed()))
        assertEquals(2, SimilarRecordedTrips.count(current, listOf(prior)))
    }

    @Test
    fun sparseSyncedPreviewMatchesDenseRecordingAndReverseDirection() {
        val dense = (0..240).map { GeoPoint(40.123 + it * 0.0005, 20.123 + it * 0.0005) }
        val current = trip(dense, previewOnly = false)
        val reverse = trip(route.reversed())
        assertEquals(3, SimilarRecordedTrips.count(current, listOf(trip(), reverse)))
    }

    @Test
    fun unknownGeometryDraftManualAndUnfinishedTripsDoNotInflateCount() {
        val current = trip()
        val history = listOf(
            trip().copy(previewPolyline = null),
            trip().copy(confirmation = TripConfirmation.draft),
            trip().copy(source = TripOrigin.manual),
            trip().copy(endDate = null),
            trip().copy(distance = 10.0, endDate = date.plusSeconds(10)),
            trip(),
        )
        assertEquals(2, SimilarRecordedTrips.count(current, history))
    }

    @Test
    fun sameEndpointsDoNotCountACompletelyDifferentRoute() {
        val detour = trip(listOf(route[0], GeoPoint(40.123, 20.9), GeoPoint(40.243, 20.9), route[1]))
        assertEquals(1, SimilarRecordedTrips.count(trip(), listOf(detour)))
        assertEquals(1, SimilarRecordedTrips.count(trip(), listOf(trip().copy(distance = 45_000.0))))
    }

    @Test
    fun knownRecordingGapsAreNotJoinedForSimilarity() {
        val current = trip()
        val paused = trip().copy(recordingBreaks = listOf(date.plusSeconds(60)))
        val dense = trip(previewOnly = false)
        val signalGap = dense.copy(trackPoints = dense.trackPoints.toMutableList().also {
            it[1] = TrackPoint(latitude = route[1].latitude, longitude = route[1].longitude, timestamp = date.plusSeconds(120))
        })
        val interpolated = dense.copy(trackPoints = dense.trackPoints.toMutableList().also {
            it[1] = TrackPoint(latitude = route[1].latitude, longitude = route[1].longitude, timestamp = date.plusSeconds(1), isInterpolated = true)
        })
        assertEquals(1, SimilarRecordedTrips.count(current, listOf(paused, signalGap, interpolated)))
        assertEquals(0, SimilarRecordedTrips.count(paused, listOf(current)))
        assertEquals(0, SimilarRecordedTrips.count(signalGap, listOf(current)))
    }

    @Test
    fun antimeridianRouteUsesTheShortCrossing() {
        val crossing = listOf(GeoPoint(40.123, 179.9), GeoPoint(40.123, -179.9))
        val denseCrossing = listOf(crossing[0], GeoPoint(40.123, 180.0), crossing[1])
        assertEquals(2, SimilarRecordedTrips.count(trip(crossing), listOf(trip(denseCrossing.reversed()))))
    }

    @Test
    fun invalidCoordinatesDoNotTurnIntoMatchingGridCells() {
        val invalid = trip(listOf(route[0], GeoPoint(Double.NaN, 20.0)))
        assertEquals(1, SimilarRecordedTrips.count(trip(), listOf(invalid)))
        assertEquals(0, SimilarRecordedTrips.count(invalid, listOf(trip())))
    }

    @Test
    fun fiveThousandTripHistoryStaysWithinBudget() {
        fun route(offset: Double) = (0 until 60).map { GeoPoint(52.28 + offset + it * 0.002, 76.95 + offset + it * 0.0015) }
        val current = trip(route(0.0))
        val history = (0 until 5_000).map { i -> if (i % 10 == 0) trip(route(0.0)) else trip(route((i % 97) * 0.05)) }
        SimilarRecordedTrips.count(current, history.take(50))
        var best = Double.POSITIVE_INFINITY
        repeat(3) {
            val start = System.nanoTime()
            SimilarRecordedTrips.count(current, history)
            best = minOf(best, (System.nanoTime() - start) / 1e6)
        }
        assertTrue(best < 100, "5000 trips took $best ms")
    }

    // iOS: GamificationModels.swift
    @Test
    fun rarityAndLevelThresholds() {
        assertEquals(RoadRarity.common, RoadRarity.from(19.9))
        assertEquals(RoadRarity.uncommon, RoadRarity.from(20.0))
        assertEquals(RoadRarity.rare, RoadRarity.from(100.0))
        assertEquals(RoadRarity.epic, RoadRarity.from(300.0))
        assertEquals(RoadRarity.legendary, RoadRarity.from(1000.0))
        assertEquals(listOf(RoadLevel.discovered, RoadLevel.bronze, RoadLevel.silver, RoadLevel.gold, RoadLevel.mastered),
            listOf(2, 3, 10, 25, 50).map { RoadLevel.from(it) })
    }

    @Test
    fun roadsMatchInEitherDirectionAndNeedSeventyPercentOverlap() {
        val start = RoadCollection.neighbourhood("ucfv0")
        val end = RoadCollection.neighbourhood("ucfv8")
        val fingerprint = setOf("ucfv0", "ucfv1", "ucfv2", "ucfv8")
        assertTrue(RoadCollection.matches("ucfv0", "ucfv8", "ucfv0,ucfv1,ucfv2,ucfv8", start, end, fingerprint))
        assertTrue(RoadCollection.matches("ucfv8", "ucfv0", "ucfv8,ucfv2,ucfv1,ucfv0", start, end, fingerprint))
        assertTrue(!RoadCollection.matches("ucfv0", "ucfv8", "ucfv0,ucfvx,ucfvy,ucfvz,ucfv8", start, end, fingerprint))
    }
}
