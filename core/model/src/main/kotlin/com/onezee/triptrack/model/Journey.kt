// iOS: Journey.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Journey.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class Journey(
    @Required
    val id: UUID = UUID.randomUUID(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var userId: UUID? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var title: String? = null,
    var startDate: Instant,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var endDate: Instant? = null,
    @Required
    var excludedTripIds: List<UUID> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var coverPhotoId: UUID? = null,
    @Required
    var isPrivate: Boolean = true,
    @Required
    var conflictVersion: Int = 1,
    @Required
    var lastModifiedAt: Instant = Instant.now(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var serverCreatedAt: Instant? = null,
)
