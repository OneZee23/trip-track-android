// iOS: TripOriginTests.swift, PowertrainTests.swift, DistanceUnitTests.swift,
// VehicleStateTests.swift, VehicleEnergyWireTests.swift, VehiclePassportWireTests.swift, GarageDataTests.swift,
// PlacesPresentationTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class StorageContractTests {
    private val json = ModelJson.storage
    private val wire = ModelJson.wire
    private val id = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val date = Instant.parse("2026-09-29T10:00:00Z")
    private val oldVehicle = """{"id":"$id","name":"Старая","avatarEmoji":"🚗","odometerKm":0,"level":1,"cityConsumption":10,"highwayConsumption":6,"fuelPrice":56,"conflictVersion":0,"lastModifiedAt":"2026-09-29T10:00:00Z"}"""

    @Test fun tripOriginUnknownStringsDecodeAsRecorded() {
        for (raw in listOf("future-source", "", "MANUAL", "Recorded")) {
            assertEquals(TripOrigin.recorded, json.decodeFromString<TripOrigin>(json.encodeToString(raw)))
        }
    }

    @Test fun tripOriginKnownValuesRoundTrip() {
        for (value in TripOrigin.entries) assertEquals(value, json.decodeFromString<TripOrigin>(json.encodeToString(value)))
        assertEquals(TripOrigin.recorded, Trip(startDate = date).source)
    }

    @Test fun rawValueParsingIsCaseSensitive() {
        assertEquals("km", DistanceUnit.km.rawValue)
        assertEquals("miles", DistanceUnit.miles.rawValue)
        assertNull(DistanceUnit.fromRawValue("KM"))
        assertNull(DistanceUnit.fromRawValue("mi"))
        for (raw in listOf("kilometres", "", "MILES")) assertNull(DistanceUnit.fromRawValue(raw))
        assertEquals("distanceUnit", DistanceUnit.storageKey)
        assertEquals(1609.344, DistanceUnit.metresPerMile)
        assertEquals(0.3048, DistanceUnit.metresPerFoot)
        assertEquals(listOf("fuel", "electric", "pluginHybrid"), Powertrain.entries.map { it.rawValue })
        for (raw in listOf(null, "", "hydrogen", "FUEL")) assertNull(Powertrain.parse(raw))
        for (raw in listOf(null, "", "imperial_uk")) assertNull(DashboardUnits.parse(raw))
        assertEquals(DashboardUnits.imperial, DashboardUnits.parse("imperial"))
        assertEquals(DashboardUnits.metric, DashboardUnits.parse(" METRIC "))
        assertEquals(Powertrain.pluginHybrid, Powertrain.parse("pluginHybrid"))
    }

    @Test fun integerEnumContractsStayNumeric() {
        assertEquals(listOf(0, 1, 2), SyncStatus.entries.map { it.rawValue.toInt() })
        assertEquals(listOf(0, 1, 2, 3), PhotoUploadStatus.entries.map { it.rawValue.toInt() })
        assertEquals("1", json.encodeToString(CompanionStatus.accepted))
        assertEquals(CompanionStatus.declined, json.decodeFromString<CompanionStatus>("2"))
        assertEquals("500", json.encodeToString(HomeSettings.Radius.block))
        assertFailsWith<SerializationException> { json.decodeFromString<CompanionStatus>("3") }
        assertFailsWith<SerializationException> { json.decodeFromString<HomeSettings.Radius>("300") }
    }

    @Test fun placesSortKeepsItsIosRawValues() {
        assertEquals(listOf("recent", "frequent", "name"), PlacesSort.entries.map { it.rawValue })
        assertEquals(PlacesSort.byName, PlacesSort.fromRawValue("name"))
        assertNull(PlacesSort.fromRawValue("byName"))
        for (sort in PlacesSort.entries) {
            assertEquals(JsonPrimitive(sort.rawValue), json.parseToJsonElement(json.encodeToString(sort)))
            assertEquals(sort, json.decodeFromString<PlacesSort>(json.encodeToString(sort)))
        }
    }

    @Test fun tripEnergyModeToleratesFutureStrings() {
        for (raw in listOf("", "regenerative", "whatever", "ELECTRIC")) {
            assertEquals(TripEnergyMode.auto, json.decodeFromString<TripEnergyMode>(json.encodeToString(raw)))
        }
        assertEquals(TripEnergyMode.auto, TripEnergyMode.parse(null))
        assertNull(TripEnergyMode.auto.stored)
        assertEquals("electric", TripEnergyMode.electric.stored)
        assertEquals("fuel", TripEnergyMode.fuel.stored)
        for (value in TripEnergyMode.entries) assertEquals(value, json.decodeFromString<TripEnergyMode>(json.encodeToString(value)))
    }

    @Test fun oldVehicleDefaultsRemainDistinct() {
        val value = Vehicle(id = id, name = "Нива", createdAt = date)
        val raw = jsonObject(json.encodeToString(value)).filterKeys { it in setOf(
            "id", "name", "avatarEmoji", "odometerKm", "level", "createdAt", "cityConsumption", "highwayConsumption", "fuelPrice",
        ) }
        val decoded = json.decodeFromString<Vehicle>(JsonObject(raw).toString())
        assertEquals("car", decoded.avatarStyle)
        assertEquals(VehicleType.car, decoded.type)
        assertFalse(decoded.plateVisible)
        assertTrue(decoded.visibleToOthers)
        assertFalse(decoded.photosVisible)
        assertTrue(decoded.mapVisible)
        assertFalse(decoded.isArchived)
        assertEquals(Powertrain.fuel, decoded.powertrain)
        assertEquals(DashboardUnits.app, decoded.dashboardUnits)
        assertEquals(0.0, decoded.electricConsumption)
        assertNull(decoded.manualOdometerKm)
        assertNull(decoded.soldAt)
        assertTrue(decoded.mods.isEmpty())
    }

    @Test fun vehicleCurrencyDefaultIsAnExplicitSetting() {
        val value = Vehicle(id = id, createdAt = date)
        val fields = jsonObject(json.encodeToString(value))
        val configured = ModelJson.storage(fuelCurrency = "₽")
        val other = ModelJson.storage(fuelCurrency = "£")
        for (currency in listOf(null, JsonNull)) {
            val raw = JsonObject(fields.toMutableMap().apply {
                if (currency == null) remove("fuelCurrency") else put("fuelCurrency", currency)
            }).toString()
            assertEquals("₽", configured.decodeFromString<Vehicle>(raw).fuelCurrency)
            assertEquals("£", other.decodeFromString<Vehicle>(raw).fuelCurrency)
            assertEquals("€", json.decodeFromString<Vehicle>(raw).fuelCurrency)
            assertEquals("₽", configured.decodeFromString<List<Vehicle>>("[$raw]").single().fuelCurrency)
        }
        val stored = value.copy(fuelCurrency = "$")
        val raw = json.encodeToString(stored)
        assertEquals(stored, configured.decodeFromString<Vehicle>(raw))
        assertEquals(jsonObject(raw), jsonObject(configured.encodeToString(stored)))
    }

    @Test fun futureVehicleEnumsAndMalformedGarageFieldsDoNotBreakSnapshot() {
        val raw = jsonObject(json.encodeToString(Vehicle(id = id, createdAt = date))).toMutableMap().apply {
            put("dashboardUnits", JsonPrimitive(42))
            put("powertrain", JsonPrimitive("hydrogen"))
            put("modTags", JsonPrimitive(false))
            put("mods", JsonPrimitive("future"))
            put("paintFinish", JsonPrimitive("future"))
            put("catalogVariantId", JsonPrimitive(42))
        }
        val decoded = json.decodeFromString<Vehicle>(JsonObject(raw).toString())
        assertEquals(DashboardUnits.app, decoded.dashboardUnits)
        assertEquals(Powertrain.fuel, decoded.powertrain)
        assertTrue(decoded.modTags.isEmpty())
        assertTrue(decoded.mods.isEmpty())
        assertEquals("future", decoded.paintFinish)
        assertNull(decoded.catalogVariantId)
    }

    @Test fun syncPayloadDistinguishesAbsentNullAndValue() {
        val absent = wire.decodeFromString<VehicleSyncPayload>(oldVehicle)
        assertFalse(absent.manualOdometerKnown)
        assertFalse(absent.soldAtKnown)
        val clear = wire.decodeFromString<VehicleSyncPayload>(oldVehicle.dropLast(1) + ",\"manualOdometerKm\":null,\"soldAt\":null}")
        assertTrue(clear.manualOdometerKnown)
        assertTrue(clear.soldAtKnown)
        assertNull(clear.manualOdometerKm)
        assertNull(clear.soldAt)
        val value = wire.decodeFromString<VehicleSyncPayload>(oldVehicle.dropLast(1) + ",\"manualOdometerKm\":142000,\"soldAt\":\"2026-09-29T10:00:00Z\"}")
        assertEquals(142000.0, value.manualOdometerKm)
        assertEquals(date, value.soldAt)
        assertTrue(value.manualOdometerKnown)
        assertTrue(value.soldAtKnown)
    }

    @Test fun syncPayloadAlwaysEncodesExplicitClears() {
        val encoded = jsonObject(wire.encodeToString(wire.decodeFromString<VehicleSyncPayload>(oldVehicle)))
        assertEquals(JsonNull, encoded["manualOdometerKm"])
        assertEquals(JsonNull, encoded["soldAt"])
        assertFalse(encoded.containsKey("manualOdometerKnown"))
        assertFalse(encoded.containsKey("soldAtKnown"))
        assertFalse(encoded.containsKey("powertrain"))
        assertFalse(encoded.containsKey("about"))
    }

    @Test fun unknownSyncEnumsMeanNoOpinionAndKeepOtherFields() {
        for (raw in listOf("\"hydrogen\"", "42", "{}", "null")) {
            val decoded = wire.decodeFromString<VehicleSyncPayload>(oldVehicle.dropLast(1) + ",\"powertrain\":$raw,\"dashboardUnits\":$raw,\"electricRangeKm\":50}")
            assertNull(decoded.powertrain)
            assertNull(decoded.dashboardUnits)
            assertEquals(50.0, decoded.electricRangeKm)
        }
    }

    @Test fun allHybridKeysReachTheWire() {
        val payload = wire.decodeFromString<VehicleSyncPayload>(oldVehicle).copy(powertrain = Powertrain.pluginHybrid,
            electricConsumption = 18.0, electricityPrice = 8.0, electricRangeKm = 50.0)
        val encoded = jsonObject(wire.encodeToString(payload))
        assertEquals("pluginHybrid", encoded["powertrain"]?.jsonPrimitive?.content)
        assertEquals(18.0, encoded["electricConsumption"]?.jsonPrimitive?.double)
        assertEquals(8.0, encoded["electricityPrice"]?.jsonPrimitive?.double)
        assertEquals(50.0, encoded["electricRangeKm"]?.jsonPrimitive?.double)
    }

    @Test fun garageWireRoundTripPreservesSilenceAndClear() {
        val absent = wire.decodeFromString<VehicleSyncPayload>(oldVehicle)
        for (key in listOf("drive2Url", "modTags", "paintFinish", "mods", "catalogModelId", "catalogGenerationId", "catalogVariantId")) {
            assertFalse(jsonObject(wire.encodeToString(absent)).containsKey(key))
        }
        val mod = VehicleMod(id = id, title = "CarPlay", date = "2026-10-20", odometerKm = 142000.0, photoIds = listOf(id))
        val updated = absent.copy(drive2Url = "https://www.drive2.ru/r/vw/polo/123/", modTags = listOf("CarPlay"),
            paintFinish = "matte", mods = listOf(mod), catalogModelId = "vw-polo", catalogGenerationId = "polo-v", catalogVariantId = "1.6-at")
        val back = wire.decodeFromString<VehicleSyncPayload>(wire.encodeToString(updated))
        assertEquals(updated.copy(manualOdometerKnown = true, soldAtKnown = true), back)
        val cleared = absent.copy(drive2Url = "", modTags = emptyList(), mods = emptyList(), catalogModelId = "", paintFinish = "gloss")
        val encoded = jsonObject(wire.encodeToString(cleared))
        assertEquals(JsonArray(emptyList()), encoded["mods"])
        assertEquals(JsonArray(emptyList()), encoded["modTags"])
        assertEquals(JsonPrimitive(""), encoded["drive2Url"])
    }

    @Test fun publicVehicleToleratesUnknownNewFields() {
        val raw = """{"id":"$id","name":"Car","avatarEmoji":"🚗","level":1,"odometerKm":0,"paintFinish":"future","mods":false,"modTags":42,"catalogVariantId":false}"""
        val value = json.decodeFromString<PublicVehicle>(raw)
        assertEquals("future", value.paintFinish)
        assertNull(value.mods)
        assertNull(value.modTags)
        assertNull(value.catalogVariantId)
    }

    @Test fun garageModsRoundTrip() {
        val a = VehicleMod(id = UUID.fromString("00000000-0000-4000-8000-000000000001"), title = "CarPlay",
            date = "2025-08-18", odometerKm = 91200.0, photoIds = listOf(UUID.randomUUID()))
        val b = VehicleMod(title = "Шумка", date = "2026-03-12", odometerKm = null)
        val c = VehicleMod(id = UUID.fromString("00000000-0000-4000-8000-000000000002"), title = "Шины",
            date = a.date, odometerKm = 93400.0)
        val values = listOf(a, b, c)
        assertEquals(values, json.decodeFromString<List<VehicleMod>>(json.encodeToString(values)))
    }

    @Test fun requiredOldFieldsStillRejectBrokenRecords() {
        assertFailsWith<SerializationException> { json.decodeFromString<Vehicle>("""{"name":"A"}""") }
        assertFailsWith<SerializationException> { json.decodeFromString<TrackPoint>("""{"latitude":0,"longitude":0}""") }
        assertFailsWith<SerializationException> { wire.decodeFromString<VehicleSyncPayload>(oldVehicle.replace("\"fuelPrice\":56,", "")) }
    }
    @Test fun journeyRoundTripKeepsDateWindowAndExclusions() {
        val value = Journey(id = id, title = "Грузия", startDate = date, excludedTripIds = listOf(id), lastModifiedAt = date)
        assertEquals(value, json.decodeFromString<Journey>(json.encodeToString(value)))
        assertNull(value.endDate)
        assertTrue(value.isPrivate)
        assertEquals(1, value.conflictVersion)
    }

    @Test fun checkpointAndSegmentRoundTripKeepPhotoAndPlaceLinks() {
        val checkpoint = TripCheckpoint(id = id, timestamp = date, latitude = 44.0, longitude = 37.0,
            distanceFromStart = 45200.0, elapsedFromStart = 3600.0, photoId = id, photoIds = listOf(id), placeId = id)
        assertEquals(checkpoint, json.decodeFromString<TripCheckpoint>(json.encodeToString(checkpoint)))
        val segment = TripSegment(id = id, fromCheckpointId = id, toCheckpointId = UUID.randomUUID(), name = "До моря")
        assertEquals(segment, json.decodeFromString<TripSegment>(json.encodeToString(segment)))
    }

    @Test fun homeSettingsStoreRadiusInMetres() {
        val value = HomeSettings(latitude = 44.0, longitude = 37.0, radius = HomeSettings.Radius.district)
        val raw = json.encodeToString(value)
        assertEquals(JsonPrimitive(1000), jsonObject(raw)["radius"])
        assertEquals(value, json.decodeFromString<HomeSettings>(raw))
        assertTrue(value.showsOnMap)
        assertFalse(value.trimsPublicTracks)
    }

    @Test fun transferAndRawFutureGarageValuesSurvive() {
        val trip = Trip(startDate = date, isTransfer = true)
        assertTrue(json.decodeFromString<Trip>(json.encodeToString(trip)).isTransfer)
        val vehicle = Vehicle(id = id, createdAt = date, paintFinish = "future", cardStyle = "future")
        val decoded = json.decodeFromString<Vehicle>(json.encodeToString(vehicle))
        assertEquals("future", decoded.paintFinish)
        assertEquals("future", decoded.cardStyle)
    }

}
