// iOS: ProductAnalytics.swift. Consent and its random erase token are independent of the account.
package com.onezee.triptrack.feature.settings

import com.onezee.triptrack.network.*
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.json.JSONArray
import org.json.JSONObject

// iOS: ProductAnalytics URLSession seam.
interface UsageService {
    suspend fun snapshot(value: ProductAnalyticsSnapshot)
    suspend fun erase(token: String)
}

// iOS: ProductAnalytics.flush. ApiClient's usage transport sends no credentials.
class ApiUsageService(private val apiClient: ApiClient) : UsageService {
    override suspend fun snapshot(value: ProductAnalyticsSnapshot) = apiClient.postUsage(ApiEndpoint.usageSnapshot, value)
    override suspend fun erase(token: String) = apiClient.postUsage(ApiEndpoint.usageErase, UsageEraseRequest(token))
}

// iOS: ProductAnalytics. A retry replaces one bounded snapshot, never adds events twice.
class ProductAnalytics(private val preferences: PresentationPreferences, private val service: UsageService,
                       private val scope: CoroutineScope, private val networkEnabled: Boolean,
                       private val now: () -> Instant = Instant::now) {
    private var snapshot: ProductAnalyticsSnapshot? = null
    private var activeRecording: UUID? = null
    private var pendingErase = emptyList<String>()
    private var acknowledgedRevision = 0
    private var sending = false
    private var scheduled: Job? = null
    private val mutableEnabled = MutableStateFlow(false)
    val enabled = mutableEnabled.asStateFlow()
    private val mutableDeletionPending = MutableStateFlow(false)
    val deletionPending = mutableDeletionPending.asStateFlow()
    init {
        preferences.string(KEY)?.let { raw -> runCatching {
            val value = JSONObject(raw)
            snapshot = value.optJSONObject("snapshot")?.let { NetworkJson.wire.decodeFromString<ProductAnalyticsSnapshot>(it.toString()) }
            activeRecording = value.optString("activeRecording").takeIf { it.isNotBlank() }?.let(UUID::fromString)
            acknowledgedRevision = value.optInt("acknowledgedRevision")
            pendingErase = value.optJSONArray("pendingErase")?.let { list -> (0 until list.length()).map(list::getString) }.orEmpty().take(16)
        } }
        mutableEnabled.value = snapshot != null
        mutableDeletionPending.value = pendingErase.isNotEmpty()
    }
    fun setEnabled(value: Boolean, hadTrips: Boolean = false) {
        if (value == enabled.value) return
        if (value) {
            if (pendingErase.size >= 16) return
            snapshot = ProductAnalyticsSnapshot(1, UUID.randomUUID().toString(), 1, day(), day(), hadTrips, 0, 0, 0, 0, 0, 0, 0)
            acknowledgedRevision = 0
        } else {
            snapshot?.let { pendingErase = pendingErase + it.token }
            snapshot = null
        }
        activeRecording = null
        mutableEnabled.value = value
        persist(); scheduleFlush()
    }
    fun recordingStarted(id: UUID) {
        if (!enabled.value || activeRecording == id) return
        activeRecording = id
        update { it.copy(starts = (it.starts + 1).coerceAtMost(1_000_000)) }
    }
    fun recordingFinished(id: UUID, saved: Boolean, eligible: Boolean, elapsed: Double) {
        if (!enabled.value || activeRecording != id) return
        activeRecording = null
        update { old ->
            if (!saved) old.copy(saveFailures = (old.saveFailures + 1).coerceAtMost(1_000_000))
            else if (!eligible) old
            else old.copy(saves = (old.saves + 1).coerceAtMost(1_000_000),
                firstSaveDay = if (old.saves == 0) day() else old.firstSaveDay,
                secondSaveDay = if (old.saves == 1) day() else old.secondSaveDay,
                fastSaves = (old.fastSaves + if (elapsed < .1) 1 else 0).coerceAtMost(1_000_000),
                mediumSaves = (old.mediumSaves + if (elapsed >= .1 && elapsed < 1) 1 else 0).coerceAtMost(1_000_000),
                slowSaves = (old.slowSaves + if (elapsed >= 1) 1 else 0).coerceAtMost(1_000_000))
        }
    }
    fun atlasOpened() = update { it.copy(atlasOpens = (it.atlasOpens + 1).coerceAtMost(1_000_000)) }
    private fun update(change: (ProductAnalyticsSnapshot) -> ProductAnalyticsSnapshot) {
        val old = snapshot ?: return
        snapshot = change(old).copy(revision = old.revision + 1, observedDay = day())
        persist(); scheduleFlush()
    }
    private fun persist() {
        mutableDeletionPending.value = pendingErase.isNotEmpty()
        preferences.set(KEY, JSONObject().put("snapshot", snapshot?.let { JSONObject(NetworkJson.wire.encodeToString(it)) })
            .put("activeRecording", activeRecording?.toString()).put("pendingErase", JSONArray(pendingErase))
            .put("acknowledgedRevision", acknowledgedRevision).toString())
    }
    private fun scheduleFlush() { scheduled?.cancel(); scheduled = scope.launch { delay(2000); flush() } }
    fun foreground() { scope.launch { flush() } }
    suspend fun flush() {
        if (!networkEnabled || sending) return
        val erase = pendingErase.firstOrNull()
        val value = snapshot?.takeIf { it.revision > acknowledgedRevision }?.copy(observedDay = day())
        if (erase == null && value == null) return
        sending = true
        try {
            if (erase != null) { service.erase(erase); pendingErase = pendingErase - erase }
            else if (value != null) { service.snapshot(value); if (snapshot?.token == value.token) acknowledgedRevision = value.revision }
            persist(); scheduleFlush()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* The persisted revision or erase token is retried on foreground. */ }
        finally { sending = false }
    }
    private fun day() = now().atZone(ZoneOffset.UTC).toLocalDate().toString()
    companion object { private const val KEY = "productAnalytics.v1" }
}
