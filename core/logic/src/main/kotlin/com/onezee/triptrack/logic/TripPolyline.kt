package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID

// iOS: Trip.swift. The server stores iPhone's little-endian Float32 pairs verbatim.
fun Trip.Companion.encodePolyline(coordinates: List<GeoPoint>): ByteArray {
    val buffer = ByteBuffer.allocate(coordinates.size * 8).order(ByteOrder.LITTLE_ENDIAN)
    for (coordinate in coordinates) {
        buffer.putFloat(coordinate.latitude.toFloat())
        buffer.putFloat(coordinate.longitude.toFloat())
    }
    return buffer.array()
}

fun Trip.Companion.decodePolyline(data: ByteArray): List<GeoPoint> {
    if (data.size < 8 || data.size % 8 != 0) return emptyList()
    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    return List(data.size / 8) { GeoPoint(buffer.float.toDouble(), buffer.float.toDouble()) }
}

private val previewCache = object : LinkedHashMap<UUID, List<GeoPoint>>(200, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, List<GeoPoint>>): Boolean = size > 200
}

val Trip.previewCoordinates: List<GeoPoint>
    get() {
        val data = previewPolyline ?: return trackPoints.map { it.coordinate }
        return synchronized(previewCache) { previewCache.getOrPut(id) { Trip.decodePolyline(data) } }
    }

fun Trip.Companion.invalidatePreviewCache(tripId: UUID) {
    synchronized(previewCache) { previewCache.remove(tripId) }
}

// iOS: TripRepository.swift (TripPreviewRef). No track fallback: suggestions only read previews.
data class TripPreviewRef(val id: UUID, val startDate: Instant, val previewPolyline: ByteArray?) {
    val previewCoordinates: List<GeoPoint>
        get() = previewPolyline?.let { Trip.decodePolyline(it) } ?: emptyList()
}
