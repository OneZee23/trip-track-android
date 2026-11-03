// iOS: the APIClient call sites of PublicProfileView, FollowListView, ReportSheet, PublicGarageView,
// PublicVehicleView, PublicJourneysView, RemoteTripSource (TripSource.swift), NotificationsInboxStore,
// CompanionsStore and the share sheets.
package com.onezee.triptrack.feature.social

import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.PublicTripsResponse
import java.util.UUID

/** iOS: FollowListMode. */
enum class FollowListMode { followers, following }

/** iOS: ReportTarget. `type` is the server's `targetType`. */
sealed class ReportTarget(val type: String, val id: UUID) {
    class User(id: UUID) : ReportTarget("user", id)
    class Trip(id: UUID) : ReportTarget("trip", id)
    // Android: the backend accepts `comment`; iOS has no comment report (docs/divergences.md).
    class Comment(id: UUID) : ReportTarget("comment", id)
    class Vehicle(id: UUID) : ReportTarget("vehicle", id)
    class VehiclePhoto(id: UUID) : ReportTarget("vehicle_photo", id)
}

/**
 * Everything the social screens ask the server. One interface, so view models are tested against a
 * fake and the wire is tested once against MockWebServer through the real [ApiClient].
 */
interface SocialApi {
    suspend fun profile(id: UUID): SocialProfile
    suspend fun profileTrips(id: UUID, cursor: String, limit: Int): SocialFeedResponse
    suspend fun badges(id: UUID): List<String>
    suspend fun garage(id: UUID): List<PublicVehicle>
    suspend fun vehicle(id: UUID, vehicleId: UUID): PublicVehicle
    suspend fun journeys(id: UUID, cursor: String?, limit: Int): PublicJourneysResponse
    suspend fun publicTrips(id: UUID, cursor: String?, limit: Int?, vehicleId: UUID? = null): PublicTripsResponse
    suspend fun follow(id: UUID, follow: Boolean)
    suspend fun people(id: UUID, mode: FollowListMode): SocialFollowersResponse
    suspend fun searchPeople(query: String, limit: Int = 25): SocialUsersResponse
    suspend fun suggestedPeople(limit: Int = 10): SocialSuggestedResponse
    suspend fun block(id: UUID, block: Boolean)
    suspend fun report(target: ReportTarget, reason: ReportReason, notes: String?)
    suspend fun shareTrip(tripId: UUID): SocialShareResponse
    suspend fun shareJourney(journeyId: UUID): SocialShareResponse
    suspend fun trip(id: UUID, includeTrack: Boolean): SocialTripResponse
    suspend fun notifications(cursor: String?): NotificationsFeedResponse
    suspend fun unreadCount(): Int
    /** Null marks everything read; an empty list is never sent (it would be a no-op at best). */
    suspend fun markRead(ids: List<UUID>?)
    suspend fun invitePreview(tripId: UUID): CompanionInvitePreview
    suspend fun companions(tripId: UUID): CompanionsListResponse
    suspend fun companionCandidates(tripId: UUID, query: String?, cursor: String?): CompanionsCandidatesResponse
    suspend fun inviteCompanion(tripId: UUID, accountId: UUID): CompanionInviteResult
    suspend fun removeCompanion(tripId: UUID, accountId: UUID)
    suspend fun respondToInvite(tripId: UUID, accept: Boolean): CompanionStatus
    suspend fun companionTrips(cursor: String?): CompanionsMyTripsResponse
}

/** The wire: [signedIn] decides whether a guest-readable call carries the token, as iOS `requiresAuth: isSignedIn`. */
class ApiSocial(private val client: ApiClient, private val signedIn: () -> Boolean) : SocialApi {
    override suspend fun profile(id: UUID): SocialProfile = client.get(ApiEndpoint.userProfile(id), requiresAuth = signedIn())
    override suspend fun profileTrips(id: UUID, cursor: String, limit: Int): SocialFeedResponse =
        client.get(ApiEndpoint.userProfileTrips(id, cursor, limit), requiresAuth = signedIn())
    override suspend fun badges(id: UUID): List<String> =
        client.get<SocialBadgesResponse>(ApiEndpoint.userBadges(id), requiresAuth = signedIn()).badges
    override suspend fun garage(id: UUID): List<PublicVehicle> =
        client.get<PublicGarageResponse>(ApiEndpoint.userGarage(id), requiresAuth = signedIn()).vehicles
    override suspend fun vehicle(id: UUID, vehicleId: UUID): PublicVehicle =
        client.get(ApiEndpoint.userVehicle(id, vehicleId), requiresAuth = signedIn())
    override suspend fun journeys(id: UUID, cursor: String?, limit: Int): PublicJourneysResponse =
        client.get(ApiEndpoint.userJourneys(id, cursor, limit), requiresAuth = signedIn())
    override suspend fun publicTrips(id: UUID, cursor: String?, limit: Int?, vehicleId: UUID?): PublicTripsResponse =
        client.get(ApiEndpoint.userTrips(id, cursor, limit, vehicleId), requiresAuth = signedIn())
    override suspend fun follow(id: UUID, follow: Boolean) {
        client.post<SocialFollowRequest, SocialFollowResponse>(if (follow) ApiEndpoint.socialFollow else ApiEndpoint.socialUnfollow, SocialFollowRequest(id))
    }
    // iOS FollowListView.load: one page of 100, the account named explicitly so a guest can read it.
    override suspend fun people(id: UUID, mode: FollowListMode): SocialFollowersResponse = client.post(
        if (mode == FollowListMode.followers) ApiEndpoint.socialFollowers else ApiEndpoint.socialFollowing,
        SocialFollowersRequest(accountId = id, limit = 100, offset = 0), requiresAuth = signedIn())
    override suspend fun searchPeople(query: String, limit: Int): SocialUsersResponse =
        client.post(ApiEndpoint.socialSearch, SocialSearchRequest(query, limit), requiresAuth = signedIn())
    override suspend fun suggestedPeople(limit: Int): SocialSuggestedResponse =
        client.post(ApiEndpoint.socialSuggested, SocialSuggestedRequest(limit), requiresAuth = signedIn())
    override suspend fun block(id: UUID, block: Boolean) {
        client.post<SocialBlockRequest, SocialBlockResponse>(if (block) ApiEndpoint.socialBlock else ApiEndpoint.socialUnblock, SocialBlockRequest(id))
    }
    override suspend fun report(target: ReportTarget, reason: ReportReason, notes: String?) {
        client.post<SocialReportRequest, SocialReportResponse>(ApiEndpoint.socialReport, SocialReportRequest(target.type, target.id, reason.rawValue, notes))
    }
    override suspend fun shareTrip(tripId: UUID): SocialShareResponse = client.post(ApiEndpoint.socialShare, SocialShareRequest(tripId))
    override suspend fun shareJourney(journeyId: UUID): SocialShareResponse = client.post(ApiEndpoint.socialShareJourney, SocialShareJourneyRequest(journeyId))
    override suspend fun trip(id: UUID, includeTrack: Boolean): SocialTripResponse =
        client.post(ApiEndpoint.socialTrip, SocialTripRequest(id, includeTrack), requiresAuth = signedIn())
    override suspend fun notifications(cursor: String?): NotificationsFeedResponse =
        client.post(ApiEndpoint.notificationsFeed, NotificationsFeedRequest(limit = 30, cursor = cursor))
    override suspend fun unreadCount(): Int =
        client.post<EmptyRequest, NotificationsUnreadCountResponse>(ApiEndpoint.notificationsUnreadCount, EmptyRequest()).count
    override suspend fun markRead(ids: List<UUID>?) {
        require(ids == null || ids.isNotEmpty()) { "an empty batch must never be sent" }
        client.post<NotificationsMarkReadRequest, EmptyResponse>(ApiEndpoint.notificationsMarkRead, NotificationsMarkReadRequest(ids))
    }
    override suspend fun invitePreview(tripId: UUID): CompanionInvitePreview = client.post(ApiEndpoint.companionsInvitePreview, CompanionsTripRequest(tripId))
    override suspend fun companions(tripId: UUID): CompanionsListResponse = client.post(ApiEndpoint.companionsList, CompanionsTripRequest(tripId))
    override suspend fun companionCandidates(tripId: UUID, query: String?, cursor: String?): CompanionsCandidatesResponse =
        client.post(ApiEndpoint.companionsCandidates, CompanionsCandidatesRequest(tripId, query, cursor))
    override suspend fun inviteCompanion(tripId: UUID, accountId: UUID): CompanionInviteResult =
        client.post<CompanionsInviteRequest, CompanionsInviteResponse>(ApiEndpoint.companionsInvite, CompanionsInviteRequest(tripId, accountId)).companion
    override suspend fun removeCompanion(tripId: UUID, accountId: UUID) {
        client.post<CompanionsRemoveRequest, CompanionsRemoveResponse>(ApiEndpoint.companionsRemove, CompanionsRemoveRequest(tripId, accountId))
    }
    override suspend fun respondToInvite(tripId: UUID, accept: Boolean): CompanionStatus =
        client.post<CompanionsRespondRequest, CompanionsRespondResponse>(ApiEndpoint.companionsRespond, CompanionsRespondRequest(tripId, accept)).status
    override suspend fun companionTrips(cursor: String?): CompanionsMyTripsResponse = client.post(ApiEndpoint.companionsMyTrips, CompanionsMyTripsRequest(cursor))
}

/** The server's code on a refusal it sends as an envelope error («USER_NOT_FOUND», «ALREADY_FOLLOWING»). */
val Throwable.serverCode: String? get() = (this as? ApiError.UnknownServer)?.code
