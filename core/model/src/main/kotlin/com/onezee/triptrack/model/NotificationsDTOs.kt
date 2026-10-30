// iOS: NotificationsDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: NotificationsDTOs.swift
@Serializable
enum class NotificationKind(val rawValue: String) {
    @SerialName("reaction")
    reaction("reaction"),
    @SerialName("follow")
    follow("follow"),
    @SerialName("comment")
    comment("comment"),
    @SerialName("companion_invite")
    companionInvite("companion_invite"),
    @SerialName("companion_accepted")
    companionAccepted("companion_accepted"),
    @SerialName("achievement")
    achievement("achievement");

    companion object {
        fun fromRawValue(value: String): NotificationKind? = entries.find { it.rawValue == value }
    }
}

// iOS: NotificationsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class NotificationItem(
    val id: UUID,
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tripId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val tripTitle: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val emoji: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val commentId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val commentText: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isFollowing: Boolean? = null,
    val isRead: Boolean,
    val createdAt: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val actor: SocialAuthor? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var badgeId: String? = null,
)

// iOS: NotificationsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class NotificationsFeedRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val limit: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cursor: String? = null,
)

// iOS: NotificationsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class NotificationsFeedResponse(
    val items: List<NotificationItem>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)

// iOS: NotificationsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class NotificationsUnreadCountResponse(
    val count: Int,
)

// iOS: NotificationsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class NotificationsMarkReadRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val ids: List<UUID>? = null,
)
