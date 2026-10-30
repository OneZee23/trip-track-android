package com.onezee.triptrack.logic

import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.math.BigDecimal
import java.math.RoundingMode

// iOS: GPXExporter.swift
object GPXExporter {
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US).withZone(ZoneOffset.UTC)
    fun displayName(trip: Trip): String = trip.title?.trim()?.takeIf { it.isNotEmpty() } ?: date.format(trip.startDate)

    fun gpx(trip: Trip, points: List<TrackPoint>): String = buildString {
        val name = escape(displayName(trip))
        val sorted = points.filterNot { it.isInterpolated }.sortedBy { it.timestamp }
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"TripTrack\" xmlns=\"http://www.topografix.com/GPX/1/1\" ")
        append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        append("  <metadata>\n    <name>$name</name>\n    <time>${exportTime.format(trip.startDate)}</time>\n  </metadata>\n")
        if (sorted.isNotEmpty()) {
            append("  <trk>\n    <name>$name</name>\n    <trkseg>\n")
            for (point in sorted) {
                append("      <trkpt lat=\"${exportNumber(point.latitude)}\" lon=\"${exportNumber(point.longitude)}\">\n")
                if (point.altitude != 0.0) append("        <ele>${exportNumber(point.altitude)}</ele>\n")
                append("        <time>${exportTime.format(point.timestamp)}</time>\n")
                if (point.speed >= 0) append("        <extensions>\n          <speed>${exportNumber(point.speed)}</speed>\n        </extensions>\n")
                append("      </trkpt>\n")
            }
            append("    </trkseg>\n  </trk>\n")
        }
        append("</gpx>\n")
    }

    private fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}

// iOS: CSVExporter.swift
object CSVExporter {
    const val header = "timestamp,latitude,longitude,altitude_m,speed_mps,course_deg,horizontal_accuracy_m"
    @Suppress("UNUSED_PARAMETER")
    fun csv(trip: Trip, points: List<TrackPoint>): String =
        (listOf(header) + points.filterNot { it.isInterpolated }.sortedBy { it.timestamp }.map { point ->
            listOf(exportTime.format(point.timestamp), exportNumber(point.latitude), exportNumber(point.longitude),
                if (point.altitude != 0.0) exportNumber(point.altitude) else "",
                if (point.speed >= 0) exportNumber(point.speed) else "",
                if (point.course >= 0) exportNumber(point.course) else "",
                exportNumber(point.horizontalAccuracy)).joinToString(",")
        }).joinToString("\n")
}

private val exportTime = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)
private fun exportNumber(value: Double): String {
    if (value.isNaN()) return "NaN"
    if (value == Double.POSITIVE_INFINITY) return "INF"
    if (value == Double.NEGATIVE_INFINITY) return "-INF"
    // Foundation printf rounds the binary double; Formatter first rounds its decimal rendering.
    val rounded = BigDecimal(value).setScale(6, RoundingMode.HALF_EVEN)
    val sign = if (rounded.signum() == 0 && value.toRawBits() < 0) "-" else ""
    return sign + rounded.toPlainString()
}
