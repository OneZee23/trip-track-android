// iOS: AuthService.swift
package com.onezee.triptrack.feature.account

import android.content.Context
import android.util.Log
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.EmptyResponse
import com.onezee.triptrack.model.LoginResponse
import com.onezee.triptrack.model.MeResponse
import com.onezee.triptrack.model.ProfileUpdateRequest
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.GoogleLoginRequest
import com.onezee.triptrack.network.GoogleNonce
import com.onezee.triptrack.network.HardSignOutReason
import com.onezee.triptrack.network.SecureStorageException
import com.onezee.triptrack.network.TokenStore
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The Google side of sign-in, from BuildConfig and the device. */
data class GoogleSignInConfig(
    /** The Web client ID (Credential Manager's serverClientId); empty when not configured. */
    val webClientId: String,
    val appVersion: String,
    val deviceName: String?,
    /** Debug builds explain a missing client ID; release builds show the ordinary failure. */
    val isDebugBuild: Boolean,
) {
    val isConfigured: Boolean get() = webClientId.isNotBlank()
}

/** iOS: AuthService's @Published session state, plus the extras the account screens read. */
data class AuthState(
    val isSignedIn: Boolean = false,
    /** iOS: needsReauth. The server rejected the refresh token: everything local survives. */
    val needsReauth: Boolean = false,
    /** The secure store could not be read at launch (locked after a reboot): «unknown», not «no session». */
    val hydrationDeferred: Boolean = false,
    val accountId: UUID? = null,
    val userName: String? = null,
    val userEmail: String? = null,
    val isAuthenticating: Boolean = false,
    /** iOS: isPublicProfile. Null until a successful `/auth/me`; the privacy toggle hides until then. */
    val isPublicProfile: Boolean? = null,
) {
    /** The account a publication may go to: none while signed out or soft-expired. */
    val signedInAccountId: UUID? get() = accountId.takeIf { isSignedIn }
}

/** How a sign-in attempt ended, for the surface that started it. */
sealed interface SignInOutcome {
    data class SignedIn(val isNewAccount: Boolean) : SignInOutcome
    /** The Google sheet was closed: no message, as iOS ignores Apple's cancel. */
    data object Cancelled : SignInOutcome
    /** No Web client ID in this build (docs/sign-in.md). */
    data object NotConfigured : SignInOutcome
    /** Another attempt is already running. */
    data object Busy : SignInOutcome
    data class Failed(val reason: String) : SignInOutcome
}

/** The sync engine as the session sees it; implemented by [SyncHost]. */
interface AccountSyncControl {
    /** The engine follows the account; `null` closes it. */
    suspend fun switchAccount(accountId: UUID?)
    /** Drops the current account's queued operations, before a purge or at a hard sign-out. */
    suspend fun clearAccountWork()
    /** iOS: AuthService.performFirstSync. A full mirror only with Cloud Sync on, then one pass. */
    suspend fun firstSync()
    fun requestSync()
}

/**
 * The ApiClient's session callbacks, wired before the service exists: the client is built first
 * because everything else talks through it.
 */
class SessionEvents {
    @Volatile var listener: Listener? = null

    interface Listener {
        fun sessionExpired()
        fun sessionRecovered()
        fun hardSignOut(reason: HardSignOutReason)
        fun identityChanged(previous: UUID?, current: UUID)
    }

    fun sessionExpired() { listener?.sessionExpired() }
    fun sessionRecovered() { listener?.sessionRecovered() }
    fun hardSignOut(reason: HardSignOutReason) { listener?.hardSignOut(reason) }
    fun identityChanged(previous: UUID?, current: UUID) { listener?.identityChanged(previous, current) }
}

/**
 * iOS: AuthService. The session as the app sees it, and the one rule of 0.7.0 that is stronger than
 * any convenience: only the person ends their session. Hard sign-outs are exactly three, and all
 * three are a decision of the person or the server's ban: «Sign out», «Delete account» and
 * `USER_BANNED`. Everything else is a SOFT expiry: tokens die, the «sign in again» card shows, and
 * the library, the sync queue, Cloud Sync consent and the name stay. The refresh token stays too:
 * the server keeps an orphaned token replayable for 30 days, so the next successful refresh brings
 * the session back without a tap.
 *
 * Android signs in with Google only (docs/divergences.md): Credential Manager gives an ID token
 * bound to a one-shot nonce, the server verifies it at `/auth/google/login`, and from there the
 * session is the iPhone's: tokens, refresh, logout and delete.
 */
class AuthService(
    private val client: ApiClient,
    private val tokenStore: TokenStore,
    private val credentials: GoogleCredentialSource,
    private val config: GoogleSignInConfig,
    /** The settings row's id: login binds this install, never the account id. */
    private val localUserId: suspend () -> UUID,
    private val language: () -> AppLanguage,
    private val accountState: AccountScopedState,
    private val accountSettings: AccountSettings,
    private val latch: ProfileSyncLatch,
    private val sync: AccountSyncControl,
    private val serverActions: AccountServerActions,
    /** iOS: LocalDataWipe.run, the second half of «Delete account — irreversibly, everywhere». */
    private val wipeLocalData: suspend () -> Unit,
    /** Where the Google credential state is cleared after a hard sign-out. */
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionEvents.Listener {
    private val extras = MutableStateFlow(Extras())

    /** Work that must outlive the screen that started it: a login already on its way. */
    val serviceScope: CoroutineScope get() = scope

    val state: StateFlow<AuthState> = combine(tokenStore.state, extras) { session, extra ->
        AuthState(
            isSignedIn = session.isSignedIn,
            needsReauth = session.needsReauth && !session.isSignedIn,
            hydrationDeferred = session.hydrationDeferred,
            accountId = session.accountId,
            userName = session.displayName,
            userEmail = extra.email,
            isAuthenticating = extra.isAuthenticating,
            isPublicProfile = extra.isPublicProfile,
        )
    }.stateIn(scope, SharingStarted.Eagerly, AuthState())

    private data class Extras(val email: String? = null, val isAuthenticating: Boolean = false, val isPublicProfile: Boolean? = null)

    private val signInLock = Mutex()
    private val profilePushLock = Mutex()
    @Volatile private var postSignInJob: Job? = null
    @Volatile private var clearing: Job? = null
    @Volatile private var signOutInProgress = false
    @Volatile private var identityChange: Pair<UUID?, UUID>? = null
    // iOS: publicProfileGeneration. A /auth/me read taken before a toggle must not revert it.
    @Volatile private var publicProfileGeneration = 0
    private val meListeners = CopyOnWriteArrayList<(MeResponse) -> Unit>()

    /** Profile owners (progress, journal, cosmetics) read the same `/auth/me` answer. */
    fun addMeListener(listener: (MeResponse) -> Unit) { meListeners += listener }

    /**
     * Launch: read the session three ways (value, missing, locked) at once, so a signed-in person
     * never sees the guest card; then, once the install's identity is loaded ([identityLoaded]),
     * start the engine of the account found there, revive a soft expiry and backfill a name the
     * server never confirmed.
     */
    fun start(identityLoaded: suspend () -> Unit = {}) {
        scope.launch {
            withContext(io) { tokenStore.hydrate() }
            identityLoaded()
            sync.switchAccount(tokenStore.state.value.accountId)
            reviveSessionIfPossible()
            backfillNameIfNeeded()
        }
    }

    /**
     * Foreground and ACTION_USER_UNLOCKED. A launch on a locked phone deferred the reading; and a
     * soft-expired session with its refresh token intact is asked about silently.
     */
    fun onForeground() {
        scope.launch {
            if (tokenStore.state.value.hydrationDeferred) {
                withContext(io) { tokenStore.hydrate() }
                if (!tokenStore.state.value.hydrationDeferred) sync.switchAccount(tokenStore.state.value.accountId)
            }
            reviveSessionIfPossible()
        }
    }

    /** iOS: reviveSessionIfPossible. The answer arrives through [sessionRecovered]. */
    fun reviveSessionIfPossible() {
        val session = tokenStore.state.value
        if (!session.needsReauth || session.isSignedIn || signOutInProgress || session.accountId == null) return
        scope.launch {
            try { client.reviveSoftExpiredSession() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A transient failure arms the client's own recovery ladder. */ }
        }
    }

    // MARK: - Sign in

    /**
     * iOS: AppleSignInButton.startAuthorization + handleAuthorization. Nothing about the previous
     * identity is touched until `/auth/google/login` answers with success: a cancelled sheet or a
     * failed request leaves the old session, soft-expired or not, exactly as it was.
     */
    suspend fun signIn(context: Context, flow: GoogleSignInFlow = GoogleSignInFlow.button): SignInOutcome {
        if (!config.isConfigured) return SignInOutcome.NotConfigured
        if (!signInLock.tryLock()) return SignInOutcome.Busy
        try {
            // A fresh nonce per attempt: Google embeds its SHA-256, the server gets the raw value.
            val nonce = GoogleNonce.create()
            val token = when (val result = requestToken(context, flow, nonce.sha256)) {
                is GoogleCredentialResult.Token -> result
                GoogleCredentialResult.Cancelled -> return SignInOutcome.Cancelled
                GoogleCredentialResult.NoAccount -> return SignInOutcome.Failed("noAccount")
                is GoogleCredentialResult.Failed -> return SignInOutcome.Failed(result.reason)
            }
            val previous = tokenStore.state.value
            extras.update { it.copy(isAuthenticating = true) }
            val response = try {
                identityChange = null
                client.loginGoogle(GoogleLoginRequest(
                    identityToken = token.idToken,
                    localUserId = localUserId().toString(),
                    nonce = nonce.raw,
                    deviceName = config.deviceName?.take(128),
                    appVersion = config.appVersion.take(32),
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiError) {
                return SignInOutcome.Failed(error.message ?: "api")
            } catch (_: SecureStorageException) {
                return SignInOutcome.Failed("storage")
            } finally {
                extras.update { it.copy(isAuthenticating = false) }
            }
            // The server already holds the new session: what follows must finish whole, or the phone
            // would be left half in the old account and half in the new one.
            withContext(NonCancellable) { onSignedIn(response, token, previous.accountId, previous.displayName) }
            return SignInOutcome.SignedIn(response.isNewAccount)
        } finally {
            signInLock.unlock()
        }
    }

    /**
     * The returning flow asks only the accounts that already used TripTrack, with auto-select, then
     * every account; when the phone offers none, the button's full picker with «add account».
     */
    private suspend fun requestToken(context: Context, flow: GoogleSignInFlow, hashedNonce: String): GoogleCredentialResult {
        if (flow == GoogleSignInFlow.button) return credentials.request(context, GoogleSignInFlow.button, hashedNonce, authorizedOnly = false)
        var result = credentials.request(context, GoogleSignInFlow.returning, hashedNonce, authorizedOnly = true)
        if (result == GoogleCredentialResult.NoAccount) result = credentials.request(context, GoogleSignInFlow.returning, hashedNonce, authorizedOnly = false)
        if (result == GoogleCredentialResult.NoAccount) result = credentials.request(context, GoogleSignInFlow.button, hashedNonce, authorizedOnly = false)
        return result
    }

    private suspend fun onSignedIn(response: LoginResponse, token: GoogleCredentialResult.Token, previousAccount: UUID?, previousName: String?) {
        val current = response.account.id
        val previous = identityChange?.first ?: previousAccount
        // iOS: prepareForIdentity. A soft expiry preserved the previous account's queue, consent and
        // name for the SAME person; a different account must not inherit them. A guest's library
        // (no previous account) is the phone's and is never purged.
        if (previous != null && previous != current) {
            Log.i(TAG, "[auth.cross_account] purging state of previous identity before sign-in")
            postSignInJob?.cancel()
            sync.clearAccountWork()
            accountState.purge()
            extras.update { Extras() }
        }
        sync.switchAccount(current)
        extras.update { it.copy(email = response.account.email?.takeIf(String::isNotBlank) ?: token.email ?: it.email) }
        // The globe opt-in is read back before the profile push, so the push echoes the server's
        // value instead of overwriting it with this phone's default. Absent: an older server.
        response.account.showOnPublicMap?.let(accountSettings::setShowOnPublicMap)
        resolveName(response, token, sameAccount = previous == current, localName = previousName)
        postSignInJob = scope.launch {
            sync.firstSync()
            backfillNameIfNeeded()
            refreshMe()
        }
    }

    /**
     * iOS: the name resolution of handleAuthorization, first non-empty wins. On the same account a
     * name kept on this phone wins (it may be a rename that never reached the server), then the
     * server's name, then Google's (clamped to the server's 30), then a generated one. Google gives
     * its name on every sign-in, unlike Apple's first-only `fullName`, so it only fills a nameless
     * account and never overwrites a name chosen in TripTrack (docs/divergences.md).
     */
    private fun resolveName(response: LoginResponse, token: GoogleCredentialResult.Token, sameAccount: Boolean, localName: String?) {
        val kept = localName?.trim()?.takeIf { sameAccount && it.isNotEmpty() }
        val server = response.account.displayName?.trim()?.takeIf(String::isNotEmpty)
        val name = kept ?: server ?: token.displayName?.let(DisplayNames::clamped)?.takeIf(String::isNotEmpty)
            ?: RandomDisplayName.generate(language())
        if (name != server) {
            try { tokenStore.setUserName(name) } catch (_: SecureStorageException) { /* The next launch backfills. */ }
            latch.markUnconfirmed()
        }
    }

    /**
     * iOS: loadFromKeychain's ProfileSyncLatch branch + pushDisplayNameToServer. The NAME ALONE: the
     * rest of a profile push mirrors THIS phone, and a second phone opened after an update would
     * otherwise roll the account back to its stale mirror.
     */
    suspend fun backfillNameIfNeeded() = profilePushLock.withLock {
        val session = tokenStore.state.value
        if (!latch.needsPush(session.isSignedIn, session.displayName)) return@withLock
        var name = session.displayName?.trim().orEmpty()
        if (name.isEmpty()) {
            name = RandomDisplayName.generate(language())
            try { tokenStore.setUserName(name) } catch (_: SecureStorageException) { return@withLock }
        }
        val accountId = session.accountId
        try {
            client.post<ProfileUpdateRequest, EmptyResponse>(ApiEndpoint.profileUpdate, ProfileUpdateRequest(displayName = name))
            // Only for a session that is still ours: a sign-out landing meanwhile already reset the
            // latch, and «confirmed» written now would swallow the NEXT account's first push.
            if (tokenStore.state.value.isSignedIn && tokenStore.state.value.accountId == accountId) latch.markConfirmed()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiError) {
            if (tokenStore.state.value.isSignedIn && tokenStore.state.value.accountId == accountId) latch.markUnconfirmed()
        }
    }

    /**
     * iOS: updateUserName. The name typed in the profile, kept with the session and pushed, so it
     * lands on every public trip card.
     */
    suspend fun updateUserName(raw: String) {
        val name = DisplayNames.clamped(raw)
        if (name.isEmpty()) return
        withContext(io) { tokenStore.setUserName(name) }
        latch.markUnconfirmed()
        backfillNameIfNeeded()
    }

    // MARK: - Account snapshot

    /**
     * iOS: refreshMe. A successful `/auth/me` seeds the public-profile switch and backfills the
     * email. Any failure (old server, network, decoding) leaves the switch hidden, never broken.
     */
    suspend fun refreshMe() {
        val session = tokenStore.state.value
        if (!session.isSignedIn) return
        val generation = publicProfileGeneration
        try {
            val me: MeResponse = client.post(ApiEndpoint.authMe, EmptyRequest())
            val now = tokenStore.state.value
            if (!now.isSignedIn || now.accountId != session.accountId || (me.id != null && me.id != session.accountId)) return
            extras.update {
                it.copy(
                    isPublicProfile = if (generation == publicProfileGeneration) me.isPublic else it.isPublicProfile,
                    email = it.email ?: me.email?.takeIf(String::isNotBlank),
                )
            }
            for (listener in meListeners) listener(me)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: ApiError) {
            Log.w(TAG, "refreshMe failed: ${error.message}")
        }
    }

    /**
     * iOS: setPublicProfile. The only path that sends `isPublic`, and the payload carries nothing
     * else, so a concurrent profile sync cannot clobber it. False tells the switch to snap back.
     */
    suspend fun setPublicProfile(isPublic: Boolean): Boolean {
        val generation = ++publicProfileGeneration
        return try {
            client.post<ProfileUpdateRequest, EmptyResponse>(ApiEndpoint.profileUpdate, ProfileUpdateRequest(isPublic = isPublic))
            if (generation == publicProfileGeneration) extras.update { it.copy(isPublicProfile = isPublic) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ApiError) {
            false
        }
    }

    // MARK: - Hard sign-outs

    /** Public trips on the server; decides which question the one sign-out dialog asks. */
    suspend fun publishedTripCount(): Int = serverActions.publishedTripCount()

    /**
     * iOS: CloudSyncView.performSignOut + AuthService.signOut. «Hide public and sign out» unpublishes
     * journeys BEFORE trips: the other order leaves, for a moment, a public journey without legs.
     */
    suspend fun signOut(hidePublic: Boolean = false) {
        if (signOutInProgress) return
        signOutInProgress = true
        try {
            if (hidePublic) {
                serverActions.unpublishAllPublicJourneys()
                serverActions.unpublishAllPublicTrips()
            }
            postSignInJob?.cancel()
            clearing = null
            client.signOut()
            clearing?.join()
        } finally {
            signOutInProgress = false
        }
    }

    /**
     * iOS: deleteAccount. Server first, and allowed to throw: erasing the phone and then failing to
     * reach the server would leave the account alive with nothing left to retry from.
     */
    suspend fun deleteAccount(wipeLocal: Boolean = true) {
        postSignInJob?.cancel()
        clearing = null
        client.deleteAccount()
        clearing?.join()
        if (wipeLocal) withContext(NonCancellable) { wipeLocalData() }
    }

    /**
     * iOS: wipeServerData. Cloud Sync goes OFF and the queue empties first, or the next pass would
     * upload everything straight back. Returns the number of objects the server kept.
     */
    suspend fun wipeServerData(): Int {
        accountSettings.resetCloudSync()
        sync.clearAccountWork()
        return serverActions.wipeServerData()
    }

    /**
     * iOS: clearLocalIdentity. Shared by the three hard sign-outs, so they leave the phone in the
     * same state. The tokens are already gone (the client cleared them before calling back).
     */
    private suspend fun clearLocalIdentity(reason: HardSignOutReason) = withContext(NonCancellable) {
        Log.i(TAG, "[auth.signout] reason=$reason")
        postSignInJob?.cancel()
        sync.clearAccountWork()
        sync.switchAccount(null)
        accountState.purge()
        extras.update { Extras() }
        credentials.clear(appContext)
    }

    // MARK: - SessionEvents.Listener (called by ApiClient, possibly from inside its lock)

    /**
     * iOS: sessionExpired. Drops only what is already dead; the post-sign-in chain is cancelled
     * because its calls would fail and re-trigger this path.
     */
    override fun sessionExpired() {
        Log.w(TAG, "[auth.session_expired] keeping local data, requesting re-login")
        postSignInJob?.cancel()
    }

    /** iOS: sessionRecovered. The card goes away by itself; the paused queue gets a pass. */
    override fun sessionRecovered() {
        Log.i(TAG, "[auth.session_recovered] refresh succeeded, session back without a prompt")
        sync.requestSync()
    }

    /**
     * The client cleared the tokens and calls back from inside its lock, so the cleanup runs on the
     * service's scope; [signOut] and [deleteAccount] wait for it. A sign-out the client dropped as
     * stale (another account signed in meanwhile) never calls back and clears nothing.
     */
    override fun hardSignOut(reason: HardSignOutReason) {
        clearing = scope.launch { clearLocalIdentity(reason) }
    }

    override fun identityChanged(previous: UUID?, current: UUID) {
        identityChange = previous to current
    }

    private companion object {
        const val TAG = "TripTrackAuth"
    }
}
