package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackGapFinder

// iOS: TrackGapFinder.swift
val TrackGapFinder.minDuration: Double get() = 10.0
val TrackGapFinder.minDistance: Double get() = 150.0
val TrackGapFinder.Point.coordinate: GeoPoint get() = GeoPoint(latitude, longitude)
val TrackGapFinder.Gap.seconds: Double get() = to.timestamp.secondsSince(from.timestamp)
val TrackGapFinder.Gap.straightMetres: Double get() = from.coordinate.distanceFrom(to.coordinate)

fun TrackGapFinder.gaps(points: List<TrackGapFinder.Point>, includeFilled: Boolean): List<TrackGapFinder.Gap> {
    val sorted = points.sortedBy { it.timestamp }
    val fills = sorted.filter { it.isInterpolated }.map { it.timestamp }
    val real = sorted.filter { !it.isInterpolated }
    val result = mutableListOf<TrackGapFinder.Gap>()
    var fillIndex = 0
    for ((a, b) in real.zipWithNext()) {
        if (a.recordingSegmentIndex != b.recordingSegmentIndex) continue
        val gap = TrackGapFinder.Gap(a, b)
        if (!(gap.seconds >= minDuration && gap.straightMetres >= minDistance)) continue
        while (fillIndex < fills.size && fills[fillIndex] <= a.timestamp) fillIndex++
        val isFilled = fillIndex < fills.size && fills[fillIndex] < b.timestamp
        if (includeFilled || !isFilled) result += gap
    }
    return result
}

fun TrackGapFinder.openGaps(points: List<TrackGapFinder.Point>): List<TrackGapFinder.Gap> = gaps(points, false)
