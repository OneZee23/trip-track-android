package com.onezee.triptrack.feature.trip

import com.onezee.triptrack.logic.TripHiddenPartsResolver
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Trip

fun Trip.filmingVersion(defaultEdgeMetres: Int?, home: Pair<GeoPoint, Double>?): Trip {
    if (trackPoints.isEmpty()) return this
    val resolution = TripHiddenPartsResolver.resolve(trackPoints, recordingBreaks,
        hiddenParts?.startMetres ?: defaultEdgeMetres ?: 0, hiddenParts?.endMetres ?: defaultEdgeMetres ?: 0,
        hiddenParts?.sections.orEmpty(), home)
    val visibleIndexes = resolution.visibleRuns.flatMap { it.toList() }
    val visiblePoints = visibleIndexes.map(trackPoints::get)
    val breaks = mutableListOf<Int>()
    var offset = 0
    resolution.visibleRuns.forEachIndexed { index, run -> if (index > 0) breaks += offset; offset += run.count() }
    val hiddenPhotos = photos.filter { photo -> TripHiddenPartsResolver.photoIsHidden(
        photo.capturedAt, photo.exifLatitude?.let { lat -> photo.exifLongitude?.let { lon -> GeoPoint(lat, lon) } },
        trackPoints, resolution, home,
    ) }.mapTo(mutableSetOf()) { it.id }
    val hiddenRanges = resolution.hiddenRanges
    return copy(
        trackPoints = visiblePoints,
        previewPolyline = resolution.previewPoints.takeIf { it.size > 1 }?.let(Trip::encodePolyline),
        previewBreaks = breaks,
        hiddenParts = null,
        photos = photos.filterNot { it.id in hiddenPhotos },
        checkpoints = checkpoints.filterNot { checkpoint -> hiddenRanges.any { checkpoint.timestamp >= it.from && checkpoint.timestamp <= it.to } },
        distance = if (hiddenParts?.numbersVisibleOnly == true) resolution.visibleDistance else distance,
    )
}

fun TripSummary.filmingVersion(defaultEdgeMetres: Int?, home: Pair<GeoPoint, Double>?): TripSummary {
    if (trackPoints.isEmpty()) return this
    val resolution = TripHiddenPartsResolver.resolve(trackPoints, recordingBreaks,
        hiddenParts?.startMetres ?: defaultEdgeMetres ?: 0, hiddenParts?.endMetres ?: defaultEdgeMetres ?: 0,
        hiddenParts?.sections.orEmpty(), home)
    val visibleIndexes = resolution.visibleRuns.flatMap { it.toList() }
    val points = visibleIndexes.map(trackPoints::get)
    val breaks = mutableListOf<Int>()
    var offset = 0
    resolution.visibleRuns.forEachIndexed { index, run -> if (index > 0) breaks += offset; offset += run.count() }
    val ranges = resolution.hiddenRanges
    val safePhotos = photos.filterNot { photo -> TripHiddenPartsResolver.photoIsHidden(
        photo.capturedAt, photo.exifCoordinate, trackPoints, resolution, home,
    ) }
    val start = points.firstOrNull()?.timestamp ?: startDate
    val end = points.lastOrNull()?.timestamp ?: endDate
    return copy(
        startDate = start,
        endDate = end,
        distance = if (hiddenParts?.numbersVisibleOnly == true) resolution.visibleDistance else distance,
        duration = if (hiddenParts?.numbersVisibleOnly == true && end != null) (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(0) / 1000.0 else duration,
        coordinates = points.map { it.coordinate },
        photos = safePhotos,
        hiddenParts = null,
        previewBreaks = breaks,
        trackPoints = points,
        recordingBreaks = emptyList(),
    )
}
