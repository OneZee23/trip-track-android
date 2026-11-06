// iOS: PlusAccess.swift, PlusStore.swift
package com.onezee.triptrack.feature.me

import android.content.SharedPreferences
import com.onezee.triptrack.feature.account.AuthService
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.MeResponse
import com.onezee.triptrack.model.PlusStatusResponse
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.ApiError
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlusEntitlement(val accountId: UUID, val active: Boolean, val until: Instant?, val promoUntil: Instant?, val fetchedAt: Instant)

interface PlusEntitlementApi {
    suspend fun status(): PlusStatusResponse
    suspend fun me(): MeResponse
}

class ApiPlusEntitlementApi(private val client: ApiClient) : PlusEntitlementApi {
    override suspend fun status(): PlusStatusResponse = client.post(ApiEndpoint.plusStatus, EmptyRequest())
    override suspend fun me(): MeResponse = client.post(ApiEndpoint.authMe, EmptyRequest())
}

class PlusEntitlementStore(
    private val preferences: SharedPreferences,
    private val api: PlusEntitlementApi,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(read())
    val entitlement: StateFlow<PlusEntitlement?> = mutable.asStateFlow()
    private val mutableIsPlus = MutableStateFlow(isActive(mutable.value))
    val isPlus: StateFlow<Boolean> = mutableIsPlus.asStateFlow()
    private var expiry: Job? = null
    @Volatile private var accountId: UUID? = null

    fun bind(auth: AuthService) {
        scope.launch {
            auth.state.map { it.signedInAccountId }.distinctUntilChanged().collect { setAccount(it) }
        }
    }

    fun setAccount(value: UUID?) {
        if (accountId == value) return
        accountId = value
        if (value == null || mutable.value?.accountId != value) save(null)
        else scheduleExpiry(mutable.value)
        mutableIsPlus.value = isActive(mutable.value)
        if (value != null) scope.launch { refresh() }
    }

    suspend fun refresh(): PlusEntitlement? = lock.withLock {
        val account = accountId ?: return@withLock null
        mutableIsPlus.value = isActive(mutable.value)
        try {
            val response = try {
                api.status()
            } catch (error: ApiError.UnknownServer) {
                if (error.code != "PLUS_UNAVAILABLE") throw error
                val me = api.me()
                if (accountId != account || me.id?.let { it != account } == true) return@withLock mutable.value
                val until = me.plusUntil?.let { runCatching { Instant.parse(it) }.getOrNull() }
                PlusStatusResponse(active = until?.isAfter(now()) == true, until = until)
            }
            if (accountId != account) return@withLock mutable.value
            val result = PlusEntitlement(account, response.active, response.until, response.promoUntil, now())
            save(result)
            result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value }
    }

    fun afterRedemption(status: PlusStatusResponse) {
        val account = accountId ?: return
        save(PlusEntitlement(account, status.active, status.until, status.promoUntil, now()))
        scope.launch { refresh() }
    }

    private fun save(value: PlusEntitlement?) {
        mutable.value = value
        preferences.edit().apply {
            if (value == null) remove(KEY_ACCOUNT).remove(KEY_ACTIVE).remove(KEY_UNTIL).remove(KEY_PROMO_UNTIL).remove(KEY_FETCHED)
            else putString(KEY_ACCOUNT, value.accountId.toString())
                .putBoolean(KEY_ACTIVE, value.active)
                .putString(KEY_UNTIL, value.until?.toString())
                .putString(KEY_PROMO_UNTIL, value.promoUntil?.toString())
                .putString(KEY_FETCHED, value.fetchedAt.toString())
        }.apply()
        scheduleExpiry(value)
        mutableIsPlus.value = isActive(value)
    }

    private fun scheduleExpiry(value: PlusEntitlement?) {
        expiry?.cancel()
        value?.until?.takeIf { value.active && it.isAfter(now()) }?.let { until ->
            expiry = scope.launch {
                delay(java.time.Duration.between(now(), until).toMillis().coerceAtLeast(1))
                mutableIsPlus.value = isActive(mutable.value)
            }
        }
    }

    private fun isActive(value: PlusEntitlement?): Boolean = value?.let { it.accountId == accountId && it.active && (it.until == null || it.until.isAfter(now())) } == true

    private fun read(): PlusEntitlement? = runCatching {
        val account = preferences.getString(KEY_ACCOUNT, null)?.let(UUID::fromString) ?: return null
        PlusEntitlement(account, preferences.getBoolean(KEY_ACTIVE, false), preferences.getString(KEY_UNTIL, null)?.let(Instant::parse),
            preferences.getString(KEY_PROMO_UNTIL, null)?.let(Instant::parse), preferences.getString(KEY_FETCHED, null)?.let(Instant::parse) ?: Instant.EPOCH)
    }.getOrNull()

    companion object {
        private const val KEY_ACCOUNT = "plus.accountId"
        private const val KEY_ACTIVE = "plus.active"
        private const val KEY_UNTIL = "plus.until"
        private const val KEY_PROMO_UNTIL = "plus.promoUntil"
        private const val KEY_FETCHED = "plus.fetchedAt"
    }
}
