// iOS: Services/Sync/SyncChunkBudget.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.TripSyncPayload

object SyncChunkBudget {
    const val maxBytes = 16 * 1024 * 1024
    const val maxTrips = 25
    const val bytesPerTrackPoint = 320
    const val bytesPerPhoto = 480
    const val bytesPerCheckpoint = 520
    const val bytesPerSegment = 200
    const val baseBytes = 8_192

    fun estimate(trackPoints: Int, photos: Int, checkpoints: Int, segments: Int): Long =
        baseBytes.toLong() + trackPoints.toLong() * bytesPerTrackPoint + photos.toLong() * bytesPerPhoto +
            checkpoints.toLong() * bytesPerCheckpoint + segments.toLong() * bytesPerSegment

    fun shouldFlush(currentTrips: Int, currentBytes: Long, nextBytes: Long): Boolean =
        currentTrips > 0 && (currentTrips >= maxTrips || currentBytes + nextBytes > maxBytes)

    fun pack(trips: List<TripSyncPayload>): List<List<TripSyncPayload>> {
        val chunks = mutableListOf<List<TripSyncPayload>>()
        var current = mutableListOf<TripSyncPayload>()
        var bytes = 0L
        for (trip in trips) {
            val next = trip.estimatedWireBytes
            if (shouldFlush(current.size, bytes, next)) {
                chunks.add(current)
                current = mutableListOf()
                bytes = 0
            }
            current.add(trip)
            bytes += next
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }
}

val TripSyncPayload.estimatedWireBytes: Long
    get() = SyncChunkBudget.estimate(trackPoints?.size ?: 0, photos?.size ?: 0, checkpoints?.size ?: 0, segments?.size ?: 0)
