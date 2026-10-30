package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import java.time.Instant
import java.time.ZoneId

// iOS: PlaceScreen.swift
val PlaceScreen.dateTiles: Int get() = 5
val PlaceScreen.visiblePasses: Int get() = 5
val PlaceScreen.DateTile.id: Instant get() = day

fun PlaceScreen.tiles(
    passes: List<PlacePass>, zone: ZoneId = ZoneId.systemDefault(), limit: Int = dateTiles,
): List<PlaceScreen.DateTile> = passes.groupingBy { it.timestamp.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant() }
    .eachCount().entries.sortedByDescending { it.key }.take(limit).map { PlaceScreen.DateTile(it.key, it.value) }

fun PlaceScreen.route(from: String?, to: String?): String? {
    // Swift's .whitespaces excludes newlines.
    val first = from?.trim { it.isFoundationWhitespace(includeNewlines = false) }?.takeIf { it.isNotEmpty() }
    val second = to?.trim { it.isFoundationWhitespace(includeNewlines = false) }?.takeIf { it.isNotEmpty() }
    return when {
        first != null && second != null -> if (first == second) first else "$first → $second"
        else -> first ?: second
    }
}

fun PlaceScreen.endpoints(preview: List<GeoPoint>): Pair<GeoPoint, GeoPoint>? =
    if (preview.size > 1) preview.first() to preview.last() else null
