// iOS: PlacesView.swift, PlaceDetailView.swift, PlaceRenameSheet.swift, PlaceCardView.swift.
package com.onezee.triptrack.world

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import com.onezee.triptrack.feature.atlas.PlacesDestination
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 3 November 2026 audit, «Places» (PL-01 to PL-10): the numbers of the iPhone's Places tab and place screen that the
 * first port missed. A phone's sizes are in points, the same numbers as dp here.
 */
@RunWith(RobolectricTestRunner::class)
class PlacesDesignTests : WorldDesignTest() {
    private fun openList(dark: Boolean = false) {
        showPlaces(dark)
        compose.onNodeWithTag("places_mode_list").performClick()
        settle()
    }

    private fun openPlace(name: String = "Дом", dark: Boolean = false) {
        showPlaces(dark)
        val id = world.placesTab.state.value.items.first { it.place.name == name }.place.id
        compose.runOnUiThread { world.placesNavigator.push(PlacesDestination.PlaceDetail(id)) }
        settle()
        compose.onNodeWithTag("place_detail").assertExists()
    }

    // PL-01: every group header gets the 6 once there are two groups, the first included: 12 + 6 under the card above.
    @Test fun firstGroupHeaderStands18UnderTheLastPassCard() {
        showPlaces()
        val card = bounds("places_last_pass_row")
        val first = compose.onAllNodesWithTag("places_group_header", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertNear(18f, (first.top - card.bottom).inDp(), .7f, "the card's bottom to «Эта неделя»")
        val groups = compose.onAllNodesWithTag("places_group_header", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(groups.size > 1, "the seeded library has several groups")
    }

    // PL-02: the placeholder is the system's tertiary label, dimmer than the magnifier beside it.
    @Test fun searchPlaceholderIsDimmerInTheDark() {
        openList(dark = true)
        val ink = screenshot()
        val box = bounds("places_search_placeholder")
        var brightest = 0
        for (y in box.top.toInt()..box.bottom.toInt()) for (x in box.left.toInt()..box.right.toInt()) brightest = maxOf(brightest, android.graphics.Color.red(ink.getPixel(x, y)))
        assertTrue(brightest in 85..114, "EBEBF5 at 30 % over the field: the brightest pixel is about 100, not the 140 of the secondary grey ($brightest)")
    }

    @Test fun searchPlaceholderIsDimmerInTheLight() {
        openList(dark = false)
        val ink = screenshot()
        val box = bounds("places_search_placeholder")
        var darkest = 255
        for (y in box.top.toInt()..box.bottom.toInt()) for (x in box.left.toInt()..box.right.toInt()) darkest = minOf(darkest, android.graphics.Color.red(ink.getPixel(x, y)))
        assertTrue(darkest >= 150, "3C3C43 at 30 % over the field: the darkest pixel is light grey ($darkest)")
    }

    // PL-03: the bare clear symbol ends 13.4 from the field's right edge.
    @Test fun clearButtonEndsAt13FromTheFieldsEdge() {
        openList()
        compose.onNodeWithTag("places_search").performClick()
        settle()
        compose.onNodeWithTag("places_search").performTextInput("Да")
        settle()
        val field = bounds("places_search_field")
        val ink = screenshot().inkIn(bounds("places_search_clear"), growX = 0f, growY = 0f)
        val right = bounds("places_search_clear").left + (ink.left + ink.width) * pxPerDp
        assertNear(13.4f, (field.right - right).inDp(), 1.0f, "the ink's right edge from the field's")
    }

    // PL-04: the results carry the tab bar's clearance whether or not the keyboard stands.
    @Test fun searchResultsClearTheTabBarAtTheEnd() {
        openList()
        compose.onNodeWithTag("places_search").performClick()
        settle()
        val count = world.placesTab.state.value.search!!.results.size
        assertTrue(count >= 8, "a long list: $count")
        compose.onNodeWithTag("places_search_results").performScrollToIndex(count - 1)
        settle()
        val results = bounds("places_search_results")
        val last = node("places_search_results").fetchSemanticsNode().children.maxOf { it.boundsInRoot.bottom }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(last.inDp() <= screen.inDp() - 105f, "the last match ends at ${last.inDp()}, above the tab bar's 110 clearance (screen ${screen.inDp()})")
        assertTrue(results.bottom > last)
    }

    // PL-05: header, 2, the row of at least 40 with no padding, 6: 79 for a two-line row.
    @Test fun usualCardHasNoExtraPadding() {
        openPlace()
        val card = bounds("place_usual_card")
        val header = bounds("place_card_header")
        val row = compose.onAllNodesWithTag("place_usual_row", useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
        assertNear(12f, (header.top - card.top).inDp(), .6f, "12 above the header")
        assertNear(2f, (row.top - header.bottom).inDp(), .6f, "only the card's 2 under it")
        val last = compose.onAllNodesWithTag("place_usual_row", useUnmergedTree = true).fetchSemanticsNodes().last().boundsInRoot
        assertNear(6f, (card.bottom - last.bottom).inDp(), .6f, "6 under the last row")
        assertTrue(row.height.inDp() >= 39.5f, "the row is 40 at least: ${row.height.inDp()}")
    }

    // PL-06: the chip's text stands on the baseline of «Здесь N раз».
    @Test fun chipSitsOnTheBaselineOfTheHereLine() {
        openPlace()
        fun baseline(tag: String): Float {
            val node = node(tag).fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            val layout = results.first()
            // The text sits in its own box; centred in the chip's 24 when it is the chip.
            val box = node.boundsInRoot
            val textTop = box.top + (box.height - layout.size.height) / 2f
            return textTop + layout.getLineBaseline(0)
        }
        val chipBaseline = baseline("place_chip")
        val here = node("place_here_text").fetchSemanticsNode()
        val hereResults = mutableListOf<TextLayoutResult>()
        here.config[SemanticsActions.GetTextLayoutResult].action?.invoke(hereResults)
        val hereBaseline = here.boundsInRoot.top + hereResults.first().getLineBaseline(0)
        assertTrue(abs(chipBaseline - hereBaseline) <= 1.5f, "the chip's baseline ${chipBaseline.inDp()} and the line's ${hereBaseline.inDp()}")
    }

    // PL-07: the back button is in the scrolled header at once, and takes taps above one half only.
    @Test fun scrolledHeaderHasTheBackButtonFromTheStart() {
        openPlace()
        node("place_header_back").assertDoesNotExist()
        fun scrollTo(progress: Float) {
            val target = progress * (322f - 96f) * pxPerDp
            val current = node("place_scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            compose.onNodeWithTag("place_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, target - current) }
            settle(300)
        }
        scrollTo(0.3f)
        node("place_header_back").assertExists()
        node("place_header_back").assertHasNoClickAction()
        scrollTo(0.8f)
        node("place_header_back").assertHasClickAction()
    }

    // PL-08: at the end of the scroll the footnote stands 110 above the physical bottom: the tab bar's clearance.
    @Test fun sheetEndsAtTheTabBarsClearance() {
        openPlace()
        compose.onNodeWithTag("place_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        settle(300)
        val sheet = bounds("place_sheet")
        val hint = bounds("place_delete_hint")
        assertNear(110f, (sheet.bottom - hint.bottom).inDp(), .8f, "the footnote above the sheet's end")
    }

    // PL-09 and PL-10: the rename buttons are 52 tall; the hint stands 10 under the field, the rows after it unmoved.
    @Test fun renameSheetHasTheIphonesButtonsAndHint() {
        openPlace()
        compose.onNodeWithTag("place_action_rename").performClick()
        compose.onNodeWithTag("place_rename_sheet").assertExists()
        assertNear(52f, bounds("place_rename_cancel").height.inDp(), .6f, "the cancel button")
        assertNear(52f, bounds("place_rename_save").height.inDp(), .6f, "the save button")
        val box = bounds("place_rename_box")
        val hint = bounds("place_rename_hint")
        assertNear(10f, (hint.top - box.bottom).inDp(), .7f, "the hint under the field")
        val next = if (node("place_rename_use_address").run { runCatching { assertExists() }.isSuccess }) bounds("place_rename_use_address") else bounds("place_rename_cancel")
        assertNear(14f, (next.top - hint.bottom).inDp(), .8f, "what follows stays 14 below the hint")
    }
}

/** Trips without a single place: the empty tab with its «Отметить в поездке» section (PL-11). */
@RunWith(RobolectricTestRunner::class)
class PlacesNewcomerDesignTests : WorldDesignTest() {
    override val seedsPlaces = false

    // PL-11: the title and its card share a column of 10.
    @Test fun markInTripTitleIsTenAboveItsCard() {
        showPlaces()
        // Below the fold: the bounds of a node out of the viewport are clipped to nothing, its position and size are not.
        val title = node("places_mark_in_trip_title").fetchSemanticsNode()
        val card = node("places_mark_in_trip_card").fetchSemanticsNode()
        assertEquals(10f, (card.positionInRoot.y - (title.positionInRoot.y + title.size.height)).inDp(), .6f)
    }
}
