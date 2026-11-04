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
    fun map(
        trip: Trip, entity: TripEntity, photos: List<TripPhotoEntity>, zone: PrivacyZoneSpec?, now: Instant = Instant.now(),
        defaultEdgeMetres: Int? = null,
    ): TripSyncPayload {
        val measured = trip.measuredPoints
        val parts = trip.hiddenParts
        val resolution = TripHiddenPartsResolver.resolve(
            trip.trackPoints, RecordingBreaks.normalized(trip.recordingBreaks),
            parts?.startMetres ?: defaultEdgeMetres ?: 0, parts?.endMetres ?: defaultEdgeMetres ?: 0,
            parts?.sections.orEmpty(), zone?.let { it.centre to it.radius },
        )
        val points = trip.trackPoints.filterIndexed { index, _ -> !resolution.hidden[index] }
        val previewRuns = resolution.visibleRuns.map { range ->
            val run = range.map { trip.trackPoints[it].coordinate }
            GeometryUtils.simplifyRDP(run, GeometryUtils.previewEpsilon)
        }
        val previewCoordinates = previewRuns.flatten()
        val previewBreaks = previewRuns.dropLast(1).runningFold(0) { total, run -> total + run.size }.drop(1)
        val preview = previewCoordinates.takeIf { it.size >= 2 }?.let(Trip::encodePolyline)
        val shouldUseVisibleNumbers = parts?.numbersVisibleOnly == true
        val hiddenById = trip.trackPoints.mapIndexed { index, point -> point.id to resolution.hidden[index] }.toMap()
        val visibleMeasured = if (shouldUseVisibleNumbers) measured.filterNot { hiddenById[it.id] ?: true } else measured
        var driving = 0.0
        var stopped = 0.0
        val measuredRuns = if (shouldUseVisibleNumbers) resolution.visibleRuns.map { range ->
            val ids = range.map { trip.trackPoints[it].id }.toSet()
            measured.filter { it.id in ids }
        } else listOf(measured)
        measuredRuns.forEach { run ->
            for ((a, b) in run.zipWithNext()) {
                val elapsed = java.time.Duration.between(a.timestamp, b.timestamp)
                val dt = elapsed.seconds + elapsed.nano / 1e9
                if (a.recordingSegmentIndex != b.recordingSegmentIndex || dt <= 0 || dt > 60) continue
                if ((a.speed + b.speed) / 2 * 3.6 < 5) stopped += dt else driving += dt
            }
        }
        val maximumSpeed = if (shouldUseVisibleNumbers) visibleMeasured.maxOfOrNull { it.speed } ?: 0.0 else trip.maxSpeed
        val averageSpeed = when {
            shouldUseVisibleNumbers && visibleMeasured.isEmpty() -> 0.0
            shouldUseVisibleNumbers -> visibleMeasured.map { it.speed }.average()
            else -> trip.averageSpeed
        }
        val elevationGain = if (shouldUseVisibleNumbers) measuredRuns.sumOf { run -> run.zipWithNext().sumOf { (a, b) -> (b.altitude - a.altitude).coerceAtLeast(0.0) } } else trip.elevation
        val maxAltitude = if (shouldUseVisibleNumbers) visibleMeasured.maxOfOrNull { it.altitude } else measured.maxOfOrNull { it.altitude }
        fun hiddenAt(timestamp: Instant) = resolution.hiddenRanges.any { timestamp >= it.from && timestamp <= it.to }
        fun coordinate(lat: Double?, lon: Double?, timestamp: Instant): GeoPoint? {
            if (lat == null || lon == null) return null
            val point = GeoPoint(lat, lon)
            return point.takeUnless { hiddenAt(timestamp) || (zone != null && PrivacyZone.hides(point, zone)) }
        }
        return TripSyncPayload(
            id = trip.id, title = trip.title, description = trip.tripDescription, startDate = if (shouldUseVisibleNumbers) resolution.visibleStart ?: trip.startDate else trip.startDate,
            endDate = if (shouldUseVisibleNumbers) resolution.visibleEnd else trip.endDate,
            distance = if (shouldUseVisibleNumbers) resolution.visibleDistance else trip.distance,
            maxSpeed = if (shouldUseVisibleNumbers) maximumSpeed else trip.maxSpeed,
            averageSpeed = if (shouldUseVisibleNumbers) averageSpeed else trip.averageSpeed,
            fuelUsed = trip.fuelUsed, elevation = elevationGain, maxAltitude = maxAltitude,
            drivingTime = if (shouldUseVisibleNumbers) driving.toInt() else driving.toInt().takeIf { visibleMeasured.isNotEmpty() },
            stoppedTime = if (shouldUseVisibleNumbers) stopped.toInt() else stopped.toInt().takeIf { visibleMeasured.isNotEmpty() },
            region = trip.region, isPrivate = trip.isPrivate, vehicleId = trip.vehicleId, isTransfer = trip.isTransfer,
            fuelCurrency = trip.fuelCurrency, previewPolyline = preview?.let { Base64.getEncoder().encodeToString(it) },
            badgesJson = entity.badgesJSON, xpEarned = entity.xpEarned, conflictVersion = entity.conflictVersion,
            lastModifiedAt = entity.lastModifiedAt ?: now, serverCreatedAt = entity.serverCreatedAt,
            trackPoints = if (trip.source == TripOrigin.manual && entity.serverCreatedAt != null && trip.trackPoints.isEmpty()) null
                else points.map { TrackPointPayload(it.id, it.latitude, it.longitude, it.altitude, it.speed, it.course, it.horizontalAccuracy, it.timestamp, it.isInterpolated) },
            photos = photos.filterNot { photo ->
                TripHiddenPartsResolver.photoIsHidden(
                    photo.capturedAt, photo.exifLatitude?.let { lat -> photo.exifLongitude?.let { lon -> GeoPoint(lat, lon) } },
                    trip.trackPoints, resolution, zone?.let { it.centre to it.radius },
                )
            }.map { photo ->
                val exif = coordinate(photo.exifLatitude, photo.exifLongitude, photo.capturedAt ?: photo.timestamp)
                TripPhotoMetadataPayload(photo.id, photo.filename, photo.caption, photo.timestamp, photo.sortOrder.toInt(), photo.capturedAt, exif?.latitude, exif?.longitude)
            },
            checkpoints = trip.checkpoints.mapIndexed { index, checkpoint ->
                val point = coordinate(checkpoint.latitude, checkpoint.longitude, checkpoint.timestamp)
                TripCheckpointPayload(checkpoint.id, checkpoint.timestamp, point?.latitude, point?.longitude,
                    checkpoint.distanceFromStart, checkpoint.elapsedFromStart, checkpoint.name, checkpoint.photoId,
                    checkpoint.photoIds, checkpoint.placeId, index)
            },
            segments = trip.segments.map { TripSegmentPayload(it.id, it.fromCheckpointId, it.toCheckpointId, it.name) },
            source = trip.source, energyMode = trip.energyMode.rawValue, recordingBreaks = RecordingBreaks.normalized(trip.recordingBreaks),
            previewBreaks = previewBreaks, hiddenParts = parts, hiddenRanges = resolution.hiddenRanges,
        )
    }
}
