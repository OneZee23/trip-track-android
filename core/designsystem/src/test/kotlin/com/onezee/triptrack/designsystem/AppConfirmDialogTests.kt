package com.onezee.triptrack.designsystem

import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppColors
import com.onezee.triptrack.designsystem.theme.AppTheme
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// iOS: AppConfirmDialog.swift and the «Dialogs» rules of CLAUDE.md.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppConfirmDialogTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private val strings get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)

    @Test fun anAnswerDismissesTheDialogBeforeItsHandlerRuns() {
        val events = mutableListOf<String>()
        var shown by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(
                    isPresented = shown,
                    onDismissRequest = {
                        events += "dismiss"
                        shown = false
                    },
                    title = "Удалить поездку?",
                    actions = listOf(AppDialogAction("Удалить", AppDialogAction.Kind.destructive) { events += "handler, shown=$shown" }),
                )
            }
        }
        compose.onNodeWithTag("app_dialog_action").performClick()
        compose.waitForIdle()
        assertEquals(listOf("dismiss", "handler, shown=false"), events)
        compose.onNodeWithTag("app_dialog").assertDoesNotExist()
    }

    @Test fun cancelIsTheLastAnswerAndSaysCancel() {
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(
                    isPresented = true,
                    onDismissRequest = {},
                    title = "?",
                    actions = listOf(
                        AppDialogAction("A", testTag = "first"),
                        AppDialogAction("B", AppDialogAction.Kind.plain, testTag = "second"),
                    ),
                )
            }
        }
        val cancel = compose.onNodeWithTag("app_dialog_cancel")
        cancel.assertTextEquals(strings.cancel())
        val cancelTop = cancel.getBoundsInRoot().top
        listOf("first", "second").forEach { tag ->
            assertTrue(compose.onNodeWithTag(tag).getBoundsInRoot().bottom <= cancelTop, "$tag must sit above Cancel")
        }
    }

    @Test fun anAcknowledgementWithoutCancelHasNoCancel() {
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(isPresented = true, onDismissRequest = {}, title = "!", actions = listOf(AppDialogAction("OK")), cancelTitle = null)
            }
        }
        compose.onNodeWithTag("app_dialog_cancel").assertDoesNotExist()
    }

    @Test fun theScrimSwallowsATapAndClosesWithoutAnswering() {
        var answered = false
        var shown by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(
                    isPresented = shown,
                    onDismissRequest = { shown = false },
                    title = "?",
                    actions = listOf(AppDialogAction("Да") { answered = true }),
                )
            }
        }
        compose.onNodeWithTag("app_dialog_scrim").performTouchInput { click(Offset(8f, 8f)) }
        compose.waitForIdle()
        assertEquals(false, shown)
        assertEquals(false, answered)
    }

    @Test fun theScrimIsHiddenFromTalkBackAndOffersNoAction() {
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(isPresented = true, onDismissRequest = {}, title = "?", actions = listOf(AppDialogAction("Да")))
            }
        }
        compose.onNodeWithTag("app_dialog_scrim")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithTag("app_dialog").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "?"))
    }

    @Test fun theKindDecidesTheColourNotTheCaller() {
        for (colors in listOf(AppColors.light, AppColors.dark)) {
            assertEquals(AppTheme.red, AppDialogAction("x", AppDialogAction.Kind.destructive).fill(colors))
            assertEquals(Color.White, AppDialogAction("x", AppDialogAction.Kind.destructive).ink(colors))
            assertEquals(AppTheme.accent, AppDialogAction("x", AppDialogAction.Kind.primary).fill(colors))
            assertEquals(colors.cardAlt, AppDialogAction("x", AppDialogAction.Kind.plain).fill(colors))
            assertEquals(colors.text, AppDialogAction("x", AppDialogAction.Kind.plain).ink(colors))
        }
    }

    @Test fun aSecondTapDuringTheExitRunsNothingTwice() {
        var handled = 0
        var dismissed = 0
        var shown by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(
                    isPresented = shown,
                    onDismissRequest = {
                        dismissed++
                        shown = false
                    },
                    title = "?",
                    actions = listOf(AppDialogAction("Удалить", AppDialogAction.Kind.destructive) { handled++ }),
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("app_dialog_action").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onAllNodesWithTag("app_dialog_action")[0].performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, handled)
        assertEquals(1, dismissed)
    }

    @Test fun theItemVariantHandsTheItemToItsHandler() {
        var item by mutableStateOf<String?>("photo-7")
        var deleted: String? = null
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(
                    item = item,
                    onDismissRequest = { item = null },
                    title = { "Удалить $it?" },
                    actions = { photo -> listOf(AppDialogAction("Удалить", AppDialogAction.Kind.destructive) { deleted = photo }) },
                )
            }
        }
        compose.onNodeWithTag("app_dialog").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Удалить photo-7?"))
        compose.onNodeWithTag("app_dialog_action").performClick()
        compose.waitForIdle()
        assertNull(item)
        assertEquals("photo-7", deleted)
    }

    @Test fun backClosesWithoutAnswering() {
        var answered = false
        var shown by mutableStateOf(true)
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(isPresented = shown, onDismissRequest = { shown = false }, title = "?", actions = listOf(AppDialogAction("Да") { answered = true }))
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            ShadowDialog.getLatestDialog().onBackPressed()
        }
        compose.waitForIdle()
        assertEquals(false, shown)
        assertEquals(false, answered)
    }

    @Test fun clearingTheStateFromOutsideClosesItAtOnce() {
        var shown by mutableStateOf(true)
        var answered = false
        compose.setContent {
            Themed(dark = false) {
                AppConfirm(isPresented = shown, onDismissRequest = { shown = false }, title = "?", actions = listOf(AppDialogAction("Да") { answered = true }))
            }
        }
        compose.onNodeWithTag("app_dialog").assertExists()
        shown = false
        compose.waitForIdle()
        compose.onNodeWithTag("app_dialog").assertDoesNotExist()
        assertEquals(false, answered)
    }

    /** Our scrim, our entrance, the whole screen, and the status bar glyphs of the app's theme. */
    @Test fun theWindowIsPlainFullScreenAndKeepsTheThemesGlyphs() {
        var dark by mutableStateOf(false)
        compose.setContent {
            Themed(dark = dark) {
                AppConfirm(isPresented = true, onDismissRequest = {}, title = "?", actions = listOf(AppDialogAction("Да")))
            }
        }
        for (theme in listOf(false, true)) {
            dark = theme
            compose.waitForIdle()
            val window = ShadowDialog.getLatestDialog().window!!
            val attributes = window.attributes
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attributes.width)
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, attributes.height)
            assertEquals(0, attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            assertEquals(0, attributes.windowAnimations)
            val darkGlyphs = window.insetsController!!.systemBarsAppearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            assertEquals(if (theme) 0 else WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, darkGlyphs, "dark=$theme")
        }
    }
}
