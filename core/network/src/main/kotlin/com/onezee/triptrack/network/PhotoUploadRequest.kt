// iOS: Services/Sync/R2PhotoStorage.swift
package com.onezee.triptrack.network

import java.time.Instant
import java.util.UUID
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import kotlinx.serialization.json.JsonPrimitive

private fun wireDate(date: Instant): String =
    (NetworkJson.wire.encodeToJsonElement(IsoInstantSerializer, date) as JsonPrimitive).content

// iOS: R2PhotoStorage.swift (upload trip photo)
data class TripPhotoUploadRequest(val tripId: UUID, val photoId: UUID, val type: String, val timestamp: Instant, val caption: String? = null) {
    fun fields(): List<Pair<String, String>> = buildList {
        add("tripId" to tripId.toString()); add("photoId" to photoId.toString())
        add("type" to type); add("timestamp" to wireDate(timestamp))
        caption?.let { add("caption" to it) }
    }
}

// iOS: R2PhotoStorage.swift (uploadVehicle)
data class VehiclePhotoUploadRequest(val vehicleId: UUID, val photoId: UUID, val type: String, val isMain: Boolean, val takenAt: Instant) {
    fun fields(): List<Pair<String, String>> = listOf("vehicleId" to vehicleId.toString(), "photoId" to photoId.toString(),
        "type" to type, "isMain" to isMain.toString(), "takenAt" to wireDate(takenAt))
}

// iOS: R2PhotoStorage.swift (uploadProfile)
data class ProfilePhotoUploadRequest(val photoId: UUID, val type: String, val takenAt: Instant? = null) {
    fun fields(): List<Pair<String, String>> = buildList {
        add("photoId" to photoId.toString()); add("type" to type)
        takenAt?.let { add("takenAt" to wireDate(it)) }
    }
}
