package com.onezee.triptrack.designsystem

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Lets the Compose rule launch a bare ComponentActivity under Robolectric without a test manifest,
 * the way Roborazzi registers its own activity, and without an action bar over the content.
 */
class RegisterComponentActivity : TestWatcher() {
    override fun starting(description: Description) {
        val app = RuntimeEnvironment.getApplication()
        val packages = Shadows.shadowOf(app.packageManager)
        val info = packages.addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        info.theme = android.R.style.Theme_Material_Light_NoActionBar
        packages.addOrUpdateActivity(info)
    }
}

/** Russian, the language the canon is drawn in. */
@Composable
fun Themed(dark: Boolean, language: AppLanguage = AppLanguage.ru, content: @Composable () -> Unit) {
    TripTrackTheme(isDark = dark, language = language, reduceMotion = false, content = content)
}

/** A component on the theme ground with some air, tagged for a tight capture. */
@Composable
fun Specimen(dark: Boolean, content: @Composable () -> Unit) {
    Themed(dark) {
        Box(Modifier.testTag(SHOT).background(AppTheme.colors.bg).padding(16.dp)) { content() }
    }
}

const val SHOT = "shot"

fun shotPath(name: String, dark: Boolean) = "src/test/screenshots/${name}_${if (dark) "dark" else "light"}.png"
