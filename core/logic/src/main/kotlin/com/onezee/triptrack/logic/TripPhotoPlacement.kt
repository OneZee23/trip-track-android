package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant

// iOS: TripPhotoPlacement.swift
val TripPhotoPlacement.Placed.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)

fun TripPhotoPlacement.place(photos: List<TripPhoto>, points: List<TrackPoint>): List<TripPhotoPlacement.Placed> =
    photos.mapNotNull { photo ->
        val own = photo.exifCoordinate
        if (own != null) TripPhotoPlacement.Placed(photo.id, photo.filename, own.latitude, own.longitude, TripPhotoPlacement.Placed.Source.photo)
        else {
            val index = photo.capturedAt?.let { nearestTimeIndex(it, points) } ?: return@mapNotNull null
            val point = points[index]
            TripPhotoPlacement.Placed(photo.id, photo.filename, point.latitude, point.longitude, TripPhotoPlacement.Placed.Source.track)
        }
    }

fun TripPhotoPlacement.prepare(
    photos: List<TripPhoto>, checkpoints: List<TripCheckpoint>, points: List<TrackPoint>, startDate: Instant,
): TripPhotoPlacement.Prepared {
    val links = TripCheckpointPhotos.link(checkpoints, photos, points)
    val attached = links.values.flatten().map { it.id }.toSet()
    val placed = place(photos, points).filter { it.id !in attached }
    if (placed.isEmpty()) return TripPhotoPlacement.Prepared(links, emptyList(), emptyMap())
    val index = TripRouteIndex(points, startDate)
    val photosById = photos.groupBy { it.id }.mapValues { it.value.first() }
    val fixes = placed.mapNotNull { item -> photosById[item.id]?.let { index.fix(it) }?.let { item.id to it } }.toMap()
    return TripPhotoPlacement.Prepared(links, placed, fixes)
}
