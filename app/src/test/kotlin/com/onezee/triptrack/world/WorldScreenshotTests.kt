// iOS: AtlasStatesShotTests, AtlasAppearanceSheetShotTests, PlacesTabShotTests, PlacesStatesShotTests.
package com.onezee.triptrack.world

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.feature.atlas.AtlasLocationPermission
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasSheetDetent
import com.onezee.triptrack.feature.atlas.PlacesDestination
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.logic.id
import com.onezee.triptrack.model.GeoPoint
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * The Atlas and Places light and dark on the seeded library. The map is the stand-in ground (no
 * MapLibre under Robolectric), so these frames check the sheet, the cards and the chrome; sheets and
 * dialogs live in their own windows and are whole-screen captures.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WorldScreenshotTests(private val dark: Boolean) : WorldScreenTest() {
    private val theme = if (dark) "dark" else "light"

    private fun shot(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/world_${name}_$theme.png")

    private fun screen(name: String) {
        settle()
        captureScreenRoboImage("src/test/screenshots/world_${name}_$theme.png")
    }

    private fun atlas(action: () -> Unit) {
        showAtlas(dark)
        compose.runOnUiThread(action)
        settle()
    }

    @Test fun atlasSummary() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_sheet").assertExists()
        shot("atlas_summary")
    }

    @Test fun atlasList() {
        atlas { world.atlas.setDetent(AtlasSheetDetent.expanded) }
        compose.onNodeWithTag("atlas_list").assertExists()
        shot("atlas_list")
    }

    @Test fun atlasRegion() {
        atlas { world.atlas.select(AtlasSelection.Region(world.atlas.state.value.exploration.regions.first().id)) }
        compose.onNodeWithTag("atlas_region_page").assertExists()
        shot("atlas_region")
    }

    @Test fun atlasPlaceCard() {
        atlas { world.atlas.selectPlace(world.atlas.state.value.placePins.first { it.name == "Дача" }.id) }
        compose.onNodeWithTag("atlas_place_card").assertExists()
        shot("atlas_place")
    }

    @Test fun atlasHomeCard() {
        home.value = HomeState(coordinate = GeoPoint(45.035, 38.975))
        atlas { world.atlas.selectHome() }
        compose.onNodeWithTag("atlas_home_card").assertExists()
        shot("atlas_home")
    }

    @Test fun atlasOffline() {
        online.value = false
        showAtlas(dark)
        compose.onNodeWithTag("atlas_offline_row").assertExists()
        shot("atlas_offline")
    }

    @Test fun atlasTripPanel() {
        atlas { world.atlas.select(AtlasSelection.Trip(world.atlas.state.value.exploration.trips.maxBy { it.distance }.id), zoom = false) }
        compose.onNodeWithTag("mymap_trip_card").assertExists()
        shot("atlas_trip")
    }

    @Test fun atlasRoadPanel() {
        atlas {
            val ids = world.atlas.state.value.exploration.trips.filter { it.title == "Утренний круг по бетонке" }.map { it.id }
            world.atlas.selectRoad(ids)
        }
        compose.onNodeWithTag("mymap_road_card").assertExists()
        shot("atlas_road")
    }

    @Test fun atlasStyleSheet() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_appearance").performClick()
        compose.onNodeWithTag("atlas_appearance_sheet").assertExists()
        screen("atlas_style_sheet")
    }

    @Test fun atlasBetaSheet() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_beta_chip").performClick()
        compose.onNodeWithTag("atlas_beta_sheet").assertExists()
        screen("atlas_beta_sheet")
    }

    /** «Как мы считаем» at the iPhone's medium height (AT-15), the third card running under the edge. */
    @Test fun atlasExplanationSheet() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_explored_title").performClick()
        compose.onNodeWithTag("atlas_explanation_sheet").assertExists()
        screen("atlas_explanation_sheet")
    }

    /** The home sheet with no home set yet (iOS h2) and with one placed, the zone on (h3, h5). */
    @Test fun atlasHomeSheetEmpty() {
        showAtlas(dark)
        compose.onNodeWithTag("atlas_appearance").performClick()
        compose.onNodeWithTag("atlas_home_row").performClick()
        settle()
        compose.onNodeWithTag("home_sheet").assertExists()
        screen("atlas_home_sheet_empty")
    }

    @Test fun atlasHomeSheetPlaced() {
        home.value = HomeState(coordinate = GeoPoint(45.035, 38.975), trimsPublicTracks = true)
        showAtlas(dark)
        compose.onNodeWithTag("atlas_appearance").performClick()
        compose.onNodeWithTag("atlas_home_row").performClick()
        settle()
        compose.onNodeWithTag("home_sheet").assertExists()
        screen("atlas_home_sheet")
    }

    @Test fun atlasLocationDenied() {
        permission.value = AtlasLocationPermission.denied
        showAtlas(dark)
        compose.onNodeWithTag("atlas_locate").performClick()
        compose.onNodeWithTag("app_dialog").assertExists()
        screen("atlas_location_denied")
    }

    @Test fun placesHalf() {
        showPlaces(dark)
        compose.onNodeWithTag("places_panel").assertExists()
        shot("places_half")
    }

    @Test fun placesList() {
        showPlaces(dark)
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        compose.onNodeWithTag("places_list_page").assertExists()
        shot("places_list")
    }

    @Test fun placesSearch() {
        showPlaces(dark)
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        compose.onNodeWithTag("places_search").performClick()
        settle()
        compose.onNodeWithTag("places_search").performTextInput("Да")
        settle()
        compose.onNodeWithTag("places_search_results").assertExists()
        shot("places_search")
    }

    @Test fun placesSearchNothingFound() {
        showPlaces(dark)
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
        compose.onNodeWithTag("places_search").performClick()
        settle()
        compose.onNodeWithTag("places_search").performTextInput("Москва")
        settle()
        compose.onNodeWithTag("places_search_empty").assertExists()
        shot("places_search_empty")
    }

    @Test fun placesSelectedPlace() {
        showPlaces(dark)
        compose.runOnUiThread {
            val state = world.placesTab.state.value
            world.placesPanel.selectPin(state.items.first { it.place.name == "Дача" }.place.id, state)
        }
        settle()
        shot("places_selected_place")
    }

    @Test fun placesSelectedSuggestion() {
        showPlaces(dark)
        compose.runOnUiThread {
            val state = world.placesTab.state.value
            world.placesPanel.selectPin(state.knownSuggestions.first().id, state)
        }
        settle()
        shot("places_selected_hint")
    }

    @Test fun placeDetail() {
        showPlaces(dark)
        val id = world.placesTab.state.value.items.first { it.place.name == "Дом" }.place.id
        compose.runOnUiThread { world.placesNavigator.push(PlacesDestination.PlaceDetail(id)) }
        settle()
        compose.onNodeWithTag("place_detail").assertExists()
        shot("place_detail")
    }

    @Test fun placeRename() {
        showPlaces(dark)
        val id = world.placesTab.state.value.items.first { it.place.name == "Дом" }.place.id
        compose.runOnUiThread { world.placesNavigator.push(PlacesDestination.PlaceDetail(id)) }
        settle()
        compose.onNodeWithTag("place_action_rename").performClick()
        compose.onNodeWithTag("place_rename_sheet").assertExists()
        screen("place_rename")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
