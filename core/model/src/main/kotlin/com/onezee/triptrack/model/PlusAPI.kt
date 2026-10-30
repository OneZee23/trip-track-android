// iOS: PlusAPI.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.PlusStatusResponseSerializer
import java.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: PlusAPI.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PlusAttachRequest(
    val signedTransaction: String,
)

// iOS: PlusAPI.swift
@KeepGeneratedSerializer
@Serializable(with = PlusStatusResponseSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class PlusStatusResponse(
    @EncodeDefault
    val active: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val until: Instant? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val productId: String? = null,
    @EncodeDefault
    val isTrial: Boolean = false,
)
