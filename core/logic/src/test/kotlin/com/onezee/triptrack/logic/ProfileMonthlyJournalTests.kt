package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.test.*
import org.junit.Test

// iOS: ProfileMonthlyJournalTests.swift
class ProfileMonthlyJournalTests {
    private val zone = ZoneId.of("UTC")

    private fun date(day: Int, month: Int = 10, year: Int = 2026) =
        LocalDateTime.of(year, month, day, 12, 0).atZone(zone).toInstant()

    private fun trip(day: Int, month: Int = 10, reversed: Boolean = false): Trip {
        val points =
            listOf(
                GeohashEncoder.centerCoordinate("ucftjk"),
                GeohashEncoder.centerCoordinate("ucftm1"),
            )
        return Trip(
            startDate = date(day, month),
            endDate = date(day, month).plusSeconds(600),
            distance = 5_000.0,
            previewPolyline = Trip.encodePolyline(if (reversed) points.reversed() else points),
        )
    }

    private fun prepare(
        trips: List<Trip>,
        pins: List<UUID> = emptyList(),
        journeys: List<Journey> = emptyList(),
        query: String = "",
    ) =
        PreparedProfileHistory.build(
            ProfileHistoryLibrary(trips),
            journeys,
            query,
            null,
            null,
            Locale.forLanguageTag("ru"),
            zone,
            pins,
        )

    @Test
    fun testDirectionsAndThreshold() {
        val trips = listOf(trip(3), trip(2, reversed = true), trip(1))
        assertTrue(HistoryFolding.routineTripIds(trips.take(2), zone = zone).isEmpty())
        assertEquals(trips.map { it.id }.toSet(), HistoryFolding.routineTripIds(trips, zone = zone))
    }

    @Test
    fun testExclusionTable() {
        val modifiers: List<Pair<String, (Trip) -> Trip>> =
            listOf(
                "photo" to
                    {
                        it.copy(
                            photos =
                                listOf(
                                    TripPhoto(
                                        id = UUID.randomUUID(),
                                        filename = "photo.jpg",
                                        timestamp = date(3),
                                    )
                                )
                        )
                    },
                "checkpoint" to
                    {
                        it.copy(
                            checkpoints =
                                listOf(
                                    TripCheckpoint(
                                        timestamp = date(3),
                                        latitude = 45.0,
                                        longitude = 39.0,
                                        distanceFromStart = 0.0,
                                        elapsedFromStart = 0.0,
                                    )
                                )
                        )
                    },
                "description" to { it.copy(tripDescription = "A memorable drive") },
                "draft" to { it.copy(confirmation = TripConfirmation.draft) },
                "unfinished" to { it.copy(endDate = null) },
                "no preview" to { it.copy(previewPolyline = null) },
                "invalid preview" to { it.copy(previewPolyline = byteArrayOf(1, 2)) },
            )
        for ((name, modify) in modifiers) assertTrue(
            HistoryFolding.routineTripIds(listOf(modify(trip(3)), trip(2), trip(1)), zone = zone)
                .isEmpty(),
            name,
        )
        assertEquals(
            3,
            HistoryFolding.routineTripIds(
                    listOf(trip(3).copy(tripDescription = " \n "), trip(2), trip(1)),
                    zone = zone,
                )
                .size,
        )
    }

    @Test
    fun testPinnedAndJourneyNeverFold() {
        val trips = listOf(trip(4), trip(3), trip(2), trip(1))
        assertEquals(
            trips.drop(1).map { it.id }.toSet(),
            HistoryFolding.routineTripIds(trips, pinnedTripIds = setOf(trips[0].id), zone = zone),
        )
        assertTrue(
            HistoryFolding.routineTripIds(
                    trips,
                    listOf(Journey(startDate = date(3), endDate = date(5))),
                    zone = zone,
                )
                .isEmpty()
        )
    }

    @Test
    fun testPrecisionSixUsesCellsNotExactCoordinatesOrCoarserCells() {
        val points = Trip.decodePolyline(trip(3).previewPolyline!!).toMutableList()
        points[0] = points[0].copy(latitude = points[0].latitude + 0.0001)
        val inside = trip(3).copy(previewPolyline = Trip.encodePolyline(points))
        assertEquals(
            3,
            HistoryFolding.routineTripIds(listOf(inside, trip(2), trip(1)), zone = zone).size,
        )
        points[0] = GeohashEncoder.centerCoordinate("ucftjm")
        val outside = trip(3).copy(previewPolyline = Trip.encodePolyline(points))
        assertTrue(
            HistoryFolding.routineTripIds(listOf(outside, trip(2), trip(1)), zone = zone).isEmpty()
        )
    }

    @Test
    fun testMonthsDoNotContributeToEachOthersThreshold() {
        assertTrue(
            HistoryFolding.routineTripIds(listOf(trip(2), trip(1), trip(30, 9)), zone = zone)
                .isEmpty()
        )
        val prepared =
            prepare(listOf(trip(3), trip(2), trip(1), trip(30, 9), trip(29, 9), trip(28, 9)))
        assertEquals(2, prepared.chapters.size)
        assertEquals(2, prepared.rows.size)
        assertEquals(6, prepared.gridRuns.flatten().size)
    }

    @Test
    fun testOneRoutineRowAtLatestRoutinePositionWithDistinctTitleSamples() {
        val routine =
            (8 downTo 1).mapIndexed { index, day ->
                trip(day).copy(title = if (index % 2 == 0) "Work" else "Town")
            }
        val notable = trip(9).copy(tripDescription = "Mountains")
        val result = prepare(listOf(notable) + routine)
        assertEquals(2, result.rows.size)
        assertEquals(notable.id, result.rows.first().id)
        val group = assertIs<HistoryRow.Routine>(result.rows[1]).value
        assertEquals(routine.map { it.id }, group.trips.map { it.id })
        assertEquals(40_000.0, group.distance)
        assertEquals(listOf("Work", "Town"), group.titleTrips.mapNotNull { it.title })
    }

    @Test
    fun testPinsFirstInSavedOrderWithoutChapterOrJourneyDoubles() {
        val trips = listOf(trip(4), trip(3), trip(2), trip(1))
        val pins = listOf(trips[3].id, trips[0].id)
        val result = prepare(trips, pins, listOf(Journey(startDate = date(1), endDate = date(5))))
        assertEquals(pins, result.pinned.map { it.id })
        val chapterIds =
            result.chapters
                .flatMap { it.rows }
                .flatMap { row ->
                    when (row) {
                        is HistoryRow.Trip -> listOf(row.value.id)
                        is HistoryRow.Journey -> row.legs.map { it.id }
                        is HistoryRow.Routine -> row.value.trips.map { it.id }
                    }
                }
        assertTrue(chapterIds.toSet().intersect(pins.toSet()).isEmpty())
        assertEquals(
            trips.map { it.id }.toSet(),
            (chapterIds + result.pinned.map { it.id }).toSet(),
        )
    }

    @Test
    fun testSummaryCountsOriginalTripsAndDistinctRegionsIncludingPins() {
        val trips =
            listOf(trip(4), trip(3), trip(2), trip(1)).mapIndexed { index, trip ->
                trip.copy(region = if (index < 2) "Berlin" else "Brandenburg")
            }
        val month = prepare(trips, listOf(trips[0].id)).chapters[0]
        assertEquals(20_000.0, month.distance)
        assertEquals(4, month.tripCount)
        assertEquals(2, month.regionCount)
        assertEquals("Октябрь", month.title(Locale.forLanguageTag("ru"), date(22), zone))
        assertEquals(
            "Октябрь 2026",
            month.title(Locale.forLanguageTag("ru"), date(22, year = 2027), zone),
        )
    }

    @Test
    fun testJourneyChapterUsesStartMonthAndSearchExposesTrips() {
        val trips = listOf(trip(2), trip(1), trip(30, 9))
        val journey = Journey(startDate = date(29, 9), endDate = date(3))
        assertEquals(
            listOf(monthStart(date(29, 9), zone)),
            prepare(trips, journeys = listOf(journey)).chapters.map { it.id },
        )
        assertEquals(
            3,
            prepare(
                    trips.map { it.copy(title = "Work") },
                    journeys = listOf(journey),
                    query = "Work",
                )
                .rows
                .size,
        )
    }

    @Test
    fun testDifferentRoutineRoutesShareOneMonthlyRowButKeepTheirOwnThreshold() {
        val first = listOf(trip(9), trip(8), trip(7))
        val preview =
            Trip.encodePolyline(
                listOf(
                    GeohashEncoder.centerCoordinate("ucftjm"),
                    GeohashEncoder.centerCoordinate("ucftm2"),
                )
            )
        val second = listOf(trip(6), trip(5), trip(4)).map { it.copy(previewPolyline = preview) }
        assertTrue(
            HistoryFolding.routineTripIds(first.take(2) + second.take(2), zone = zone).isEmpty()
        )
        val prepared = prepare(first + second)
        assertEquals(1, prepared.rows.size)
        val group = assertIs<HistoryRow.Routine>(prepared.rows[0]).value
        assertEquals(6, group.trips.size)
        assertEquals(first[0].id, group.id)
    }

    @Test
    fun testPublicGridKeepsJourneyLegsAndOmitsPinsThroughTheFoldingBoundary() {
        val trips = listOf(trip(4), trip(3), trip(2), trip(1))
        val j = Journey(startDate = date(1), endDate = date(5))
        val result =
            PreparedProfileHistory.build(
                ProfileHistoryLibrary(trips),
                listOf(j),
                "",
                null,
                null,
                Locale.ENGLISH,
                zone,
                listOf(trips[0].id),
                foldsRoutine = false,
                keepsEmptyJourneys = false,
                foldsJourneysInGrid = false,
            )
        assertEquals(listOf(j.id), result.rows.map { it.id })
        assertEquals(listOf(trips[0].id), result.pinned.map { it.id })
        assertEquals(trips.drop(1).map { it.id }, result.gridRuns.flatten().map { it.id })
    }

    @Test
    fun testForeignUnknownFieldsNeverFold() {
        val result =
            PreparedProfileHistory.build(
                ProfileHistoryLibrary(listOf(trip(3), trip(2), trip(1))),
                emptyList(),
                "",
                null,
                null,
                Locale.ENGLISH,
                zone,
                foldsRoutine = false,
            )
        assertEquals(3, result.rows.size)
    }
}
