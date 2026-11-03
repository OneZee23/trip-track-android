// iOS: ProfileRoadScene (the 25 pt fade over the foot of the band), ProfileCardShotTests.profile-road-*
package com.onezee.triptrack.feature.me

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.model.RoadSceneTime
import kotlin.math.abs
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

/** ME-13: the band of the road scene fades into the card colour over its last 25 dp, on «Я» and on a stranger's card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class ProfileRoadSceneTests {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    /** The mean brightness of one pixel row, sampled across the band. */
    private fun luma(map: PixelMap, y: Int): Float = (0 until map.width step 7).map { x -> map[x, y].let { (it.red + it.green + it.blue) / 3f } }.average().toFloat()

    @Test fun theBandFadesIntoTheCardOverItsLastTwentyFivePoints() {
        compose.setContent {
            TripTrackTheme(false, AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Column {
                        Box(Modifier.size(300.dp, 216.dp).testTag("road_faded")) { ProfileRoadScene(RoadSceneTime.day, Modifier.fillMaxSize()) }
                        Image(painterResource(MeArtwork.drawables.getValue("road_day")), null, Modifier.size(300.dp, 216.dp).testTag("road_plain"), contentScale = ContentScale.FillBounds)
                    }
                }
            }
        }
        compose.waitForIdle()
        // The same drawing with and without the fade: they agree 35 dp above the foot and part ways at the foot itself.
        val faded = compose.onNodeWithTag("road_faded").captureToImage().toPixelMap()
        val plain = compose.onNodeWithTag("road_plain").captureToImage().toPixelMap()
        val density = 420f / 160f
        val foot = faded.height - 2
        val above = faded.height - (35 * density).toInt()
        assertTrue(abs(luma(faded, above) - luma(plain, above)) < 0.01f, "the band above the fade is untouched")
        assertTrue(luma(faded, foot) - luma(plain, foot) > 0.1f, "the foot is lighter: ${luma(faded, foot)} against ${luma(plain, foot)}")
    }
}
