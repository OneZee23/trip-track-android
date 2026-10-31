package com.onezee.triptrack.feature.trip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.DetailChartPoint
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.elevation
import com.onezee.triptrack.logic.speed
import com.onezee.triptrack.map.route.SpeedColorScale
import com.onezee.triptrack.model.Measure
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val Green = AppTheme.green

/** iOS: DetailChartPoint.hasBand. The min–max band shows only when the bucket really spread. */
private val DetailChartPoint.hasBand: Boolean get() = !low.isNaN() && !high.isNaN() && high - low > 0.5

/**
 * iOS: ElevationChartCard (TripDetailSections.swift). The altitude along the road in green: the
 * area under the line, the min–max band of each bucket, the line. A finger along it reads the
 * height and the time at that point; the readout stays after the finger lifts.
 */
@Composable
fun ElevationChartCard(series: List<DetailChartPoint>, summary: String, leftLabel: String, rightLabel: String, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val lo = series.minOf { if (it.hasBand) it.low else it.mean }
    val hi = series.maxOf { if (it.hasBand) it.high else it.mean }
    val pad = maxOf((hi - lo) * 0.1, 1.0)
    ChartCard(strings.chartAltitudeLabel(), summary, modifier.testTag("elevation_chart")) {
        ScrubChart(
            series = series,
            height = 92,
            yMin = lo - pad,
            yMax = hi + pad,
            readout = { point -> Measure.elevation(point.mean, unit, strings.language.code, strings) + clockSuffix(point) },
        ) { geometry ->
            val line = geometry.monotonePath(series.map { it.mean })
            val area = Path().apply { addPath(line); lineTo(geometry.x(series.last()), geometry.bottom); lineTo(geometry.x(series.first()), geometry.bottom); close() }
            drawPath(area, Green.copy(alpha = 0.18f))
            if (series.any { it.hasBand }) drawPath(geometry.bandPath(series), Green.copy(alpha = 0.22f))
            drawPath(line, Green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(leftLabel, style = inter(9f, InterWeight.medium), color = AppTheme.colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f))
            Text(rightLabel, style = inter(14f, InterWeight.medium), color = AppTheme.colors.textTertiary, maxLines = 1)
        }
    }
}

/**
 * iOS: SpeedChartCard. The speed along the road, the line coloured by the speed bands of the map
 * (green under 50 km/h, yellow under 90, orange under 110, red above) from each bucket's mean, the
 * spread as a grey band; the axis starts at zero and never shrinks below 10 km/h.
 */
@Composable
fun SpeedChartCard(series: List<DetailChartPoint>, summary: String, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val tertiary = AppTheme.colors.textTertiary
    val hi = maxOf(series.maxOf { if (it.hasBand) it.high else it.mean }, 10 / 3.6)
    ChartCard(strings.chartSpeedLabel(), summary, modifier.testTag("speed_chart")) {
        ScrubChart(
            series = series,
            height = 80,
            yMin = 0.0,
            yMax = hi * 1.1,
            readout = { point -> Measure.speed(point.mean, unit, strings.language.code, strings) + clockSuffix(point) },
        ) { geometry ->
            if (series.any { it.hasBand }) drawPath(geometry.bandPath(series), tertiary.copy(alpha = tertiary.alpha * 0.22f))
            val line = geometry.monotonePath(series.map { it.mean })
            val first = series.first().km
            val last = series.last().km
            val brush = if (last > first) {
                Brush.horizontalGradient(
                    colorStops = series.map { ((it.km - first) / (last - first)).toFloat() to Color(SpeedColorScale.argb(it.mean)) }.toTypedArray(),
                    startX = geometry.x(series.first()), endX = geometry.x(series.last()),
                )
            } else Brush.horizontalGradient(listOf(Green, Green))
            drawPath(line, brush, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun clockSuffix(point: DetailChartPoint): String {
    val strings = appStrings
    return point.date?.let { " · " + LocalizedDateFormatter.patterns("HH:mm", strings.zone).getValue(strings.language).format(it) } ?: ""
}

@Composable
private fun ChartCard(label: String, summary: String, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier.fillMaxWidth().surfaceCard(14.dp).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = inter(10f, InterWeight.bold), letterSpacing = points(0.2f), color = colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Text(summary, style = inter(11f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
        }
        content()
    }
}

/** The plot's mapping from kilometres and values to pixels; the plot fills the chart's frame. */
class ChartGeometry(private val series: List<DetailChartPoint>, val width: Float, val bottom: Float, private val yMin: Double, private val yMax: Double) {
    private val first = series.first().km
    private val span = (series.last().km - first).takeIf { it > 0 } ?: 1.0
    fun x(point: DetailChartPoint): Float = ((point.km - first) / span * width).toFloat()
    fun y(value: Double): Float = (bottom - (value - yMin) / (yMax - yMin) * bottom).toFloat()

    /** Fritsch–Carlson monotone cubic, the interpolation Swift Charts calls `.monotone`. */
    fun monotonePath(values: List<Double>): Path {
        val xs = series.map { x(it) }
        val ys = values.map { y(it) }
        val n = xs.size
        val path = Path()
        path.moveTo(xs[0], ys[0])
        if (n == 2) { path.lineTo(xs[1], ys[1]); return path }
        val d = FloatArray(n - 1) { i -> val dx = xs[i + 1] - xs[i]; if (dx == 0f) 0f else (ys[i + 1] - ys[i]) / dx }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) { val t = 3f / sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }
        for (i in 0 until n - 1) {
            val dx = (xs[i + 1] - xs[i]) / 3f
            path.cubicTo(xs[i] + dx, ys[i] + m[i] * dx, xs[i + 1] - dx, ys[i + 1] - m[i + 1] * dx, xs[i + 1], ys[i + 1])
        }
        return path
    }

    /** The band between each bucket's minimum and maximum (the mean where a bucket did not spread). */
    fun bandPath(points: List<DetailChartPoint>): Path {
        val path = Path().apply { addPath(monotonePath(points.map { if (it.hasBand) it.high else it.mean })) }
        for (point in points.reversed()) path.lineTo(x(point), y(if (point.hasBand) point.low else point.mean))
        path.close()
        return path
    }
}

/**
 * iOS: ChartScrub. A sideways drag or a tap anywhere on the plot picks the nearest sample: a rule,
 * a dot and a readout above (below near the top). A vertical drag still scrolls the page.
 */
@Composable
private fun ScrubChart(
    series: List<DetailChartPoint>,
    height: Int,
    yMin: Double,
    yMax: Double,
    readout: @Composable (DetailChartPoint) -> String,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(ChartGeometry) -> Unit,
) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    var plotWidth by remember { mutableStateOf(1f) }
    fun pick(x: Float) {
        val first = series.first().km
        val span = (series.last().km - first).takeIf { it > 0 } ?: 1.0
        val km = first + (x / plotWidth).coerceIn(0f, 1f) * span
        val index = series.indices.minByOrNull { abs(series[it].km - km) } ?: return
        if (index != selected) { selected = index; haptics.selection() }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp)) {
        val heightPx = with(density) { height.dp.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height.dp)
                .onSizeChanged { plotWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(series) { detectTapGestures { pick(it.x) } }
                .pointerInput(series) { detectHorizontalDragGestures { change, _ -> change.consume(); pick(change.position.x) } },
        ) {
            val geometry = ChartGeometry(series, size.width, size.height, yMin, yMax)
            draw(geometry)
            selected?.let { index ->
                val point = series[index]
                val x = geometry.x(point)
                drawLine(AppTheme.accent.copy(alpha = 0.55f), Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                drawCircle(AppTheme.accent, 4.4.dp.toPx(), Offset(x, geometry.y(point.mean)))
            }
        }
        selected?.let { index ->
            val point = series[index]
            val geometry = ChartGeometry(series, plotWidth, heightPx, yMin, yMax)
            val px = geometry.x(point)
            val py = geometry.y(point.mean)
            val tooltipWidth = with(density) { 150.dp.toPx() }
            val centreX = px.coerceIn(minOf(tooltipWidth / 2, plotWidth / 2), maxOf(plotWidth - tooltipWidth / 2, plotWidth / 2))
            val above = py - with(density) { 30.dp.toPx() } >= with(density) { 18.dp.toPx() }
            val centreY = if (above) py - with(density) { 30.dp.toPx() } else py + with(density) { 30.dp.toPx() }
            ScrubTooltip(
                primary = readout(point),
                secondary = strings.chartDistanceMark(unit, point.km),
                modifier = Modifier.offset { IntOffset((centreX - tooltipWidth / 2).roundToInt(), (centreY - with(density) { 18.dp.toPx() }).roundToInt()) },
            )
        }
    }
}

/** iOS: ChartScrubTooltip. White on near-black, the reading over its kilometre mark. */
@Composable
private fun ScrubTooltip(primary: String, secondary: String, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.width(150.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .iosShadow(shape, Color.Black.copy(alpha = 0.22f), radius = 8.dp, y = 3.dp)
                .background(Color(28, 28, 30), shape)
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("chart_tooltip"),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(primary, style = inter(12f, InterWeight.bold), color = Color.White, maxLines = 1)
            Text(secondary, style = inter(10.5f), color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

/**
 * iOS: MovingStopsCard. «В движении · 2 ч 10 мин» against «Без движения · 18 мин», and the bar that
 * splits the trip between them (the moving part never thinner than 6).
 */
@Composable
fun MovingStopsCard(moving: Double, stopped: Double, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    val gray = if (AppTheme.isDark) Color.White.copy(alpha = 0.18f) else Color(0xFFD6D6DB)
    Column(
        modifier.fillMaxWidth().surfaceCard(14.dp).padding(horizontal = 16.dp, vertical = 14.dp).testTag("moving_stops"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(strings.movingDot(humanTime(moving, strings)), style = inter(12.5f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1,
                modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(1f).width(8.dp))
            Text(strings.stopsDot(humanTime(stopped, strings)), style = inter(12.5f, InterWeight.semibold), color = colors.textSecondary, maxLines = 1)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp)) {
            val available = maxOf(maxWidth.value - 2f, 0f)
            val fraction = if (moving + stopped > 0) (moving / (moving + stopped)).toFloat() else 0f
            val movingWidth = maxOf(minOf(available * fraction, available - 6f), 6f)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(movingWidth.dp).height(10.dp).background(AppTheme.accent, RoundedCornerShape(5.dp)))
                Box(Modifier.weight(1f).height(10.dp).background(gray, RoundedCornerShape(5.dp)))
            }
        }
    }
}

/** iOS: Trip.formattedTimeHuman. «2 ч 10 мин», «18 мин 5 сек», «40 сек». */
private fun humanTime(seconds: Double, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String {
    val total = maxOf(0L, seconds.toLong())
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h ${strings.hoursUnitShort()} $m ${strings.minutesUnitShort()}"
        m > 0 -> if (s > 0) "$m ${strings.minutesUnitShort()} $s ${strings.secondsUnitShort()}" else "$m ${strings.minutesUnitShort()}"
        else -> "$s ${strings.secondsUnitShort()}"
    }
}

/** The elevation card's right label: the whole distance, grouped. */
@Composable
fun groupedDistance(metres: Double): String {
    val strings = appStrings
    return Measure.distance(metres, LocalDistanceUnit.current, strings.language.code, strings, Measure.Style.grouped)
}
