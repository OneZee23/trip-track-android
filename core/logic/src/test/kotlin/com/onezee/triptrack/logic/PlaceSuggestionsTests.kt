package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

// iOS: PlaceSuggestionsTests.swift
class PlaceSuggestionsTests {
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val home = GeoPoint(45.035, 38.975)
    private val sea = GeoPoint(44.561, 38.077)
    private val rostov = GeoPoint(47.222, 39.719)
    private fun preview(from: GeoPoint, to: GeoPoint, daysAgo: Int = 0) =
        TripPreviewRef(UUID.randomUUID(), t0.minusSeconds(daysAgo * 86400L), Trip.encodePolyline(listOf(from, to)))
    private fun cell(c: GeoPoint) = Place.cell(c.latitude, c.longitude)
    private fun far(k: Int) = GeoPoint(46 + k * 0.2, 40.0)

    @Test fun testEndsOfTripsClusterIntoTheSameGeohashCell() {
        val found = PlaceSuggestions.build(listOf(preview(home, sea, 3), preview(home, rostov, 2),
            preview(home, sea, 1), preview(home, home, 40)), emptySet())
        val byCell = found.associate { it.cell to it.trips }
        assertEquals(4, byCell[cell(home)])
        assertEquals(2, byCell[cell(sea)])
        assertEquals(1, byCell[cell(rostov)])
        assertEquals(cell(home), found.first().cell)
    }
    @Test fun testRoundTripVotesOnceForItsOwnCell() {
        val found = PlaceSuggestions.build(listOf(preview(home, home, 1), preview(home, sea, 2)), emptySet())
        assertEquals(2, found.first { it.cell == cell(home) }.trips)
    }
    @Test fun testCentroidAveragesTheActualEnds() {
        val a = GeoPoint(45.0350, 38.9750)
        val b = GeoPoint(45.03505, 38.97505)
        assertEquals(cell(a), cell(b))
        val suggestion = PlaceSuggestions.build(listOf(preview(a, sea), preview(b, rostov)), emptySet()).first { it.cell == cell(a) }
        assertNear((a.latitude + b.latitude) / 2, suggestion.latitude, 1e-5)
        assertNear((a.longitude + b.longitude) / 2, suggestion.longitude, 1e-5)
    }
    @Test fun testTwoHitsAreNeededOnceTheLibraryIsBigEnough() {
        val previews = (0 until 5).map { preview(home, sea, it) } + preview(rostov, GeoPoint(43.4, 39.9), 9)
        val cells = PlaceSuggestions.build(previews, emptySet()).map { it.cell }.toSet()
        assertTrue(cell(home) in cells)
        assertFalse(cell(rostov) in cells)
    }
    @Test fun testASingleHitIsEnoughWhileTheLibraryIsTiny() {
        val found = PlaceSuggestions.build(listOf(preview(home, sea)), emptySet())
        assertEquals(setOf(cell(home), cell(sea)), found.map { it.cell }.toSet())
    }
    @Test fun testThresholdFlipsExactlyAtTheSmallLibraryBoundary() {
        assertEquals(5, PlaceSuggestions.smallLibrary)
        val lonely = GeoPoint(43.585, 39.723)
        val four = (0 until 3).map { preview(home, sea, it) } + preview(lonely, lonely, 8)
        assertTrue(PlaceSuggestions.build(four, emptySet()).any { it.cell == cell(lonely) })
        val five = four + preview(home, sea, 9)
        assertFalse(PlaceSuggestions.build(five, emptySet()).any { it.cell == cell(lonely) })
    }
    @Test fun testExistingPlacesAreNeverSuggested() {
        val cells = PlaceSuggestions.build((0 until 4).map { preview(home, sea, it) }, setOf(Place.id(cell(home)))).map { it.cell }.toSet()
        assertFalse(cell(home) in cells)
        assertTrue(cell(sea) in cells)
    }
    @Test fun testTombstonedPlacesStayForgotten() {
        val cells = PlaceSuggestions.build((0 until 4).map { preview(home, sea, it) }, setOf(Place.id(cell(sea)))).map { it.cell }.toSet()
        assertFalse(cell(sea) in cells)
    }
    @Test fun testTripsWithoutAPreviewAreSkipped() {
        assertTrue(PlaceSuggestions.build(listOf(TripPreviewRef(UUID.randomUUID(), t0, null)), emptySet()).isEmpty())
    }
    @Test fun testSortedByHitsThenRecency() {
        val found = PlaceSuggestions.build(listOf(preview(home, far(0), 30), preview(home, far(1), 29),
            preview(home, far(2), 28), preview(sea, far(3), 1), preview(sea, far(4), 2)), emptySet())
        assertEquals(listOf(cell(home), cell(sea)), found.map { it.cell })
        assertEquals(3, found.first().trips)
    }
    @Test fun testEqualCountsFallBackToTheFresherOne() {
        val old = GeoPoint(45.10, 38.90)
        val fresh = GeoPoint(45.20, 38.80)
        val found = PlaceSuggestions.build(listOf(preview(old, far(0), 10), preview(old, far(1), 11),
            preview(fresh, far(2), 1), preview(fresh, far(3), 2)), emptySet())
        assertEquals(listOf(cell(fresh), cell(old)), found.take(2).map { it.cell })
    }
    @Test fun testNeverMoreThanFive() {
        val previews = (0 until 10).flatMap { k ->
            val spot = GeoPoint(45 + k * 0.5, 38.5)
            listOf(preview(spot, spot, k * 3), preview(spot, rostov, k * 3 + 1))
        }
        assertEquals(PlaceSuggestions.limit, PlaceSuggestions.build(previews, emptySet()).size)
        assertEquals(5, PlaceSuggestions.limit)
    }
    @Test fun testOnlyOneSuggestionPerGeocoderCell() {
        val yard = GeoPoint(45.0350, 38.9750)
        val work = GeoPoint(45.0368, 38.9778)
        val shop = GeoPoint(45.0332, 38.9722)
        assertEquals(1, listOf(yard, work, shop).map { GeohashEncoder.encode(it.latitude, it.longitude, PlaceSuggestions.namePrecision) }.toSet().size)
        val previews = (0 until 4).map { preview(yard, rostov, it) } +
            (0 until 3).map { preview(work, rostov, 10 + it) } + (0 until 2).map { preview(shop, rostov, 20 + it) }
        val found = PlaceSuggestions.build(previews, emptySet())
        assertEquals(listOf(cell(yard)), found.filter { it.cell in listOf(cell(yard), cell(work), cell(shop)) }.map { it.cell })
        assertTrue(PlaceSuggestions.build(previews, setOf(Place.id(cell(yard)))).any { it.cell == cell(work) })
    }
    @Test fun testSuggestionIdMatchesThePlaceItWouldBecome() {
        for (suggestion in PlaceSuggestions.build(listOf(preview(home, sea)), emptySet())) {
            assertEquals(Place.id(suggestion.cell), suggestion.id)
        }
    }
    @Test fun lexicalTieBreakIsIndependentOfInputOrder() {
        val a = preview(home, home)
        val b = preview(sea, sea)
        val first = PlaceSuggestions.build(listOf(a, b), emptySet()).map { it.cell }
        assertEquals(first.sorted(), first)
        assertEquals(first, PlaceSuggestions.build(listOf(b, a), emptySet()).map { it.cell })
    }
}
