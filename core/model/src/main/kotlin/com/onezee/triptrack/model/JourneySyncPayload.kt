// iOS: JourneySyncPayload.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: JourneySyncPayload.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class JourneySyncPayload(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val title: String? = null,
    val startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val endDate: Instant? = null,
    val excludedTripIds: List<UUID>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val coverPhotoId: UUID? = null,
    val isPrivate: Boolean,
    val conflictVersion: Int,
    val lastModifiedAt: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var serverCreatedAt: Instant? = null,
)
