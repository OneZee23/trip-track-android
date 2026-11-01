// iOS: AutoRecordTriggerShotTests.swift, BluetoothScanSheet.swift.
package com.onezee.triptrack.feature.settings

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.location.auto.AutoTripController
import com.onezee.triptrack.location.auto.PreferencesAutoRecordSettings
import com.onezee.triptrack.model.*
import kotlin.test.assertEquals
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
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class AutoRecordTriggerShotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:org.junit.Rule val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val preferences get() = context.getSharedPreferences("trigger-shot", Context.MODE_PRIVATE)
    private val owner get() = PreferencesAutoRecordSettings(preferences)
    private val strings get() = AppStrings(context, AppLanguage.ru)
    private var screen by mutableStateOf(SettingsState())

    private fun snapshot() = SettingsState(loaded = true, autoRecordMode = owner.mode,
        autoRecordTrigger = owner.trigger, autoStopTimeout = owner.autoStopTimeout, stereos = owner.savedDevices)

    private fun open(stereos: List<String>, trigger: AutoRecordTrigger) {
        owner.mode = AutoRecordMode.remind
        owner.trigger = trigger
        owner.savedDevices = stereos.map { SavedBluetoothDevice("audio-$it", it) }
        screen = snapshot()
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                AutoRecordSettingsScreen(screen, {},
                    onTrigger = { owner.trigger = it; screen = snapshot() },
                    onMode = { owner.mode = it; screen = snapshot() },
                    onTimeout = { owner.autoStopTimeout = it; screen = snapshot() },
                    onUnlink = { owner.removeDevice(it); screen = snapshot() }, onStereo = {})
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/autorecord_${name}_${if (dark) "dark" else "light"}.png")
    }

    @Test fun testStereoOnlyNeedsALinkedStereo() {
        open(emptyList(), AutoRecordTrigger.motionOrStereo)
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsNotEnabled()
        compose.onNodeWithTag("auto_record_trigger_motion").assertIsSelected()
        capture("01-no-stereo")
        compose.runOnIdle { owner.trigger = AutoRecordTrigger.stereoOnly; screen = snapshot() }
        compose.onNodeWithText(strings.autoRecordTriggerStereoMissing()).assertExists()
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("auto_record_trigger_motion").assertIsEnabled()
        capture("02-stereo-missing")
    }

    @Test fun testStereoOnlyIsChosenAndRemembered() {
        open(listOf("Polo Radio"), AutoRecordTrigger.motionOrStereo)
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsEnabled().performClick().assertIsSelected()
        compose.onNodeWithText(strings.autoRecordStereoOnlyDescription()).assertExists()
        compose.onNodeWithText(strings.autoRecordTriggerStereoCaption()).assertExists()
        capture("03-stereo-only")
        compose.runOnIdle {
            // A new preferences owner reloads the saved choice, with no trigger launch override.
            assertEquals(AutoRecordTrigger.stereoOnly, owner.trigger)
            screen = snapshot()
        }
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsSelected()
        compose.onNodeWithTag("auto_record_trigger_motion").performClick().assertIsSelected()
    }

    @Test fun unlinkingTheLastStereoKeepsTheChoiceAndOffersMotion() {
        open(listOf("Polo Radio"), AutoRecordTrigger.stereoOnly)
        compose.onNodeWithTag("auto_record_unlink_audio-Polo Radio").performClick()
        compose.onNodeWithText(strings.autoRecordTriggerStereoMissing()).assertExists()
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsSelected().assertIsEnabled()
        compose.onNodeWithTag("auto_record_trigger_motion").performClick().assertIsSelected()
        compose.onNodeWithTag("auto_record_trigger_stereo").assertIsNotEnabled()
        compose.onNodeWithText(strings.autoRecordTriggerNeedsStereo()).assertExists()
    }

    @Test fun bluetoothAndAndroidAutoHaveTheirOwnLabels() {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
                    StereoOutputRow(SavedBluetoothDevice("audio-Polo Radio", "Polo Radio"), false, true, {})
                    StereoOutputRow(SavedBluetoothDevice(AutoTripController.ANDROID_AUTO, strings.androidAuto()), false, true, {})
                }
            }
        }
        compose.onNodeWithText("Bluetooth-аудио").assertExists()
        compose.onNodeWithTag("stereo_Android Auto").assertIsEnabled().performClick()
        capture("04-output-labels")
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
