package com.onezee.triptrack.feature.trip.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.HiddenSection
import com.onezee.triptrack.model.TrackPoint
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.map.route.RouteLine
import com.onezee.triptrack.map.route.RouteShape
import com.onezee.triptrack.map.route.SpeedColorScale
import java.time.Instant

internal fun hiddenEditorRoute(trip: Trip, result: com.onezee.triptrack.logic.TripHiddenPartsResolution): RouteShape {
    val visible = result.visibleRuns.flatMap { range ->
        val points = trip.trackPoints.subList(range.first, range.last + 1)
        RouteGeometry.build(points.map { GeoPoint(it.latitude, it.longitude) }, points.map { it.speed }).lines
    }
    val segments = trip.trackPoints.map { point -> trip.recordingBreaks.count { point.timestamp >= it } }
    val hidden = com.onezee.triptrack.logic.TripHiddenPartsResolver.drawnHiddenRuns(result.hidden, segments).map { range ->
        RouteLine(range.map { trip.trackPoints[it].let { point -> GeoPoint(point.latitude, point.longitude) } }, null, hidden = true)
    }
    val all = trip.trackPoints.map { GeoPoint(it.latitude, it.longitude) }
    val speeds = trip.trackPoints.map { it.speed }
    val bounds = RouteGeometry.build(all, speeds, breaks = trip.recordingBreaks.map { boundary ->
        trip.trackPoints.indexOfFirst { it.timestamp >= boundary }
    }.filter { it > 0 }).bounds
    return RouteShape(visible, bounds, hidden)
}

internal fun hiddenRouteLockMarkers(points: List<TrackPoint>, hidden: List<Boolean>): List<MapMarker> {
    val icon = hiddenLockBitmap()
    val markers = mutableListOf<MapMarker>()
    var index = 0
    while (index < hidden.size) {
        if (!hidden[index]) { index++; continue }
        val first = index
        while (index + 1 < hidden.size && hidden[index + 1]) index++
        listOf(first - 1, index + 1).filter { it in points.indices }.forEachIndexed { offset, pointIndex ->
            val point = points[pointIndex]
            markers += MapMarker("hidden_lock_${first}_$offset", GeoPoint(point.latitude, point.longitude),
                MapMarker.Kind.HIDDEN_LOCK, label = "", image = icon)
        }
        index++
    }
    return markers
}

internal fun hiddenLockBitmap(): Bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(24f, 24f, 23f, paint)
    paint.color = android.graphics.Color.rgb(90, 91, 101)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawRoundRect(RectF(16f, 13f, 32f, 29f), 8f, 8f, paint)
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(14f, 22f, 34f, 38f), 3f, 3f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(24f, 28f, 2f, paint)
    canvas.drawRect(23f, 28f, 25f, 33f, paint)
}

internal fun hiddenTimelineFraction(time: Instant, start: Instant, end: Instant): Float {
    val length = (end.toEpochMilli() - start.toEpochMilli()).toDouble()
    if (length <= 0) return 0f
    return ((time.toEpochMilli() - start.toEpochMilli()) / length).toFloat().coerceIn(0f, 1f)
}

@Composable
internal fun HiddenTripBar(
    trip: Trip,
    hidden: List<Boolean>,
    sections: List<HiddenSection>,
    strings: com.onezee.triptrack.designsystem.strings.AppStrings,
) {
    val start = trip.trackPoints.firstOrNull()?.timestamp ?: trip.startDate
    val end = trip.trackPoints.lastOrNull()?.timestamp ?: trip.endDate ?: start
    val colors = AppTheme.colors
    Canvas(Modifier.fillMaxWidth().height(24.dp).testTag("hidden_trip_bar")) {
        val y = size.height / 2
        val barHeight = 12.dp.toPx()
        drawRoundRect(colors.cardAlt, topLeft = Offset(0f, y - barHeight / 2), size = androidx.compose.ui.geometry.Size(size.width, barHeight), cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2))
        val points = trip.trackPoints
        if (points.size > 1) {
            for (index in 0 until points.lastIndex) {
                val left = size.width * hiddenTimelineFraction(points[index].timestamp, start, end)
                val right = size.width * hiddenTimelineFraction(points[index + 1].timestamp, start, end)
                val color = if (hidden.getOrNull(index) == true) colors.textTertiary.copy(alpha = .65f)
                    else Color(SpeedColorScale.argb(points[index].speed))
                drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = barHeight, cap = StrokeCap.Round)
                if (hidden.getOrNull(index) == true) {
                    var x = left - barHeight
                    while (x < right) {
                        drawLine(Color.White.copy(alpha = .7f), Offset(x, y + barHeight / 2), Offset(x + barHeight, y - barHeight / 2), strokeWidth = 1.2.dp.toPx())
                        x += 6.dp.toPx()
                    }
                }
            }
        }
        sections.forEach { section ->
            listOf(section.from, section.to).forEach { time ->
                val fraction = hiddenTimelineFraction(time, start, end)
                drawCircle(colors.card, radius = 8.dp.toPx(), center = Offset(size.width * fraction, y))
                drawCircle(colors.textSecondary, radius = 8.dp.toPx(), center = Offset(size.width * fraction, y), style = Stroke(1.dp.toPx()))
            }
        }
    }
    val clock = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(strings.language.locale)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        com.onezee.triptrack.designsystem.components.Text(clock.format(start.atZone(strings.zone)), style = inter(10f), color = AppTheme.colors.textTertiary)
        com.onezee.triptrack.designsystem.components.Text(clock.format(end.atZone(strings.zone)), style = inter(10f), color = AppTheme.colors.textTertiary)
    }
}
