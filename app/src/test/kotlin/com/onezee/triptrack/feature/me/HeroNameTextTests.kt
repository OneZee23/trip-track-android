package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The profile name, like iOS: one shrinking line, then two lines split at a space, never a lone letter (5 Nov 2026). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h852dp-420dpi")
class HeroNameTextTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun show(name: String, width: Int) {
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.width(width.dp)) { HeroNameText(name, Color.Black) }
            }
        }
        compose.waitForIdle()
    }

    @Test fun aNameThatFitsStaysOneLineAtFullSize() {
        show("Ярослав Подзаборовский", 360)
        val bounds = compose.onNodeWithText("Ярослав Подзаборовский").getBoundsInRoot()
        assertTrue("one line is ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top < 34.dp)
    }

    @Test fun aLongNameShrinksOntoOneLineWhenThatIsEnough() {
        show("Ярослав Подзаборовский", 160)
        val bounds = compose.onNodeWithText("Ярослав Подзаборовский").getBoundsInRoot()
        assertTrue("one line is ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top < 20.dp)
    }

    @Test fun aNarrowColumnSplitsTheNameAtTheSpace() {
        show("Ярослав Подзаборовский", 94)
        assertEquals(1, compose.onAllNodesWithText("Ярослав").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithText("Подзаборовский").fetchSemanticsNodes().size)
    }

    @Test fun linesSplitWhereTheyAreClosest() {
        assertEquals("Ярослав" to "Подзаборовский", heroNameLines("Ярослав Подзаборовский"))
        assertEquals("Анна Мария" to "Петрова", heroNameLines("Анна Мария Петрова"))
        assertNull(heroNameLines("Vixor66"))
    }
}
