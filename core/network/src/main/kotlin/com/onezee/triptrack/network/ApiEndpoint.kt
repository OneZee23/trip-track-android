// iOS: APIEndpoint.swift
package com.onezee.triptrack.network

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl

object ApiEndpoint {
    const val defaultBaseUrl = "https://api.trip-track.app"
    const val login = "/auth/login"
    const val refresh = "/auth/refresh"
    const val logout = "/auth/logout"
    const val deleteAccount = "/auth/delete-account"
    const val profileUpdate = "/auth/profile-update"
    const val deviceToken = "/auth/device-token"
    const val authMe = "/auth/me"
    const val tripUpsert = "/trips/upsert"
    const val tripDetail = "/trips/detail"
    const val tripDelete = "/trips/delete"
    const val vehicleUpsert = "/vehicles/upsert"
    const val vehicleList = "/vehicles/list"
    const val vehicleDelete = "/vehicles/delete"
    const val journeyUpsert = "/journeys/upsert"
    const val journeyList = "/journeys/list"
    const val journeyDelete = "/journeys/delete"
    const val settingsUpsert = "/settings/upsert"
    const val profilePhotoUpload = "/profile/photos/upload"
    const val profilePhotoList = "/profile/photos/list"
    const val profilePhotoDelete = "/profile/photos/delete"
    const val profilePhotoOrder = "/profile/photos/order"
    const val photoUpload = "/photos/upload"
    const val photoURL = "/photos/url"
    const val photoDelete = "/photos/delete"
    const val vehiclePhotoUpload = "/vehicles/photos/upload"
    const val vehiclePhotoDelete = "/vehicles/photos/delete"
    const val vehiclePhotoMain = "/vehicles/photos/main"
    const val vehiclePhotoList = "/vehicles/photos/list"
    const val secretsReveal = "/secrets/reveal"
    const val secretsForgetAll = "/secrets/forget-all"
    const val plusAttach = "/plus/attach"
    const val plusStatus = "/plus/status"
    const val promoRedeem = "/promo/redeem"
    const val syncPull = "/sync/pull"
    const val syncPush = "/sync/push"
    const val syncManifest = "/sync/manifest"
    const val socialFeed = "/social/feed"
    const val socialFollow = "/social/follow"
    const val socialUnfollow = "/social/unfollow"
    const val socialFollowers = "/social/followers"
    const val socialFollowing = "/social/following"
    const val socialReact = "/social/react"
    const val socialUnreact = "/social/unreact"
    const val socialShare = "/social/share"
    const val socialSearch = "/social/search"
    const val socialSuggested = "/social/suggested"
    const val socialBlock = "/social/block"
    const val socialUnblock = "/social/unblock"
    const val socialBlocked = "/social/blocked"
    const val socialReport = "/social/report"
    const val socialReactions = "/social/reactions"
    const val socialTrip = "/social/trip"
    const val socialTripPhotos = "/social/trip/photos"
    const val socialComment = "/social/comment"
    const val socialComments = "/social/comments"
    const val socialCommentDelete = "/social/comment/delete"
    const val socialJourney = "/social/journey"
    const val socialShareJourney = "/social/share-journey"
    const val groupsWaitlist = "/groups/waitlist"
    const val groupsWaitlistJoin = "/groups/waitlist/join"
    const val groupsWaitlistLeave = "/groups/waitlist/leave"
    const val notificationsFeed = "/notifications/feed"
    const val notificationsUnreadCount = "/notifications/unread-count"
    const val notificationsMarkRead = "/notifications/mark-read"
    const val adminSummary = "/admin/summary"
    const val adminSubscriptions = "/admin/subscriptions"
    const val adminEvents = "/admin/events"
    const val adminFlags = "/admin/flags"
    const val adminFlag = "/admin/flag"
    const val notificationPrefsGet = "/auth/notification-prefs/get"
    const val notificationPrefsUpdate = "/auth/notification-prefs/update"
    const val companionsList = "/companions/list"
    const val companionsCandidates = "/companions/candidates"
    const val companionsInvite = "/companions/invite"
    const val companionsRespond = "/companions/respond"
    const val companionsRemove = "/companions/remove"
    const val companionsInvitePreview = "/companions/invite-preview"
    const val companionsMyTrips = "/companions/my-trips"
    // Android: AuthDTOs.swift login contract with Google identity proof.
    const val googleLogin = "/auth/google/login"
    const val usageSnapshot = "/usage/snapshot"
    const val usageErase = "/usage/erase"
    const val webRoutingCapabilities = "/web-routing/capabilities"
    const val webRoutingSearch = "/web-routing/search"
    const val webRoutingRoute = "/web-routing/route"

    private fun query(path: String, vararg values: Pair<String, String?>): String {
        val url = defaultBaseUrl.toHttpUrl().newBuilder().encodedPath(path)
        values.forEach { (key, value) -> if (!value.isNullOrEmpty()) url.addQueryParameter(key, value) }
        val built = url.build()
        return built.encodedPath + (built.encodedQuery?.let { "?$it" } ?: "")
    }

    fun appConfig(version: String? = null) = query("/app/config", "version" to version)
    fun secretsCatalog(version: String? = null) = query("/secrets/catalog", "version" to version)
    fun remoteCatalog(name: String, version: String? = null): String {
        require(name == "riddles" || name == "vehicles")
        return query("/app/catalog/$name", "version" to version)
    }

    fun userProfile(id: UUID) = "/users/$id/profile"
    fun userGarage(id: UUID) = "/users/$id/garage"
    fun userVehicle(id: UUID, vehicleId: UUID) = "/users/$id/vehicles/$vehicleId"
    fun userBadges(id: UUID) = "/users/$id/badges"
    fun userTrips(id: UUID, cursor: String? = null, limit: Int? = null, vehicleId: UUID? = null) =
        query("/users/$id/trips", "limit" to limit?.toString(), "vehicleId" to vehicleId?.toString(), "cursor" to cursor)
    fun userProfileTrips(id: UUID, cursor: String? = null, limit: Int? = null) =
        query("/users/$id/profile-trips", "limit" to limit?.toString(), "cursor" to cursor)
    fun userJourneys(id: UUID, cursor: String? = null, limit: Int? = null) =
        query("/users/$id/journeys", "limit" to limit?.toString(), "cursor" to cursor)

    fun feedCursor(startDate: Instant, id: UUID): String =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(startDate) + "|$id"
}
