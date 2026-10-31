// iOS: SettingsManager.swift (loadAutoRecordSettings), VehicleStateTests.swift,
// VehiclePhotoRecoveryTests.swift. Migration covers the two historical Android stores.
package com.onezee.triptrack.location

import android.content.Context
import com.onezee.triptrack.location.auto.PreferencesAutoRecordSettings
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.util.UUID
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoRecordSettingsTests {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val current get() = context.getSharedPreferences("canonical", Context.MODE_PRIVATE)
    private val legacy get() = context.getSharedPreferences("legacy", Context.MODE_PRIVATE)

    @Test fun recordingOnlyChoicesMigrateAndNeverComeBackAfterRemoval() {
        val old = PreferencesAutoRecordSettings(legacy)
        old.mode = AutoRecordMode.remind
        old.autoStopTimeout = 5
        old.addDevice(SavedBluetoothDevice("AA", "Stereo"))
        val settings = PreferencesAutoRecordSettings(current, legacy)
        assertEquals(AutoRecordMode.remind, settings.mode)
        assertEquals(5, settings.autoStopTimeout)
        assertEquals(listOf(SavedBluetoothDevice("AA", "Stereo")), settings.savedDevices)
        assertTrue(legacy.all.isEmpty())
        settings.removeDevice("AA")
        settings.mode = AutoRecordMode.off
        val restarted = PreferencesAutoRecordSettings(current, legacy)
        assertTrue(restarted.savedDevices.isEmpty())
        assertEquals(AutoRecordMode.off, restarted.mode)
    }

    @Test fun explicitSettingsChoicesWinEvenWhenTheyAreOffOrEmpty() {
        current.edit().putString("autoRecordMode", "off").putString("savedBluetoothDevices", "[]")
            .putString("autoStopTimeout", "7").commit()
        val old = PreferencesAutoRecordSettings(legacy)
        old.mode = AutoRecordMode.auto
        old.autoStopTimeout = 9
        old.addDevice(SavedBluetoothDevice("AA", "Old"))
        val settings = PreferencesAutoRecordSettings(current, legacy)
        assertEquals(AutoRecordMode.off, settings.mode)
        assertEquals(7, settings.autoStopTimeout)
        assertTrue(settings.savedDevices.isEmpty())
        assertTrue(legacy.all.isEmpty())
    }

    @Test fun migrationCopiesEachMissingKeyAndKeepsTheVehicleBinding() {
        current.edit().putString("autoRecordMode", "remind").commit()
        val vehicle = UUID.randomUUID()
        val old = PreferencesAutoRecordSettings(legacy)
        old.mode = AutoRecordMode.auto
        old.addDevice(SavedBluetoothDevice("AA", "Ford Stereo", vehicle))
        val settings = PreferencesAutoRecordSettings(current, legacy)
        assertEquals(AutoRecordMode.remind, settings.mode)
        assertEquals(vehicle, settings.savedDevice("aa", null)?.vehicleId)
        // iOS: VehicleStateTests.testDeletingAVehicleTakesItsStereoBindingWithIt.
        settings.removeDevices(vehicle)
        assertNull(PreferencesAutoRecordSettings(current).savedDevice("AA", "Ford Stereo"))
    }

    @Test fun freshReadersSeeEveryWriteAndMalformedValuesUseIosDefaults() {
        val screen = PreferencesAutoRecordSettings(current)
        val recorder = PreferencesAutoRecordSettings(current)
        screen.mode = AutoRecordMode.auto
        screen.addDevice(SavedBluetoothDevice("AA", "Car"))
        recorder.addDevice(SavedBluetoothDevice("aa", "Same car"))
        assertEquals(AutoRecordMode.auto, recorder.mode)
        assertEquals(1, recorder.savedDevices.size)
        recorder.removeDevice("aa")
        assertTrue(screen.savedDevices.isEmpty())
        current.edit().putString("savedBluetoothDevices", "broken").putString("autoRecordMode", "unknown")
            .putString("autoStopTimeout", "-1").commit()
        assertTrue(recorder.savedDevices.isEmpty())
        assertEquals(AutoRecordMode.off, recorder.mode)
        assertEquals(3, recorder.autoStopTimeout)
    }
}
