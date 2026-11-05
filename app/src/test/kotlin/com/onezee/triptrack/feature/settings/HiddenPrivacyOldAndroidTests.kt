package com.onezee.triptrack.feature.settings

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29, 30], qualifiers = "ru-w393dp-h780dp-420dpi")
class HiddenPrivacyOldAndroidTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test fun questionCanBeCompletedWithOldInsets() {
        var saved: Int? = null
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                PrivacyEdgeQuestionSheet({ saved = it }, {})
            }
        }
        compose.onNodeWithTag("privacy_edge_choice_500").performClick()
        compose.onNodeWithTag("privacy_edge_save").performClick()
        assertEquals(500, saved)
    }
}
