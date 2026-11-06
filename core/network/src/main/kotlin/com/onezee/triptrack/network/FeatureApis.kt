// iOS: Networking/{PlusAPI,SecretsAPI}.swift, RemoteConfigStore.swift, SocialCommentsStore.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.*

// iOS: PlusAPI.swift
class PlusApi(private val client: ApiClient) {
    suspend fun status(): PlusStatusResponse = client.post(ApiEndpoint.plusStatus, EmptyRequest())
    suspend fun redeem(code: String): PromoRedeemResponse = client.post(ApiEndpoint.promoRedeem, PromoRedeemRequest(code.trim()))
    suspend fun attach(signedTransaction: String): PlusStatusResponse = client.post(ApiEndpoint.plusAttach, PlusAttachRequest(signedTransaction))
}

// iOS: SecretsAPI.swift
class SecretsApi(private val client: ApiClient) {
    suspend fun catalog(version: String? = null): SecretCatalogResponse = client.get(ApiEndpoint.secretsCatalog(version), requiresAuth = false)
    suspend fun reveal(body: SecretRevealRequest): SecretRevealResponse = client.post(ApiEndpoint.secretsReveal, body)
}

// iOS: RemoteConfigStore.swift (RemoteConfigAPI), RemoteCatalogs.swift
class RemoteConfigApi(private val client: ApiClient) {
    suspend fun config(version: String? = null): RemoteConfigResponse = client.get(ApiEndpoint.appConfig(version), requiresAuth = false)
    suspend fun catalog(name: String, version: String? = null): RemoteCatalogResponse = client.get(ApiEndpoint.remoteCatalog(name, version), requiresAuth = false)
}

// iOS: Services/Social/SocialCommentsStore.swift
class SocialCommentsApi(private val client: ApiClient) {
    suspend fun create(request: SocialCommentCreateRequest): SocialCommentCreateResponse =
        client.post(ApiEndpoint.socialComment, request, singleAttempt = true)
}

// iOS: ManualTripSuggestions.swift, ManualTripRouter.swift. Geoapify credentials stay on the server.
class ManualTripsApi(private val client: ApiClient) {
    suspend fun capabilities(): ManualTripCapabilities =
        client.post(ApiEndpoint.webRoutingCapabilities, EmptyRequest())

    suspend fun search(query: String, language: String): ManualTripSearchResponse {
        require(query.length in 3..200)
        require(language in LANGUAGES)
        return client.post(ApiEndpoint.webRoutingSearch, ManualTripSearchRequest(query, language))
    }

    suspend fun route(waypoints: List<com.onezee.triptrack.model.GeoPoint>): ManualTripRouteResponse {
        require(waypoints.size in 2..5)
        return client.post(ApiEndpoint.webRoutingRoute, ManualTripRouteRequest(waypoints.map { listOf(it.latitude, it.longitude) }))
    }

    companion object {
        val LANGUAGES = setOf("en", "ru", "de", "es", "fr", "it", "pl", "id", "tr", "uk", "kk", "pt")
    }
}

// iOS: Views/Admin/AdminModel.swift. Server authorization remains authoritative for every call.
class AdminApi(private val client: ApiClient) {
    suspend fun summary(): AdminSummary = client.post(ApiEndpoint.adminSummary, EmptyRequest())
    suspend fun subscriptions(): AdminItems<AdminSubscription> = client.post(ApiEndpoint.adminSubscriptions, EmptyRequest())
    suspend fun events(): AdminItems<AdminEvent> = client.post(ApiEndpoint.adminEvents, EmptyRequest())
    suspend fun flags(): AdminItems<AdminFlag> = client.post(ApiEndpoint.adminFlags, EmptyRequest())
    suspend fun setFlag(name: String, enabled: Boolean) = client.post<AdminFlagRequest, EmptyResponse>(ApiEndpoint.adminFlag, AdminFlagRequest(name, enabled))
}
