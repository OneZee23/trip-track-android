package com.onezee.triptrack.feature.trip.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TripHiddenParts
import java.time.Instant
import java.util.UUID
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

/** Build 15 printed «Скрыть от других» one letter per line: the summary beside it took the whole width (POCO, 5 Nov). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w393dp-h780dp-420dpi")
class HiddenPartsEntryCardTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test
    fun titleStaysOnOneLineAboveTheSummary() {
        val t0 = Instant.parse("2026-11-02T17:05:00Z")
        val parts = TripHiddenParts(startMetres = 500, endMetres = 500,
            sections = listOf(HiddenSection(UUID.randomUUID(), t0.plusSeconds(180), t0.plusSeconds(360))))
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { HiddenPartsEntryCard(parts, null) {} }
            }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText("Скрыть от других", useUnmergedTree = true).getBoundsInRoot()
        val summary = compose.onNodeWithText("Начало", substring = true, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("title is ${title.right - title.left} wide", title.right - title.left > 100.dp)
        assertTrue("title is ${title.bottom - title.top} tall", title.bottom - title.top < 30.dp)
        assertTrue("summary starts at ${summary.top}, title ends at ${title.bottom}", summary.top >= title.bottom - 1.dp)
        captureScreenRoboImage("src/test/screenshots/hidden_parts_entry_card.png")
    }
}
