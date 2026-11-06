package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.TestTripTrackApplication
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.components.ToastView
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.ManualTripPlaceDTO
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class ManualTripScreenshotTests(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes() = listOf(arrayOf(false), arrayOf(true))
    }

    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val from = ManualPlace("Краснодар", GeoPoint(45.0355, 38.9753), "Краснодар")
    private val to = ManualPlace("Джубга", GeoPoint(44.3196, 38.7089), "Джубга")
    private val route = listOf(from.point, GeoPoint(44.8, 38.9), to.point)

    private fun shot(name: String, seed: ManualTripUiSeed, toast: Boolean = false) {
        val graph = (compose.activity.application as TestTripTrackApplication).appGraph
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(Modifier.background(AppTheme.colors.bg)) {
                        if (toast) ToastView(
                            ToastItem(
                                type = ToastType.undo,
                                message = graph.strings.tr(com.onezee.triptrack.designsystem.R.string.manualTripSuccessMessage),
                                undoLabel = graph.strings.tr(com.onezee.triptrack.designsystem.R.string.manualTripReturnShort),
                                undoAction = {},
                            ), {}, Modifier.align(Alignment.BottomCenter),
                        ) else ManualTripSheet(graph, {}, {}, seed = seed)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/manual_trip_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun emptyPlaces() = shot("places_empty", ManualTripUiSeed())

    @Test fun typingWithResults() = shot("places_results", ManualTripUiSeed(
        query = "Крас",
        results = listOf(ManualTripPlaceDTO("Краснодар", 45.0355, 38.9753), ManualTripPlaceDTO("Краснодарский край", 45.2, 39.0)),
    ))

    @Test fun offlineSearch() = shot("places_offline", ManualTripUiSeed(query = "Крас", error = "offline"))

    @Test fun routeWithoutStops() = shot("route", ManualTripUiSeed(stage = true, from = from, to = to, route = route, title = "Краснодар → Джубга", routeLengthMetres = 82_000.0, suggestedDurationSeconds = 3600.0))

    @Test fun routeWithStops() = shot("route_via", ManualTripUiSeed(
        stage = true, from = from, to = to, via = listOf(ViaPlace(1, ManualPlace("Горячий Ключ", GeoPoint(44.6, 39.1)))), route = route, title = "Краснодар → Джубга", routeLengthMetres = 82_000.0, suggestedDurationSeconds = 3600.0,
    ))

    @Test fun noRouteError() = shot("route_no_route", ManualTripUiSeed(stage = true, from = from, to = to, error = "noRoute"))

    @Test fun offlineRouteError() = shot("route_offline", ManualTripUiSeed(stage = true, from = from, to = to, error = "offline"))

    @Test fun tooManyStopsError() = shot("route_too_many_stops", ManualTripUiSeed(stage = true, from = from, to = to, error = "tooManyStops"))

    @Test fun failedRouteError() = shot("route_failed", ManualTripUiSeed(stage = true, from = from, to = to, error = "failed"))

    @Test fun proRequiredRouteError() = shot("route_locked", ManualTripUiSeed(stage = true, from = from, to = to, error = "locked"))

    @Test fun returnTripToast() = shot("return_toast", ManualTripUiSeed(stage = true, from = to, to = from, route = route), toast = true)
}
