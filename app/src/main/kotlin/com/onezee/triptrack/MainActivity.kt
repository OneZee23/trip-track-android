package com.onezee.triptrack

import android.content.Intent
import android.graphics.Color
import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.graphics.drawable.toDrawable
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.designsystem.theme.ThemeMode
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
        val graph = (application as TripTrackApplication).appGraph
        delegate.localNightMode = graph.themeManager.mode.nightMode()
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.onezee.triptrack.feature.me.MeLaunch.apply(graph, intent)
        // A link that started the app; a recreate (rotation, theme) must not open it twice.
        if (savedInstanceState == null) openLink(intent)
        setContent {
            val dark = graph.themeManager.mode.isDark(isSystemInDarkTheme())
            SystemBarsFollowTheme(dark, graph.themeManager.mode)
            TripTrackTheme(isDark = dark, language = graph.languageManager.current(LocalConfiguration.current)) {
                ScreenRecordingObserver(graph)
                // iOS: TripTrackApp gates the whole window on hasCompletedOnboarding.
                if (graph.onboarding.completed) AppShell(graph, resumeTick.intValue)
                else OnboardingRoute(graph, resumeTick.intValue)
            }
        }
    }

    @Composable
    private fun ScreenRecordingObserver(graph: AppGraph) {
        DisposableEffect(graph) {
            if (Build.VERSION.SDK_INT < 35) {
                graph.settingsManager.setScreenRecording(false)
                onDispose { }
            } else {
                val callback = java.util.function.Consumer<Int> { state ->
                    graph.settingsManager.setScreenRecording(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
                }
                val manager = windowManager
                val current = manager.addScreenRecordingCallback(mainExecutor, callback)
                graph.settingsManager.setScreenRecording(current == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
                onDispose {
                    manager.removeScreenRecordingCallback(callback)
                    graph.settingsManager.setScreenRecording(false)
                }
            }
        }
    }

    /** singleTask: a link tapped while the app runs arrives here instead of a second activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openLink(intent)
    }

    /**
     * iOS TripTrackApp.handleDeepLink: App Links (`trip-track.app/u/`, `/s/`, `/j/`) and the
     * `triptrack://` scheme. A tapped notification arrives with its push category and data as
     * extras: the seam the FCM service (later) fills, routed the way iOS routes a push tap.
     */
    private fun openLink(intent: Intent?) {
        val social = (application as TripTrackApplication).appGraph.social
        val category = intent?.getStringExtra(EXTRA_PUSH_CATEGORY)
        if (category != null) {
            val data = intent.extras?.keySet().orEmpty().filter { it != EXTRA_PUSH_CATEGORY }
                .mapNotNull { key -> intent.getStringExtra(key)?.let { key to it } }.toMap()
            social.pushTapped(category, data)
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString?.let(com.onezee.triptrack.feature.social.DeepLink::parse) ?: return
        social.open(link)
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
        (application as TripTrackApplication).appGraph.me.analytics.foreground()
        (application as TripTrackApplication).appGraph.location.onAppForeground()
    }

    /**
     * The in-app theme can differ from the phone's, so the bar glyphs and the window ground follow
     * the app, not the system: dark glyphs on a light app, light on a dark one. The window ground
     * is what shows for the frame before Compose draws, and through a recreate.
     */
    @Composable
    private fun SystemBarsFollowTheme(dark: Boolean, mode: ThemeMode) {
        DisposableEffect(dark, mode) {
            delegate.localNightMode = mode.nightMode()
            // Android 12 retains this for the next cold-start splash, before our process exists.
            if (Build.VERSION.SDK_INT >= 31) getSystemService(UiModeManager::class.java).setApplicationNightMode(when (mode) {
                ThemeMode.dark -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.system -> UiModeManager.MODE_NIGHT_AUTO
            })
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { dark },
            )
            window.setBackgroundDrawable(AppTheme.colors(dark).bg.toArgb().toDrawable())
            onDispose {}
        }
    }

    private fun ThemeMode.nightMode(): Int = when (this) {
        ThemeMode.dark -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.light -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.system -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    companion object {
        /** The push category a notification tap carries (REACTION, COMMENT, FOLLOW …). */
        const val EXTRA_PUSH_CATEGORY = "push_category"

        // The scrims androidx.activity puts behind three-button navigation by default.
        private val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
