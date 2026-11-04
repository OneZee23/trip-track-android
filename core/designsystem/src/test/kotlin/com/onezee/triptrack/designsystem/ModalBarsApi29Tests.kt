package com.onezee.triptrack.designsystem

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import com.onezee.triptrack.designsystem.components.configurePlainModalWindow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Android 10/11 dialog windows do not inherit the activity's edge-to-edge setup.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30])
class ModalBarsApi29Tests {
    @Test fun dialogUsesTransparentEdgeToEdgeBarsWithThemeGlyphs() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val window = activity.window
        for (dark in listOf(false, true)) {
            window.configurePlainModalWindow(dark)
            val flags = window.decorView.systemUiVisibility
            assertEquals(android.graphics.Color.TRANSPARENT, window.statusBarColor)
            assertEquals(android.graphics.Color.TRANSPARENT, window.navigationBarColor)
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, window.attributes.width)
            assertEquals(WindowManager.LayoutParams.MATCH_PARENT, window.attributes.height)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_STABLE != 0)
                assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN != 0)
                assertTrue(flags and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION != 0)
            }
            val lightStatus: Boolean
            val lightNavigation: Boolean
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val appearance = window.insetsController!!.systemBarsAppearance
                lightStatus = appearance and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0
                lightNavigation = appearance and WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS != 0
            } else {
                lightStatus = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
                lightNavigation = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR != 0
            }
            assertEquals(!dark, lightStatus, "status glyphs, dark=$dark")
            assertEquals(!dark, lightNavigation, "navigation glyphs, dark=$dark")
        }
    }
}
