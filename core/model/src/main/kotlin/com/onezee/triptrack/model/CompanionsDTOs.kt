// iOS: CompanionsDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.CompanionStatusSerializer
import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: CompanionsDTOs.swift
@Serializable(with = CompanionStatusSerializer::class)
enum class CompanionStatus(val rawValue: Int) {
    pending(0),
    accepted(1),
    declined(2);

    companion object {
        fun fromRawValue(value: Int): CompanionStatus? = entries.find { it.rawValue == value }
    }
}

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionItem(
    val accountId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val status: CompanionStatus,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionCandidate(
    @SerialName("id")
    val accountId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    val profileLevel: Int,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionDriverPreview(
    val accountId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionInvitePreview(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val driver: CompanionDriverPreview? = null,
    val startDate: Instant,
    val distance: Double,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val duration: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val region: String? = null,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsTripRequest(
    val tripId: UUID,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsInviteRequest(
    val tripId: UUID,
    val accountId: UUID,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsRespondRequest(
    val tripId: UUID,
    val accept: Boolean,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsRemoveRequest(
    val tripId: UUID,
    val accountId: UUID,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsCandidatesRequest(
    val tripId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val query: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cursor: String? = null,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsMyTripsRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val cursor: String? = null,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsListResponse(
    val items: List<CompanionItem>,
    val isOwnerView: Boolean,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsCandidatesResponse(
    val items: List<CompanionCandidate>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionInviteResult(
    val accountId: UUID,
    val status: CompanionStatus,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsInviteResponse(
    val companion: CompanionInviteResult,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsRespondResponse(
    val status: CompanionStatus,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsRemoveResponse(
    val ok: Boolean,
)

// iOS: CompanionsDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class CompanionsMyTripsResponse(
    val items: List<SocialFeedTrip>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nextCursor: String? = null,
)
