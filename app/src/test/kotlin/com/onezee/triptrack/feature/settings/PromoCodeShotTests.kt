// Android-only promo sheet; all states are captured in both app themes at a compact width.
package com.onezee.triptrack.feature.settings

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.ui.LocalAppVersion
import java.time.Instant
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

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w360dp-h800dp-420dpi")
class PromoCodeShotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private fun shot(name: String, signedIn: Boolean = true, busy: Boolean = false, error: Int? = null, until: Instant? = null) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalAppVersion provides "0.9.2-dev") {
                    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                        ContentSizedSheet({}, background = AppTheme.colors.bg) {
                            PromoCodeSheetContent(signedIn, "AbC-1234", busy, error, until, {}, {}, {})
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/promo_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun idle() = shot("idle")
    @Test fun checking() = shot("checking", busy = true)
    @Test fun success() = shot("success", until = Instant.parse("2026-12-06T10:00:00Z"))
    @Test fun notFound() = shot("not_found", error = com.onezee.triptrack.designsystem.R.string.promo_not_found)
    @Test fun alreadyRedeemed() = shot("already_redeemed", error = com.onezee.triptrack.designsystem.R.string.promo_already_redeemed)
    @Test fun inactive() = shot("inactive", error = com.onezee.triptrack.designsystem.R.string.promo_inactive)
    @Test fun expired() = shot("expired", error = com.onezee.triptrack.designsystem.R.string.promo_expired)
    @Test fun usedUp() = shot("used_up", error = com.onezee.triptrack.designsystem.R.string.promo_used_up)
    @Test fun tooManyRequests() = shot("too_many", error = com.onezee.triptrack.designsystem.R.string.promo_too_many)
    @Test fun validation() = shot("validation", error = com.onezee.triptrack.designsystem.R.string.promo_validation)
    @Test fun offline() = shot("offline", error = com.onezee.triptrack.designsystem.R.string.promo_offline)
    @Test fun signedOut() = shot("signed_out", signedIn = false)

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
