package com.onezee.triptrack.location.recording

import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.logic.RecordingBreaks
import com.onezee.triptrack.logic.countsForDistance
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.logic.maximumRecordedSpeed
import com.onezee.triptrack.logic.totalDistance
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripDistanceGate
import java.time.Duration
import java.time.Instant

/** Seconds from [from] to [to], negative when [to] is earlier. */
internal fun secondsBetween(from: Instant, to: Instant): Double {
    val duration = Duration.between(from, to)
    return duration.seconds + duration.nano / 1e9
}

internal fun Instant.plusSecondsDouble(seconds: Double): Instant =
    plusNanos((seconds * 1e9).toLong())

// iOS: TripManager.swift, recordingEndDate / trimmedEndDate / finalRecordingStats / generatePreviewPolyline.
object RecordingEnd {
    /** ~7.2 km/h: clearly driving, not parking. */
    const val movingThresholdMs = 2.0
    /** Short stops at lights and car parks stay in the trip; only a longer still tail is cut. */
    const val minTailToTrim = 60.0

    /**
     * The trimmed tail first (it comes from real point times), then the caller's hint (the moment the
     * odometer last moved, from an auto-stop), then now. Clamped to the start: a stale hint once
     * produced a trip that ended before it began.
     */
    fun endDate(points: List<TrackPoint>, startDate: Instant, suggested: Instant?, now: Instant): Instant {
        val proposed = trimmedEndDate(points) ?: suggested ?: now
        return maxOf(proposed, startDate)
    }

    /** The last moment where a point AND its predecessor both moved, if the still tail after it is a minute or more. */
    fun trimmedEndDate(points: List<TrackPoint>): Instant? {
        if (points.size <= 2) return null
        var lastMoving: Int? = null
        for (i in points.lastIndex downTo 1) {
            if (points[i].speed > movingThresholdMs && points[i - 1].speed > movingThresholdMs) {
                lastMoving = i
                break
            }
        }
        val index = lastMoving ?: return null
        val lastMovingAt = points[index].timestamp
        return if (secondsBetween(lastMovingAt, points.last().timestamp) >= minTailToTrim) lastMovingAt else null
    }

    data class Stats(val distance: Double, val maxSpeed: Double)

    /**
     * The odometer of the trip: the five-metre step over trusted points, per recording segment. It
     * OVERWRITES what the live recording summed, so it must be the same door as every other count.
     * One point or none keeps the live numbers.
     */
    fun finalStats(points: List<TrackPoint>, breaks: List<Instant>, liveDistance: Double, liveMaxSpeed: Double): Stats {
        if (points.size <= 1) return Stats(liveDistance, liveMaxSpeed)
        val sorted = RecordingBreaks.normalized(breaks)
        val samples = points.filter { it.countsForDistance }.map {
            TripDistanceGate.Sample(
                it.latitude, it.longitude, it.timestamp,
                RecordingBreaks.segmentIndex(it.timestamp, sorted), it.speed,
            )
        }
        return Stats(TripDistanceGate.totalDistance(samples), TripDistanceGate.maximumRecordedSpeed(samples))
    }

    /** Every stored point, by time: real, coarse and filled alike, so the Atlas and the trip map agree. */
    fun previewPolyline(points: List<TrackPoint>): ByteArray? {
        if (points.size < 2) return null
        val coordinates = points.sortedBy { it.timestamp }.map { GeoPoint(it.latitude, it.longitude) }
        return Trip.encodePolyline(GeometryUtils.simplifyRDP(coordinates, GeometryUtils.previewEpsilon))
    }
}
