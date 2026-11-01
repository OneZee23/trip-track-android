package com.onezee.triptrack.location

import android.media.AudioDeviceInfo
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.onezee.triptrack.location.auto.CarConnectionWatcher
import com.onezee.triptrack.location.auto.CarStereoConnections
import com.onezee.triptrack.location.auto.AutoRecordSettings
import com.onezee.triptrack.location.auto.AutoTripController
import com.onezee.triptrack.model.SavedBluetoothDevice
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.annotation.Config

// iOS: AudioRouteDetectorTests.swift. CarPlay maps to the two CarConnection states.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioRouteDetectorTests {
    @Test fun androidAutoIsLinkableWithoutBluetoothAndItsRouteStaysLive() {
        var type = CarConnectionWatcher.CONNECTION_TYPE_NOT_CONNECTED
        ShadowContentResolver.registerProviderInternal("androidx.car.app.connection", object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
                MatrixCursor(arrayOf(CarConnectionWatcher.CAR_CONNECTION_STATE)).apply { addRow(arrayOf(type)) }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        })
        val context: Context = RuntimeEnvironment.getApplication()
        val routes = CarStereoConnections(context) { "Android Auto" }
        val settings = AutoRecordSettings.InMemory()
        val events = mutableListOf<Boolean>()
        val watcher = CarConnectionWatcher(context) { events += it }
        watcher.start()
        try {
            assertTrue(routes.outputs().isEmpty())
            type = CarConnectionWatcher.CONNECTION_TYPE_PROJECTION
            context.sendBroadcast(Intent(CarConnectionWatcher.ACTION_CAR_CONNECTION_UPDATED))
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val output = SavedBluetoothDevice(AutoTripController.ANDROID_AUTO, "Android Auto")
            assertEquals(listOf(output), routes.outputs())
            assertNull(routes.linkedStereo(settings))
            settings.addDevice(output)
            assertEquals(output, routes.linkedStereo(settings))
            type = CarConnectionWatcher.CONNECTION_TYPE_NOT_CONNECTED
            context.sendBroadcast(Intent(CarConnectionWatcher.ACTION_CAR_CONNECTION_UPDATED))
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNull(routes.linkedStereo(settings))
            assertEquals(listOf(true, false), events)
        } finally { watcher.stop() }
    }

    @Test fun testCarStereoPorts() {
        for (port in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)) {
            assertTrue(CarStereoConnections.isCarStereoPort(port), port.toString())
        }
        assertTrue(CarConnectionWatcher.isConnected(CarConnectionWatcher.CONNECTION_TYPE_NATIVE))
        assertTrue(CarConnectionWatcher.isConnected(CarConnectionWatcher.CONNECTION_TYPE_PROJECTION))
    }

    @Test fun testOtherAudioPortsAreNotCarStereos() {
        for (port in listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) {
            assertFalse(CarStereoConnections.isCarStereoPort(port), port.toString())
        }
        assertFalse(CarConnectionWatcher.isConnected(CarConnectionWatcher.CONNECTION_TYPE_NOT_CONNECTED))
        assertFalse(CarConnectionWatcher.isConnected(-1))
    }
}
