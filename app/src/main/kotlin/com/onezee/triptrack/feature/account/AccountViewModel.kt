// iOS: CloudSyncView.swift, SyncStatusSheetView.swift (state and actions)
package com.onezee.triptrack.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.feature.settings.PresentationPreferences
import com.onezee.triptrack.feature.settings.SettingsManager
import com.onezee.triptrack.sync.SyncQueueState
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one dialog the account screen can show; each is a house dialog. */
sealed interface AccountDialog {
    /** GDPR just-in-time consent, on the very first enable only. */
    data object EnableCloudSync : AccountDialog
    /** ONE dialog whatever the question: with public trips it offers to hide them first. */
    data class SignOut(val publishedTrips: Int) : AccountDialog
    data object DeleteAccount : AccountDialog
    data object WipeServer : AccountDialog
}

/** iOS: the @State and @ObservedObject values CloudSyncView and SyncStatusSheetView read. */
data class AccountScreenState(
    val auth: AuthState = AuthState(),
    val avatarEmoji: String = "🏎️",
    val cloudSyncEnabled: Boolean = false,
    val settingsLoaded: Boolean = false,
    val queue: SyncQueueState = SyncQueueState(),
    val lastSyncedAt: Instant? = null,
    val counts: SyncCounts = SyncCounts(),
    /** From `/social/blocked`; null when not fetched or failed, and the row stays navigable. */
    val blockedCount: Int? = null,
    /** Optimistic public-profile value while its write is in flight; null mirrors the server. */
    val publicProfileDraft: Boolean? = null,
    val dialog: AccountDialog? = null,
    val showsStatus: Boolean = false,
    val isRetrying: Boolean = false,
    val isWipingServer: Boolean = false,
    /** How many objects the last «clear server» could not delete; null: clean or never run. */
    val wipeServerFailures: Int? = null,
    val isDeletingAccount: Boolean = false,
    val deleteAccountFailed: Boolean = false,
    /** The screen closes itself after a sign-out or a deletion: nothing is left on it. */
    val closed: Boolean = false,
) {
    val syncState: SyncState
        get() = SyncState.current(cloudSyncEnabled, queue.isSyncing, queue.pendingCount, queue.batchProcessed, queue.batchTotal)

    val publicProfile: Boolean get() = publicProfileDraft ?: auth.isPublicProfile ?: true
}

/** Where the screen reads what it does not own. */
interface AccountScreenSources {
    suspend fun avatarEmoji(): String?
    suspend fun counts(): SyncCounts
    suspend fun lastSyncedAt(): Instant?
    /** Null when the request failed. */
    suspend fun blockedCount(): Int?
}

class AccountViewModel(
    private val auth: AuthService,
    private val settingsManager: SettingsManager,
    private val syncHost: SyncHost,
    private val preferences: PresentationPreferences,
    private val sources: AccountScreenSources,
    dispatcher: CoroutineDispatcher,
) : ViewModel(CoroutineScope(SupervisorJob() + dispatcher)) {
    private val mutableState = MutableStateFlow(AccountScreenState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch { auth.state.collect { session -> mutableState.update { it.copy(auth = session) } } }
        viewModelScope.launch {
            settingsManager.state.collect { settings ->
                mutableState.update { it.copy(cloudSyncEnabled = settings.cloudSyncEnabled, settingsLoaded = settings.loaded) }
            }
        }
        viewModelScope.launch {
            syncHost.queueState.collect { queue ->
                val finished = state.value.queue.isSyncing && !queue.isSyncing
                mutableState.update { it.copy(queue = queue) }
                // A pass just ended: the counts and the «updated» line may have moved.
                if (finished) reloadSnapshot()
            }
        }
        viewModelScope.launch { pullsCompleted().collect { reloadSnapshot() } }
        refresh()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pullsCompleted() = syncHost.engine.flatMapLatest { it?.coordinator?.completed ?: emptyFlow() }

    /** The screen closed: its collectors stop with it. */
    fun dispose() = viewModelScope.cancel()

    /** iOS: CloudSyncView .task and .onAppear. */
    fun refresh() {
        reloadSnapshot()
        viewModelScope.launch { sources.avatarEmoji()?.let { emoji -> mutableState.update { it.copy(avatarEmoji = emoji) } } }
        viewModelScope.launch { auth.refreshMe() }
        viewModelScope.launch {
            if (!auth.state.value.isSignedIn) return@launch
            val count = sources.blockedCount() ?: return@launch
            mutableState.update { it.copy(blockedCount = count) }
        }
    }

    private fun reloadSnapshot() {
        viewModelScope.launch {
            val counts = sources.counts()
            val last = sources.lastSyncedAt()
            mutableState.update { it.copy(counts = counts, lastSyncedAt = last) }
        }
    }

    /**
     * iOS: cloudSyncBinding. ON goes through the first-enable consent dialog once; OFF clears the
     * queue, which the settings effect does with the switch.
     */
    fun setCloudSync(enabled: Boolean) {
        val current = state.value
        if (!current.settingsLoaded || enabled == current.cloudSyncEnabled) return
        if (!enabled) settingsManager.setCloudSync(false)
        else if (preferences.string(AccountSettings.FIRST_TOGGLE_KEY) != "true") mutableState.update { it.copy(dialog = AccountDialog.EnableCloudSync) }
        else settingsManager.setCloudSync(true)
    }

    /** The consent's confirm: the full mirror is the settings effect's (SyncSettingsEffects). */
    fun confirmEnableCloudSync() {
        preferences.set(AccountSettings.FIRST_TOGGLE_KEY, "true")
        settingsManager.setCloudSync(true)
    }

    fun dismissDialog() { mutableState.update { it.copy(dialog = null) } }

    fun showStatus(show: Boolean) {
        mutableState.update { it.copy(showsStatus = show) }
        if (show) reloadSnapshot()
    }

    /** iOS: SyncStatusSheetView.retryButton. «Retry now» also reopens the automatic retry budget. */
    fun retryFailedNow() {
        val queue = syncHost.engine.value?.queue ?: return
        mutableState.update { it.copy(isRetrying = true) }
        viewModelScope.launch {
            try { queue.retryFailedNow() } finally { mutableState.update { it.copy(isRetrying = false) } }
        }
    }

    /** Optimistic with revert: the draft drops whether the write held (the mirror moved) or not. */
    fun setPublicProfile(isPublic: Boolean, onFailure: () -> Unit = {}) {
        mutableState.update { it.copy(publicProfileDraft = isPublic) }
        viewModelScope.launch {
            val ok = auth.setPublicProfile(isPublic)
            if (!ok) onFailure()
            mutableState.update { it.copy(publicProfileDraft = null) }
        }
    }

    /** The count is read when the row is TAPPED: it decides which question the one dialog asks. */
    fun requestSignOut() {
        viewModelScope.launch {
            val published = auth.publishedTripCount()
            mutableState.update { it.copy(dialog = AccountDialog.SignOut(published)) }
        }
    }

    fun signOut(hidePublic: Boolean) {
        viewModelScope.launch {
            try {
                auth.signOut(hidePublic)
                mutableState.update { it.copy(closed = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The secure store refused to clear (locked): the session stays, the screen too.
            }
        }
    }

    fun requestDeleteAccount() { mutableState.update { it.copy(dialog = AccountDialog.DeleteAccount, deleteAccountFailed = false) } }

    /** iOS: performDeleteAccount. A refusal keeps the account and says so under the card. */
    fun deleteAccount(onFailure: () -> Unit = {}) {
        mutableState.update { it.copy(isDeletingAccount = true, deleteAccountFailed = false) }
        viewModelScope.launch {
            try {
                auth.deleteAccount()
                mutableState.update { it.copy(isDeletingAccount = false, closed = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(isDeletingAccount = false, deleteAccountFailed = true) }
                onFailure()
            }
        }
    }

    fun requestWipeServer() { mutableState.update { it.copy(dialog = AccountDialog.WipeServer) } }

    fun wipeServer() {
        mutableState.update { it.copy(isWipingServer = true) }
        viewModelScope.launch {
            val failed = try { auth.wipeServerData() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { 1 }
            mutableState.update { it.copy(isWipingServer = false, wipeServerFailures = failed.takeIf { count -> count > 0 }) }
        }
    }
}

/** The settings marker's state alone, for the row that opens the screen. */
@OptIn(ExperimentalCoroutinesApi::class)
fun syncStateFlow(settingsManager: SettingsManager, syncHost: SyncHost) =
    settingsManager.state.map { it.cloudSyncEnabled }.distinctUntilChanged().flatMapLatest { enabled ->
        syncHost.queueState.map { queue -> SyncState.current(enabled, queue.isSyncing, queue.pendingCount, queue.batchProcessed, queue.batchTotal) }
    }
