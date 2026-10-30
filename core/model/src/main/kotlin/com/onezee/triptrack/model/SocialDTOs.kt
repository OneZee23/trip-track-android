// iOS: SocialDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.SocialFindSerializer
import com.onezee.triptrack.model.serialization.SocialProfileSerializer
import com.onezee.triptrack.model.serialization.SocialProfileVisibilitySerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialAuthor(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val profileLevel: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isPlus: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var avatarFrame: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFeedVehicle(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var id: UUID? = null,
    val name: String,
    val avatarEmoji: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarStyle: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFeedRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cursor: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var type: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFeedResponse(
    val trips: List<SocialFeedTrip>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialBadgesResponse(
    val badges: List<String>,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class ReactionTally(
    val emoji: String,
    val count: Int,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFeedTrip(
    val id: UUID,
    val author: SocialAuthor,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    val distance: Double,
    val duration: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxSpeed: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val elevation: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val maxAltitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val drivingTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stoppedTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val region: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isPrivate: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previewPolyline: String? = null,
    var photoCount: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var firstPhotoThumbnail: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val vehicle: SocialFeedVehicle? = null,
    val reactionCount: Int,
    val reactionBreakdown: List<ReactionTally>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val myReaction: String? = null,
    val badgeIds: List<String>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("commentCount")
    val commentCountRaw: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var journey: SocialFeedTripJourney? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var source: TripOrigin? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFeedTripJourney(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialJourneyHead(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val coverPhotoId: UUID? = null,
    val isPrivate: Boolean,
    val legCount: Int,
    val author: SocialAuthor,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialJourneyResponse(
    val journey: SocialJourneyHead,
    val legs: List<SocialFeedTrip>,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialJourneyRequest(
    val journeyId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialShareJourneyRequest(
    val journeyId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expiresInDays: Int? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicJourneyLeg(
    val id: UUID,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    val distance: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val duration: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val region: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previewPolyline: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicJourneyDto(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val coverPhotoId: UUID? = null,
    val legCount: Int,
    val legs: List<PublicJourneyLeg>,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PublicJourneysResponse(
    val journeys: List<PublicJourneyDto>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFollowRequest(
    val targetAccountId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFollowResponse(
    val following: Boolean,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFollowersRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val accountId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val offset: Int? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFollowersResponse(
    val users: List<SocialAuthor>,
    val total: Int,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialSearchRequest(
    val query: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialSuggestedRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialUsersResponse(
    val users: List<SocialAuthor>,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReactRequest(
    val tripId: UUID,
    val emoji: String,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReactResponse(
    val reacted: Boolean,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialUnreactRequest(
    val tripId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialShareRequest(
    val tripId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expiresInDays: Int? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialShareResponse(
    val shareUrl: String,
    val shareCode: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val expiresAt: Instant? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialProfileStats(
    val totalKm: Double,
    val tripCount: Int,
    val regionsCount: Int,
    val publicTripCount: Int,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialProfileRecentTrip(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    val startDate: Instant,
    val distance: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val region: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previewPolyline: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var author: SocialAuthor? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var description: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var endDate: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var duration: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var maxSpeed: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var elevation: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var maxAltitude: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var drivingTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var stoppedTime: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isPrivate: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var photoCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var firstPhotoThumbnail: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var vehicle: SocialFeedVehicle? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var reactionCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var reactionBreakdown: List<ReactionTally>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var myReaction: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var badgeIds: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var commentCount: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var averageSpeed: Double? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialActiveVehicle(
    val id: UUID,
    val name: String,
    val level: Int,
    val odometerKm: Double,
    val avatarEmoji: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarStyle: String? = null,
)

// iOS: SocialDTOs.swift
@KeepGeneratedSerializer
@Serializable(with = SocialProfileSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialProfile(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var photos: List<ProfilePhotoDTO>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var pinnedTrips: List<SocialProfileRecentTrip>? = null,
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val profileLevel: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileBackground: String? = null,
    val currentStreak: Int,
    val bestStreak: Int,
    val stats: SocialProfileStats,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val activeVehicle: SocialActiveVehicle? = null,
    val recentBadges: List<String>,
    val recentTrips: List<SocialProfileRecentTrip>,
    val followerCount: Int,
    val followingCount: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isFollowing: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bio: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val visibility: SocialProfileVisibility? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val finds: List<SocialFind>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isPlus: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var avatarFrame: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var hasPlus: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var profileXp: Int? = null,
)

// iOS: SocialDTOs.swift
@KeepGeneratedSerializer
@Serializable(with = SocialFindSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialFind(
    val secretId: String,
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val symbol: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val rarity: String? = null,
    val foundAt: Instant,
    @EncodeDefault
    val first: Boolean = false,
)

// iOS: SocialDTOs.swift
@KeepGeneratedSerializer
@Serializable(with = SocialProfileVisibilitySerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialProfileVisibility(
    @EncodeDefault
    val counters: Boolean = true,
    @EncodeDefault
    val stats: Boolean = true,
    @EncodeDefault
    val map: Boolean = true,
    @EncodeDefault
    val achievements: Boolean = true,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialSuggestedUser(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val profileLevel: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val matchReason: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val totalKm: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isPlus: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var avatarFrame: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialSuggestedResponse(
    val users: List<SocialSuggestedUser>,
)

// iOS: SocialDTOs.swift
enum class SuggestionMatchReason {
    sharedRegion,
    nearby,
    popular,
}

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class ProfileUpdateRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var bio: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var pinnedTripIds: List<String>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileBackground: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileLevel: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileXp: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val currentStreak: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val bestStreak: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val activeVehicleId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val language: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showOnPublicMap: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var isPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var countersPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var statsPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var mapPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var achievementsPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var avatarFrame: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var showPlusBadge: Boolean? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialBlockRequest(
    val targetAccountId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialBlockResponse(
    val blocked: Boolean,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class BlockedUser(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val profileLevel: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val blockedAt: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialBlockedListResponse(
    val users: List<BlockedUser>,
)

// iOS: SocialDTOs.swift
@Serializable
enum class ReportReason(val rawValue: String) {
    @SerialName("spam")
    spam("spam"),
    @SerialName("harassment")
    harassment("harassment"),
    @SerialName("hate")
    hate("hate"),
    @SerialName("nudity")
    nudity("nudity"),
    @SerialName("violence")
    violence("violence"),
    @SerialName("illegal")
    illegal("illegal"),
    @SerialName("impersonation")
    impersonation("impersonation"),
    @SerialName("other")
    other("other");

    companion object {
        fun fromRawValue(value: String): ReportReason? = entries.find { it.rawValue == value }
    }
}

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReportRequest(
    val targetType: String,
    val targetId: UUID,
    val reason: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notes: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReportResponse(
    val reported: Boolean,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReactionEntry(
    val user: SocialAuthor,
    val emoji: String,
    val createdAt: Instant,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialReactionsResponse(
    val reactions: List<SocialReactionEntry>,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialCommentCreateRequest(
    val tripId: UUID,
    val text: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var parentId: UUID? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialCommentCreateResponse(
    val id: UUID,
    val createdAt: Instant,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripCommentsRequest(
    val tripId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cursor: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripComment(
    val id: UUID,
    val user: SocialAuthor,
    val text: String,
    val createdAt: Instant,
    val isMine: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val parentId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val replyToName: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripCommentsResponse(
    val comments: List<TripComment>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialCommentDeleteRequest(
    val commentId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialCommentDeleteResponse(
    val deleted: Boolean,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTripRequest(
    val tripId: UUID,
    @Required
    var includeTrack: Boolean = false,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTrackPoint(
    val lat: Double,
    val lon: Double,
    val speed: Double,
    val t: Instant,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTripResponse(
    val item: SocialFeedTrip,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var track: List<SocialTrackPoint>? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTripPhotosRequest(
    val tripId: UUID,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTripPhoto(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val caption: String? = null,
    val timestamp: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val originalUrl: String? = null,
)

// iOS: SocialDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SocialTripPhotosResponse(
    val photos: List<SocialTripPhoto>,
)

// iOS: SocialDTOs.swift
enum class ProfileVisibilityBlock {
    counters,
    stats,
    map,
    achievements,
}

// iOS: SocialDTOs.swift
data class ProfileVisibilityFlags(
    var counters: Boolean,
    var stats: Boolean,
    var map: Boolean,
    var achievements: Boolean,
)
