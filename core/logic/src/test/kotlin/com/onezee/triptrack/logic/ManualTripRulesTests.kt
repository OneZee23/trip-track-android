// iOS: ManualTripBuilderTests.swift, ManualTripDateDefaultsTests.swift,
// ManualTripDurationPolicyTests.swift, ManualTripDesignTests.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.TripDistanceGate
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.logic.isJunk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualTripRulesTests {
    @Test fun builderUsesStoredPointsAndEvenTimestamps() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val start = now.minusSeconds(3600)
        val trip = ManualTripRules.build(listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.01)), start, 600.0, now, null, "Rostov → Coast")
        assertNotNull(trip)
        assertEquals(TripOrigin.manual, trip.source)
        assertEquals(RoadFillState.done, trip.roadFillState)
        assertEquals(600, trip.trackPoints.last().timestamp.epochSecond - trip.trackPoints.first().timestamp.epochSecond)
        assertEquals(TripDistanceGate.totalDistance(trip.trackPoints.map { TripDistanceGate.Sample(it.latitude, it.longitude, it.timestamp) }), trip.distance)
        assertEquals(-1.0, trip.trackPoints.first().horizontalAccuracy)
    }

    @Test fun builderRejectsFutureAndUnfinishedTrips() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val points = listOf(GeoPoint(45.0, 39.0), GeoPoint(45.01, 39.01))
        assertNull(ManualTripRules.build(points, now.plusSeconds(1), 60.0, now, null, null))
        assertNull(ManualTripRules.build(points, now.minusSeconds(30), 60.0, now, null, null))
        assertNull(ManualTripRules.build(points, now.minusSeconds(ManualTripRules.MAXIMUM_AGE_SECONDS.toLong() + 1), 60.0, now, null, null))
        assertNotNull(ManualTripRules.build(points, now.minusSeconds(60), 60.0, now, null, null))
        assertNotNull(ManualTripRules.build(points, now.minusSeconds(ManualTripRules.MAXIMUM_AGE_SECONDS.toLong()), 60.0, now, null, null))
        assertNull(ManualTripRules.build(emptyList(), now.minusSeconds(60), 60.0, now, null, null))
        assertNull(ManualTripRules.build(points, now.minusSeconds(60), 0.0, now, null, null))
    }

    @Test fun tooShortBuilderDurationCanZeroDistanceAndFeasibleFloorPreservesTheRoute() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val coordinates = (0 until 200).map { index ->
            val offset = index.toDouble()
            GeoPoint(45.0 + offset * 0.0005, 39.0 + kotlin.math.sin(offset / 20) * 0.0004)
        }
        val tooShort = ManualTripRules.build(coordinates, now.minusSeconds(60), 60.0, now, null, null)!!
        assertEquals(0.0, tooShort.distance, 0.001)
        assertTrue(tooShort.isJunk(now))

        val straightDistance = TripDistanceGate.totalDistance(coordinates.map { point ->
            TripDistanceGate.Sample(point.latitude, point.longitude, null)
        })
        val feasible = ManualTripRules.feasibleDuration(straightDistance)
        val usable = ManualTripRules.build(coordinates, now.minusMillis((feasible * 1000).toLong()), feasible, now, null, null)!!
        assertEquals(straightDistance, usable.distance, straightDistance * 0.01)
        assertTrue(!usable.isJunk(now))
    }

    @Test fun endedBoundaryIsInclusiveAndSharedWithTheBuilder() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        assertTrue(ManualTripRules.hasEnded(now.minusSeconds(3600), 3600.0, now))
        assertTrue(!ManualTripRules.hasEnded(now.minusSeconds(3600), 3601.0, now))
    }

    @Test fun builderFillsEvenSamplesAndManualMetadata() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val start = now.minusSeconds(90)
        val vehicle = java.util.UUID.randomUUID()
        val trip = ManualTripRules.build(
            listOf(GeoPoint(45.0, 39.0), GeoPoint(45.001, 39.001), GeoPoint(45.002, 39.002)),
            start, 90.0, now, vehicle, "  Test  ",
        )!!
        assertEquals(45, trip.trackPoints[1].timestamp.epochSecond - trip.trackPoints[0].timestamp.epochSecond)
        assertEquals(90, trip.trackPoints.last().timestamp.epochSecond - trip.trackPoints.first().timestamp.epochSecond)
        assertEquals(trip.averageSpeed, trip.maxSpeed)
        assertTrue(trip.trackPoints.all { it.altitude == 0.0 && it.course >= 0.0 })
        assertEquals(vehicle, trip.vehicleId)
        assertEquals("Test", trip.title)
        assertTrue(trip.titleIsCustom && trip.isPrivate)
        val unnamed = ManualTripRules.build(
            listOf(GeoPoint(45.0, 39.0), GeoPoint(45.001, 39.001)), start, 90.0, now, null, "  ",
        )!!
        assertNull(unnamed.title)
        assertTrue(!unnamed.titleIsCustom)
    }

    @Test fun dateDefaultsAndBoundsStayOrdered() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val zone = ZoneId.of("Europe/Moscow")
        assertEquals(Instant.parse("2026-11-06T06:00:00Z"), ManualTripRules.defaultStart(LocalDate.parse("2026-11-06"), now, zone))
        assertEquals(now.minusSeconds(3900), ManualTripRules.untouchedStart(now, 3600.0, now))
        val current = now.plusSeconds(3600)
        val bounds = ManualTripRules.bounds(current, now, zone)
        assertTrue(bounds.start <= bounds.endInclusive)
        assertEquals(current, bounds.endInclusive)
    }

    @Test fun durationAndTitleRulesMatchIos() {
        assertEquals(1800.0, ManualTripRules.duration(100.0, 1780.0, false, 900.0))
        assertEquals(5400.0, ManualTripRules.duration(100.0, 5410.0, false, 900.0))
        assertEquals(1800.0, ManualTripRules.duration(1800.0, 5400.0, true, 900.0))
        assertEquals("Rostov → Coast", ManualTripRules.title("Rostov", "Coast"))
        assertEquals("Rostov", ManualTripRules.title("Rostov", "rostov"))
        assertTrue(ManualTripRules.feasibleDuration(50_000.0) >= 60.0)
        assertEquals(900.0, ManualTripRules.minimumDuration(0.0))
        assertEquals(1800.0, ManualTripRules.minimumDuration(50_000.0))
        assertTrue(ManualTripRules.minimumDuration(40_000_000.0) > ManualTripRules.MAXIMUM_DURATION_SECONDS)
        assertNull(ManualTripRules.title(null, null))
    }

    @Test fun localSuggestionsKeepIosOrderAndDeduplicateWithinPlaceCell() {
        val home = ManualTripSuggestionPoint("Home", GeoPoint(45.0, 39.0))
        val nearHome = ManualTripSuggestionPoint("Nearby", GeoPoint(45.0005, 39.0))
        val place = ManualTripSuggestionPoint("Cottage", GeoPoint(46.0, 39.0))
        val recent = ManualTripSuggestionPoint("Recent", GeoPoint(47.0, 39.0))
        val suggestions = ManualTripSuggestions.build(home, listOf(nearHome, place), listOf(recent))
        assertEquals(listOf("Home", "Cottage", "Recent"), suggestions.map { it.name })
        assertEquals(listOf("Cottage", "Recent"), ManualTripSuggestions.build(home, listOf(place), listOf(recent), excluding = home.point).map { it.name })
    }

    @Test fun frequentPlacesSortByPassCountThenRecencyAndCapAtThree() {
        val early = Instant.parse("2026-01-01T00:00:00Z")
        val values = listOf(
            ManualTripFrequentPlace("old-tie", 4, early),
            ManualTripFrequentPlace("most", 9, early),
            ManualTripFrequentPlace("zero", 0, early.plusSeconds(99)),
            ManualTripFrequentPlace("new-tie", 4, early.plusSeconds(1)),
            ManualTripFrequentPlace("third", 2, early),
        )
        assertEquals(listOf("most", "new-tie", "old-tie"), ManualTripSuggestions.topFrequentPlaces(values))
        assertEquals(emptyList(), ManualTripSuggestions.topFrequentPlaces(values, limit = 0))
    }

    @Test fun suggestionsStopAtFiveAfterDedupe() {
        val points = (0..6).map { ManualTripSuggestionPoint("P$it", GeoPoint(40.0 + it, 30.0)) }
        assertEquals(listOf("P0", "P1", "P2", "P3", "P4"), ManualTripSuggestions.build(null, points, emptyList()).map { it.name })
    }

    @Test fun presetsUseNineAndMirrorTheTripWithoutCopyingDuration() {
        val now = Instant.parse("2026-11-06T12:00:00Z")
        val zone = ZoneId.of("Europe/Moscow")
        val day = ManualTripPreset.forDay(LocalDate.parse("2026-11-06"), now, zone)
        assertEquals(Instant.parse("2026-11-06T06:00:00Z"), day.startDate)
        val from = ManualTripSuggestionPoint("A", GeoPoint(45.0, 39.0))
        val to = ManualTripSuggestionPoint("B", GeoPoint(46.0, 40.0))
        val preset = ManualTripPreset.returnTrip(from, to, null, now.minusSeconds(3600), now)
        assertEquals(to, preset.from)
        assertEquals(from, preset.to)
        assertEquals(now, preset.startDate)
        assertNull(preset.durationSeconds)
        val vehicleId = java.util.UUID.randomUUID()
        val capped = ManualTripPreset.returnTrip(from, to, vehicleId, now, now)
        assertEquals(now, capped.startDate)
        assertEquals(vehicleId, capped.vehicleId)
    }

    @Test fun summaryLineUsesOneMiddleDotSeparator() {
        assertEquals("420 km · 5 hr · yesterday 09:00", ManualTripRules.summaryLine("420 km", "5 hr", "yesterday 09:00"))
    }

    @Test fun activeFieldMovesFromThenKeepsDestinationActive() {
        assertEquals(ManualTripField.from, ManualTripRules.activeField(null, null))
        assertEquals(ManualTripField.to, ManualTripRules.activeField("from", null))
        assertEquals(ManualTripField.to, ManualTripRules.activeField("from", "to"))
    }
}
