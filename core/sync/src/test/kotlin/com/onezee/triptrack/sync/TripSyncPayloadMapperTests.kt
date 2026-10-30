package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.NetworkJson
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Test

// iOS: Sync/TripSyncPayloadMapperTests.swift, HomePrivacyTrimTests.swift, VehicleEnergyWireTests.swift.
class TripSyncPayloadMapperTests {
    private val t0 = Instant.ofEpochSecond(1_760_000_000)
    private val home = GeoPoint(45.035, 38.975)
    private val zone = PrivacyZoneSpec(home, 500.0)
    private fun point(north: Double, seconds: Long, altitude: Double = 10.0, speed: Double = 20.0, accuracy: Double = 5.0, interpolated: Boolean = false) =
        TrackPoint(latitude = home.latitude + north / 111_320, longitude = home.longitude, altitude = altitude, speed = speed,
            horizontalAccuracy = accuracy, timestamp = t0.plusSeconds(seconds), isInterpolated = interpolated)
    private fun mapped(trip: Trip, zone: PrivacyZoneSpec? = null, photos: List<TripPhotoEntity> = emptyList(), entity: TripEntity = TripEntity(trip.id, startDate = trip.startDate)) =
        TripSyncPayloadMapper.map(trip, entity, photos, zone, t0)
    private fun points() = listOf(point(50.0, 0, 900.0), point(1500.0, 30), point(3000.0, 60, 20.0), point(120.0, 90, 950.0), point(2000.0, 120, 30.0))

    @Test fun testBasicTripMapping() {
        val id = UUID.randomUUID()
        val entity = TripEntity(id, startDate = Instant.ofEpochSecond(1_700_000_000), distance = 1234.5, conflictVersion = 3,
            lastModifiedAt = Instant.ofEpochSecond(1_700_001_000), badgesJSON = "[\"badge1\",\"badge2\"]", xpEarned = 150)
        val trip = Trip(id, entity.startDate, distance = 1234.5, maxSpeed = 20.0, averageSpeed = 10.0,
            trackPoints = listOf(TrackPoint(latitude = 50.0, longitude = 30.0, altitude = 0.0, speed = 10.0, course = 0.0, horizontalAccuracy = 5.0)),
            title = "Morning trip", fuelUsed = 0.5, elevation = 10.0, region = "Berlin", isPrivate = false,
            fuelCurrency = "€", previewPolyline = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
        val p = mapped(trip, entity = entity)
        assertEquals(id, p.id); assertEquals(1234.5, p.distance); assertEquals("Morning trip", p.title); assertEquals("Berlin", p.region)
        assertFalse(p.isPrivate); assertEquals("3q2+7w==", p.previewPolyline); assertEquals(entity.badgesJSON, p.badgesJson)
        assertEquals(150, p.xpEarned); assertEquals(3, p.conflictVersion); assertEquals(50.0, p.trackPoints!!.single().latitude)
    }
    @Test fun testTripWithNoOptionals() {
        val p = mapped(Trip(startDate = t0, isPrivate = false))
        assertNull(p.title); assertNull(p.previewPolyline); assertNull(p.badgesJson); assertEquals(emptyList(), p.trackPoints)
        assertNull(p.maxAltitude); assertNull(p.drivingTime)
    }
    @Test fun testWithTheZoneOnOnlyThePointsOutsideItLeaveThePhone() {
        val points = points(); val trip = Trip(startDate = t0, trackPoints = points, previewPolyline = Trip.encodePolyline(points.map { it.coordinate }))
        val p = mapped(trip, zone)
        assertEquals(3, p.trackPoints!!.size)
        assertTrue(p.trackPoints!!.all { !PrivacyZone.hides(GeoPoint(it.latitude, it.longitude), zone) })
        assertEquals(3, Trip.decodePolyline(Base64.getDecoder().decode(p.previewPolyline)).size)
        assertEquals(5, trip.trackPoints.size); assertEquals(5, Trip.decodePolyline(trip.previewPolyline!!).size)
        assertEquals(950.0, p.maxAltitude); assertEquals(120, p.drivingTime)
    }
    @Test fun testWithoutTheZoneTheWireIsExactlyWhatItWasBefore() {
        val points = points(); val preview = Trip.encodePolyline(points.map { it.coordinate }); val trip = Trip(trackPoints = points, previewPolyline = preview)
        val p = mapped(trip); assertEquals(points.size, p.trackPoints!!.size); assertEquals(Base64.getEncoder().encodeToString(preview), p.previewPolyline)
    }
    @Test fun testATripEntirelyInsideTheZoneKeepsItsNumbersAndLosesItsTrack() {
        val points = listOf(point(10.0, 0, 800.0), point(60.0, 30, 810.0), point(120.0, 60, 815.0))
        val trip = Trip(startDate = t0, endDate = t0.plusSeconds(60), distance = 430.0, maxSpeed = 12.0, averageSpeed = 7.0,
            trackPoints = points, fuelUsed = 0.4, elevation = 15.0, previewPolyline = Trip.encodePolyline(points.map { it.coordinate }))
        val whole = mapped(trip); val trimmed = mapped(trip, zone)
        assertEquals(emptyList(), trimmed.trackPoints); assertNull(trimmed.previewPolyline)
        assertEquals(whole.distance, trimmed.distance); assertEquals(whole.maxSpeed, trimmed.maxSpeed); assertEquals(whole.averageSpeed, trimmed.averageSpeed)
        assertEquals(whole.elevation, trimmed.elevation); assertEquals(whole.maxAltitude, trimmed.maxAltitude); assertEquals(whole.drivingTime, trimmed.drivingTime)
        assertEquals(whole.stoppedTime, trimmed.stoppedTime)
    }
    @Test fun testHidingATripDoesNotSendTheYardBackUp() { assertEquals(3, mapped(Trip(trackPoints = points(), isPrivate = true), zone).trackPoints!!.size) }
    @Test fun boundaryRemainsAndInteriorVisitsAreRemoved() {
        val p = point(500.0, 0); val coordinate = p.coordinate; val exact = PrivacyZoneSpec(home, GeometryUtils.haversineDistance(home, coordinate))
        assertFalse(PrivacyZone.hides(coordinate, exact)); assertEquals(3, mapped(Trip(trackPoints = points()), zone).trackPoints!!.size)
    }
    @Test fun oneRemainingPointSendsExplicitEmptyTrack() {
        val p = mapped(Trip(trackPoints = listOf(point(50.0, 0), point(1500.0, 30))), zone)
        val json = NetworkJson.wire.encodeToJsonElement(p).jsonObject
        assertEquals(JsonArray(emptyList()), json["trackPoints"]); assertFalse("previewPolyline" in json)
    }
    @Test fun hiddenExifAndCheckpointLoseBothCoordinatesButKeepIdentity() {
        val id = UUID.randomUUID(); val checkpoint = TripCheckpoint(timestamp = t0, latitude = home.latitude, longitude = home.longitude, distanceFromStart = 0.0, elapsedFromStart = 0.0, name = "Дом")
        val photo = TripPhotoEntity(id, filename = "photo.jpg", timestamp = t0, capturedAt = t0, exifLatitude = home.latitude, exifLongitude = home.longitude)
        val p = mapped(Trip(checkpoints = listOf(checkpoint)), zone, listOf(photo))
        assertEquals(checkpoint.id, p.checkpoints!!.single().id); assertEquals("Дом", p.checkpoints!!.single().name)
        assertNull(p.checkpoints!!.single().latitude); assertNull(p.checkpoints!!.single().longitude)
        val json = NetworkJson.wire.encodeToJsonElement(p).jsonObject
        val wirePhoto = json.getValue("photos").jsonArray.single().jsonObject
        assertFalse("exifLatitude" in wirePhoto); assertFalse("exifLongitude" in wirePhoto); assertEquals(t0, p.photos!!.single().capturedAt)
    }
    @Test fun halfAnExifPairIsOmittedEvenWithoutZone() {
        val p = mapped(Trip(), photos = listOf(TripPhotoEntity(UUID.randomUUID(), timestamp = t0, exifLatitude = 45.0)))
        assertNull(p.photos!!.single().exifLatitude); assertNull(p.photos!!.single().exifLongitude)
    }
    @Test fun movementSkipsCoarseFillBreaksAndLongGaps() {
        val trip = Trip(trackPoints = listOf(point(0.0, 0), point(1.0, 30), point(2.0, 60), point(3.0, 70, speed = -1.0, interpolated = true),
            point(4.0, 90, accuracy = 100.0), point(5.0, 180, speed = 0.0), point(6.0, 200, speed = 0.0)), recordingBreaks = listOf(t0.plusSeconds(60)))
        val p = mapped(trip); assertEquals(30, p.drivingTime); assertEquals(20, p.stoppedTime)
        assertFalse(NetworkJson.wire.encodeToString(p).contains("recordingSegmentIndex"))
    }
    @Test fun manualSummaryOmitsTrackUntilHydrated() {
        val trip = Trip(source = TripOrigin.manual); val entity = TripEntity(trip.id, startDate = t0, serverCreatedAt = t0)
        assertNull(mapped(trip, entity = entity).trackPoints)
        assertEquals(emptyList(), mapped(trip.copy(trackPoints = listOf(point(0.0, 0), point(1.0, 1))), zone, entity = entity).trackPoints)
    }
    @Test fun autoTravelsAsAnExplicitStringAndModesRoundTrip() {
        for (mode in TripEnergyMode.entries) {
            val json = NetworkJson.wire.encodeToJsonElement(mapped(Trip(energyMode = mode))).jsonObject
            assertEquals(mode.rawValue, json.getValue("energyMode").jsonPrimitive.content)
        }
    }
}
