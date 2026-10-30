package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import kotlin.test.assertTrue
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.ActionPopover
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.onezee.triptrack.designsystem.components.SettingsOptionPicker
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

// iOS: SettingsOptionPicker.swift, ContentSizedSheet.swift, ToastView.swift.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SheetAndToastTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun showPicker(options: List<String>, selection: String, events: MutableList<String>) {
        var shown by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                if (shown) {
                    SettingsOptionPicker(
                        title = "Тема",
                        options = options,
                        selection = selection,
                        footnote = "",
                        label = { it },
                        onSelect = { events += "select $it" },
                        onDismissRequest = {
                            events += "dismiss"
                            shown = false
                        },
                    )
                }
            }
        }
    }

    @Test fun aPickReportsItselfThenTheSheetCloses() {
        val events = mutableListOf<String>()
        showPicker(listOf("A", "B", "C"), selection = "A", events)
        compose.onNodeWithTag("settings_option_row_1").performClick()
        compose.waitForIdle()
        assertEquals(listOf("select B", "dismiss"), events)
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    @Test fun theChosenRowIsSelected() {
        showPicker(listOf("A", "B", "C"), selection = "B", mutableListOf())
        compose.onNodeWithTag("settings_option_row_1").assertIsSelected()
        compose.onNodeWithTag("settings_option_row_0").assertIsNotSelected()
        compose.onNodeWithTag("settings_option_row_2").assertIsNotSelected()
    }

    @Test fun closeDismissesWithoutAPick() {
        val events = mutableListOf<String>()
        showPicker(listOf("A", "B"), selection = "A", events)
        compose.onNodeWithTag("settings_option_close").performClick()
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), events)
    }

    @Test fun theScrimDismissesWithoutAPick() {
        val events = mutableListOf<String>()
        showPicker(listOf("A", "B"), selection = "A", events)
        compose.onNodeWithTag("sheet_scrim").performTouchInput { click(Offset(8f, 8f)) }
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), events)
    }

    @Test fun aLongListScrolls() {
        showPicker((1..13).map { "L$it" }, selection = "L1", mutableListOf())
        compose.onNode(hasScrollAction()).assertExists()
        compose.onNodeWithTag("settings_option_row_12").performScrollTo()
    }

    @Test fun aShortListIsSizedToItsRowsAndDoesNotScroll() {
        showPicker(listOf("A", "B", "C"), selection = "A", mutableListOf())
        compose.onNode(hasScrollAction()).assertDoesNotExist()
    }

    private fun assertToastLeavesAfter(type: ToastType, delay: Long) {
        var item by mutableStateOf<ToastItem?>(ToastItem(type, "Готово", undoLabel = "Отменить", undoAction = {}))
        compose.setContent { Themed(dark = false) { ToastHost(item, onDismiss = { item = null }) } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(delay - 200)
        compose.onNodeWithTag("toast").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertNull(item, "$type should be gone after $delay ms")
    }

    @Test fun anOrdinaryToastLeavesAfterTwoAndAHalfSeconds() = assertToastLeavesAfter(ToastType.success, 2_500)

    @Test fun anUndoToastStaysASecondLonger() = assertToastLeavesAfter(ToastType.undo, 3_500)

    @Test fun undoRunsTheActionAndDismisses() {
        var undone = false
        var item by mutableStateOf<ToastItem?>(ToastItem(ToastType.undo, "Поездка удалена", "Отменить", undoAction = { undone = true }))
        compose.setContent { Themed(dark = false) { ToastHost(item, onDismiss = { item = null }) } }
        compose.onNodeWithTag("toast_undo").performClick()
        compose.waitForIdle()
        assertEquals(true, undone)
        assertNull(item)
    }

    @Test fun onlyAnUndoToastOffersUndoAndEveryToastIsAnnounced() {
        val item = ToastItem(ToastType.success, "Сохранено", "Отменить", undoAction = {})
        compose.setContent { Themed(dark = false) { ToastHost(item, onDismiss = {}) } }
        compose.onNodeWithTag("toast_undo").assertDoesNotExist()
        compose.onNodeWithTag("toast").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** The sheet slides in from below: on its very first frame it is not on screen yet. */
    @Test fun theSheetStartsBelowTheScreen() {
        compose.mainClock.autoAdvance = false
        showPicker(listOf("A", "B"), selection = "A", mutableListOf())
        compose.mainClock.advanceTimeByFrame()
        val bottom = compose.onNodeWithTag("sheet_scrim").getBoundsInRoot().bottom
        val top = compose.onNodeWithTag("sheet").getBoundsInRoot().top
        assertTrue(top >= bottom - 1.dp, "sheet top $top, screen bottom $bottom")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(compose.onNodeWithTag("sheet").getBoundsInRoot().top < bottom)
    }

    /** The popover's shadow margin is still a tap outside it. */
    @Test fun aTapBesideThePopoverClosesIt() {
        var expanded by mutableStateOf(true)
        var chosen = false
        compose.setContent {
            Themed(dark = false) {
                Box(Modifier.padding(top = 80.dp, start = 120.dp)) {
                    Box(Modifier.size(40.dp))
                    ActionPopover(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        items = listOf(ActionPopoverItem("Изменить", AppGlyph.pencil) { chosen = true }),
                    )
                }
            }
        }
        compose.onNode(isPopup()).performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()
        assertEquals(false, expanded)
        assertEquals(false, chosen)
    }

    @Test fun aPopoverRowClosesThePopoverThenActs() {
        val events = mutableListOf<String>()
        var expanded by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                Box(Modifier.padding(top = 80.dp, start = 120.dp)) {
                    Box(Modifier.size(40.dp))
                    ActionPopover(
                        expanded = expanded,
                        onDismissRequest = {
                            events += "close"
                            expanded = false
                        },
                        items = listOf(ActionPopoverItem("Изменить", AppGlyph.pencil, testTag = "edit") { events += "edit, open=$expanded" }),
                    )
                }
            }
        }
        compose.onNodeWithTag("edit").performClick()
        compose.waitForIdle()
        assertEquals(listOf("close", "edit, open=false"), events)
        compose.onNode(isPopup()).assertDoesNotExist()
    }
}
