package com.onezee.triptrack.location.auto

import android.content.Context
import android.content.SharedPreferences
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * iOS: SettingsManager's auto-record half (`autoRecordMode`, `autoStopTimeout`,
 * `savedBluetoothDevices`) under the same keys. Local to the phone, never synced, as on iOS.
 *
 * A saved stereo's `uuid` is its Bluetooth address on Android: the name is for people, and two
 * stereos of one car brand are often called the same.
 */
interface AutoRecordSettings {
    var mode: AutoRecordMode
    /** Minutes between the stop question and the automatic stop. */
    var autoStopTimeout: Int
    var savedDevices: List<SavedBluetoothDevice>

    fun savedDevice(address: String?, name: String?): SavedBluetoothDevice? =
        savedDevices.firstOrNull { address != null && it.uuid.equals(address, ignoreCase = true) }
            ?: savedDevices.firstOrNull { name != null && it.name == name }

    fun addDevice(device: SavedBluetoothDevice) {
        if (savedDevices.none { it.uuid.equals(device.uuid, ignoreCase = true) }) savedDevices = savedDevices + device
    }

    fun removeDevice(uuid: String) {
        savedDevices = savedDevices.filterNot { it.uuid.equals(uuid, ignoreCase = true) }
    }

    fun removeDevices(forVehicle: UUID) {
        savedDevices = savedDevices.filterNot { it.vehicleId == forVehicle }
    }

    class InMemory(
        override var mode: AutoRecordMode = AutoRecordMode.off,
        override var autoStopTimeout: Int = 3,
        override var savedDevices: List<SavedBluetoothDevice> = emptyList(),
    ) : AutoRecordSettings
}

class PreferencesAutoRecordSettings(private val preferences: SharedPreferences) : AutoRecordSettings {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    private val devices = ListSerializer(SavedBluetoothDevice.serializer())

    override var mode: AutoRecordMode
        get() = preferences.getString(MODE_KEY, null)?.let(AutoRecordMode::fromRawValue) ?: AutoRecordMode.off
        set(value) {
            preferences.edit().putString(MODE_KEY, value.rawValue).apply()
        }

    override var autoStopTimeout: Int
        get() = preferences.getInt(TIMEOUT_KEY, 0).takeIf { it > 0 } ?: 3
        set(value) {
            preferences.edit().putInt(TIMEOUT_KEY, value).apply()
        }

    override var savedDevices: List<SavedBluetoothDevice>
        get() = preferences.getString(DEVICES_KEY, null)
            ?.let { runCatching { json.decodeFromString(devices, it) }.getOrNull() }.orEmpty()
        set(value) {
            preferences.edit().putString(DEVICES_KEY, json.encodeToString(devices, value)).apply()
        }

    companion object {
        const val FILE_NAME = "triptrack.autorecord"
        const val MODE_KEY = "autoRecordMode"
        const val TIMEOUT_KEY = "autoStopTimeout"
        const val DEVICES_KEY = "savedBluetoothDevices"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/**
 * The auto-trip service's memory between events. iOS keeps it in the long-lived service; Android
 * may start a fresh process for every activity transition or Bluetooth broadcast, and without it
 * every event would look like the first one of a session.
 */
interface AutoTripMemory {
    var lastTripTriggerTime: Instant?
    var hasRemindedForCurrentTrip: Boolean
    var lastAutomotiveEndTime: Instant?
    /** The saved stereo connected right now (its address), if any. */
    var connectedStereo: String?

    class InMemory : AutoTripMemory {
        override var lastTripTriggerTime: Instant? = null
        override var hasRemindedForCurrentTrip: Boolean = false
        override var lastAutomotiveEndTime: Instant? = null
        override var connectedStereo: String? = null
    }
}

class PreferencesAutoTripMemory(private val preferences: SharedPreferences) : AutoTripMemory {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override var lastTripTriggerTime: Instant?
        get() = instant(LAST_TRIGGER)
        set(value) = put(LAST_TRIGGER, value)
    override var hasRemindedForCurrentTrip: Boolean
        get() = preferences.getBoolean(REMINDED, false)
        set(value) {
            preferences.edit().putBoolean(REMINDED, value).apply()
        }
    override var lastAutomotiveEndTime: Instant?
        get() = instant(LAST_END)
        set(value) = put(LAST_END, value)
    override var connectedStereo: String?
        get() = preferences.getString(STEREO, null)
        set(value) {
            preferences.edit().putString(STEREO, value).apply()
        }

    private fun instant(key: String): Instant? = preferences.getLong(key, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(Instant::ofEpochMilli)

    private fun put(key: String, value: Instant?) {
        preferences.edit().apply { if (value == null) remove(key) else putLong(key, value.toEpochMilli()) }.apply()
    }

    companion object {
        const val FILE_NAME = "triptrack.autotrip"
        private const val LAST_TRIGGER = "lastTripTriggerTime"
        private const val REMINDED = "hasRemindedForCurrentTrip"
        private const val LAST_END = "lastAutomotiveEndTime"
        private const val STEREO = "connectedStereo"
    }
}
