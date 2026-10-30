package com.onezee.triptrack.sync

import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.db.entity.TripPhotoEntity
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import java.time.Instant
import java.util.Base64

// iOS: TripSyncPayload.swift (Zone). Supplied by the caller, never read from global settings.
data class PrivacyZoneSpec(val centre: GeoPoint, val radius: Double)

// iOS: PrivacyZone.swift. Only the outgoing mapper calls these functions.
internal object PrivacyZone {
    fun hides(point: GeoPoint, zone: PrivacyZoneSpec): Boolean = GeometryUtils.haversineDistance(point, zone.centre) < zone.radius
    fun trim(points: List<GeoPoint>, zone: PrivacyZoneSpec): List<GeoPoint> = points.filterNot { hides(it, zone) }
}

// iOS: TripSyncPayload.swift (init(trip:entity:zone:), wireTrack/Preview/Exif/Checkpoint).
object TripSyncPayloadMapper {
    fun map(trip: Trip, entity: TripEntity, photos: List<TripPhotoEntity>, zone: PrivacyZoneSpec?, now: Instant = Instant.now()): TripSyncPayload {
        val measured = trip.measuredPoints
        // The sync mapper intentionally has iOS's simple split, without the UI's distance gate.
        var driving = 0.0
        var stopped = 0.0
        for ((a, b) in measured.zipWithNext()) {
            val elapsed = java.time.Duration.between(a.timestamp, b.timestamp)
            val dt = elapsed.seconds + elapsed.nano / 1e9
            if (a.recordingSegmentIndex != b.recordingSegmentIndex || dt <= 0 || dt > 60) continue
            if ((a.speed + b.speed) / 2 * 3.6 < 5) stopped += dt else driving += dt
        }
        val points = if (zone == null) trip.trackPoints else trip.trackPoints.filterNot { PrivacyZone.hides(it.coordinate, zone) }.let {
            if (it.size >= 2) it else emptyList()
        }
        val preview = trip.previewPolyline?.let { data ->
            if (zone == null) data else PrivacyZone.trim(Trip.decodePolyline(data), zone).takeIf { it.size >= 2 }?.let { Trip.encodePolyline(it) }
        }
        fun coordinate(lat: Double?, lon: Double?): GeoPoint? {
            if (lat == null || lon == null) return null
            val point = GeoPoint(lat, lon)
            return point.takeUnless { zone != null && PrivacyZone.hides(point, zone) }
        }
        return TripSyncPayload(
            id = trip.id, title = trip.title, description = trip.tripDescription, startDate = trip.startDate,
            endDate = trip.endDate, distance = trip.distance, maxSpeed = trip.maxSpeed, averageSpeed = trip.averageSpeed,
            fuelUsed = trip.fuelUsed, elevation = trip.elevation, maxAltitude = measured.maxOfOrNull { it.altitude },
            drivingTime = driving.toInt().takeIf { measured.isNotEmpty() }, stoppedTime = stopped.toInt().takeIf { measured.isNotEmpty() },
            region = trip.region, isPrivate = trip.isPrivate, vehicleId = trip.vehicleId, isTransfer = trip.isTransfer,
            fuelCurrency = trip.fuelCurrency, previewPolyline = preview?.let { Base64.getEncoder().encodeToString(it) },
            badgesJson = entity.badgesJSON, xpEarned = entity.xpEarned, conflictVersion = entity.conflictVersion,
            lastModifiedAt = entity.lastModifiedAt ?: now, serverCreatedAt = entity.serverCreatedAt,
            trackPoints = if (trip.source == TripOrigin.manual && entity.serverCreatedAt != null && trip.trackPoints.isEmpty()) null
                else points.map { TrackPointPayload(it.id, it.latitude, it.longitude, it.altitude, it.speed, it.course, it.horizontalAccuracy, it.timestamp, it.isInterpolated) },
            photos = photos.map { photo ->
                val exif = coordinate(photo.exifLatitude, photo.exifLongitude)
                TripPhotoMetadataPayload(photo.id, photo.filename, photo.caption, photo.timestamp, photo.sortOrder.toInt(), photo.capturedAt, exif?.latitude, exif?.longitude)
            },
            checkpoints = trip.checkpoints.mapIndexed { index, checkpoint ->
                val point = coordinate(checkpoint.latitude, checkpoint.longitude)
                TripCheckpointPayload(checkpoint.id, checkpoint.timestamp, point?.latitude, point?.longitude,
                    checkpoint.distanceFromStart, checkpoint.elapsedFromStart, checkpoint.name, checkpoint.photoId,
                    checkpoint.photoIds, checkpoint.placeId, index)
            },
            segments = trip.segments.map { TripSegmentPayload(it.id, it.fromCheckpointId, it.toCheckpointId, it.name) },
            source = trip.source, energyMode = trip.energyMode.rawValue, recordingBreaks = RecordingBreaks.normalized(trip.recordingBreaks),
        )
    }
}
