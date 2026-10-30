// iOS: CodableRoundTripTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.ModelJson
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class CodableRoundTripTests {
    private val json = ModelJson.storage
    private val date = Instant.ofEpochSecond(1700000000)

    @Test fun trackPointRoundTrip() {
        val point = TrackPoint(latitude = 55.7558, longitude = 37.6173, altitude = 150.0,
            speed = 16.7, course = 90.0, horizontalAccuracy = 5.0, timestamp = date, isInterpolated = true)
        assertEquals(point, json.decodeFromString<TrackPoint>(json.encodeToString(point)))
    }

    @Test fun trackPointSegmentIndexIsDerived() {
        val point = TrackPoint(latitude = 55.7558, longitude = 37.6173, timestamp = date, recordingSegmentIndex = 4)
        val encoded = json.encodeToString(point)
        assertFalse(jsonObject(encoded).containsKey("recordingSegmentIndex"))
        assertEquals(0, json.decodeFromString<TrackPoint>(encoded).recordingSegmentIndex)
    }

    @Test fun tripPhotoRoundTrip() {
        val photo = TripPhoto(id = UUID.randomUUID(), filename = "abc123/photo.jpg", caption = "Nice view", timestamp = date)
        assertEquals(photo, json.decodeFromString<TripPhoto>(json.encodeToString(photo)))
    }

    @Test fun tripPhotoNilCaption() {
        val photo = TripPhoto(id = UUID.randomUUID(), filename = "test.jpg", caption = null, timestamp = date)
        assertNull(json.decodeFromString<TripPhoto>(json.encodeToString(photo)).caption)
        assertFalse(jsonObject(json.encodeToString(photo)).containsKey("caption"))
    }

    @Test fun vehicleRoundTrip() {
        val vehicle = Vehicle(name = "Tesla Model 3", avatarEmoji = "pixel_car_blue", odometerKm = 15000.0,
            level = 3, stickers = listOf(VehicleSticker.flag100km, VehicleSticker.mountain), createdAt = date,
            cityConsumption = 0.0, highwayConsumption = 0.0, fuelPrice = 0.0)
        assertEquals(vehicle, json.decodeFromString<Vehicle>(json.encodeToString(vehicle)))
    }

    @Test fun tripRoundTrip() {
        val point = TrackPoint(latitude = 55.7558, longitude = 37.6173, altitude = 150.0, speed = 16.7, timestamp = date)
        val photo = TripPhoto(UUID.randomUUID(), "trip/photo.jpg", "Test", date)
        // Float32 coordinate pair from Trip.encodePolyline; binary geometry belongs to core:logic.
        val polyline = byteArrayOf(-16, 5, 95, 66, 29, 120, 22, 66)
        val trip = Trip(startDate = date, endDate = Instant.ofEpochSecond(1700003600), distance = 45200.0,
            maxSpeed = 33.3, averageSpeed = 12.6, trackPoints = listOf(point), photos = listOf(photo),
            title = "Moscow → Tula", tripDescription = "Weekend trip", fuelUsed = 5.2, elevation = 320.0,
            region = "Tula Oblast", isPrivate = true, vehicleId = UUID.randomUUID(), fuelCurrency = "RUB",
            previewPolyline = polyline, earnedBadgeIds = listOf("first_trip", "century"))
        val decoded = json.decodeFromString<Trip>(json.encodeToString(trip))
        assertContentEquals(polyline, decoded.previewPolyline)
        assertEquals(trip.copy(previewPolyline = null), decoded.copy(previewPolyline = null))
    }

    @Test fun tripMinimalRoundTrip() {
        val trip = Trip(startDate = date)
        val decoded = json.decodeFromString<Trip>(json.encodeToString(trip))
        assertEquals(trip, decoded)
        assertNull(decoded.endDate)
        assertNull(decoded.title)
        assertTrue(decoded.trackPoints.isEmpty())
        assertTrue(decoded.photos.isEmpty())
    }

    @Test fun profileUpdateOmitsNilFields() {
        assertEquals(emptySet(), jsonObject(json.encodeToString(ProfileUpdateRequest())).keys)
    }

    @Test fun profileUpdateEncodesGlobeOptIn() {
        for (value in listOf(true, false)) {
            val objectValue = jsonObject(json.encodeToString(ProfileUpdateRequest(showOnPublicMap = value)))
            assertEquals(setOf("showOnPublicMap"), objectValue.keys)
            assertEquals(value, objectValue.getValue("showOnPublicMap").jsonPrimitive.boolean)
        }
    }

    @Test fun accountDTODecodesOptionalGlobeFlag() {
        val id = UUID.randomUUID()
        assertNull(json.decodeFromString<AccountDTO>("""{"id":"$id","avatarEmoji":"🏎️"}""").showOnPublicMap)
        assertEquals(true, json.decodeFromString<AccountDTO>("""{"id":"$id","avatarEmoji":"🏎️","showOnPublicMap":true}""").showOnPublicMap)
    }
}
