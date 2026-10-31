package com.onezee.triptrack

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell on a phone (Pixel 7 width and density), light and dark: Лента with the bar, Я with
 * the settings card. Robolectric draws no system bars, so the content starts at the very top.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class ShellScreenshotTests(private val dark: Boolean) {
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OnboardingDone()).around(compose)

    private val theme = if (dark) "dark" else "light"

    private fun prepare() {
        val graph = (RuntimeEnvironment.getApplication() as TripTrackApplication).appGraph
        compose.runOnUiThread { graph.themeManager.select(if (dark) ThemeMode.dark else ThemeMode.light) }
        compose.waitForIdle()
    }

    @Test fun feed() {
        prepare()
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_feed_$theme.png")
    }

    @Test fun me() {
        prepare()
        compose.onNodeWithTag("tab_profile").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/shell_me_$theme.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
