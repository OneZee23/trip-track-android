// iOS: JSONEncoder/JSONDecoder, APIClient.swift, ISODate.swift
package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.Vehicle
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

object ModelJson {
    val storage = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun storage(fuelCurrency: String): Json = Json(storage) {
        serializersModule = SerializersModule {
            contextual(Vehicle::class, VehicleCurrencySerializer(fuelCurrency))
        }
    }

    val wire = Json(storage) {
        serializersModule = SerializersModule {
            contextual(Instant::class, IsoInstantSerializer)
        }
    }
}
