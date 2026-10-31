// iOS: NotificationPreferencesView.swift
package com.onezee.triptrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.buildJsonObject

// iOS: NotificationPreferencesView.swift.
data class NotificationPreferencesState(
    val reactions: Boolean = true,
    val follows: Boolean = true,
    val comments: Boolean = true,
    val weeklyRecap: Boolean = true,
    val companions: Boolean = true,
    val isAdmin: Boolean = false,
    val newAccounts: Boolean = true,
    val loaded: Boolean = false,
    val failed: Boolean = false,
) {
    val master get() = reactions || follows || comments || weeklyRecap || companions
}
// iOS: NotificationPreferencesView.swift (load/save).
interface NotificationPreferencesService {
    suspend fun load(): NotificationPrefsResponse
    suspend fun save(request: NotificationPrefsUpdateRequest): NotificationPrefsResponse
}
// iOS: APIClient.post. Use the already ported envelope and DTOs.
class ApiNotificationPreferencesService(private val apiClient: ApiClient) : NotificationPreferencesService {
    override suspend fun load(): NotificationPrefsResponse = apiClient.post(ApiEndpoint.notificationPrefsGet, buildJsonObject {})
    override suspend fun save(request: NotificationPrefsUpdateRequest): NotificationPrefsResponse =
        apiClient.post<NotificationPrefsUpdateRequest, NotificationPrefsResponse>(ApiEndpoint.notificationPrefsUpdate, request)
}
// iOS: NotificationPreferencesView.swift.
class NotificationPreferencesViewModel(
    private val service: NotificationPreferencesService,
    private val preferences: PresentationPreferences,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(NotificationPreferencesState())
    val state = mutableState.asStateFlow()
    private var saveTask: Job? = null
    init { load() }
    fun load() { viewModelScope.launch {
        if (state.value.loaded) return@launch
        try {
            val response = service.load()
            apply(response)
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.update { it.copy(loaded = true, failed = true) } }
    } }
    // iOS: NotificationSwitches.swift. Muting preserves the person's category selection.
    fun setMaster(on: Boolean) {
        if (!state.value.loaded || on == state.value.master) return
        if (!on) {
            val value = state.value
            preferences.set("com.triptrack.settings.notificationsPreMute", listOf(value.reactions, value.follows, value.comments, value.weeklyRecap, value.companions).joinToString(","))
            setPreferences(false, false, false, false, false)
        } else {
            val saved = preferences.string("com.triptrack.settings.notificationsPreMute")?.split(",")?.mapNotNull { it.toBooleanStrictOrNull() }
                ?.takeIf { it.size == 5 && it.any { value -> value } } ?: List(5) { true }
            setPreferences(saved[0], saved[1], saved[2], saved[3], saved[4])
        }
    }
    private fun apply(response: NotificationPrefsResponse) {
        mutableState.value = NotificationPreferencesState(response.notifyReactions, response.notifyFollows,
            response.notifyComments ?: true, response.notifyWeeklyRecap, response.notifyCompanions ?: true,
            response.isAdmin ?: false, response.notifyNewAccounts ?: true, loaded = true)
    }
    fun setPreferences(reactions: Boolean = state.value.reactions, follows: Boolean = state.value.follows,
                       comments: Boolean = state.value.comments, weeklyRecap: Boolean = state.value.weeklyRecap,
                       companions: Boolean = state.value.companions, newAccounts: Boolean = state.value.newAccounts) {
        if (!state.value.loaded) return
        mutableState.update { it.copy(reactions = reactions, follows = follows, comments = comments, weeklyRecap = weeklyRecap, companions = companions, newAccounts = newAccounts) }
        saveTask?.cancel()
        saveTask = viewModelScope.launch {
            delay(400)
            val snapshot = state.value
            try {
                val response = service.save(NotificationPrefsUpdateRequest(snapshot.reactions, snapshot.follows, snapshot.comments, snapshot.weeklyRecap, snapshot.companions, snapshot.newAccounts))
                ensureActive()
                apply(response)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
        }
    }
}
