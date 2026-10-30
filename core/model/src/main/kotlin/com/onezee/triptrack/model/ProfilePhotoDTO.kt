// iOS: ProfilePhotoDTO.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: ProfilePhotoDTO.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class ProfilePhotoDTO(
    val id: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var thumbnailUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var originalUrl: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var takenAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var sortOrder: Int? = null,
)
