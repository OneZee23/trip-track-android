// iOS: SecretCatalog.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.SecretRecordSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable

// iOS: SecretCatalog.swift
@KeepGeneratedSerializer
@Serializable(with = SecretRecordSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SecretRecord(
    val id: String,
    val hashes: List<UInt>,
    @EncodeDefault
    val reach: Double = 0.0,
    val symbol: SealSymbol,
    @EncodeDefault
    val polygon: Boolean = false,
)

// iOS: SecretCatalog.swift
object BundleSecretCatalog {

    // iOS: SecretCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Payload(
        val salt: String,
        val secrets: List<SecretRecord>,
    )
}
