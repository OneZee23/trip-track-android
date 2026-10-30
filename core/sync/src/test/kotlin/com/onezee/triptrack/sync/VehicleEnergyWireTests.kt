package com.onezee.triptrack.sync

import com.onezee.triptrack.model.VehicleSyncPayload
import com.onezee.triptrack.network.NetworkJson
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Test

// iOS: VehicleEnergyWireTests.swift (DTO half); the store half remains in core:data.
class VehicleEnergyWireTests {
    private fun fuel() = VehicleSyncPayload(UUID.randomUUID(), "Corolla", "🚗", 0.0, level = 1,
        cityConsumption = 10.0, highwayConsumption = 6.0, fuelPrice = 56.0, conflictVersion = 0,
        lastModifiedAt = Instant.parse("2026-09-29T10:00:00Z"))
    @Test fun testFuelVehicleOmitsTheEnergyKeys() {
        val json = NetworkJson.wire.encodeToJsonElement(fuel()).jsonObject
        assertFalse("powertrain" in json); assertFalse("electricRangeKm" in json)
    }
    @Test fun testDecodingWithoutTheKeysMeansNoOpinion() {
        val decoded = NetworkJson.wire.decodeFromJsonElement<VehicleSyncPayload>(NetworkJson.wire.encodeToJsonElement(fuel()))
        assertNull(decoded.powertrain); assertNull(decoded.electricConsumption); assertNull(decoded.electricityPrice); assertNull(decoded.electricRangeKm)
    }
    @Test fun testUnknownPowertrainDoesNotBreakTheVehicle() {
        val json = NetworkJson.wire.encodeToJsonElement(fuel()).jsonObject
        val decoded = NetworkJson.wire.decodeFromJsonElement<VehicleSyncPayload>(JsonObject(json + mapOf("powertrain" to JsonPrimitive("hydrogen"), "electricRangeKm" to JsonPrimitive(50))))
        assertNull(decoded.powertrain); assertEquals(50.0, decoded.electricRangeKm)
    }
}
