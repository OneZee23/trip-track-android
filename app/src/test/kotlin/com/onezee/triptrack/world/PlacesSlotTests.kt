// iOS: PlacesSlotTests.swift, PlacesScreenStateTests.swift (all cases, same inputs and numbers)
package com.onezee.triptrack.world

import com.onezee.triptrack.feature.places.PlacesEmptyState
import com.onezee.triptrack.feature.places.PlacesPanelStop
import com.onezee.triptrack.feature.places.PlacesSearchOutcome
import com.onezee.triptrack.feature.places.PlacesSlot
import com.onezee.triptrack.feature.places.SlotGesture
import com.onezee.triptrack.logic.filter
import com.onezee.triptrack.model.Place
import com.onezee.triptrack.model.PlaceListItem
import com.onezee.triptrack.model.PlaceStats
import com.onezee.triptrack.model.PlacesPresentation
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

class PlacesSlotTests {
    private val se = PlacesSlot(667.0, 20.0, 0.0)
    private val mini = PlacesSlot(812.0, 50.0, 34.0)
    private val thirteen = PlacesSlot(844.0, 47.0, 34.0)
    private val sixteen = PlacesSlot(852.0, 59.0, 34.0)
    private val proMax = PlacesSlot(932.0, 59.0, 34.0)

    @Test fun stopsMatchTheSpecTable() {
        assertEquals(654.0, thirteen.top(PlacesPanelStop.map))
        assertEquals(590.0, thirteen.top(PlacesPanelStop.selectedPlace))
        assertEquals(522.0, thirteen.top(PlacesPanelStop.selectedHint))
        assertEquals(380.0, thirteen.top(PlacesPanelStop.half))
        assertEquals(280.0, thirteen.top(PlacesPanelStop.empty))
        assertEquals(103.0, thirteen.top(PlacesPanelStop.list), "safe area 47 + 56")

        assertEquals(622.0, mini.top(PlacesPanelStop.map))
        assertEquals(348.0, mini.top(PlacesPanelStop.half))
        assertEquals(106.0, mini.top(PlacesPanelStop.list))
        assertEquals(248.0, mini.top(PlacesPanelStop.empty))

        assertEquals(662.0, sixteen.top(PlacesPanelStop.map))
        assertEquals(388.0, sixteen.top(PlacesPanelStop.half))
        assertEquals(115.0, sixteen.top(PlacesPanelStop.list))
        assertEquals(288.0, sixteen.top(PlacesPanelStop.empty))

        assertEquals(742.0, proMax.top(PlacesPanelStop.map))
        assertEquals(468.0, proMax.top(PlacesPanelStop.half))
        assertEquals(115.0, proMax.top(PlacesPanelStop.list))
        assertEquals(368.0, proMax.top(PlacesPanelStop.empty))
    }

    /** The tab bar's gap is 22 everywhere: an SE stands 12 below the spec's table, a recorded difference. */
    @Test fun seIsTwelvePointsBelowTheTableAndHasNoHalf() {
        assertEquals(489.0 - 12, se.top(PlacesPanelStop.map))
        assertEquals(115.0 - 12, se.top(PlacesPanelStop.empty))
        assertEquals(289.0 - 12, se.placeSheetTop)
        assertEquals(76.0, se.top(PlacesPanelStop.list), "the top position counts from the safe area")

        assertFalse(se.showsHalf)
        assertEquals(PlacesPanelStop.list, se.defaultStop)
        assertEquals(listOf(PlacesPanelStop.list, PlacesPanelStop.map), se.reachableStops)
        for (slot in listOf(mini, thirteen, sixteen, proMax)) {
            assertTrue(slot.showsHalf)
            assertEquals(PlacesPanelStop.half, slot.defaultStop)
        }
    }

    @Test fun theListNeverSlidesUnderTheStatusBar() {
        for (slot in listOf(se, mini, thirteen, sixteen, proMax)) {
            assertTrue(slot.top(PlacesPanelStop.list) >= slot.safeTop)
            assertEquals(56.0, slot.top(PlacesPanelStop.list) - slot.safeTop)
        }
    }

    @Test fun settleBoundariesSitAtFortyFivePercent() {
        val map = thirteen.top(PlacesPanelStop.map)
        val half = thirteen.top(PlacesPanelStop.half)
        val list = thirteen.top(PlacesPanelStop.list)
        val upper = SlotGesture.boundary(map, half)
        val lower = SlotGesture.boundary(half, list)
        assertEquals(PlacesPanelStop.map, thirteen.settle(upper + 1, 0.0, PlacesPanelStop.half))
        assertEquals(PlacesPanelStop.half, thirteen.settle(upper - 1, 0.0, PlacesPanelStop.map))
        assertEquals(PlacesPanelStop.half, thirteen.settle(lower + 1, 0.0, PlacesPanelStop.list))
        assertEquals(PlacesPanelStop.list, thirteen.settle(lower - 1, 0.0, PlacesPanelStop.half))
    }

    /** A flick goes to the NEIGHBOUR in its direction, one stop, not across the scale. */
    @Test fun aFlickMovesExactlyOneStop() {
        assertEquals(PlacesPanelStop.half, thirteen.settle(640.0, -400.0, PlacesPanelStop.map), "up from the map is the half, not the list")
        assertEquals(PlacesPanelStop.list, thirteen.settle(370.0, -400.0, PlacesPanelStop.half))
        assertEquals(PlacesPanelStop.half, thirteen.settle(120.0, 400.0, PlacesPanelStop.list))
        assertEquals(PlacesPanelStop.map, thirteen.settle(640.0, -299.0, PlacesPanelStop.map), "299 pt/s is not a flick yet")
    }

    @Test fun onSEAFlickSkipsTheMissingHalf() {
        assertEquals(PlacesPanelStop.list, se.settle(470.0, -400.0, PlacesPanelStop.map))
        assertEquals(PlacesPanelStop.map, se.settle(90.0, 400.0, PlacesPanelStop.list))
        val boundary = SlotGesture.boundary(se.top(PlacesPanelStop.map), se.top(PlacesPanelStop.list))
        assertEquals(PlacesPanelStop.map, se.settle(boundary + 1, 0.0, PlacesPanelStop.list))
        assertEquals(PlacesPanelStop.list, se.settle(boundary - 1, 0.0, PlacesPanelStop.map))
    }

    @Test fun rubberBandOutsideTheBounds() {
        assertEquals(300.0, thirteen.clamped(300.0), "inside the bounds the finger leads one to one")
        assertEquals(thirteen.top(PlacesPanelStop.list) - 30, thirteen.clamped(thirteen.top(PlacesPanelStop.list) - 100), 0.001)
        assertEquals(654.0 + 30, thirteen.clamped(654.0 + 100), 0.001)
    }

    /** The map's inset never follows the list: the camera fits by the sum of insets. */
    @Test fun mapInsetNeverFollowsTheList() {
        assertEquals(thirteen.mapBottomInset(PlacesPanelStop.half), thirteen.mapBottomInset(PlacesPanelStop.list))
        val framing = listOf(PlacesPanelStop.map, PlacesPanelStop.selectedPlace, PlacesPanelStop.selectedHint, PlacesPanelStop.half, PlacesPanelStop.list)
        for (slot in listOf(se, mini, thirteen, sixteen, proMax)) {
            for (stop in PlacesPanelStop.entries) assertTrue(slot.mapBottomInset(stop) > 0, "$slot $stop")
            for (stop in framing) assertTrue(slot.height - slot.safeTop - slot.mapBottomInset(stop) > 120, "no frame left: $slot $stop")
        }
    }

    // PlacesScreenStateTests

    @Test fun suggestionsDoNotCancelTheEmptyTab() {
        assertEquals(PlacesEmptyState.noPlaces, PlacesEmptyState.resolve(0, 3))
        assertEquals(PlacesEmptyState.noPlaces, PlacesEmptyState.resolve(0, 1), "one trip is state 13/14, not 15")
        assertEquals(PlacesPanelStop.empty, PlacesEmptyState.resolve(0, 3).stop(thirteen))
        assertEquals(280.0, thirteen.top(PlacesPanelStop.empty))
    }

    @Test fun noTripsAtAllStandsAtTheDefaultStop() {
        val state = PlacesEmptyState.resolve(0, 0)
        assertEquals(PlacesEmptyState.noTrips, state)
        assertEquals(PlacesPanelStop.half, state.stop(thirteen))
        assertEquals(380.0, thirteen.top(PlacesPanelStop.half))
        assertEquals(PlacesPanelStop.list, state.stop(se))
    }

    @Test fun aPlaceEndsEveryEmptyState() {
        for (trips in 0..3) assertEquals(PlacesEmptyState.notEmpty, PlacesEmptyState.resolve(1, trips))
        assertEquals(thirteen.defaultStop, PlacesEmptyState.notEmpty.stop(thirteen))
    }

    @Test fun emptyStopsArePinnedAndUnreachableByFinger() {
        assertTrue(PlacesEmptyState.noPlaces.isPinned)
        assertTrue(PlacesEmptyState.noTrips.isPinned)
        assertFalse(PlacesEmptyState.notEmpty.isPinned)
        assertFalse(PlacesPanelStop.empty.isDraggable)
        assertFalse(PlacesPanelStop.empty in thirteen.reachableStops)
        assertFalse(PlacesPanelStop.empty in se.reachableStops)
        var top = 0.0
        while (top <= 844) {
            for (velocity in listOf(-900.0, 0.0, 900.0)) assertNotEquals(PlacesPanelStop.empty, thirteen.settle(top, velocity, PlacesPanelStop.half))
            top += 20
        }
    }

    @Test fun emptyQueryIsNotASearch() {
        assertEquals(PlacesSearchOutcome.idle, PlacesSearchOutcome.resolve("", 0))
        assertEquals(PlacesSearchOutcome.idle, PlacesSearchOutcome.resolve("   ", 0), "a field with one space is not a search yet")
        assertEquals(PlacesSearchOutcome.idle, PlacesSearchOutcome.resolve("\n\t ", 12))
    }

    @Test fun matchesAndNothingFound() {
        assertEquals(PlacesSearchOutcome.matches, PlacesSearchOutcome.resolve("дач", 2))
        assertEquals(PlacesSearchOutcome.nothingFound, PlacesSearchOutcome.resolve("дач", 0))
        assertEquals(PlacesSearchOutcome.nothingFound, PlacesSearchOutcome.resolve("  дач  ", 0), "spaces round it do not empty the query")
    }

    @Test fun searchPathNeverGroups() {
        val day = Instant.ofEpochSecond(1_700_000_000)
        val items = (0 until 10).map { index ->
            PlaceListItem(Place(UUID.randomUUID(), "ucfv0j$index", 45.0, 39.0, "Дача $index", day),
                PlaceStats(index, day, day, 600.0, false, emptyList()), 600.0)
        }
        val found = PlacesPresentation.filter(items, "Дача 3", Locale.forLanguageTag("ru"))
        assertEquals(1, found.size)
        assertEquals(PlacesSearchOutcome.matches, PlacesSearchOutcome.resolve("Дача 3", found.size))
        val none = PlacesPresentation.filter(items, "Море", Locale.forLanguageTag("ru"))
        assertEquals(PlacesSearchOutcome.nothingFound, PlacesSearchOutcome.resolve("Море", none.size))
    }
}
