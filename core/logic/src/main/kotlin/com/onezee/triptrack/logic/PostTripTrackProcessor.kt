package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*

// iOS: PostTripTrackProcessor.swift. Returns a snapshot; no storage or sync side effects.
object PostTripTrackProcessor {
    fun recalculateStats(trip: Trip): Trip {
        if (trip.trackPoints.size <= 1) return trip
        val trusted = trip.measuredPoints.sortedBy { it.timestamp }
        val samples = trusted.map { it.distanceSample() }
        val distance = TripDistanceGate.totalDistance(samples)
        val average = trip.endDate?.let {
            val elapsed = it.secondsSince(trip.startDate)
            if (elapsed > 0) distance / elapsed else 0.0
        } ?: trip.averageSpeed
        return trip.copy(distance = distance, maxSpeed = TripDistanceGate.maximumRecordedSpeed(samples), averageSpeed = average)
    }

    fun removeSpikePoints(points: List<TrackPoint>, recordingBreaks: List<java.time.Instant>): List<TrackPoint> {
        if (points.size < 3) return points
        return points.filterIndexed { index, current ->
            if (index == 0 || index == points.lastIndex) return@filterIndexed true
            val previous = points[index - 1]
            val next = points[index + 1]
            if (RecordingBreaks.crosses(previous.timestamp, next.timestamp, recordingBreaks)) return@filterIndexed true
            val incoming = current.coordinate.distanceFrom(previous.coordinate)
            val outgoing = next.coordinate.distanceFrom(current.coordinate)
            val direct = next.coordinate.distanceFrom(previous.coordinate)
            if (direct <= 5) return@filterIndexed true
            val ratio = (incoming + outgoing) / maxOf(direct, 1.0)
            val dt = current.timestamp.secondsSince(previous.timestamp)
            val implausible = dt > 0 && incoming / dt > 50 && incoming > 50
            !(ratio > 3.0 || (implausible && ratio > 1.5))
        }
    }

    fun fillOpenGaps(trip: Trip): Trip {
        val points = RecordingBreaks.annotate(trip.trackPoints, trip.recordingBreaks)
        val gaps = TrackGapFinder.openGaps(points.map { it.gapPoint() }).filter(GapFill::isFillable)
        if (gaps.isEmpty()) return trip
        val altitudes = points.filter { !it.isInterpolated }.associate { it.timestamp to it.altitude }
        val fills = gaps.flatMap { gap ->
            GapFill.resample(listOf(gap.from.coordinate, gap.to.coordinate), gap.from.timestamp, gap.to.timestamp,
                altitudes[gap.from.timestamp] ?: 0.0, altitudes[gap.to.timestamp] ?: 0.0)
        }
        return trip.copy(trackPoints = RecordingBreaks.annotate((points + fills).sortedBy { it.timestamp }, trip.recordingBreaks))
    }

    fun process(trip: Trip): Trip {
        if (trip.source == TripOrigin.manual) return trip.copy(roadFillState = RoadFillState.done)
        val originals = trip.trackPoints.filter { !it.isInterpolated }.sortedBy { it.timestamp }
        if (originals.size < 2) return trip.copy(roadFillState = RoadFillState.done)
        val cleaned = removeSpikePoints(originals, trip.recordingBreaks)
        val keptIds = cleaned.map { it.id }.toSet()
        val remaining = trip.trackPoints.filter { point -> point.isInterpolated || point.id in keptIds }
        val cleanedTrip = trip.copy(trackPoints = remaining)
        if (cleaned.size < 2) return cleanedTrip.copy(roadFillState = RoadFillState.done)
        val filled = fillOpenGaps(cleanedTrip)
        val state = if (filled.trackPoints.size > cleanedTrip.trackPoints.size) RoadFillState.pending else RoadFillState.done
        return recalculateStats(filled).copy(roadFillState = state)
    }
}

internal fun TrackPoint.gapPoint(): TrackGapFinder.Point = TrackGapFinder.Point(
    latitude, longitude, timestamp, isInterpolated, recordingSegmentIndex,
)
