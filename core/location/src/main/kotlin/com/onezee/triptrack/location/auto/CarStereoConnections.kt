package com.onezee.triptrack.location.auto

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.onezee.triptrack.model.SavedBluetoothDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

// iOS: AudioRouteDetector.swift. The same output snapshot feeds the link sheet and wake policy.
class CarStereoConnections(private val context: Context, private val androidAutoName: () -> String) {
    private val audio get() = context.getSystemService(AudioManager::class.java)
    private val car = CarConnectionWatcher(context) {}

    fun outputs(): List<SavedBluetoothDevice> = buildList {
        if (CarConnectionWatcher.isConnected(car.connectionType())) {
            add(SavedBluetoothDevice(AutoTripController.ANDROID_AUTO, androidAutoName()))
        }
        val devices = try { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty() }
        catch (_: SecurityException) { emptyArray() }
        devices.filter { isCarStereoPort(it.type) }.forEach { device ->
            val name = device.productName.toString()
            if (name.isNotBlank()) {
                val address = device.address.takeIf { it.isNotBlank() } ?: "audio-$name"
                add(SavedBluetoothDevice(address, name))
            }
        }
    }.distinctBy { it.uuid.uppercase(java.util.Locale.ROOT) }

    fun linkedStereo(settings: AutoRecordSettings): SavedBluetoothDevice? =
        outputs().firstNotNullOfOrNull { settings.savedDevice(it.uuid, it.name) }

    // The sheet stays live when a cable or Bluetooth output arrives while it is open.
    val changes = callbackFlow {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { trySend(Unit) }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { trySend(Unit) }
        }
        val watcher = CarConnectionWatcher(context) { trySend(Unit) }
        audio?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        watcher.start()
        trySend(Unit)
        awaitClose {
            audio?.unregisterAudioDeviceCallback(callback)
            watcher.stop()
        }
    }.conflate()

    companion object {
        @SuppressLint("InlinedApi")
        fun isCarStereoPort(type: Int): Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
}
