package com.onezee.triptrack.location

import android.content.Context
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.location.auto.PreferencesAutoRecordSettings
import com.onezee.triptrack.location.auto.StopPromptReason
import com.onezee.triptrack.location.recording.RawFixLog
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.service.RecordingTexts
import com.onezee.triptrack.location.service.formattedDuration
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The words of the ongoing notification come from the generated iOS strings and `Measure`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecordingTextsTests {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val start = Instant.ofEpochSecond(1_780_000_000)
    private val driving = RecorderState(isRecording = true, startDate = start, distance = 8_400.0, speed = 15.0)

    private fun texts(language: AppLanguage, unit: DistanceUnit = DistanceUnit.km) = RecordingTexts(AppStrings(context, language), unit)

    @Test
    fun theOngoingNotificationReadsLikeTheLiveActivity() {
        val en = texts(AppLanguage.en)
        assertEquals("Recording", en.ongoingTitle(driving))
        assertEquals("8.4 km · 54 km/h", en.ongoingText(driving, start))
        assertEquals("Pause", en.pauseAction(driving))
        assertEquals("Finish", en.finishAction())
        val ru = texts(AppLanguage.ru)
        assertEquals("8.4 км · 54 км/ч", ru.ongoingText(driving, start))
        assertEquals("5.2 mi · 34 mph", texts(AppLanguage.en, DistanceUnit.miles).ongoingText(driving, start))
    }

    @Test
    fun aPauseShowsTheDrivingClockInsteadOfTheSpeed() {
        val paused = driving.copy(isPaused = true, pausedSeconds = 600.0, pauseStartedAt = start.plusSeconds(3_000))
        val en = texts(AppLanguage.en)
        assertEquals("Paused", en.ongoingTitle(paused))
        assertEquals("Resume", en.pauseAction(paused))
        assertEquals("8.4 km · 40:00", en.ongoingText(paused, start.plusSeconds(3_600)))
        assertEquals("1:02:05", formattedDuration(3_725.0))
    }

    @Test
    fun promptsUseTheIosCopy() {
        val en = texts(AppLanguage.en)
        assertTrue(en.stopPromptBody(3, StopPromptReason.bluetooth).startsWith("Bluetooth disconnected."))
        assertEquals("No location access — turn it on in Settings", en.autoStartFailedBody(StartRefusal.locationDenied))
        assertEquals("Open the app and start the trip manually", en.autoStartFailedBody(StartRefusal.unknown))
        assertEquals("12.4 km · 23:10", en.autoStoppedBody(12_400.0, 1_390.0))
        assertTrue(en.draftConfirmBody(7_400.0).startsWith("7.4 km"))
    }

    // The raw journal sits in noBackupFilesDir: no Auto Backup and no device transfer reads it.
    @Test
    fun theRawJournalLivesWhereNoBackupReaches() {
        val dir = File(context.noBackupFilesDir, RawFixLog.DIRECTORY)
        assertTrue(dir.absolutePath.startsWith(context.noBackupFilesDir.absolutePath))
    }

    @Test
    fun autoRecordSettingsKeepTheIosKeysAndDefaults() {
        val settings = PreferencesAutoRecordSettings(context)
        assertEquals(AutoRecordMode.off, settings.mode)
        assertEquals(3, settings.autoStopTimeout)
        settings.mode = AutoRecordMode.remind
        settings.addDevice(SavedBluetoothDevice("00:11:22:33:44:55", "Polo Audio"))
        settings.addDevice(SavedBluetoothDevice("00:11:22:33:44:55", "Polo Audio"))
        val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        assertEquals("remind", preferences.getString("autoRecordMode", null))
        assertEquals("[{\"uuid\":\"00:11:22:33:44:55\",\"name\":\"Polo Audio\"}]", preferences.getString("savedBluetoothDevices", null))
        assertEquals("Polo Audio", PreferencesAutoRecordSettings(context).savedDevice("00:11:22:33:44:55", null)?.name)
    }
}
