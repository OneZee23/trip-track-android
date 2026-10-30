package com.onezee.triptrack.logic

import com.onezee.triptrack.logic.JourneyTestKit.georgia
import com.onezee.triptrack.logic.JourneyTestKit.krd
import com.onezee.triptrack.logic.JourneyTestKit.trip
import com.onezee.triptrack.model.*
import java.time.Instant
import kotlin.test.*
import org.junit.Test

// iOS: JourneySuggesterTests.swift
class JourneySuggesterTests {
    private val work = GeoPoint(45.75, 38.98)
    private val dacha = GeoPoint(45.93, 38.98)

    private fun commute() =
        (0 until 14).flatMap { day ->
            listOf(trip(day, 8.0, krd, work, 80.0, 1.2), trip(day, 18.0, work, krd, 80.0, 1.3))
        }

    private fun suggestion(
        trips: List<Trip>,
        existing: List<Journey> = emptyList(),
        delay: Long = 3_600,
    ): List<Trip>? =
        JourneySuggester.suggestion(trips, krd, existing, trips.last().endDate!!.plusSeconds(delay))

    @Test
    fun testInferHomePicksTheCellWithTheMostNights() {
        val trips =
            commute() +
                listOf(1, 2, 3).flatMap { day ->
                    listOf(
                        trip(day, 20.0, krd, dacha, 100.0, 1.5),
                        trip(day + 1, 7.0, dacha, krd, 100.0, 1.5),
                    )
                }
        val home = assertNotNull(JourneySuggester.inferHome(trips))
        assertEquals(krd.latitude, home.latitude, 0.002)
        assertEquals(krd.longitude, home.longitude, 0.002)
    }

    @Test
    fun testInferHomeStaysSilentOnThinData() {
        assertNull(
            JourneySuggester.inferHome((0 until 5).map { trip(it, 18.0, work, krd, 80.0, 1.3) })
        )
    }

    @Test
    fun testWholeGeorgiaChainIsSuggested() {
        val trips = georgia()
        val s = suggestion(trips)
        assertEquals(6, s?.size)
        assertEquals(trips.map { it.id }, s?.map { it.id })
    }

    @Test
    fun testSameHotelEveryNightIsStillAJourney() {
        val hotel = GeoPoint(47.73, 38.98)
        val nearby = GeoPoint(47.75, 38.98)
        val trips =
            listOf(trip(0, 9.0, krd, hotel, 300.0, 4.0)) +
                (1..4).map { trip(it, 19.0, nearby, hotel, 12.0, 1.0) } +
                trip(5, 9.0, hotel, krd, 300.0, 4.0)
        assertEquals(trips.map { it.id }, suggestion(trips)?.map { it.id })
    }

    @Test
    fun testSuggestionWaitsForTheWayBack() {
        assertNull(suggestion(georgia().take(4)))
    }

    @Test
    fun testDailyCommuteIsNotAJourney() {
        assertNull(suggestion(commute()))
    }

    @Test
    fun testSameDayOutAndBackIsNotAJourney() {
        val spot = GeoPoint(44.32, 38.70)
        assertNull(
            suggestion(
                listOf(trip(0, 7.0, krd, spot, 200.0, 3.0), trip(0, 18.0, spot, krd, 200.0, 3.2))
            )
        )
    }

    @Test
    fun testNightTenKilometresFromHomeIsNotAJourney() {
        val near = GeoPoint(45.12, 38.98)
        assertNull(
            suggestion(
                listOf(trip(0, 22.0, krd, near, 10.0, 0.3), trip(1, 10.0, near, krd, 10.0, 0.3))
            )
        )
    }

    @Test
    fun testWeekendsAtTheDachaAreUsualEnvironment() {
        assertNull(
            suggestion(
                (0 until 4).flatMap { week ->
                    listOf(
                        trip(week * 7, 10.0, krd, dacha, 100.0, 1.5),
                        trip(week * 7 + 1, 17.0, dacha, krd, 100.0, 1.5),
                    )
                }
            )
        )
    }

    @Test
    fun testTripAlreadyInAJourneyBreaksTheChain() {
        val trips = georgia()
        val taken =
            Journey(
                startDate = trips[1].startDate.minusSeconds(60),
                endDate = trips[1].startDate.plusSeconds(60),
            )
        assertNull(suggestion(trips, listOf(taken)))
    }

    @Test
    fun testHomeQuestionComesBackAMonthAfterNo() {
        val now = Instant.now()
        assertTrue(
            JourneySuggester.shouldAskHome(
                null,
                false,
                now.addingSeconds(-JourneySuggester.homeReaskDelay - 60),
                now,
            )
        )
    }

    @Test
    fun testHomeQuestionStaysAwayRightAfterNo() {
        val now = Instant.now()
        assertFalse(JourneySuggester.shouldAskHome(null, false, now.minusSeconds(2 * 86_400), now))
    }

    @Test
    fun testHomeQuestionNeverComesBackAfterYes() {
        val now = Instant.now()
        assertFalse(JourneySuggester.shouldAskHome(null, true, null, now))
        assertFalse(JourneySuggester.shouldAskHome(krd, true, null, now))
    }

    @Test
    fun testHomeQuestionIsAskedWhenNobodyAnsweredYet() {
        assertTrue(JourneySuggester.shouldAskHome(null, false, null, Instant.now()))
    }

    @Test
    fun testHomeQuestionIsSilentWhenHomeIsAlreadyKnown() {
        assertFalse(JourneySuggester.shouldAskHome(krd, false, null, Instant.now()))
    }

    @Test
    fun testOldChainIsNotOfferedForever() {
        assertNull(suggestion(georgia(), delay = 30 * 86_400))
    }

    private val gelendzhik = GeoPoint(44.56, 38.08)
    private val divnomorskoye = GeoPoint(44.50, 38.13)

    @Test
    fun testChainJoinsTwoLegsOfTheSameDay() {
        val a = trip(0, 10.0, krd, gelendzhik, 177.0, 2.5)
        val b = trip(0, 15.0, gelendzhik, divnomorskoye, 9.6, 0.23)
        assertEquals(
            listOf(a.id, b.id),
            JourneySuggester.chainAround(b, listOf(a, b)).map { it.id },
        )
    }

    @Test
    fun testChainSkipsAYesterdayTripSomewhereElse() {
        val town = GeoPoint(45.57, 38.98)
        val city = trip(-1, 18.0, town, town, 12.0, 0.5)
        val a = trip(0, 10.0, krd, gelendzhik, 177.0, 2.5)
        val b = trip(0, 15.0, gelendzhik, divnomorskoye, 9.6, 0.23)
        assertEquals(
            listOf(a.id, b.id),
            JourneySuggester.chainAround(b, listOf(city, a, b)).map { it.id },
        )
    }

    @Test
    fun testChainBreaksOnALongGapAtTheSameSpot() {
        val a = trip(0, 10.0, krd, gelendzhik, 177.0, 2.5)
        val b = trip(0, 15.0, gelendzhik, divnomorskoye, 9.6, 0.23)
        val later = trip(3, 12.0, divnomorskoye, krd, 190.0, 3.0)
        assertEquals(
            listOf(a.id, b.id),
            JourneySuggester.chainAround(b, listOf(a, b, later)).map { it.id },
        )
    }

    @Test
    fun testChainKeepsTheAnchorAlone() {
        val a = trip(0, 10.0, krd, gelendzhik, 177.0, 2.5)
        assertEquals(listOf(a.id), JourneySuggester.chainAround(a, listOf(a)).map { it.id })
    }
}
