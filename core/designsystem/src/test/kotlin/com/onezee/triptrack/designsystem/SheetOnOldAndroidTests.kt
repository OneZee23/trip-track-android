package com.onezee.triptrack.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A house sheet opens on Android 10 and 11. The navigation-bar fallback for those versions once
 * read the inset through the platform `WindowInsets.Type`, an API 30 class: on the owner's POCO F1
 * (Android 10) every sheet threw NoSuchMethodError and closed the app (5 Nov 2026), while the tests,
 * all on API 35, stayed green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30])
class SheetOnOldAndroidTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RegisterComponentActivity()).around(compose)

    @Test fun sheetOpens() {
        compose.setContent {
            Themed(dark = false) {
                ContentSizedSheet(onDismissRequest = {}) {
                    BasicText("payload", Modifier.testTag("payload"))
                }
            }
        }
        compose.onNodeWithTag("payload", useUnmergedTree = true).assertExists()
    }
}
