// iOS: AtlasRegionTests (exits), AtlasSlotTests (the gesture's rules on screen), AtlasPlaceTapTests (the
// card's way to Places), PlacesStatesShotTests (search as a modal state), PlacesTabShotTests.
package com.onezee.triptrack.world

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasSheetDetent
import com.onezee.triptrack.feature.atlas.PlacesDestination
import com.onezee.triptrack.feature.places.PlacesPanelStop
import com.onezee.triptrack.logic.id
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorldBehaviourTests : WorldScreenTest() {
    private val atlasState get() = world.atlas.state.value

    /** A quick vertical swipe in the node's own pixels (0 is its top edge). */
    private fun drag(tag: String, from: Float, to: Float, millis: Long = 160) {
        compose.onNodeWithTag(tag).performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX, from), androidx.compose.ui.geometry.Offset(centerX, to), durationMillis = millis)
        }
        settle()
    }

    /** The top of a node in dp of the root. */
    private fun top(tag: String): Float = compose.onNodeWithTag(tag).getBoundsInRoot().top.value

    @Test fun aFlickUpOpensTheListAndTheStripAboveItCollapsesIt() {
        showAtlas()
        drag("atlas_sheet", 30f, -900f)
        assertEquals(AtlasSheetDetent.expanded, atlasState.detent)
        compose.onNodeWithTag("atlas_list").assertExists()
        // The strip of map over the open list IS «collapse», it never selects what is under it.
        compose.onNodeWithTag("atlas_collapse_strip").performClick()
        settle()
        assertEquals(AtlasSheetDetent.collapsed, atlasState.detent)
        assertNull(atlasState.selection)
    }

    @Test fun aSlowShortDragSpringsBackToTheSummary() {
        showAtlas()
        val sheetTop = top("atlas_sheet")
        drag("atlas_sheet", 30f, -40f, millis = 1_000)
        assertEquals(AtlasSheetDetent.collapsed, atlasState.detent)
        assertEquals(sheetTop, top("atlas_sheet"), 2f)
    }

    @Test fun theHandleTogglesTheList() {
        showAtlas()
        compose.onNodeWithTag("atlas_sheet_handle").performClick()
        settle()
        assertEquals(AtlasSheetDetent.expanded, atlasState.detent)
        compose.onNodeWithTag("atlas_sheet_handle").performClick()
        settle()
        assertEquals(AtlasSheetDetent.collapsed, atlasState.detent)
    }

    /** Back returns to where the person came from (the list), × to the summary. */
    @Test fun aRegionFromTheListGoesBackToTheListAndCloseToTheSummary() {
        showAtlas()
        compose.runOnUiThread { world.atlas.setDetent(AtlasSheetDetent.expanded) }
        settle()
        val region = atlasState.exploration.regions.first()
        compose.onNodeWithTag("atlas_region_${region.id}").performClick()
        settle()
        assertEquals(AtlasSelection.Region(region.id), atlasState.selection)
        compose.onNodeWithTag("atlas_region_page").assertExists()
        compose.onNodeWithTag("atlas_substate_back").performClick()
        settle()
        assertNull(atlasState.selection)
        assertEquals(AtlasSheetDetent.expanded, atlasState.detent)

        compose.onNodeWithTag("atlas_region_${region.id}").performClick()
        settle()
        compose.onNodeWithTag("atlas_substate_close").performClick()
        settle()
        assertNull(atlasState.selection)
        assertEquals(AtlasSheetDetent.collapsed, atlasState.detent)
    }

    /** Dragged below its own height the region closes; a flick down is the same answer. */
    @Test fun dragDownBelowTheRegionClosesIt() {
        showAtlas()
        compose.runOnUiThread { world.atlas.select(AtlasSelection.Region(atlasState.exploration.regions.first().id)) }
        settle()
        drag("atlas_region_sheet", 10f, 600f)
        assertNull(atlasState.selection)
    }

    /**
     * The place card leads into Places by two phases: the tab first, the place 300 ms later on that
     * tab's stack (iOS `.openPlace → .navigateToPlace`).
     */
    @Test fun thePlaceCardOpensThePlaceInThePlacesTab() {
        showAtlas()
        val pin = atlasState.placePins.first { it.name == "Дача" }
        compose.runOnUiThread { world.atlas.selectPlace(pin.id) }
        settle()
        assertEquals(AtlasSheetDetent.peek, atlasState.detent)
        compose.onNodeWithTag("atlas_place_open").performClick()
        assertEquals(AppTab.places, selectedTab)
        settle(600)
        assertEquals(PlacesDestination.PlaceDetail(pin.id), world.placesNavigator.top)
        // The card's × closes it and the summary comes back.
        compose.onNodeWithTag("atlas_place_close").performClick()
        settle()
        assertNull(atlasState.selectedPlaceId)
        assertEquals(AtlasSheetDetent.collapsed, atlasState.detent)
    }

    @Test fun aRegionTripOpensTheTripOverTheAtlas() {
        showAtlas()
        compose.runOnUiThread { world.atlas.select(AtlasSelection.Region(atlasState.exploration.regions.first().id)) }
        settle()
        // The region's trips are under the tab bar until its sheet is pulled up, as on the phone.
        compose.onNodeWithTag("atlas_sheet_handle").performClick()
        settle()
        assertEquals(AtlasSheetDetent.expanded, atlasState.detent)
        compose.onAllNodesWithTag("atlas_region_trip")[0].performClick()
        settle()
        assertEquals(1, world.atlasTrips.stack.size)
        compose.onNodeWithTag("trip_detail").assertExists()
    }

    /** Focus in the field IS the entry; «Отмена» clears the query and returns the panel. */
    @Test fun searchIsAModalStateOfThePanel() {
        showPlaces()
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        assertEquals(PlacesPanelStop.list, world.placesPanel.stop)
        compose.onNodeWithTag("places_search").performClick()
        settle()
        assertNotNull(world.placesTab.state.value.search)
        compose.onNodeWithTag("places_search_scrim").assertExists()
        compose.onNodeWithTag("places_search").performTextInput("Дач")
        settle()
        assertEquals(listOf("Дача"), world.placesTab.state.value.search!!.results.map { it.place.name })
        compose.onNodeWithTag("places_search_cancel").performClick()
        settle()
        assertNull(world.placesTab.state.value.search)
        assertEquals(PlacesPanelStop.list, world.placesPanel.stop)
    }

    @Test fun theScrimIsTheSecondWayOutOfTheSearch() {
        showPlaces()
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        compose.onNodeWithTag("places_search").performClick()
        settle()
        compose.onNodeWithTag("places_search_scrim").performTouchInput { click(androidx.compose.ui.geometry.Offset(centerX, 20f)) }
        settle()
        assertNull(world.placesTab.state.value.search)
    }

    @Test fun theSwitchMovesThePanelAndDoesNotChangeScreens() {
        showPlaces()
        assertEquals(PlacesPanelStop.half, world.placesPanel.stop)
        compose.onNodeWithTag("places_mode_map").performClick()
        settle()
        assertEquals(PlacesPanelStop.map, world.placesPanel.stop)
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        assertEquals(PlacesPanelStop.list, world.placesPanel.stop)
        assertTrue(world.placesNavigator.stack.isEmpty())
    }

    @Test fun aFlickUpFromTheHalfIsTheList() {
        showPlaces()
        drag("places_panel", 20f, -900f)
        assertEquals(PlacesPanelStop.list, world.placesPanel.stop)
    }

    @Test fun aPlaceIsPushedOnce() {
        showPlaces()
        val id = world.placesTab.state.value.items.first().place.id
        compose.runOnUiThread {
            world.placesNavigator.push(PlacesDestination.PlaceDetail(id))
            world.placesNavigator.push(PlacesDestination.PlaceDetail(id))
        }
        settle()
        assertEquals(1, world.placesNavigator.stack.size)
        compose.onNodeWithTag("place_detail").assertExists()
    }

    @Test fun aChosenPinHoldsTheSlotAndTheCrossGivesItBack() {
        showPlaces()
        compose.runOnUiThread {
            val state = world.placesTab.state.value
            world.placesPanel.selectPin(state.items.first { it.place.name == "Дача" }.place.id, state)
        }
        settle()
        assertEquals(PlacesPanelStop.selectedPlace, world.placesPanel.stop)
        compose.onNodeWithTag("places_selection_close").performClick()
        settle()
        assertEquals(PlacesPanelStop.half, world.placesPanel.stop)
        assertNull(world.placesPanel.selectedPlace)
    }

    @Test fun deletingAPlaceClosesItsScreen() {
        showPlaces()
        val id = world.placesTab.state.value.items.first { it.place.name == "Дача" }.place.id
        compose.runOnUiThread { world.placesNavigator.push(PlacesDestination.PlaceDetail(id)) }
        settle()
        compose.onNodeWithTag("place_action_delete").performClick()
        settle()
        compose.onNodeWithTag("app_dialog").assertExists()
        compose.onNodeWithTag("app_dialog_action").performClick()
        settle()
        assertTrue(world.placesNavigator.stack.isEmpty())
        assertTrue(world.placesTab.state.value.items.none { it.place.id == id })
    }

    @Test fun renamingAPlaceSavesTheNewName() {
        showPlaces()
        val id = world.placesTab.state.value.items.first { it.place.name == "Дача" }.place.id
        compose.runOnUiThread { world.placesNavigator.push(PlacesDestination.PlaceDetail(id)) }
        settle()
        compose.onNodeWithTag("place_action_rename").performClick()
        settle()
        compose.onNodeWithTag("place_rename_field").performTextInput(" у реки")
        compose.onNodeWithTag("place_rename_save").performClick()
        settle()
        assertEquals("Дача у реки", onMain { world.places.fetchPlace(id) }?.name)
    }
}

/** Trips and no place: the empty tab stands still, and saving a suggestion makes a place with its history. */
@RunWith(RobolectricTestRunner::class)
class PlacesNewcomerBehaviourTests : WorldScreenTest() {
    override val seedsPlaces = false

    @Test fun theEmptyTabIsPinned() {
        showPlaces()
        assertEquals(PlacesPanelStop.empty, world.placesPanel.stop)
        compose.onNodeWithTag("places_panel").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(centerX, 30f), androidx.compose.ui.geometry.Offset(centerX, -900f), durationMillis = 160)
        }
        settle()
        assertEquals(PlacesPanelStop.empty, world.placesPanel.stop)
    }

    @Test fun savingASuggestionMakesAPlaceWithItsHistory() {
        showPlaces()
        val suggestion = world.placesTab.state.value.knownSuggestions.first()
        compose.onNodeWithTag("place_suggestion_save_${suggestion.cell}").performClick()
        settle()
        val item = world.placesTab.state.value.items.single()
        assertEquals(suggestion.id, item.place.id)
        assertTrue(item.stats.passCount > 0, "the new place knows its past passes at once")
        assertIs<PlacesPanelStop>(world.placesPanel.stop)
        assertEquals(PlacesPanelStop.half, world.placesPanel.stop, "a place ends the empty tab")
    }
}
