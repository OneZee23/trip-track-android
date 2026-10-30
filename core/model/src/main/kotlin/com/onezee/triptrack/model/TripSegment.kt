// iOS: TripSegment.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripSegment.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripSegment(
    @Required
    val id: UUID = UUID.randomUUID(),
    var fromCheckpointId: UUID,
    var toCheckpointId: UUID,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var name: String? = null,
) {
    companion object {
        const val maxPerTrip = 50
    }
}

// iOS: TripSegment.swift
object TripSegmentMetrics {

    // iOS: TripSegment.swift
    data class Resolved(
        val from: TripCheckpoint,
        val to: TripCheckpoint,
        val elapsed: Double,
        val metres: Double,
    )
}
