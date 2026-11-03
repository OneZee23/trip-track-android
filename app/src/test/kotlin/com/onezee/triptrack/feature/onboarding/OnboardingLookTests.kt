// iOS: OnboardingView.mockEndpointDot. ON-02 of the 3 November audit; the frames are AccountScreenshotTests (onboarding_*).
package com.onezee.triptrack.feature.onboarding

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.assertTrue
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class OnboardingLookTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    private fun isWhite(argb: Int) = channels(argb).toList().all { it >= 245 }
    private fun isColour(argb: Int, expected: Int) = channels(argb).toList().zip(channels(expected).toList()).all { (a, b) -> abs(a - b) <= 14 }

    /**
     * iOS draws each end of the route as a 9 pt disc of colour with a 2 pt white stroke centred on its edge: 7 pt of colour inside
     * a 2 pt ring, 11 pt across. Probed straight up from each centre, away from the route: the colour at 2 dp, the ring at 4 dp
     * (the old 9 dp disc was still colour there) and the map again at 6 dp (the old 13 dp ring was still white there).
     */
    @Test fun eachEndOfTheRouteIsAColouredDiscOfSevenInsideAWhiteRingOfTwo() {
        compose.setContent {
            TripTrackTheme(isDark = false, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.width(360.dp)) { MockMap(AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)) }
            }
        }
        compose.waitForIdle()
        val map = compose.onNodeWithTag("onboarding_mock_map").captureToImage().asAndroidBitmap()
        val density = map.width / 360f
        for ((x, y, colour) in listOf(Triple(0.11f, 0.83f, AppTheme.red), Triple(0.72f, 0.11f, AppTheme.green))) {
            val centreX = map.width * x
            val centreY = map.height * y
            // The pixel that holds the point `dp` above the true centre, so the sample is within half a pixel of the radius asked for.
            fun up(dp: Float) = map.getPixel(floor(centreX).toInt(), floor(centreY - dp * density).toInt())
            assertTrue(isColour(up(2f), colour.toArgb()), "the colour at 2 dp from the centre: ${channels(up(2f))}")
            assertTrue(isWhite(up(4f)), "the ring at 4 dp from the centre: ${channels(up(4f))}")
            assertTrue(!isWhite(up(6f)), "the map again at 6 dp from the centre: ${channels(up(6f))}")
        }
    }
}
