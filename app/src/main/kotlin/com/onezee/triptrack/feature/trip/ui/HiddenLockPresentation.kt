package com.onezee.triptrack.feature.trip.ui

import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.logic.HiddenLockHandleMath
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.TripHiddenParts
import com.onezee.triptrack.ui.durationText
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// iOS: HiddenPartsEditorModel.swift.
internal object HiddenLockPresentation {
    data class Bubble(val title: String, val caption: String)

    fun bubble(
        key: String,
        points: List<TrackPoint>,
        prefix: List<Double>,
        parts: TripHiddenParts,
        handles: List<HiddenLockHandleMath.HandleIndex>,
        unit: DistanceUnit,
        strings: AppStrings,
    ): Bubble? {
        val handle = handles.firstOrNull { it.key == key } ?: return null
        val point = points.getOrNull(handle.index) ?: return null
        val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(strings.language.locale)
        if (key == "start") {
            if (handle.ghost) return Bubble(strings.hiddenHandleStartVisible(), strings.hiddenHandleDragInward())
            if (handle.parkingOnly) {
                val departure = HiddenLockHandleMath.parkedStops(points).departureIndex ?: return null
                val duration = Duration.between(points.first().timestamp, points[departure].timestamp).seconds.toDouble()
                return Bubble(strings.hiddenHandleParkingOnly(), strings.hiddenHandleBeforeDeparture(durationText(duration, strings)))
            }
            val stops = HiddenLockHandleMath.parkedStops(points)
            val metres = HiddenLockHandleMath.displayedStartMetres(prefix, handle.index, stops.departureIndex)
            return Bubble(
                strings.hiddenHandleStartHidden(Measure.distance(metres, unit, strings.language.code, strings)),
                strings.hiddenHandleUntil(clock.format(point.timestamp.atZone(strings.zone))),
            )
        }
        if (key == "end") {
            if (handle.ghost) return Bubble(strings.hiddenHandleEndVisible(), strings.hiddenHandleDragInward())
            if (handle.parkingOnly) {
                val arrival = HiddenLockHandleMath.parkedStops(points).arrivalIndex ?: return null
                val duration = Duration.between(points[arrival].timestamp, points.last().timestamp).seconds.toDouble()
                return Bubble(strings.hiddenHandleParkingOnly(), strings.hiddenHandleAfterStop(durationText(duration, strings)))
            }
            val stops = HiddenLockHandleMath.parkedStops(points)
            val metres = HiddenLockHandleMath.displayedEndMetres(prefix, handle.index, stops.arrivalIndex)
            return Bubble(
                strings.hiddenHandleEndHidden(Measure.distance(metres, unit, strings.language.code, strings)),
                strings.hiddenHandleFrom(clock.format(point.timestamp.atZone(strings.zone))),
            )
        }
        val sectionId = key.substringAfter(':')
        val section = parts.sections.firstOrNull { it.id.toString() == sectionId } ?: return null
        val indices = HiddenLockHandleMath.sectionIndices(points, section) ?: return null
        val title = strings.hiddenSectionTitle(
            clock.format(points[indices.first].timestamp.atZone(strings.zone)),
            clock.format(points[indices.last].timestamp.atZone(strings.zone)),
        )
        val metres = prefix[indices.last] - prefix[indices.first]
        return Bubble(title, Measure.distance(metres, unit, strings.language.code, strings))
    }

    fun summary(
        points: List<TrackPoint>,
        prefix: List<Double>,
        parts: TripHiddenParts,
        defaultEdgeMetres: Int?,
        handles: List<HiddenLockHandleMath.HandleIndex>,
        unit: DistanceUnit,
        strings: AppStrings,
    ): String {
        val start = handles.firstOrNull { it.key == "start" }
        val end = handles.firstOrNull { it.key == "end" }
        val stops = HiddenLockHandleMath.parkedStops(points)
        val entries = buildList {
            val startMetres = parts.startMetres ?: defaultEdgeMetres ?: 0
            val endMetres = parts.endMetres ?: defaultEdgeMetres ?: 0
            if (startMetres > 0 && start != null) {
                add(if (start.parkingOnly) strings.hiddenSummaryStartParking() else {
                    val metres = HiddenLockHandleMath.displayedStartMetres(prefix, start.index, stops.departureIndex)
                    "${strings.hiddenStart()} ${Measure.distance(metres, unit, strings.language.code, strings)}"
                })
            }
            if (endMetres > 0 && end != null) {
                add(if (end.parkingOnly) strings.hiddenSummaryEndParking() else {
                    val metres = HiddenLockHandleMath.displayedEndMetres(prefix, end.index, stops.arrivalIndex)
                    "${strings.hiddenEnd().replaceFirstChar { it.lowercase() }} ${Measure.distance(metres, unit, strings.language.code, strings)}"
                })
            }
            if (parts.sections.isNotEmpty()) add(strings.plural(R.plurals.hiddenMiddleCount, parts.sections.size, parts.sections.size))
        }
        return if (entries.isEmpty()) strings.hiddenNothing() else entries.joinToString(" · ")
    }

    fun accessibilityLabel(key: String, strings: AppStrings): String = when {
        key == "start" -> strings.hiddenHandleStartLabel()
        key == "end" -> strings.hiddenHandleEndLabel()
        key.startsWith("section-start:") -> strings.hiddenSectionStartHandle()
        else -> strings.hiddenSectionEndHandle()
    }
}
