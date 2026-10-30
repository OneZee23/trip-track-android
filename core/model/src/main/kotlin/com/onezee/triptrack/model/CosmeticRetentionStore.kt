// iOS: CosmeticRetentionStore.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

// iOS: CosmeticRetentionStore.swift
object CosmeticRetentionStore {

    // iOS: CosmeticRetentionStore.swift
    data class Key(
        val rawValue: String,
        val kind: ProShowcaseKind,
    )

    // iOS: CosmeticRetentionStore.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Entry(
        var premium: String,
        var temporary: String,
    )

    // iOS: CosmeticRetentionStore.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Ledger(
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        var initialOwner: String? = null,
        @Required
        var owners: Map<String, String> = emptyMap(),
        @Required
        var pending: Map<String, Map<String, Entry>> = emptyMap(),
    )
}
