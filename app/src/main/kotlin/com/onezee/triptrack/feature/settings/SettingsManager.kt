// iOS: SettingsManager.swift, HomeManager.swift, HomeSettings.swift
package com.onezee.triptrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.data.SettingsStore
import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.data.sync.*
import com.onezee.triptrack.logic.guessFromRegion
import com.onezee.triptrack.model.*
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

// iOS: HomeSettings.swift. The coordinate is local and is never included in a settings upload.
data class HomeState(
    val coordinate: GeoPoint? = null,
    val radius: HomeSettings.Radius = HomeSettings.Radius.block,
    val showsOnMap: Boolean = true,
    val trimsPublicTracks: Boolean = false,
) {
    val mapPin get() = coordinate?.takeIf { showsOnMap }
    val activeZone get() = coordinate?.takeIf { trimsPublicTracks }?.let { HomeZone(it, radius.rawValue.toDouble()) }
}

// iOS: HomeSettings.activeZone.
data class HomeZone(val centre: GeoPoint, val radius: Double)

// iOS: SettingsView.swift / SettingsOptionPicker.swift.
enum class SettingsSheet { settings, units, theme, language, autoRecord, stereo, cloudSync, privacy, home, notifications }

// iOS: SettingsManager.swift.
data class SettingsState(
    val loaded: Boolean = false,
    val localUserId: UUID? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.km,
    val volumeUnit: VolumeUnit = VolumeUnit.liters,
    val theme: String = "dark",
    val language: String = "ru",
    val autoRecordMode: AutoRecordMode = AutoRecordMode.off,
    val autoStopTimeout: Int = 3,
    val stereos: List<SavedBluetoothDevice> = emptyList(),
    val cloudSyncEnabled: Boolean = false,
    val showOnPublicMap: Boolean = false,
    val home: HomeState = HomeState(),
    val selectedVehicleId: UUID? = null,
    val sheets: List<SettingsSheet> = emptyList(),
    val failed: Boolean = false,
) {
    val sheet get() = sheets.lastOrNull()
}

// iOS: cloudSyncEnabled / homePrivacyZoneChanged. Sync and recording owners implement side effects.
interface SettingsEffects {
    suspend fun cloudSyncChanged(enabled: Boolean)
    suspend fun homeZoneChanged(before: HomeZone?, after: HomeZone?)
    suspend fun autoRecordChanged(mode: AutoRecordMode, timeout: Int, stereos: List<SavedBluetoothDevice>)
    suspend fun publicMapChanged(enabled: Boolean)
}

// iOS: SettingsManager.swift. Each setter serializes persistence before publishing a new snapshot.
class SettingsManager(
    private val settingsStore: SettingsStore,
    private val preferences: PresentationPreferences,
    private val syncRequests: SyncRequestSink,
    private val effects: SettingsEffects,
    dispatcher: CoroutineDispatcher,
    events: Flow<WorldChange> = emptyFlow(),
    private val deviceLocale: Locale = Locale.getDefault(),
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()
    private val edits = Mutex()
    private var syncTask: Job? = null
    init {
        reload(launch = true)
        viewModelScope.launch { events.filter { it == WorldChange.sync || it == WorldChange.settings }.collect { reload() } }
    }
    fun reload(launch: Boolean = false) = edit {
        var row = settingsStore.loadOrCreate()
        val hadChoice = preferences.string(DistanceUnit.storageKey) != null
        var distance = preferences.string(DistanceUnit.storageKey)?.let(DistanceUnit::fromRawValue)
        var volume = preferences.string("volumeUnit")?.let(VolumeUnit::fromRawValue)
        if (launch) {
            distance = distance ?: row.distanceUnit?.let(DistanceUnit::fromRawValue) ?: DistanceUnit.km
            volume = volume ?: row.volumeUnit?.let(VolumeUnit::fromRawValue) ?: VolumeUnit.liters
            if (!hadChoice && distance == DistanceUnit.km) distance = DistanceUnit.guessFromRegion(deviceLocale)
            row = settingsStore.seedUnits(distance.rawValue, volume.rawValue)
        } else {
            distance = row.distanceUnit?.let(DistanceUnit::fromRawValue) ?: distance ?: state.value.distanceUnit
            volume = row.volumeUnit?.let(VolumeUnit::fromRawValue) ?: volume ?: state.value.volumeUnit
        }
        preferences.set(DistanceUnit.storageKey, distance.rawValue)
        preferences.set("volumeUnit", volume.rawValue)
        val home = preferences.string("home.settings")?.let { raw -> runCatching { Json.decodeFromString<HomeSettings>(raw) }.getOrNull() }
        val stereos = preferences.string("savedBluetoothDevices")?.let { raw -> runCatching { Json.decodeFromString<List<SavedBluetoothDevice>>(raw) }.getOrNull() }.orEmpty()
        mutableState.update { it.copy(loaded = true, localUserId = row.id, distanceUnit = distance, volumeUnit = volume,
            theme = row.themeMode ?: "dark", language = row.language ?: "ru", selectedVehicleId = row.selectedVehicleId,
            autoRecordMode = preferences.string("autoRecordMode")?.let(AutoRecordMode::fromRawValue) ?: AutoRecordMode.off,
            autoStopTimeout = preferences.string("autoStopTimeout")?.toIntOrNull()?.takeIf { value -> value > 0 } ?: 3,
            stereos = stereos, cloudSyncEnabled = preferences.string(CLOUD_KEY)?.toBooleanStrictOrNull() ?: false,
            showOnPublicMap = preferences.string(PUBLIC_MAP_KEY)?.toBooleanStrictOrNull() ?: false,
            home = home?.let { value -> HomeState(if (value.latitude != null && value.longitude != null) GeoPoint(value.latitude!!, value.longitude!!) else null,
                value.radius, value.showsOnMap, value.trimsPublicTracks) } ?: HomeState(), failed = false) }
    }
    fun setDistanceUnit(unit: DistanceUnit) = saveRow({ it.copy(distanceUnit = unit.rawValue) }) {
        preferences.set(DistanceUnit.storageKey, unit.rawValue); it.copy(distanceUnit = unit)
    }
    fun setVolumeUnit(unit: VolumeUnit) = saveRow({ it.copy(volumeUnit = unit.rawValue) }) {
        preferences.set("volumeUnit", unit.rawValue); it.copy(volumeUnit = unit)
    }
    fun setTheme(theme: String) { require(theme in setOf("dark", "light", "system")); saveRow({ it.copy(themeMode = theme) }) { it.copy(theme = theme) } }
    fun setLanguage(language: String) { require(language in LANGUAGES); saveRow({ it.copy(language = language) }) { it.copy(language = language) } }
    fun selectVehicle(id: UUID?) = saveRow({ it.copy(selectedVehicleId = id) }) { it.copy(selectedVehicleId = id) }
    fun setCloudSync(enabled: Boolean) = edit {
        preferences.set(CLOUD_KEY, enabled.toString())
        mutableState.update { it.copy(cloudSyncEnabled = enabled) }
        effects.cloudSyncChanged(enabled)
    }
    fun setShowOnPublicMap(enabled: Boolean) = edit {
        preferences.set(PUBLIC_MAP_KEY, enabled.toString())
        mutableState.update { it.copy(showOnPublicMap = enabled) }
        effects.publicMapChanged(enabled)
    }
    fun setAutoRecord(mode: AutoRecordMode, timeout: Int = state.value.autoStopTimeout) = edit {
        preferences.set("autoRecordMode", mode.rawValue)
        preferences.set("autoStopTimeout", timeout.toString())
        mutableState.update { it.copy(autoRecordMode = mode, autoStopTimeout = timeout) }
        effects.autoRecordChanged(mode, timeout, state.value.stereos)
    }
    fun addStereo(device: SavedBluetoothDevice) = changeStereos { old -> if (old.any { it.uuid == device.uuid }) old else old + device }
    fun removeStereo(uuid: String) = changeStereos { it.filterNot { stereo -> stereo.uuid == uuid } }
    fun removeStereos(vehicleId: UUID) = changeStereos { it.filterNot { stereo -> stereo.vehicleId == vehicleId } }
    fun vehicleForStereo(name: String, vehicles: List<Vehicle>): UUID? = state.value.stereos.firstOrNull { it.name == name }?.vehicleId
        ?.takeIf { id -> vehicles.any { it.id == id && it.soldAt == null } }
    private fun changeStereos(transform: (List<SavedBluetoothDevice>) -> List<SavedBluetoothDevice>) = edit {
        val stereos = transform(state.value.stereos)
        preferences.set("savedBluetoothDevices", Json.encodeToString(stereos))
        mutableState.update { it.copy(stereos = stereos) }
        effects.autoRecordChanged(state.value.autoRecordMode, state.value.autoStopTimeout, stereos)
    }
    fun setHome(home: HomeState) = edit {
        val before = state.value.home.activeZone
        preferences.set("home.settings", Json.encodeToString(HomeSettings(home.coordinate?.latitude, home.coordinate?.longitude, home.radius, home.showsOnMap, home.trimsPublicTracks)))
        mutableState.update { it.copy(home = home) }
        if (before != home.activeZone) effects.homeZoneChanged(before, home.activeZone)
    }
    fun openSheet(sheet: SettingsSheet) { mutableState.update { if (it.sheet == sheet) it else it.copy(sheets = it.sheets + sheet) } }
    fun dismissSheet() { mutableState.update { it.copy(sheets = it.sheets.dropLast(1)) } }
    private fun saveRow(transform: (UserSettingsEntity) -> UserSettingsEntity, present: (SettingsState) -> SettingsState) = edit {
        val row = settingsStore.save { stored ->
            transform(stored.copy(
                distanceUnit = preferences.string(DistanceUnit.storageKey)?.let(DistanceUnit::fromRawValue)?.rawValue ?: state.value.distanceUnit.rawValue,
                volumeUnit = preferences.string("volumeUnit")?.let(VolumeUnit::fromRawValue)?.rawValue ?: state.value.volumeUnit.rawValue,
            ))
        } ?: return@edit
        mutableState.update { present(it).copy(failed = false,
            distanceUnit = row.distanceUnit?.let(DistanceUnit::fromRawValue) ?: it.distanceUnit,
            volumeUnit = row.volumeUnit?.let(VolumeUnit::fromRawValue) ?: it.volumeUnit) }
        syncTask?.cancel()
        syncTask = viewModelScope.launch { delay(5_000); syncRequests.enqueue(SyncRequest(SyncEntityType.settings, row.id, SyncAction.upload)) }
    }
    private fun edit(block: suspend () -> Unit) { viewModelScope.launch {
        edits.withLock {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    } }
    companion object {
        private const val CLOUD_KEY = "com.triptrack.settings.cloudSyncEnabled"
        private const val PUBLIC_MAP_KEY = "com.triptrack.settings.showOnPublicMap"
        private val LANGUAGES = setOf("ru", "en", "de", "fr", "es", "pt", "it", "tr", "pl", "uk", "id", "fil", "kk")
    }
}
