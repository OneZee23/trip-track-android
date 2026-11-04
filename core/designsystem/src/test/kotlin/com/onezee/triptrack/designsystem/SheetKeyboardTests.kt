package com.onezee.triptrack.designsystem

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Insets
import android.view.WindowInsets
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.contentSizedSheetBottomInset
import com.onezee.triptrack.designsystem.components.contentClearedBottomPadding
import com.onezee.triptrack.designsystem.components.SheetDetent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The keyboard pushes a house sheet up. Build 13 left every field in a sheet under the keyboard:
 * a tester tapped «Имя» on «Мой профиль» and saw the keys and no field (4 Nov 2026). The sheet is a
 * Compose dialog drawn edge to edge, which the system does not resize, so the sheet has to stand on
 * the keyboard itself. The keyboard is played here by the insets the system would dispatch.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class SheetKeyboardTests {
    @Test fun androidTenAndElevenFallBackToTheActivityInsetOnlyWhenDialogHasNone() {
        assertEquals(48, contentSizedSheetBottomInset(29, 0, 48))
        assertEquals(48, contentSizedSheetBottomInset(30, 0, 48))
        assertEquals(24, contentSizedSheetBottomInset(29, 24, 48))
        assertEquals(0, contentSizedSheetBottomInset(31, 0, 48))
    }

    @Test fun aCaptionMayCoverAGestureHandleButNotAButtonBar() {
        assertEquals(0.dp, contentClearedBottomPadding(16.dp))
        assertEquals(0.dp, contentClearedBottomPadding(0.dp))
        assertEquals(32.dp, contentClearedBottomPadding(48.dp))
    }
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private lateinit var sheetView: View

    private fun show(detent: SheetDetent = SheetDetent.content, clearsBar: Boolean = false) {
        compose.setContent {
            Themed(dark = false) {
                ContentSizedSheet(onDismissRequest = {}, detent = detent, contentClearsNavigationBar = clearsBar) {
                    sheetView = LocalView.current
                    val content = Modifier.fillMaxWidth().testTag("content")
                    Box(if (detent == SheetDetent.large) content.fillMaxSize() else content.height(120.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    /** What the system dispatches: a 48 dp three-button bar, and the keyboard over it when it is up. */
    private fun insets(keyboard: Dp) {
        val px = { dp: Dp -> (dp.value * sheetView.resources.displayMetrics.density).toInt() }
        val insets = WindowInsets.Builder()
            .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, px(BAR)))
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, px(keyboard)))
            .setVisible(WindowInsets.Type.ime(), keyboard > 0.dp)
            .build()
        compose.runOnUiThread { sheetView.dispatchApplyWindowInsets(insets) }
        compose.waitForIdle()
    }

    private fun windowBottom() = compose.onNodeWithTag("sheet_scrim").getBoundsInRoot().bottom
    private fun content() = compose.onNodeWithTag("content").getBoundsInRoot()

    /** The window's bottom row under the middle of the sheet: the sheet's opaque ground, or the see-through scrim. */
    private fun groundAtTheBottomEdge(): Boolean {
        val window = compose.onNodeWithTag("sheet_scrim").captureToImage().asAndroidBitmap()
        return android.graphics.Color.alpha(window.getPixel(window.width / 2, window.height - 1)) == 255
    }

    @Test fun withoutTheKeyboardTheSheetReachesTheBottomAndItsContentClearsTheBar() {
        show()
        insets(keyboard = 0.dp)
        assertTrue(groundAtTheBottomEdge(), "the sheet's ground runs under the bar to the bottom edge")
        assertEquals(windowBottom().value - BAR.value, content().bottom.value, 0.5f, "the content ends above the bar")
    }

    @Test fun theSheetStandsOnTheKeyboard() {
        show()
        insets(keyboard = KEYBOARD)
        // The keyboard's inset already holds the bar under it: adding the bar again would float the
        // field a bar's height over the keys.
        assertEquals(windowBottom().value - KEYBOARD.value, content().bottom.value, 0.5f, "the content ends on the keyboard's top")
        assertTrue(!groundAtTheBottomEdge(), "the sheet left the bottom edge to the keyboard")
    }

    @Test fun theKeyboardGoingDownBringsTheSheetBack() {
        show()
        insets(keyboard = KEYBOARD)
        insets(keyboard = 0.dp)
        assertTrue(groundAtTheBottomEdge())
        assertEquals(windowBottom().value - BAR.value, content().bottom.value, 0.5f)
    }

    @Test fun aLargeSheetGetsShorterByTheKeyboard() {
        show(SheetDetent.large)
        insets(keyboard = 0.dp)
        val top = content().top
        insets(keyboard = KEYBOARD)
        assertEquals(windowBottom().value - KEYBOARD.value, content().bottom.value, 0.5f, "it stands on the keyboard")
        assertEquals(top.value, content().top.value, 0.5f, "its top stays under the status bar")
    }

    @Test fun aSheetThatDrawsIntoTheBarStillStandsOnTheKeyboard() {
        show(clearsBar = true)
        insets(keyboard = 0.dp)
        assertEquals(windowBottom().value, content().bottom.value, 0.5f, "without the keyboard it draws into the bar's strip")
        insets(keyboard = KEYBOARD)
        assertEquals(windowBottom().value - KEYBOARD.value, content().bottom.value, 0.5f)
    }

    private companion object {
        val BAR = 48.dp
        val KEYBOARD = 300.dp
    }
}
