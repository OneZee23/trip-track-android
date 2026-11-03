// iOS: DraftsFlowShotTests (d5 swipe «Моя», d6 swipe «Удалить», l10 empty, l3 list), DraftsLayoutTests
package com.onezee.triptrack.trips

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.drafts.ui.DraftsEmpty
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** DR-03 to DR-05 of the 3 November audit: the drafts list where and as iOS draws it. */
@RunWith(RobolectricTestRunner::class)
class DraftsLookTests : TripsScreenTest() {
    private fun near(expected: Float, actual: Float, tolerance: Float, what: String) =
        assertTrue(abs(expected - actual) <= tolerance, "$what expected $expected, was $actual")

    /** The header's top-left pixel: glass over the page, away from the back circle and the title. */
    private fun headerPixel(): Triple<Int, Int, Int> {
        val argb = compose.onNodeWithTag("drafts_header").captureToImage().let { image -> val buffer = IntArray(1); image.readPixels(buffer, 4, 4, 1, 1); buffer[0] }
        return Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }

    @Test fun theHeaderIsAShadeDarkerThanThePageInLight() {
        showMe(dark = false)
        push(TripsDestination.Drafts)
        // iOS measured 241, 240, 237 on 248, 246, 242: the thin material is below the page, the house glass tint was above it (254, 253, 252).
        val (r, g, b) = headerPixel()
        near(241f, r.toFloat(), 3f, "red"); near(240f, g.toFloat(), 3f, "green"); near(237f, b.toFloat(), 3f, "blue")
    }

    @Test fun theHeaderIsLighterThanThePageInDark() {
        showMe(dark = true)
        push(TripsDestination.Drafts)
        // iOS measured 40, 39, 41 on 18, 18, 20.
        val (r, g, b) = headerPixel()
        near(40f, r.toFloat(), 3f, "red"); near(39f, g.toFloat(), 3f, "green"); near(41f, b.toFloat(), 3f, "blue")
    }

    @Test fun aSwipeOpensADetachedPlateOf74AtEightFromTheRow() {
        showMe()
        push(TripsDestination.Drafts)
        // A slow drag of 60 past the first 8: it settles open, the row 82 from where it began.
        compose.onAllNodesWithTag("draft_row")[0].performTouchInput { swipe(center, Offset(center.x - 60.dp.toPx(), center.y), durationMillis = 800) }
        compose.waitForIdle()
        val plate = compose.onNodeWithTag("draft_swipe_delete").getBoundsInRoot()
        val row = compose.onAllNodesWithTag("draft_row")[0].getBoundsInRoot()
        near(74f, plate.width.value, 0.75f, "plate width"); near(8f, plate.left.value - row.right.value, 0.75f, "gap between the row and the plate")
        near(row.height.value, plate.height.value, 0.75f, "plate height")
    }

    @Test fun aSwipeTheOtherWayOpensThePlateOfMineOnTheLeft() {
        showMe()
        push(TripsDestination.Drafts)
        compose.onAllNodesWithTag("draft_row")[0].performTouchInput { swipe(center, Offset(center.x + 60.dp.toPx(), center.y), durationMillis = 800) }
        compose.waitForIdle()
        val plate = compose.onNodeWithTag("draft_swipe_mine").getBoundsInRoot()
        val row = compose.onAllNodesWithTag("draft_row")[0].getBoundsInRoot()
        near(74f, plate.width.value, 0.75f, "plate width"); near(8f, row.left.value - plate.right.value, 0.75f, "gap between the plate and the row")
    }

    @Test fun theEmptyBlockStandsAtThirtyPercentOfTheScreenFromItsTop() {
        compose.setContent { TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) { Box(Modifier.fillMaxSize()) { DraftsEmpty(914.dp) } } }
        compose.waitForIdle()
        // DR-05: 30 % of 914, not 30 % less the status bar (iOS applies that inside the safe area).
        near(274.2f, compose.onNodeWithTag("drafts_empty").getBoundsInRoot().top.value, 0.75f, "top of the block")
    }
}
