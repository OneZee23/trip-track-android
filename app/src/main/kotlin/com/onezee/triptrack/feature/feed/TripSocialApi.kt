package com.onezee.triptrack.feature.feed

import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.*
import java.util.UUID

// iOS: SocialFeedStore.swift, TripCommentsStore.swift, TripDetailView.swift, JourneyDetailView.swift
interface TripSocialApi {
    suspend fun feed(request: SocialFeedRequest): SocialFeedResponse
    suspend fun react(tripId: UUID, previous: String?, emoji: String)
    suspend fun reactions(tripId: UUID): List<SocialReactionEntry>
    suspend fun comments(request: TripCommentsRequest): TripCommentsResponse
    suspend fun createComment(request: SocialCommentCreateRequest): SocialCommentCreateResponse
    suspend fun deleteComment(id: UUID)
    suspend fun trip(id: UUID): SocialTripResponse
    suspend fun photos(id: UUID): List<SocialTripPhoto>
    suspend fun journey(id: UUID): SocialJourneyResponse
}

// iOS: APIClient.swift call sites in the social stores. Comment creation must stay singleAttempt.
class ApiTripSocial(private val client: ApiClient, private val signedIn: () -> Boolean) : TripSocialApi {
    override suspend fun feed(request: SocialFeedRequest): SocialFeedResponse = client.post(ApiEndpoint.socialFeed, request, requiresAuth = signedIn())
    override suspend fun react(tripId: UUID, previous: String?, emoji: String) {
        if (previous == emoji) client.post<SocialUnreactRequest, SocialReactResponse>(ApiEndpoint.socialUnreact, SocialUnreactRequest(tripId))
        else client.post<SocialReactRequest, SocialReactResponse>(ApiEndpoint.socialReact, SocialReactRequest(tripId, emoji))
    }
    override suspend fun reactions(tripId: UUID): List<SocialReactionEntry> =
        client.post<SocialTripRequest, SocialReactionsResponse>(ApiEndpoint.socialReactions, SocialTripRequest(tripId), requiresAuth = signedIn()).reactions
    override suspend fun comments(request: TripCommentsRequest): TripCommentsResponse = client.post(ApiEndpoint.socialComments, request, requiresAuth = signedIn())
    override suspend fun createComment(request: SocialCommentCreateRequest): SocialCommentCreateResponse =
        SocialCommentsApi(client).create(request)
    override suspend fun deleteComment(id: UUID) { client.post<SocialCommentDeleteRequest, SocialCommentDeleteResponse>(ApiEndpoint.socialCommentDelete, SocialCommentDeleteRequest(id)) }
    override suspend fun trip(id: UUID): SocialTripResponse = client.post(ApiEndpoint.socialTrip, SocialTripRequest(id, true), requiresAuth = signedIn())
    override suspend fun photos(id: UUID): List<SocialTripPhoto> = client.post<SocialTripPhotosRequest, SocialTripPhotosResponse>(ApiEndpoint.socialTripPhotos, SocialTripPhotosRequest(id), requiresAuth = signedIn()).photos
    override suspend fun journey(id: UUID): SocialJourneyResponse = client.post(ApiEndpoint.socialJourney, SocialJourneyRequest(id), requiresAuth = signedIn())
}

// iOS: SocialFeedTrip.swift (Trip.init(social:))
fun SocialFeedTrip.asTrip(): Trip = Trip(id = id, startDate = startDate,
    endDate = endDate ?: startDate.plusSeconds(duration.toLong()), distance = distance, maxSpeed = maxSpeed ?: 0.0,
    averageSpeed = if (duration > 0) distance / duration else 0.0, title = title, tripDescription = description,
    region = region, elevation = elevation ?: 0.0, isPrivate = isPrivate ?: false, isOnServer = true, source = source ?: TripOrigin.recorded,
    previewPolyline = previewPolyline?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }, earnedBadgeIds = badgeIds,
    previewBreaks = previewBreaks)
