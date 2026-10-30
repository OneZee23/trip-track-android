package com.onezee.triptrack.sync

import com.onezee.triptrack.data.DiscoveryStore
import com.onezee.triptrack.model.DiscoveryKind
import com.onezee.triptrack.model.SecretRevealRequest
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.SecretsApi
import java.util.UUID

// iOS: DiscoveryReveal.swift (retry/isPermanent). The find itself stays local after rejection.
class DiscoverySync(private val api: SecretsApi, private val store: DiscoveryStore, private val isAllowed: () -> Boolean) {
    suspend fun retry(id: UUID) {
        if (!isAllowed()) return
        val item = store.discovery(id)?.takeUnless { it.kind == DiscoveryKind.milestone } ?: return
        try { store.apply(api.reveal(SecretRevealRequest(item.key, item.kind.rawValue, item.tripId))) }
        catch (error: ApiError) { if (!isPermanent(error)) throw error }
    }
    companion object {
        fun isPermanent(error: ApiError): Boolean = when (error) {
            is ApiError.UnknownServer -> error.code in setOf("SECRET_NOT_FOUND", "RIDDLE_NOT_FOUND", "DISCOVERY_NOT_FOUND")
            is ApiError.ValidationFailed, ApiError.TripNotFound, ApiError.PhotoNotFound, ApiError.UserBanned -> true
            is ApiError.InvalidHttpStatus -> error.status in 400..499 && error.status != 408 && error.status != 429
            else -> false
        }
    }
}
