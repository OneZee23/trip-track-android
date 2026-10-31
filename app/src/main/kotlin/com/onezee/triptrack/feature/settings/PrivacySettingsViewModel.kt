// iOS: PrivacySettingsView.swift, AuthService.setVisibility/setPublicProfile
package com.onezee.triptrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// iOS: PrivacySettingsView.swift.
data class PrivacySettingsState(
    val isPublic: Boolean = true,
    val visibility: Map<ProfileVisibilityBlock, Boolean> = ProfileVisibilityBlock.entries.associateWith { true },
    val pending: Set<ProfileVisibilityBlock> = emptySet(),
    val publicPending: Boolean = false,
    val loaded: Boolean = false,
    val supportsBlocks: Boolean = false,
    val failed: Boolean = false,
)
// iOS: AuthService.swift. Only the edited field travels in a profile patch.
interface ProfilePrivacyService {
    suspend fun load(): MeResponse
    suspend fun save(patch: ProfileUpdateRequest)
}
// iOS: APIClient.post.
class ApiProfilePrivacyService(private val apiClient: ApiClient) : ProfilePrivacyService {
    override suspend fun load(): MeResponse = apiClient.post(ApiEndpoint.authMe, EmptyRequest())
    // The server answers `{"status":"ok"}` without a payload: only EmptyResponse decodes that.
    override suspend fun save(patch: ProfileUpdateRequest) { apiClient.post<ProfileUpdateRequest, EmptyResponse>(ApiEndpoint.profileUpdate, patch) }
}
// iOS: PrivacySettingsView.swift. Each block has its own serial writes and optimistic generation.
class PrivacySettingsViewModel(
    private val service: ProfilePrivacyService,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(PrivacySettingsState())
    val state = mutableState.asStateFlow()
    private val queues = ProfileVisibilityBlock.entries.associateWith { Mutex() }
    private val generations = mutableMapOf<ProfileVisibilityBlock, Int>()
    private val confirmed = ProfileVisibilityBlock.entries.associateWith { true }.toMutableMap()
    private val publicQueue = Mutex()
    private var publicGeneration = 0
    private var confirmedPublic = true
    init { load() }
    fun load() { viewModelScope.launch {
        try {
            val remote = service.load()
            confirmedPublic = remote.isPublic
            confirmed[ProfileVisibilityBlock.counters] = remote.countersPublic ?: true
            confirmed[ProfileVisibilityBlock.stats] = remote.statsPublic ?: true
            confirmed[ProfileVisibilityBlock.map] = remote.mapPublic ?: true
            confirmed[ProfileVisibilityBlock.achievements] = remote.achievementsPublic ?: true
            mutableState.value = PrivacySettingsState(remote.isPublic, confirmed.toMap(), loaded = true,
                supportsBlocks = listOf(remote.countersPublic, remote.statsPublic, remote.mapPublic, remote.achievementsPublic).any { it != null })
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.update { it.copy(failed = true) } }
    } }
    fun setVisibility(block: ProfileVisibilityBlock, enabled: Boolean) {
        if (!state.value.loaded || !state.value.supportsBlocks) return
        val generation = (generations[block] ?: 0) + 1
        generations[block] = generation
        mutableState.update { it.copy(visibility = it.visibility + (block to enabled), pending = it.pending + block) }
        viewModelScope.launch { queues.getValue(block).withLock {
            var failed = false
            try {
                val patch = ProfileUpdateRequest().apply { when (block) {
                    ProfileVisibilityBlock.counters -> countersPublic = enabled
                    ProfileVisibilityBlock.stats -> statsPublic = enabled
                    ProfileVisibilityBlock.map -> mapPublic = enabled
                    ProfileVisibilityBlock.achievements -> achievementsPublic = enabled
                } }
                service.save(patch)
                confirmed[block] = enabled
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            if (generations[block] == generation) mutableState.update {
                it.copy(visibility = it.visibility + (block to confirmed.getValue(block)), pending = it.pending - block, failed = failed)
            }
        } }
    }
    fun setPublicProfile(enabled: Boolean) {
        if (!state.value.loaded) return
        val generation = ++publicGeneration
        mutableState.update { it.copy(isPublic = enabled, publicPending = true) }
        viewModelScope.launch { publicQueue.withLock {
            var failed = false
            try { service.save(ProfileUpdateRequest(isPublic = enabled)); confirmedPublic = enabled }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { failed = true }
            if (generation == publicGeneration) mutableState.update { it.copy(isPublic = confirmedPublic, publicPending = false, failed = failed) }
        } }
    }
}
