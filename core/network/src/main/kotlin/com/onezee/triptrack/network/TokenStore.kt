// iOS: TokenStore.swift, KeychainHelper.swift, AuthService.swift (session state)
package com.onezee.triptrack.network

import com.onezee.triptrack.model.LoginResponse
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// iOS: KeychainRead in KeychainHelper.swift
sealed interface SecureRead<out T> {
    data class Value<T>(val value: T) : SecureRead<T>
    data object Missing : SecureRead<Nothing>
    data class Unavailable(val reason: String) : SecureRead<Nothing>
}

// iOS: KeychainOps in KeychainHelper.swift
interface SecureVault {
    val apiHost: String? get() = null
    fun read(): SecureRead<Map<String, String>>
    // A failed write must leave the previous snapshot readable.
    fun write(values: Map<String, String>)
}

// iOS: KeychainError in KeychainHelper.swift
class SecureStorageException(cause: Throwable? = null) : Exception("Secure storage unavailable", cause)

// iOS: AuthService.swift (isSignedIn, needsReauth, hydrationDeferred)
data class SessionState(
    val isSignedIn: Boolean = false,
    val needsReauth: Boolean = false,
    val hydrationDeferred: Boolean = false,
    val accountId: UUID? = null,
    val displayName: String? = null,
)

// iOS: AuthService.swift (three hard sign-out paths)
enum class HardSignOutReason { userSignOut, accountDeletion, banned }

// iOS: TokenStore.swift
class TokenStore(private val vault: SecureVault) {
    internal val apiHost: String? get() = vault.apiHost
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    val accessTokenRead: SecureRead<String> get() = readKey(accessTokenKey)
    val refreshTokenRead: SecureRead<String> get() = readKey(refreshTokenKey)
    val accountIdRead: SecureRead<String> get() = readKey(accountIdKey)

    @Synchronized
    fun hydrate() {
        when (val result = vault.read()) {
            is SecureRead.Unavailable -> mutableState.value = mutableState.value.copy(hydrationDeferred = true)
            SecureRead.Missing -> mutableState.value = SessionState()
            is SecureRead.Value -> {
                val values = result.value
                if (values[signedInKey] != null && values[accountIdKey] != null &&
                    (values[accessTokenKey] == null || values[refreshTokenKey] == null)) {
                    update { it.remove(signedInKey); it[expiredKey] = "true" }
                } else if (values[signedInKey] != null && values[accountIdKey] != null && values[expiredKey] != null) {
                    update { it.remove(expiredKey) }
                } else publish(values)
            }
        }
    }

    @Synchronized
    fun set(accessToken: String, refreshToken: String) = update {
        it[accessTokenKey] = accessToken
        it[refreshTokenKey] = refreshToken
    }

    @Synchronized
    internal fun acceptLogin(response: LoginResponse) = update {
        it[accessTokenKey] = response.accessToken
        it[refreshTokenKey] = response.refreshToken
        it[accountIdKey] = response.account.id.toString()
        it[signedInKey] = "true"
        it.remove(expiredKey)
        it.remove(userNameKey)
        response.account.displayName?.let { name -> it[userNameKey] = name }
    }

    @Synchronized
    internal fun sessionExpired() {
        if (!state.value.isSignedIn && accessTokenRead !is SecureRead.Value) return
        update {
            it.remove(accessTokenKey)
            it.remove(signedInKey)
            it[expiredKey] = "true"
        }
    }

    @Synchronized
    internal fun sessionRecovered() {
        update {
            if (it[accountIdKey] != null && it[accessTokenKey] != null && it[refreshTokenKey] != null) {
                it[signedInKey] = "true"
                it.remove(expiredKey)
            }
        }
    }

    @Synchronized
    internal fun clear(reason: HardSignOutReason) {
        vault.write(emptyMap())
        mutableState.value = SessionState()
    }

    @Synchronized
    fun clearAccessToken() = update { it.remove(accessTokenKey) }

    /**
     * iOS: `KeychainHelper.saveString(name, for: Keys.userName)` in AuthService. The name the person
     * typed, or the one sign-in resolved, kept with the session it belongs to: a soft expiry keeps
     * it, every hard sign-out clears it with the rest of the snapshot. Blank removes it.
     */
    @Synchronized
    fun setUserName(name: String?) = update { values ->
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) values.remove(userNameKey) else values[userNameKey] = trimmed
    }

    @Synchronized
    private fun readKey(key: String): SecureRead<String> = when (val result = vault.read()) {
        SecureRead.Missing -> SecureRead.Missing
        is SecureRead.Unavailable -> result
        is SecureRead.Value -> result.value[key]?.let { SecureRead.Value(it) } ?: SecureRead.Missing
    }

    private fun update(change: (MutableMap<String, String>) -> Unit) {
        val values = when (val result = vault.read()) {
            is SecureRead.Value -> result.value.toMutableMap()
            SecureRead.Missing -> mutableMapOf()
            is SecureRead.Unavailable -> throw SecureStorageException()
        }
        change(values)
        vault.write(values)
        publish(values)
    }

    private fun publish(values: Map<String, String>) {
        mutableState.value = SessionState(
            isSignedIn = values[signedInKey] != null && values[accountIdKey] != null &&
                values[accessTokenKey] != null && values[refreshTokenKey] != null,
            needsReauth = values[expiredKey] == "true",
            accountId = values[accountIdKey]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
            displayName = values[userNameKey],
        )
    }

    companion object {
        const val accessTokenKey = "com.triptrack.auth.accessToken"
        const val refreshTokenKey = "com.triptrack.auth.refreshToken"
        const val accountIdKey = "com.triptrack.auth.accountId"
        const val signedInKey = "com.triptrack.auth.isSignedIn"
        const val expiredKey = "com.triptrack.auth.sessionExpired"
        const val userNameKey = "com.triptrack.auth.userName"

        fun composeService(applicationId: String?, host: String?): String =
            "${applicationId?.takeIf(String::isNotEmpty) ?: "com.triptrack"}.auth.${host?.takeIf(String::isNotEmpty)?.lowercase() ?: "unknown"}"
    }
}
