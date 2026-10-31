package com.onezee.triptrack.replay

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.AdaptiveIconDrawable
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.BuildConfig
import com.onezee.triptrack.R
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// iOS: Tools/AppIcon/D2.svg, Tools/build_app_icons.py, Info.plist UILaunchScreen.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class LaunchArtworkTests {
    @Test fun adaptiveAndRoundIconsIncludeTheThemedLayer() {
        val app = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248,246,242))
        for ((index,id) in listOf(R.mipmap.ic_launcher,R.mipmap.ic_launcher_round).withIndex()) {
            val icon = app.getDrawable(id) as AdaptiveIconDrawable
            assertNotNull(icon.foreground); assertNotNull(icon.background); assertNotNull(icon.monochrome)
            icon.setBounds(index * 240 + 24,24,index * 240 + 216,216)
            icon.draw(canvas)
            val themed = AdaptiveIconDrawable(ColorDrawable(Color.rgb(218,225,208)), assertNotNull(icon.monochrome).mutate().apply { setTint(Color.rgb(42,57,33)) })
            themed.setBounds(index * 240 + 24,264,index * 240 + 216,456)
            themed.draw(canvas)
        }
        val variant = if (BuildConfig.DEBUG) "debug" else "release"
        bitmap.captureRoboImage("src/test/screenshots/launcher_$variant.png")
    }
    @Test fun splashUsesTheThemeGroundAndAnExplicitEmptyIcon() {
        val app = RuntimeEnvironment.getApplication()
        for ((night, expected) in listOf(false to Color.rgb(248,246,242),true to Color.rgb(18,18,20))) {
            val config = Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = app.createConfigurationContext(config)
            val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_TripTrack_Starting,true) }
            val attributes = theme.obtainStyledAttributes(intArrayOf(
                androidx.core.splashscreen.R.attr.windowSplashScreenBackground,
                androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
                androidx.core.splashscreen.R.attr.postSplashScreenTheme,
            ))
            try {
                assertEquals(expected,attributes.getColor(0,0))
                assertEquals(R.style.Theme_TripTrack,attributes.getResourceId(2,0))
                val bitmap = Bitmap.createBitmap(288,288,Bitmap.Config.ARGB_8888)
                val icon = assertNotNull(attributes.getDrawable(1))
                icon.setBounds(0,0,288,288); icon.draw(Canvas(bitmap))
                assertTrue(IntArray(288*288).also { bitmap.getPixels(it,0,288,0,0,288,288) }.all { Color.alpha(it) == 0 })
            } finally { attributes.recycle() }
        }
    }
}
