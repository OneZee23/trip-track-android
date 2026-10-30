// iOS: AuthDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.MeResponseSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: AuthDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class LoginRequest(
    val identityToken: String,
    val localUserId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val deviceName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val nonce: String? = null,
)

// iOS: AuthDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class AccountDTO(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val email: String? = null,
    val avatarEmoji: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showOnPublicMap: Boolean? = null,
)

// iOS: AuthDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val account: AccountDTO,
    val isNewAccount: Boolean,
)

// iOS: AuthDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class RefreshRequest(
    val refreshToken: String,
)

// iOS: AuthDTOs.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)

// iOS: AuthDTOs.swift
@KeepGeneratedSerializer
@Serializable(with = MeResponseSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class MeResponse(
    @Required
    var bioProvided: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var bio: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var photos: List<ProfilePhotoDTO>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var pinnedTripIds: List<UUID>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val countersPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val statsPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val mapPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val achievementsPublic: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val id: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val email: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarEmoji: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileBackground: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val avatarFrame: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showPlusBadge: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val profileLevel: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var profileXp: Int? = null,
    val isPublic: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val showOnPublicMap: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notifyReactions: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notifyFollows: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notifyComments: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notifyWeeklyRecap: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val createdAt: String? = null,
)

// iOS: AuthDTOs.swift
@Serializable
class EmptyRequest

// iOS: AuthDTOs.swift
@Serializable
class EmptyResponse
