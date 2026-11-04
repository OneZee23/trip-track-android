@file:UseSerializers(UUIDSerializer::class, InstantSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripHiddenParts.swift.
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripHiddenParts(
    val startMetres: Int? = null,
    val endMetres: Int? = null,
    val sections: List<HiddenSection> = emptyList(),
    val numbersVisibleOnly: Boolean = false,
)

// iOS: HiddenSection.swift.
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class HiddenSection(val id: UUID, val from: Instant, val to: Instant)

// iOS: HiddenRange.swift.
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class HiddenRange(val from: Instant, val to: Instant)
