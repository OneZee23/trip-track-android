// iOS: PlaceLastPass.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.PlaceLastPass
import com.onezee.triptrack.model.PlacePass
import com.onezee.triptrack.model.PlaceStats
import java.util.UUID
import kotlin.math.abs

fun PlaceLastPass.reading(placeId: UUID, passes: List<PlacePass>): PlaceLastPass.Reading? {
    val last = passes.maxByOrNull { it.timestamp } ?: return null
    if (!last.hasCourse) return null
    val sameWay = passes.filter { it.id != last.id && it.hasCourse && PlaceStats.angularDistance(it.course, last.course) <= PlaceStats.directionTolerance }
    if (sameWay.size < 2) return null
    val sorted = sameWay.map { it.elapsedFromStart }.sorted()
    val median = if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2 else sorted[sorted.size / 2]
    if (median <= 0) return null
    val delta = last.elapsedFromStart - median
    val size = abs(delta)
    if (size < 300 || size < median * 0.1) return null
    return PlaceLastPass.Reading(placeId, last.timestamp, size, delta < 0)
}
