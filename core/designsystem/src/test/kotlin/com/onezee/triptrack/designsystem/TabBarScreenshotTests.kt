package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.CustomTabBar
import com.onezee.triptrack.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tab bar across a phone: Pixel 7 width and density, gesture navigation. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class TabBarScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    private fun capture(name: String, selected: AppTab, unseen: Boolean) {
        compose.setContent {
            Themed(dark) {
                // Room above the capsule for the raised record disk, which draws past the bar.
                Box(Modifier.testTag(SHOT).fillMaxWidth().background(AppTheme.colors.bg).padding(top = 28.dp)) {
                    CustomTabBar(selectedTab = selected, onSelect = {}, hasUnseenDrafts = unseen)
                }
            }
        }
        compose.onNodeWithTag(SHOT).captureRoboImage(shotPath(name, dark))
    }

    @Test fun feedSelected() = capture("tab_bar", AppTab.home, unseen = false)

    @Test fun profileSelectedWithUnseenDrafts() = capture("tab_bar_profile_unseen", AppTab.profile, unseen = true)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
