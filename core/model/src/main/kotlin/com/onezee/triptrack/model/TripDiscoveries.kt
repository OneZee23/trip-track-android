// iOS: TripDiscoveries.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: TripDiscoveries.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class TripDiscoveries(
    val tripId: UUID,
    val newKm: Double,
    val newRegionIds: List<String>,
    val secrets: List<Discovery>,
    val riddles: List<Discovery>,
    val milestones: List<Discovery>,
)
