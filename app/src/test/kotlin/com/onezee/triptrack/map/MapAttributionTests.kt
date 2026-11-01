package com.onezee.triptrack.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.map.attribution.MapAttribution
import com.onezee.triptrack.map.attribution.MapAttributionSheet
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: ContentSizedSheet.swift; Android replaces MapLibre's AttributionDialogManager.
// No native map is created: Robolectric checks the actual house sheet, provider links and dismissal.
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi", application = Application::class)
@OptIn(ExperimentalRoborazziApi::class)
class MapAttributionTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun show(credits: List<MapAttribution> = MapAttribution.openFreeMap, onOpen: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                var visible by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
                if (visible) MapAttributionSheet(credits, onDismissRequest = { visible = false; onDismiss() }, onOpenLink = onOpen)
            }
        }
        compose.waitForIdle()
    }

    @Test fun attributionSheet() {
        show()
        compose.onNodeWithText("Источники карты").assertIsDisplayed()
        MapAttribution.openFreeMap.forEach { compose.onNodeWithText(it.title).assertIsDisplayed() }
        captureScreenRoboImage("src/test/screenshots/map_attribution_${if (dark) "dark" else "light"}.png")
    }

    @Test fun eachProviderOpensItsOriginalLink() {
        val opened = mutableListOf<String>()
        show(onOpen = { opened += it })
        MapAttribution.openFreeMap.indices.forEach { compose.onNodeWithTag("map_attribution_link_$it").performClick() }
        assertEquals(MapAttribution.openFreeMap.map { it.url }, opened)
    }

    @Test fun houseCloseDismissesTheSheet() {
        var closed = 0
        show(onDismiss = { closed++ })
        compose.onNodeWithTag("map_attribution_close").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("map_attribution_header").assertDoesNotExist()
        assertEquals(1, closed)
    }

    @Test fun loadedSourcesKeepEveryAttributionAndLinkWithoutDuplicates() {
        val html = """<a href="https://openfreemap.org/">OpenFreeMap</a> © <a href="https://openmaptiles.org/">OpenMapTiles</a> """ +
            """<a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a> <a href="https://example.org/tiles">Extra tiles</a>"""
        val credits = MapAttribution.fromSources(compose.activity, listOf(html, html))
        assertEquals(4, credits.size)
        assertTrue(credits.any { it.title == "Extra tiles" && it.url == "https://example.org/tiles" })
        show(credits)
        compose.onNodeWithText("Extra tiles").assertIsDisplayed()
    }

    @Test fun beforeTileJsonArrivesTheRequiredCreditsStayReachable() {
        val credits = MapAttribution.fromSources(compose.activity, emptyList())
        assertEquals(MapAttribution.openFreeMap, credits)
    }

    @Test fun missingBrowserKeepsTheSheetAndExplainsTheFailure() {
        show(onOpen = { throw IllegalArgumentException("No browser") })
        compose.onNodeWithTag("map_attribution_link_0").performClick()
        compose.onNodeWithText("На телефоне нет браузера для открытия ссылки.").assertIsDisplayed()
        compose.onNodeWithTag("map_attribution_close").assertIsDisplayed()
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
