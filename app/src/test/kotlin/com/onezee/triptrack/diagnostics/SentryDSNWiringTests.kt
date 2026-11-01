package com.onezee.triptrack.diagnostics

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.onezee.triptrack.BuildConfig
import com.onezee.triptrack.MainActivity
import com.onezee.triptrack.TestTripTrackApplication
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import io.sentry.Sentry

// iOS: SentryDSNWiringTests.swift, Info.plist UISupportedInterfaceOrientations.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestTripTrackApplication::class)
class SentryDSNWiringTests {
    @Test fun emptyDsnLeavesSdkOffAndWritesOneLogLine() {
        if (BuildConfig.SENTRY_DSN.isNotEmpty()) return
        val context = ApplicationProvider.getApplicationContext<TestTripTrackApplication>()
        ShadowLog.clear()
        SentryService.start(context)
        assertFalse(Sentry.isEnabled())
        assertEquals(1, ShadowLog.getLogsForTag("Sentry").count {
            it.msg == "Sentry disabled: TRIPTRACK_SENTRY_DSN is empty"
        })
    }

    @Test fun compiledBuildConfigCarriesResolvedKeyFromGradle() {
        // Reflect the compiled field: a source-text check missed the iOS wiring bug for ten releases.
        val field = BuildConfig::class.java.getDeclaredField("SENTRY_DSN")
        val value = assertIs<String>(field.get(null))
        val expected = assertNotNull(System.getProperty("triptrack.expectedSentryDsn"))
        assertTrue(value == expected, "Compiled Sentry DSN must equal the Gradle input (values hidden)")
        assertFalse(value.contains("$(") || value.contains("\u0024{"))
        if (BuildConfig.DEBUG) assertTrue(value.isEmpty())
    }

    @Test fun mergedManifestDisablesSentryAutoInitAndKeepsPhonesPortrait() {
        val context = ApplicationProvider.getApplicationContext<TestTripTrackApplication>()
        @Suppress("DEPRECATION")
        val application = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertTrue(application.metaData.containsKey("io.sentry.auto-init"))
        assertFalse(application.metaData.getBoolean("io.sentry.auto-init", true))
        @Suppress("DEPRECATION")
        val activity = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, activity.screenOrientation)
    }
}
