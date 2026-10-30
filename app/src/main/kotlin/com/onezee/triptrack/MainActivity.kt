package com.onezee.triptrack

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.shell.AppShell

/**
 * The one activity. An AppCompatActivity because AppCompat carries the per-app language below
 * Android 13. Edge to edge: every tab draws under the system bars, as on iOS, and the tab bar
 * floats over the content.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as TripTrackApplication).appGraph
        setContent {
            val dark = graph.themeManager.mode.isDark(isSystemInDarkTheme())
            SystemBarsFollowTheme(dark)
            TripTrackTheme(isDark = dark, language = graph.languageManager.current(LocalConfiguration.current)) {
                AppShell(graph)
            }
        }
    }

    /**
     * The in-app theme can differ from the phone's, so the bar glyphs and the window ground follow
     * the app, not the system: dark glyphs on a light app, light on a dark one. The window ground
     * is what shows for the frame before Compose draws, and through a recreate.
     */
    @Composable
    private fun SystemBarsFollowTheme(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { dark },
            )
            window.setBackgroundDrawable(ColorDrawable(AppTheme.colors(dark).bg.toArgb()))
            onDispose {}
        }
    }

    private companion object {
        // The scrims androidx.activity puts behind three-button navigation by default.
        val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
