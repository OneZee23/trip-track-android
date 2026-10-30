// iOS: SocialDTOs.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

object SocialProfileSerializer : IosObjectSerializer<SocialProfile>({ SocialProfile.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.toMutableMap().apply {
        tolerate("photos", ListSerializer(ProfilePhotoDTO.serializer()), json)
        tolerate("pinnedTrips", ListSerializer(SocialProfileRecentTrip.serializer()), json)
        val finds = get("finds")
        if (finds is JsonArray) {
            put("finds", JsonArray(finds.filter { item ->
                try {
                    json.decodeFromJsonElement(SocialFind.serializer(), item)
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }))
        }
    })
}

object SocialFindSerializer : IosObjectSerializer<SocialFind>({ SocialFind.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.withoutNulls(setOf("first")).toMutableMap().apply {
        tolerate("symbol", String.serializer(), json)
        tolerate("rarity", String.serializer(), json)
        tolerate("first", Boolean.serializer(), json)
    })
}

object SocialProfileVisibilitySerializer : IosObjectSerializer<SocialProfileVisibility>({ SocialProfileVisibility.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject =
        value.withoutNulls(setOf("counters", "stats", "map", "achievements"))
}

// iOS: GarageDTOs.swift
object PublicVehicleSerializer : IosObjectSerializer<PublicVehicle>({ PublicVehicle.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.toMutableMap().apply {
        for (key in listOf(
            "avatarStyle", "vehicleType", "make", "model", "bodyType", "about", "plate", "mainPhoto", "cardStyle",
            "drive2Url", "paintFinish", "catalogModelId", "catalogGenerationId", "catalogVariantId",
        )) tolerate(key, String.serializer(), json)
        tolerate("year", Int.serializer(), json)
        for (key in listOf("isArchived", "mapVisible", "photosVisible")) tolerate(key, Boolean.serializer(), json)
        for (key in listOf("soldAt", "createdAt")) tolerate(key, InstantSerializer, json)
        tolerate("photos", ListSerializer(PublicVehiclePhoto.serializer()), json)
        tolerate("tripPhotos", ListSerializer(PublicGarageTripPhoto.serializer()), json)
        tolerate("modTags", ListSerializer(String.serializer()), json)
        tolerate("mods", ListSerializer(PublicVehicleMod.serializer()), json)
    })
}
