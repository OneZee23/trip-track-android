package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.UUID
import kotlin.test.*

// iOS: PolylineTests.swift
class PolylineTests {
    @Test fun testEncodeDecodeRoundtrip() {
        val original = listOf(GeoPoint(45.035, 38.975), GeoPoint(45.036, 38.976), GeoPoint(45.037, 38.977))
        val decoded = Trip.decodePolyline(Trip.encodePolyline(original))
        assertEquals(original.size, decoded.size)
        for ((a, b) in original.zip(decoded)) {
            assertNear(a.latitude, b.latitude, 0.001)
            assertNear(a.longitude, b.longitude, 0.001)
        }
    }
    @Test fun testEncodeDecodePreservesOrder() {
        val coords = (0 until 20).map { GeoPoint(45 + it * 0.01, 38 + it * 0.005) }
        val decoded = Trip.decodePolyline(Trip.encodePolyline(coords))
        assertEquals(20, decoded.size)
        assertTrue(decoded.zipWithNext().all { (a, b) -> b.latitude > a.latitude })
    }
    @Test fun testDecodeEmptyData() { assertTrue(Trip.decodePolyline(byteArrayOf()).isEmpty()) }
    @Test fun testDecodeInvalidData() { assertTrue(Trip.decodePolyline(byteArrayOf(1, 2, 3, 4, 5)).isEmpty()) }

    // Frozen by running the unchanged encode/decode methods from Trip.swift on Apple Swift.
    @Test fun bytesMatchIOSVectors() {
        val vectors = listOf(
            listOf(GeoPoint(45.035, 38.975), GeoPoint(45.036, 38.976), GeoPoint(45.037, 38.977)) to
                "d723344266e61b42dd2434426de71b42e325344273e81b42",
            listOf(GeoPoint(55.7558, 37.6173)) to "f0055f421d781642",
            listOf(GeoPoint(55.755826, 37.617299)) to "f7055f421d781642",
            listOf(GeoPoint(0.0, 0.0), GeoPoint(-0.0, -0.0), GeoPoint(-33.9249, -18.4241),
                GeoPoint(90.0, 180.0), GeoPoint(-90.0, -180.0)) to
                "0000000000000000000000800000008019b307c28f6493c10000b442000034430000b4c2000034c3",
        )
        for ((points, hex) in vectors) {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertContentEquals(bytes, Trip.encodePolyline(points))
            val decoded = Trip.decodePolyline(bytes)
            assertEquals(points.size, decoded.size)
            points.zip(decoded).forEach { (a, b) ->
                assertEquals(a.latitude.toFloat().toDouble().toRawBits(), b.latitude.toRawBits())
                assertEquals(a.longitude.toFloat().toDouble().toRawBits(), b.longitude.toRawBits())
            }
        }
    }
}

// iOS: TripPolylineCacheTests.swift
class TripPolylineCacheTests {
    @Test fun testEncodeEmptyArrayProducesEmptyData() { assertEquals(0, Trip.encodePolyline(emptyList()).size) }
    @Test fun testDecodeEmptyArrayRoundTrip() { assertTrue(Trip.decodePolyline(Trip.encodePolyline(emptyList())).isEmpty()) }
    @Test fun testSinglePointRoundTrip() {
        val original = GeoPoint(55.7558, 37.6173)
        val decoded = Trip.decodePolyline(Trip.encodePolyline(listOf(original)))
        assertEquals(1, decoded.size)
        assertNear(original.latitude, decoded[0].latitude, 1e-4)
        assertNear(original.longitude, decoded[0].longitude, 1e-4)
    }
    @Test fun testLargePolylineRoundTrip() {
        val original = (0 until 1000).map { GeoPoint(55 + it * 0.0001, 37 + it * 0.0001) }
        val data = Trip.encodePolyline(original)
        assertEquals(8000, data.size)
        val decoded = Trip.decodePolyline(data)
        assertEquals(1000, decoded.size)
        for (i in listOf(0, 500, 999)) {
            assertNear(original[i].latitude, decoded[i].latitude, 1e-4)
            assertNear(original[i].longitude, decoded[i].longitude, 1e-4)
        }
    }
    @Test fun testFloat32PrecisionAtRealLatLon() {
        val original = GeoPoint(55.755826, 37.617299)
        val decoded = Trip.decodePolyline(Trip.encodePolyline(listOf(original)))
        assertNear(original.latitude, decoded[0].latitude, 1e-5)
        assertNear(original.longitude, decoded[0].longitude, 1e-5)
    }
    @Test fun testDecodeOddByteCountRejected() { assertTrue(Trip.decodePolyline(byteArrayOf(1, 2, 3, 4, 5, 6, 7)).isEmpty()) }
    @Test fun testDecodeFourBytesRejected() { assertTrue(Trip.decodePolyline(byteArrayOf(1, 2, 3, 4)).isEmpty()) }
    @Test fun testPreviewCoordinatesUseCacheOnSecondCall() {
        val coords = listOf(GeoPoint(55.7, 37.6), GeoPoint(55.8, 37.7))
        val trip = Trip(previewPolyline = Trip.encodePolyline(coords))
        val first = trip.previewCoordinates
        val second = trip.previewCoordinates
        assertEquals(2, first.size)
        assertEquals(first.size, second.size)
        assertEquals(first[0].latitude, second[0].latitude)
    }
    @Test fun testInvalidatePreviewCacheForcesRecompute() {
        val coords = listOf(GeoPoint(55.7, 37.6), GeoPoint(55.8, 37.7), GeoPoint(55.9, 37.8))
        val id = UUID.randomUUID()
        val trip = Trip(id = id, previewPolyline = Trip.encodePolyline(coords))
        trip.previewCoordinates
        Trip.invalidatePreviewCache(id)
        val after = trip.previewCoordinates
        assertEquals(3, after.size)
        assertNear(55.9, after[2].latitude, 1e-4)
    }
    @Test fun testPreviewCoordinatesFallsBackToTrackPointsWhenNoPolyline() {
        val trip = Trip(trackPoints = listOf(TrackPoint(latitude = 55.7, longitude = 37.6), TrackPoint(latitude = 55.8, longitude = 37.7)))
        val coords = trip.previewCoordinates
        assertEquals(2, coords.size)
        assertEquals(55.7, coords[0].latitude)
        assertEquals(37.7, coords[1].longitude)
    }
}
