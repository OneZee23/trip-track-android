// iOS: VehicleSyncPayload.swift, AuthDTOs.swift, PlusAPI.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

object VehicleSyncPayloadSerializer : IosObjectSerializer<VehicleSyncPayload>({ VehicleSyncPayload.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.toMutableMap().apply {
        tolerate("dashboardUnits", DashboardUnits.serializer(), json)
        tolerate("powertrain", Powertrain.serializer(), json)
        for (key in listOf("drive2Url", "paintFinish", "catalogModelId", "catalogGenerationId", "catalogVariantId")) {
            tolerate(key, String.serializer(), json)
        }
        tolerate("modTags", ListSerializer(String.serializer()), json)
        tolerate("mods", ListSerializer(VehicleMod.serializer()), json)
    })

    override fun decoded(value: VehicleSyncPayload, original: JsonObject): VehicleSyncPayload = value.copy(
        manualOdometerKnown = original.containsKey("manualOdometerKm"),
        soldAtKnown = original.containsKey("soldAt"),
    )

    override fun write(value: JsonObject): JsonObject = JsonObject(value.toMutableMap().apply {
        putIfAbsent("manualOdometerKm", JsonNull)
        putIfAbsent("soldAt", JsonNull)
    })
}

object MeResponseSerializer : IosObjectSerializer<MeResponse>({ MeResponse.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.toMutableMap().apply {
        val bio = value["bio"]
        put("bioProvided", JsonPrimitive(bio == JsonNull || (bio is JsonPrimitive && bio.isString)))
        tolerate("bio", String.serializer(), json)
        tolerate("photos", ListSerializer(ProfilePhotoDTO.serializer()), json)
        tolerate("pinnedTripIds", ListSerializer(UUIDSerializer), json)
    })
}

object PlusStatusResponseSerializer : IosObjectSerializer<PlusStatusResponse>({ PlusStatusResponse.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = value.withoutNulls(setOf("active", "isTrial"))
}
