package com.onezee.triptrack

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.graphics.drawable.toDrawable
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.onboarding.OnboardingRoute
import com.onezee.triptrack.shell.AppShell

/**
 * The one activity. An AppCompatActivity because AppCompat carries the per-app language below
 * Android 13. Edge to edge: every tab draws under the system bars, as on iOS, and the tab bar
 * floats over the content.
 */
class MainActivity : AppCompatActivity() {
    // Bumped on every return to the screen: permission and health answers are read again then.
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as TripTrackApplication).appGraph
        setContent {
            val dark = graph.themeManager.mode.isDark(isSystemInDarkTheme())
            SystemBarsFollowTheme(dark)
            TripTrackTheme(isDark = dark, language = graph.languageManager.current(LocalConfiguration.current)) {
                // iOS: TripTrackApp gates the whole window on hasCompletedOnboarding.
                if (graph.onboarding.completed) AppShell(graph, resumeTick.intValue)
                else OnboardingRoute(graph, resumeTick.intValue)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as TripTrackApplication).appGraph.onForeground()
    }

    override fun onStop() {
        super.onStop()
        (application as TripTrackApplication).appGraph.onBackground()
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        (application as TripTrackApplication).appGraph.location.onAppForeground()
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
            window.setBackgroundDrawable(AppTheme.colors(dark).bg.toArgb().toDrawable())
            onDispose {}
        }
    }

    private companion object {
        // The scrims androidx.activity puts behind three-button navigation by default.
        val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
