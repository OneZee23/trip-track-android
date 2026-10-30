// iOS: VehicleMod.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: VehicleMod.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class VehicleMod(
    @Required
    var id: UUID = UUID.randomUUID(),
    var title: String,
    var date: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var odometerKm: Double? = null,
    @Required
    var photoIds: List<UUID> = emptyList(),
) {
    companion object {
        const val limit = 100
        const val photoLimit = 6
    }
}
