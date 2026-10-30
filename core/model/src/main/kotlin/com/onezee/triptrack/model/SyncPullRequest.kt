// iOS: SyncPullRequest.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import java.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: SyncPullRequest.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SyncPullRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lastSyncedAt: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val entityTypes: List<String>? = null,
)
