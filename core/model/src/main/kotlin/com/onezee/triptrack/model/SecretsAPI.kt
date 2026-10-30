// iOS: SecretsAPI.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.SecretCatalogResponseSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// iOS: SecretsAPI.swift
@KeepGeneratedSerializer
@Serializable(with = SecretCatalogResponseSerializer::class)
@JsonIgnoreUnknownKeys
data class SecretCatalogResponse(
    val version: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val salt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val unchanged: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val secrets: List<SecretRecord>? = null,
)

// iOS: SecretsAPI.swift
@Serializable
@JsonIgnoreUnknownKeys
data class SecretRevealRequest(
    val id: String,
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val tripId: UUID? = null,
)

// iOS: SecretsAPI.swift
@Serializable
@JsonIgnoreUnknownKeys
data class SecretRevealResponse(
    val id: String,
    val kind: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val title: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val story: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val symbol: String? = null,
    val verified: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val foundAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val finders: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val first: First? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rarity: String? = null,
) {
    // iOS: SecretsAPI.swift
    @Serializable
    @JsonIgnoreUnknownKeys
    data class First(
        @EncodeDefault(EncodeDefault.Mode.NEVER) val displayName: String? = null,
        val foundAt: Instant,
    )
}
