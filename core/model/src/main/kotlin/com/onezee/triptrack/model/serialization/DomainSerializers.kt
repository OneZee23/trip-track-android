// iOS: Vehicle.swift, Trip.swift, Discovery.swift, RiddleCatalog.swift, SecretCatalog.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

// iOS: Vehicle.swift (FuelCurrency.current is supplied by the caller)
internal class VehicleCurrencySerializer(val fuelCurrency: String) : KSerializer<Vehicle> by VehicleSerializer

object VehicleSerializer : IosObjectSerializer<Vehicle>({ Vehicle.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject {
        val fields = value.withoutNulls(setOf(
            "avatarStyle", "type", "plate", "plateVisible", "visibleToOthers", "stickers", "fuelCurrency",
            "dashboardUnits", "powertrain", "electricConsumption", "electricityPrice", "electricRangeKm",
            "modTags", "mods", "about", "make", "model", "year", "bodyType", "photosVisible", "mapVisible", "isArchived",
        )).toMutableMap()
        // The snapshot's reward odometer is derived locally and ignored by Swift's decoder.
        fields.remove("rewardOdometerKm")
        fields.putIfAbsent("fuelCurrency", JsonPrimitive(
            (json.serializersModule.getContextual(Vehicle::class) as? VehicleCurrencySerializer)?.fuelCurrency ?: "€",
        ))
        fields.tolerate("dashboardUnits", DashboardUnits.serializer(), json)
        fields.tolerate("powertrain", Powertrain.serializer(), json)
        for (key in listOf("drive2URL", "paintFinish", "catalogModelId", "catalogGenerationId", "catalogVariantId")) {
            fields.tolerate(key, String.serializer(), json)
        }
        fields.tolerate("modTags", ListSerializer(String.serializer()), json)
        fields.tolerate("mods", ListSerializer(VehicleMod.serializer()), json)
        return JsonObject(fields)
    }
}

object TripSerializer : IosObjectSerializer<Trip>({ Trip.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = value.withoutNulls(setOf(
        "checkpoints", "segments", "titleIsCustom", "isTransfer", "companions", "isOnServer", "source",
        "confirmation", "roadFillState", "energyMode", "recordingBreaks",
    ))
}

object DiscoverySerializer : IosObjectSerializer<Discovery>({ Discovery.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject {
        val fields = value.withoutNulls(setOf("verified")).toMutableMap()
        val kind = json.decodeFromJsonElement(DiscoveryKind.serializer(), value.getValue("kind"))
        val key = json.decodeFromJsonElement(String.serializer(), value.getValue("key"))
        fields["id"] = JsonPrimitive(Discovery.id(kind, key).toString().uppercase())
        fields["coordinate"] = buildJsonObject {
            put("latitude", value["latitude"] ?: throw SerializationException("Missing latitude"))
            put("longitude", value["longitude"] ?: throw SerializationException("Missing longitude"))
        }
        fields.remove("latitude")
        fields.remove("longitude")
        return JsonObject(fields)
    }

    override fun write(value: JsonObject): JsonObject = JsonObject(value.toMutableMap().apply {
        val point = remove("coordinate")!!.jsonObject
        put("latitude", point.getValue("latitude"))
        put("longitude", point.getValue("longitude"))
    })
}

object RiddleSerializer : IosObjectSerializer<Riddle>({ Riddle.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.withoutNulls(setOf("n")).toMutableMap().apply {
        val pair = value["c"] as? JsonArray ?: throw SerializationException("coordinate must be [lat, lon]")
        if (pair.size != 2) throw SerializationException("coordinate must be [lat, lon]")
        put("c", buildJsonObject {
            put("latitude", pair[0])
            put("longitude", pair[1])
        })
    })

    override fun write(value: JsonObject): JsonObject = JsonObject(value.toMutableMap().apply {
        val point = getValue("c").jsonObject
        put("c", JsonArray(listOf(point.getValue("latitude"), point.getValue("longitude"))))
    })
}

object SecretRecordSerializer : IosObjectSerializer<SecretRecord>({ SecretRecord.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.withoutNulls(setOf("reach", "polygon")).toMutableMap().apply {
        val symbol = value["symbol"] as? JsonPrimitive ?: throw SerializationException("Missing symbol")
        if (!symbol.isString) throw SerializationException("symbol must be a string")
        if (SealSymbol.fromRawValue(symbol.content) == null) put("symbol", JsonPrimitive(SealSymbol.generic.rawValue))
    })
}

// iOS: SecretsAPI.swift (Entry decodes absent symbols as generic)
object SecretCatalogResponseSerializer : IosObjectSerializer<SecretCatalogResponse>({ SecretCatalogResponse.generatedSerializer() }) {
    override fun read(value: JsonObject, json: Json): JsonObject = JsonObject(value.toMutableMap().apply {
        val secrets = get("secrets")
        if (secrets is JsonArray) {
            put("secrets", JsonArray(secrets.map { item ->
                JsonObject(item.jsonObject.toMutableMap().apply {
                    if (get("symbol") == null || get("symbol") == JsonNull) put("symbol", JsonPrimitive("seal"))
                })
            }))
        }
    })
}
