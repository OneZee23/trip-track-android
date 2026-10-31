// iOS: CloudSyncView.swift, SyncStatusSheetView.swift
package com.onezee.triptrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.sync.SyncQueue
import com.onezee.triptrack.sync.SyncQueueState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// iOS: CloudSyncView.swift.
data class CloudSyncState(
    val enabled: Boolean = false,
    val loaded: Boolean = false,
    val showsEnableConfirmation: Boolean = false,
    val showsStatus: Boolean = false,
    val queue: SyncQueueState = SyncQueueState(),
)

// iOS: CloudSyncView.cloudSyncBinding. The settings effect owns full re-mirroring on enable.
class CloudSyncViewModel(
    private val settingsManager: SettingsManager,
    private val syncQueue: SyncQueue,
    private val preferences: PresentationPreferences,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(CloudSyncState())
    val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch { settingsManager.state.collect { settings -> mutableState.update { it.copy(enabled = settings.cloudSyncEnabled, loaded = settings.loaded) } } }
        viewModelScope.launch { syncQueue.state.collect { queue -> mutableState.update { it.copy(queue = queue) } } }
    }
    fun setEnabled(enabled: Boolean) {
        if (!state.value.loaded || enabled == state.value.enabled) return
        if (enabled && preferences.string(FIRST_TOGGLE_KEY) != "true") {
            mutableState.update { it.copy(showsEnableConfirmation = true) }
        } else if (enabled) confirmEnable()
        else {
            settingsManager.setCloudSync(false)
            syncQueue.clearAll()
        }
    }
    fun confirmEnable() {
        preferences.set(FIRST_TOGGLE_KEY, "true")
        mutableState.update { it.copy(showsEnableConfirmation = false) }
        settingsManager.setCloudSync(true)
    }
    fun cancelEnable() { mutableState.update { it.copy(showsEnableConfirmation = false) } }
    fun showStatus(show: Boolean) { mutableState.update { it.copy(showsStatus = show) } }
    fun syncNow() { viewModelScope.launch { syncQueue.processQueue() } }
    fun retryFailed() { viewModelScope.launch { syncQueue.retryFailedNow() } }
    companion object { private const val FIRST_TOGGLE_KEY = "com.triptrack.sync.firstToggleShown" }
}
