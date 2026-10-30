package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.UUID
import kotlin.math.abs

// iOS: TripPhoto.swift / TripCheckpoint.swift
val TripPhoto.exifCoordinate: GeoPoint?
    get() = exifLatitude?.let { lat -> exifLongitude?.let { lon -> GeoPoint(lat, lon) } }
val TripCheckpoint.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)
val TripCheckpoint.hasCoordinate: Boolean get() = latitude != 0.0 || longitude != 0.0

// iOS: TripCheckpointPhotos.swift
object TripCheckpointPhotos {
    const val timeWindow = 900.0
    const val distanceWindow = 300.0

    fun link(checkpoints: List<TripCheckpoint>, photos: List<TripPhoto>, points: List<TrackPoint>): Map<UUID, List<TripPhoto>> {
        if (checkpoints.isEmpty() || photos.isEmpty()) return emptyMap()
        val result = mutableMapOf<UUID, MutableList<TripPhoto>>()
        val taken = mutableSetOf<UUID>()
        // Manual attachments claim photos before automatic matches to neighbouring checkpoints.
        for (checkpoint in checkpoints) {
            for (id in listOfNotNull(checkpoint.photoId) + checkpoint.photoIds) {
                if (id in taken) continue
                val photo = photos.firstOrNull { it.id == id } ?: continue
                result.getOrPut(checkpoint.id) { mutableListOf() }.add(photo)
                taken.add(id)
            }
        }
        for (photo in photos) {
            if (photo.id in taken) continue
            val owner = nearestCheckpoint(photo, checkpoints, points) ?: continue
            result.getOrPut(owner.id) { mutableListOf() }.add(photo)
            taken.add(photo.id)
        }
        return result.mapValues { (id, list) ->
            val checkpoint = checkpoints.first { it.id == id }
            fun rank(photo: TripPhoto) = when {
                photo.id == checkpoint.photoId -> 0
                photo.id in checkpoint.photoIds -> 1
                else -> 2
            }
            list.sortedWith(compareBy<TripPhoto> { rank(it) }
                .thenBy { if (rank(it) == 1) checkpoint.photoIds.indexOf(it.id) else 0 }
                .thenBy { if (rank(it) == 2) it.capturedAt ?: it.timestamp else null })
        }
    }

    fun nearestCheckpoint(photo: TripPhoto, checkpoints: List<TripCheckpoint>, points: List<TrackPoint>): TripCheckpoint? {
        val captured = photo.capturedAt
        if (captured != null && points.isNotEmpty() &&
            captured >= points.first().timestamp.addingSeconds(-timeWindow) &&
            captured <= points.last().timestamp.addingSeconds(timeWindow)) {
            checkpoints.map { it to abs(it.timestamp.secondsSince(captured)) }
                .filter { it.second <= timeWindow }.minByOrNull { it.second }?.let { return it.first }
        }
        val coordinate = photo.exifCoordinate ?: return null
        // Zeroed coordinates mean a private location was removed; time remains usable.
        return checkpoints.filter { it.hasCoordinate }.map { it to coordinate.distanceFrom(it.coordinate) }
            .filter { it.second <= distanceWindow }.minByOrNull { it.second }?.first
    }
}
