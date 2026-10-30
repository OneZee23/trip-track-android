// iOS: TripCompanion.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripCompanion.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripCompanion(
    val accountId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var displayName: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var avatarEmoji: String? = null,
    var status: CompanionStatus,
)
