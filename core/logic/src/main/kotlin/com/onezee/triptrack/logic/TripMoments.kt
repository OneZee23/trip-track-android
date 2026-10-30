package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.util.UUID

// iOS: TripMoment.swift
val TripMoment.id: UUID get() = when (this) {
    is TripMoment.Checkpoint -> value1.id
    is TripMoment.Photos -> photos.first().id
}
val TripMoment.elapsedFromStart: Double get() = when (this) {
    is TripMoment.Checkpoint -> value1.elapsedFromStart
    is TripMoment.Photos -> fix.elapsedFromStart
}
val TripMoment.distanceFromStart: Double get() = when (this) {
    is TripMoment.Checkpoint -> value1.distanceFromStart
    is TripMoment.Photos -> fix.distanceFromStart
}

fun TripMoments.build(
    checkpoints: List<TripCheckpoint>, links: Map<UUID, List<TripPhoto>>, loose: List<TripMoments.PlacedPhoto>,
    gap: Double = TripCheckpointPhotos.timeWindow,
): List<TripMoment> {
    val moments = checkpoints.sortedBy { it.elapsedFromStart }.mapIndexed { index, checkpoint ->
        TripMoment.Checkpoint(checkpoint, index + 1, links[checkpoint.id] ?: emptyList())
    }.toMutableList<TripMoment>()
    val group = mutableListOf<TripMoments.PlacedPhoto>()
    fun flush() {
        group.firstOrNull()?.let { moments.add(TripMoment.Photos(it.fix, group.map { item -> item.photo })) }
        group.clear()
    }
    for (item in loose.sortedBy { it.fix.elapsedFromStart }) {
        // The window starts at the first photo, so a chain cannot stretch a stack indefinitely.
        if (group.firstOrNull()?.let { item.fix.elapsedFromStart - it.fix.elapsedFromStart > gap } == true) flush()
        group.add(item)
    }
    flush()
    return moments.sortedBy { it.elapsedFromStart }
}
