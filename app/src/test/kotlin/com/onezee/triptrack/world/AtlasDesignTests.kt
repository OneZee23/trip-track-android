// iOS: MyMapView.swift, MyMapSheet.swift, AtlasSummaryContent.swift, AtlasRegionContent.swift, AtlasPlaceCard.swift,
// AtlasMapControls.swift, AtlasBetaChip.swift, AtlasControlsSheet.swift, AtlasBetaSheet.swift, HomeSheet.swift,
// CarLoadingView.swift, SkeletonPlaceholder.swift.
package com.onezee.triptrack.world

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.data.RoomTripRepository
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasSheetDetent
import com.onezee.triptrack.feature.atlas.ui.homeZone
import com.onezee.triptrack.feature.atlas.ui.posterPillBottom
import com.onezee.triptrack.feature.settings.HomeState
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 3 November 2026 audit, «Atlas» (AT-01 to AT-19): the numbers of the iPhone's Atlas screen that the first
 * port missed. A phone's sizes are in points, the same numbers as dp here.
 */
@RunWith(RobolectricTestRunner::class)
class AtlasDesignTests : WorldDesignTest() {
    private fun placeCardFor(name: String) {
        showAtlas()
        compose.runOnUiThread { world.atlas.selectPlace(world.atlas.state.value.placePins.first { it.name == name }.id) }
        settle()
    }

    // AT-01: square.and.arrow.up at 16 semibold is 14.3 × 18.0 of ink, on the live disc and on the offline one.
    @Test fun shareGlyphFillsTheIphonesInk() {
        showAtlas()
        assertInk("atlas_share_glyph", 14.3f, 18.0f, growX = 3f, growY = 3f)
    }

    @Test fun offlineShareGlyphHasTheSameInk() {
        online.value = false
        showAtlas()
        assertInk("atlas_share_glyph", 14.3f, 18.0f, growX = 3f, growY = 3f)
    }

    // AT-02: the three layers 18.0 × 19.7 and the arrow 17 square, in the capsule of 44.
    @Test fun mapControlGlyphsAreAsLargeAsTheIphones() {
        showAtlas()
        assertInk("atlas_appearance_glyph", 18.2f, 20.0f, growX = 3f, growY = 3f)
        assertInk("atlas_locate_glyph", 17.2f, 17.3f, growX = 3f, growY = 3f)
        assertEquals(44f, bounds("atlas_appearance").width.inDp(), .45f)
    }

    // AT-03: «Бета ›» is 53 wide: the chevron stands in a 7 wide box.
    @Test fun betaChipsAre53Wide() {
        showAtlas()
        assertNear(53.0f, bounds("atlas_beta_chip_pill").width.inDp(), .7f, "the pill of the Atlas tab")
    }

    // AT-04: the count stands at the right edge; only «Поездки здесь» keeps it beside the heading.
    @Test fun regionCitiesCountStandsAtTheRightEdge() {
        showAtlas()
        compose.runOnUiThread { world.atlas.select(AtlasSelection.Region(world.atlas.state.value.exploration.regions.first().id)) }
        settle()
        val count = bounds("atlas_section_count")
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertNear(screen.inDp() - 16f, count.right.inDp(), .6f, "the count ends at the content's right edge")
        assertTrue(count.left.inDp() > 200f, "and not beside «Города», which ends by x 80: ${count.left.inDp()}")
    }

    @Test fun expandedListCountsStandAtTheRightEdge() {
        showAtlas()
        compose.runOnUiThread { world.atlas.setDetent(AtlasSheetDetent.expanded) }
        settle()
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot.width.inDp()
        val counts = compose.onAllNodesWithTag("atlas_section_count", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(counts.isNotEmpty(), "at least «Города» shows its count")
        for (count in counts) assertNear(screen - 16f, count.boundsInRoot.right.inDp(), .6f, "a count ends at the right edge")
    }

    // AT-11: one column of spacing 10: the numbers, «Регионы», its card and «Города» are 10 apart.
    @Test fun expandedListSectionsAreTenApart() {
        showAtlas()
        compose.runOnUiThread { world.atlas.setDetent(AtlasSheetDetent.expanded) }
        settle()
        val card = bounds("atlas_stats_card")
        val regions = bounds("atlas_header_regions")
        assertNear(10f, (regions.top - card.bottom).inDp(), .6f, "the numbers to «Регионы»")
        val regionsCard = bounds("atlas_regions_card")
        assertNear(10f, (bounds("atlas_header_cities").top - regionsCard.bottom).inDp(), .6f, "the regions card to «Города»")
        assertNear(10f, (regionsCard.top - regions.bottom).inDp(), .6f, "«Регионы» to its card")
    }

    // AT-07: the home card leaves the map controls at the peek, where the 96 card hides the capsule; a place carries them up.
    @Test fun homeCardDoesNotMoveTheMapControlsButAPlaceCardDoes() {
        home.value = HomeState(coordinate = krasnodar())
        showAtlas()
        compose.runOnUiThread { world.atlas.selectHome() }
        settle()
        val card = bounds("atlas_home_card")
        val appearance = bounds("atlas_appearance")
        val locate = bounds("atlas_locate")
        assertTrue(appearance.top >= card.top - 1f && locate.bottom <= card.bottom + 1f,
            "the capsule (${appearance.top.inDp()} to ${locate.bottom.inDp()}) is inside the card (${card.top.inDp()} to ${card.bottom.inDp()})")
        compose.runOnUiThread { world.atlas.closeSelection(); world.atlas.selectPlace(world.atlas.state.value.placePins.first { it.name == "Дача" }.id) }
        settle()
        val place = bounds("atlas_place_card")
        assertNear(place.top.inDp() - 12f, bounds("atlas_locate").bottom.inDp(), .8f, "a place card carries the capsule 12 above it")
    }

    // AT-08: the card centres its content in the 96: the tile 16 under the top, the action 16 over the bottom.
    @Test fun placeCardCentresItsContent() {
        placeCardFor("Дача")
        val card = bounds("atlas_place_card")
        val tile = bounds("atlas_place_tile")
        val open = bounds("atlas_place_open")
        assertNear(96f, card.height.inDp(), .5f, "the card's own height")
        assertNear(16.45f, (tile.top - card.top).inDp(), .8f, "the tile under the card's top")
        assertNear((tile.top - card.top).inDp(), (card.bottom - open.bottom).inDp(), .9f, "the same above the bottom: centred")
        assertNear(16.45f + 0f, (bounds("atlas_place_name").top - card.top).inDp(), .8f, "the name starts with the tile")
    }

    // AT-09 and AT-10: the house 21.7 × 19.0 in the 44 tile; the cross 10.7 square in the 36 disc.
    @Test fun homeCardHasTheIphonesHouseAndCross() {
        home.value = HomeState(coordinate = krasnodar())
        showAtlas()
        compose.runOnUiThread { world.atlas.selectHome() }
        settle()
        assertInk("atlas_place_tile_glyph", 21.7f, 19.0f, growX = 3f, growY = 3f)
        assertInk("atlas_place_close_glyph", 10.7f, 10.7f, growX = 3f, growY = 3f)
    }

    // AT-12 and AT-13: the trip row starts 39 under the panel's top, the heading of the road 41; the × is a 40 NavCircleIcon at 14 / 16.
    @Test fun tripCardStartsLowerAndCloseIsTheNavCircle() {
        showAtlas()
        compose.runOnUiThread { world.atlas.select(AtlasSelection.Trip(world.atlas.state.value.exploration.trips.maxBy { it.distance }.id), zoom = false) }
        settle()
        val panel = bounds("mymap_trip_card")
        assertNear(39f, (bounds("mymap_trip_row").top - panel.top).inDp(), .8f, "the thumbnail under the panel's top")
        val close = bounds("mymap_close")
        assertNear(40f, close.width.inDp(), .5f, "the × is 40 wide")
        assertNear(14f, (close.top - panel.top).inDp(), .6f, "14 from the top")
        assertNear(16f, (panel.right - close.right).inDp(), .6f, "16 from the edge")
    }

    // AT-12 and AT-14: the road's rows are their own: 52 tall, 16 from the edge, the chevron at the full secondary.
    @Test fun roadCardHasItsOwnRows() {
        showAtlas()
        compose.runOnUiThread {
            val ids = world.atlas.state.value.exploration.trips.filter { it.title == "Утренний круг по бетонке" }.map { it.id }
            world.atlas.selectRoad(ids)
        }
        settle()
        val panel = bounds("mymap_road_card")
        assertNear(41f, (bounds("mymap_road_heading").top - panel.top).inDp(), .8f, "the heading under the panel's top")
        val row = compose.onAllNodesWithTag("mymap_road_trip", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertNear(52f, row.height.inDp(), .6f, "a row is 52 tall")
        val chevron = compose.onAllNodesWithTag("mymap_road_chevron", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertNear(16f, (row.right - chevron.right).inDp(), .6f, "the chevron 16 from the edge")
    }

    // AT-15 and AT-16: the explanation opens at the iPhone's medium height, its bodies with 3 of extra line spacing.
    @Test fun explanationOpensHalfHighWithSpacedLines() {
        showAtlas()
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot.height.inDp()
        compose.onNodeWithTag("atlas_explored_title").performClick()
        compose.onNodeWithTag("atlas_explanation_sheet").assertExists()
        assertNear(window * 453f / 852f, bounds("atlas_explanation_sheet").height.inDp(), 1.5f, "a medium detent of the window")
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithTag("atlas_explanation_body", useUnmergedTree = true)[0].fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        assertTrue(layout.lineCount >= 2, "the first body runs over several lines")
        assertNear(15f * 2478f / 2048f + 3f, (layout.getLineBottom(1) - layout.getLineBottom(0)).inDp(), .5f, "the line pitch of Inter 15 plus 3")
    }

    @Test fun betaBodyHasThreeOfLineSpacing() {
        showAtlas()
        compose.onNodeWithTag("atlas_beta_chip").performClick()
        compose.onNodeWithTag("atlas_beta_sheet").assertExists()
        val results = mutableListOf<TextLayoutResult>()
        node("atlas_beta_body").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        assertTrue(layout.lineCount >= 2, "the body runs over several lines")
        assertNear(14f * 2478f / 2048f + 3f, (layout.getLineBottom(1) - layout.getLineBottom(0)).inDp(), .5f, "the line pitch of Inter 14 plus 3")
    }

    // AT-10 and AT-17: the home sheet's cross is the iPhone's, its two toggles ask for 44, the sheet «Вид карты» keeps 56.
    @Test fun homeSheetTogglesAre44AndTheStyleSheetKeeps56() {
        home.value = HomeState(coordinate = krasnodar())
        showAtlas()
        compose.onNodeWithTag("atlas_appearance").performClick()
        compose.onNodeWithTag("atlas_appearance_sheet").assertExists()
        assertNear(56f, bounds("atlas_photos_toggle").height.inDp(), .5f, "the style sheet's row")
        assertNear(15.6f, bounds("atlas_controls_close_glyph").width.inDp(), .5f, "the cross of the shared header: box of 15.6")
        compose.onNodeWithTag("atlas_home_row").performClick()
        settle()
        compose.onNodeWithTag("home_sheet").assertExists()
        assertNear(44f, bounds("home_shows_toggle").height.inDp(), .5f, "the first card's toggle")
        assertNear(44f, bounds("home_zone_toggle").height.inDp(), .5f, "the zone's toggle")
        assertNear(15.6f, bounds("atlas_controls_close_glyph").width.inDp(), .5f, "the same header")
    }

    // AT-18: the zone is a disc with a fill: the accent at 16 % inside and 70 % along a 2 pt edge.
    @Test fun zoneIsATintedDisc() {
        val zone = homeZone(GeoPoint(45.035, 38.975), 500.0, androidx.compose.ui.graphics.Color(0xFFC8472D))
        assertEquals(0.16, zone.fillOpacity, 1e-9)
        assertEquals(0.7, zone.strokeOpacity, 1e-9)
        assertEquals(2.0, zone.strokeWidthPoints, 1e-9)
        assertEquals(65, zone.ring.size)
        assertEquals(0xFFC8472D.toInt(), zone.colour, "the colour is opaque: the opacities are the two strengths")
    }

    // AT-19: the white spinner and the words on a pill, its foot 286 above the bottom.
    @Test fun posterProgressHasTheSpinnerAndStandsOnTheSheet() {
        assertEquals(286.dp, posterPillBottom(0.dp))
        assertEquals(334.dp, posterPillBottom(48.dp), "a three-button navigation bar lifts the sheet and the pill with it")
        show(false, com.onezee.triptrack.designsystem.navigation.AppTab.maps, settles = false) { com.onezee.triptrack.feature.atlas.ui.PosterProgressPill() }
        node("mymap_share_spinner").assertExists()
        assertNear(20f, bounds("mymap_share_spinner").width.inDp(), .5f, "the spinner is 20")
        assertNear(40f, bounds("mymap_share_progress").height.inDp(), .6f, "10 + 20 + 10")
    }
}

/** The Atlas while it is counted: the skeleton lies on the sheet, and the car is the iPhone's size. */
@RunWith(RobolectricTestRunner::class)
class AtlasLoadingDesignTests : WorldDesignTest() {
    private val gate = CompletableDeferred<Unit>()

    override fun worldRepository(repository: RoomTripRepository): TripRepository = object : TripRepository by repository {
        override suspend fun fetchTripsForMap(): List<Trip> {
            gate.await()
            return repository.fetchTripsForMap()
        }
    }

    // AT-05: three equal columns of bars on the sheet itself, 36 tall, none of them in a card.
    @Test fun statsSkeletonLiesOnTheSheet() {
        showAtlas()
        val skeleton = bounds("atlas_stats_skeleton")
        assertNear(16f, skeleton.left.inDp(), .5f, "the first bar starts at the sheet's 16")
        assertNear(36f, skeleton.height.inDp(), .6f, "20 + 6 + 10")
        val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width.inDp()
        assertNear(width - 32f, skeleton.width.inDp(), .6f, "to the right 16")
        compose.waitForIdle()
        val shot = compose.onRoot().captureToImage().asAndroidBitmap()
        fun pixel(x: Float, y: Float) = shot.getPixel((x * pxPerDp).toInt(), (y * pxPerDp).toInt())
        val middle = skeleton.top.inDp() + 15f
        val sheet = pixel(6f, middle)
        // Inside the first column, past the 64 wide bar, a card would be white and the sheet is not.
        assertEquals(sheet, pixel(16f + 100f, skeleton.top.inDp() + 5f), "no card under the bars")
        assertTrue(pixel(18f, middle) != sheet, "the first bar stands at x 16")
        val columnStart = 16f + (width - 32f - 16f) / 3f + 8f
        assertTrue(pixel(columnStart + 3f, middle) != sheet, "the second bar stands at x $columnStart")
        gate.complete(Unit)
    }

    // AT-06: car.fill at 28 semibold is 33.7 × 27.0 of ink.
    @Test fun loadingCarIsTheIphonesSize() {
        showAtlas()
        val ink = screenshot().inkIn(bounds("atlas_loading_car"), growX = 2f, growY = 0f)
        // The house car artwork is squarer than the symbol (32 × 29.7 against 33.7 × 27.0): the box is set by the point
        // size, and the two agree in area to 2 %.
        assertTrue(abs(kotlin.math.sqrt(33.7f * 27.0f) - kotlin.math.sqrt(ink.width * ink.height)) <= 1.0f,
            "the car's ink is ${ink.width} × ${ink.height}, the iPhone's is 33.7 × 27.0")
        gate.complete(Unit)
    }
}
