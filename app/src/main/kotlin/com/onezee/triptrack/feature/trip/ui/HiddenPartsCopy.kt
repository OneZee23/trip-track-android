package com.onezee.triptrack.feature.trip.ui

import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.logic.radius
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripHiddenParts

// iOS: AppStrings+HiddenParts.swift.
internal fun hiddenPartsSummary(
    strings: AppStrings,
    parts: TripHiddenParts?,
    defaultEdgeMetres: Int?,
    unit: DistanceUnit,
): String {
    val value = parts ?: TripHiddenParts()
    val entries = buildList {
        val start = value.startMetres ?: defaultEdgeMetres ?: 0
        val end = value.endMetres ?: defaultEdgeMetres ?: 0
        if (start > 0) add("${strings.hiddenStart()} ${Measure.radius(start.toDouble(), unit, strings.language.code, strings)}")
        if (end > 0) add("${strings.hiddenEnd().replaceFirstChar { it.lowercase() }} ${Measure.radius(end.toDouble(), unit, strings.language.code, strings)}")
        if (value.sections.isNotEmpty()) add(strings.plural(R.plurals.hiddenMiddleCount, value.sections.size, value.sections.size))
    }
    return if (entries.isEmpty()) strings.hiddenNothing() else entries.joinToString(" · ")
}

internal fun hiddenPartsLocalCopy(strings: AppStrings, hiddenParts: Int, hiddenPhotos: Int): String {
    if (hiddenParts == 0) return strings.tr(R.string.hiddenLocalCardEmpty)
    if (hiddenPhotos == 0) {
        return strings.plural(R.plurals.hiddenLocalCardPiecesOnly, hiddenParts, hiddenParts)
    }
    val pieces = strings.plural(R.plurals.hiddenLocalPieceCount, hiddenParts, hiddenParts)
    return strings.tr(R.string.hiddenLocalCardBody, pieces, strings.photosCount(hiddenPhotos))
}

internal fun hiddenPublishRow(strings: AppStrings, summary: String): String =
    if (summary == strings.hiddenNothing()) strings.hiddenEditorTitle()
    else strings.tr(R.string.hiddenPublishRow, summary)
