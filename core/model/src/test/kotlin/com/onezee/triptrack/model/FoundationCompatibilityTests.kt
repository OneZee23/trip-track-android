// iOS: CodableRoundTripTests.swift, ISODate.swift, VehicleStateTests.swift, GarageDataTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class FoundationCompatibilityTests {
    @Test fun foundationDatesUseThe2001ReferenceEpoch() {
        assertEquals(Instant.parse("2001-01-01T00:00:00Z"), ModelJson.storage.decodeFromString(InstantSerializer, "0"))
        assertEquals(Instant.ofEpochSecond(1700000000), ModelJson.storage.decodeFromString(InstantSerializer, "721692800"))
        assertEquals(Instant.parse("2000-12-31T23:59:59.500Z"), ModelJson.storage.decodeFromString(InstantSerializer, "-0.5"))
        val encoded = ModelJson.storage.parseToJsonElement(
            ModelJson.storage.encodeToString(InstantSerializer, Instant.ofEpochSecond(1700000000)),
        ).jsonPrimitive
        assertFalse(encoded.isString)
        assertEquals(721692800.0, encoded.double)
    }

    @Test fun wireDatesStayUtcAndMillisecondPrecise() {
        val expected = Instant.ofEpochSecond(1777451615, 49000000)
        for (raw in listOf("2026-04-29T08:33:35.049Z", "2026-04-29T11:33:35.049+03:00", "2026-04-29T08:33:35.049+0000")) {
            assertEquals(expected, ModelJson.wire.decodeFromString(InstantSerializer, "\"$raw\""))
        }
        assertEquals(Instant.parse("2026-09-29T10:00:00Z"), ModelJson.wire.decodeFromString(InstantSerializer, "\"2026-09-29T10:00:00Z\""))
        assertEquals("\"2026-04-29T08:33:35.049Z\"", ModelJson.wire.encodeToString(InstantSerializer, expected))
    }

    @Test fun foundationDataUsesBase64RatherThanIntegerArrays() {
        val data = byteArrayOf(0, 1, 2, -1)
        assertEquals("\"AAEC/w==\"", ModelJson.storage.encodeToString(Base64Serializer, data))
        assertContentEquals(data, ModelJson.storage.decodeFromString(Base64Serializer, "\"AAEC/w==\""))
    }

    @Test fun uuidUsesFoundationCaseAndRejectsShortGroups() {
        val expected = UUID.fromString("7a1d0e8e-1111-4222-8333-944455556666")
        assertEquals(expected, ModelJson.storage.decodeFromString(UUIDSerializer, "\"7A1D0E8E-1111-4222-8333-944455556666\""))
        assertEquals("\"7A1D0E8E-1111-4222-8333-944455556666\"", ModelJson.storage.encodeToString(UUIDSerializer, expected))
        assertFailsWith<SerializationException> { ModelJson.storage.decodeFromString(UUIDSerializer, "\"1-1-1-1-1\"") }
    }

    @Test fun oldTripDefaultsDecodeWithoutNewFields() {
        val encoded = jsonObject(ModelJson.storage.encodeToString(Trip(startDate = Instant.ofEpochSecond(1700000000)))).filterKeys { it !in setOf(
            "checkpoints", "segments", "titleIsCustom", "isTransfer", "companions", "isOnServer", "source", "confirmation", "roadFillState", "energyMode", "recordingBreaks",
        ) }
        val value = ModelJson.storage.decodeFromString<Trip>(JsonObject(encoded).toString())
        assertEquals(TripOrigin.recorded, value.source)
        assertEquals(TripConfirmation.confirmed, value.confirmation)
        assertEquals(RoadFillState.unchecked, value.roadFillState)
        assertEquals(TripEnergyMode.auto, value.energyMode)
        assertEquals(emptyList(), value.checkpoints)
        assertEquals(emptyList(), value.segments)
        assertEquals(emptyList(), value.recordingBreaks)
        assertFalse(value.titleIsCustom)
        assertFalse(value.isTransfer)
        assertFalse(value.isOnServer)
    }

    @Test fun carCatalogKeepsFractionalHorsepower() {
        val raw = """{"schema":2,"makes":[{"id":"lada","name":"Lada","models":[{"id":"niva","name":"Niva","generations":[{"id":"1","name":"I","variants":[{"id":"1.7","powerHp":79.6,"displacementCc":1690}]}]}]}]}"""
        val catalog = ModelJson.storage.decodeFromString<CarCatalog.Document>(raw)
        val variant = catalog.makes.single().models.single().generations.single().variants.single()
        assertEquals(79.6, variant.powerHp)
        assertEquals(1690.0, variant.displacementCc)
        assertNull(variant.batteryKwh)
    }
}
