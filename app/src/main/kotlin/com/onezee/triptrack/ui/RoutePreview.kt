package com.onezee.triptrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.logic.GeometryUtils
import com.onezee.triptrack.model.GeoPoint
import kotlin.math.max
import kotlin.math.min

/**
 * iOS: LightRoutePreview.swift. The route as a line on the card-alt ground, drawn with a Canvas
 * instead of a map: a list of fifty cards cannot afford fifty maps. Several routes share one frame
 * (the map teaser of a profile); only a single route gets its start and end dots, since twenty
 * coloured dots on ten routes read as a rash, not as starts and finishes.
 *
 * The projection is plain degrees, as on iOS: at a card's scale nobody sees Mercator.
 */
@Composable
fun LightRoutePreview(
    routes: List<List<GeoPoint>>,
    modifier: Modifier = Modifier,
    accent: Color = SystemColors.orange(AppTheme.isDark),
    background: Color = AppTheme.colors.cardAlt,
) {
    val dark = AppTheme.isDark
    // Previews from the feed are ~20 points already; only a full track is thinned.
    val simplified = remember(routes) {
        routes.filter { it.size >= 2 }.mapNotNull { coordinates ->
            val s = if (coordinates.size > 30) GeometryUtils.simplifyRDP(coordinates, 0.00003) else coordinates
            s.takeIf { it.size >= 2 }
        }
    }
    Canvas(modifier.background(background)) {
        val first = simplified.firstOrNull()?.firstOrNull() ?: return@Canvas
        var minLat = first.latitude
        var maxLat = first.latitude
        var minLon = first.longitude
        var maxLon = first.longitude
        for (route in simplified) for (point in route) {
            minLat = min(minLat, point.latitude)
            maxLat = max(maxLat, point.latitude)
            minLon = min(minLon, point.longitude)
            maxLon = max(maxLon, point.longitude)
        }
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon
        if (latRange <= 0 && lonRange <= 0) return@Canvas
        val padding = 16.dp.toPx()
        val drawW = size.width - padding * 2
        val drawH = size.height - padding * 2
        val latScale = if (latRange > 0) drawH / latRange else 1.0
        val lonScale = if (lonRange > 0) drawW / lonRange else 1.0
        val scale = min(latScale, lonScale)
        val midLat = (minLat + maxLat) / 2
        val midLon = (minLon + maxLon) / 2
        fun toPoint(point: GeoPoint) = Offset(
            (size.width / 2 + (point.longitude - midLon) * scale).toFloat(),
            (size.height / 2 - (point.latitude - midLat) * scale).toFloat(),
        )
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (route in simplified) {
            val path = Path().apply {
                val start = toPoint(route[0])
                moveTo(start.x, start.y)
                for (i in 1 until route.size) toPoint(route[i]).let { lineTo(it.x, it.y) }
            }
            drawPath(path, accent.copy(alpha = accent.alpha * 0.7f), style = stroke)
        }
        val single = simplified.singleOrNull() ?: return@Canvas
        val dot = 5.dp.toPx() / 2
        drawCircle(SystemColors.green(dark), dot, toPoint(single.first()))
        // The finish mirrors the start: a flag on a pole overhung the route at thumbnail scale.
        drawCircle(SystemColors.red(dark), dot, toPoint(single.last()))
    }
}

/** UIKit's system colours, light and dark, that SwiftUI's `.orange`, `.green` and `.red` resolve to. */
object SystemColors {
    fun orange(dark: Boolean) = if (dark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
    fun green(dark: Boolean) = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    fun red(dark: Boolean) = if (dark) Color(0xFFFF453A) else Color(0xFFFF3B30)
    fun blue(dark: Boolean) = if (dark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    fun yellow(dark: Boolean) = if (dark) Color(0xFFFFD60A) else Color(0xFFFFCC00)
    fun gray(dark: Boolean) = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    fun teal(dark: Boolean) = if (dark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
    fun indigo(dark: Boolean) = if (dark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
    fun purple(dark: Boolean) = if (dark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
}
