package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.LocalContentColor

/**
 * The SF Symbols of the Atlas and Places screens that the house set has no drawing for, redrawn on the
 * same 24-unit grid and semibold weight as the design system's glyphs. Kept with the screens that use
 * them; they can move into the design system's set when another screen needs one.
 */
enum class WorldGlyph {
    /** square.3.layers.3d: «Вид карты». */
    layers,
    /** location: «Моё местоположение». */
    location,
    /** location.slash: the same, with location denied. */
    locationSlash,
    /** mappin: a place. */
    mappin,
    /** mappin.slash: «Мест пока нет». */
    mappinSlash,
    /** arrow.up: the course of a pass, rotated by its degrees. */
    arrowUp,
    /** record.circle: «Отметьте в поездке». */
    recordCircle,
    /** hand.tap: «Нажмите на точку». */
    handTap,
    /** sparkles: «Похоже, вы здесь бываете». */
    sparkles,
    /** arrow.uturn.backward: «Вернуть название по адресу». */
    arrowUturnBackward,
    /** point.topleft.down.to.point.bottomright.curvepath: «Новые дороги». */
    curvePath,
    /** arrow.triangle.2.circlepath: «Всего проехано». */
    circlePath,
}

@Composable
fun WorldGlyphIcon(
    glyph: WorldGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    rotationDegrees: Float = 0f,
) {
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(described)) {
        scale(this.size.width / GRID, this.size.height / GRID, pivot = Offset.Zero) {
            rotate(rotationDegrees, pivot = Offset(12f, 12f)) { drawWorldGlyph(glyph, tint) }
        }
    }
}

private const val GRID = 24f

private fun line(width: Float = 1.9f) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun path(vararg points: Float, close: Boolean = false): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    if (close) close()
}

/** The teardrop of a map pin with its round head centred at (12, 9.4). */
private fun pinPath(): Path = Path().apply {
    moveTo(12f, 21.2f)
    cubicTo(12f, 21.2f, 5.4f, 14.4f, 5.4f, 9.4f)
    cubicTo(5.4f, 5.8f, 8.4f, 2.8f, 12f, 2.8f)
    cubicTo(15.6f, 2.8f, 18.6f, 5.8f, 18.6f, 9.4f)
    cubicTo(18.6f, 14.4f, 12f, 21.2f, 12f, 21.2f)
    close()
}

private fun DrawScope.drawWorldGlyph(glyph: WorldGlyph, tint: Color) {
    when (glyph) {
        WorldGlyph.layers -> {
            drawPath(path(12f, 3.6f, 21f, 8.2f, 12f, 12.8f, 3f, 8.2f, close = true), tint, style = line(1.8f))
            drawPath(path(3f, 12.2f, 12f, 16.8f, 21f, 12.2f), tint, style = line(1.8f))
            drawPath(path(3f, 16f, 12f, 20.6f, 21f, 16f), tint, style = line(1.8f))
        }
        WorldGlyph.location, WorldGlyph.locationSlash -> {
            drawPath(path(20.4f, 3.6f, 3.6f, 10.8f, 11.2f, 12.8f, 13.2f, 20.4f, close = true), tint, style = line(1.8f))
            if (glyph == WorldGlyph.locationSlash) drawLine(tint, Offset(3.4f, 3.4f), Offset(20.6f, 20.6f), 1.9f, StrokeCap.Round)
        }
        WorldGlyph.mappin -> {
            drawCircle(tint, 4.2f, Offset(12f, 7.4f))
            drawLine(tint, Offset(12f, 11.4f), Offset(12f, 21f), 2.2f, StrokeCap.Round)
        }
        WorldGlyph.mappinSlash -> {
            drawPath(pinPath(), tint, style = line(1.8f))
            drawCircle(tint, 2.3f, Offset(12f, 9.4f))
            drawLine(tint, Offset(4f, 3.6f), Offset(20f, 20.4f), 1.9f, StrokeCap.Round)
        }
        WorldGlyph.arrowUp -> {
            drawLine(tint, Offset(12f, 20f), Offset(12f, 4.6f), 2.4f, StrokeCap.Round)
            drawPath(path(5.6f, 10.6f, 12f, 4.2f, 18.4f, 10.6f), tint, style = line(2.4f))
        }
        WorldGlyph.recordCircle -> {
            drawCircle(tint, 8.6f, Offset(12f, 12f), style = line(1.8f))
            drawCircle(tint, 4.4f, Offset(12f, 12f))
        }
        WorldGlyph.handTap -> {
            // A raised finger with the tap's arc above it.
            drawPath(Path().apply {
                moveTo(10.2f, 20.6f)
                lineTo(7.2f, 15.8f)
                cubicTo(6.6f, 14.8f, 7.6f, 13.8f, 8.6f, 14.4f)
                lineTo(10.2f, 15.6f)
                lineTo(10.2f, 8.6f)
                cubicTo(10.2f, 7.6f, 11.8f, 7.6f, 11.8f, 8.6f)
                lineTo(11.8f, 12.6f)
                lineTo(16.6f, 13.6f)
                cubicTo(17.6f, 13.8f, 18.2f, 14.8f, 18f, 15.8f)
                lineTo(17.2f, 20.6f)
            }, tint, style = line(1.7f))
            drawArc(tint, 200f, 140f, false, topLeft = Offset(7.4f, 2.6f), size = androidx.compose.ui.geometry.Size(9.2f, 9.2f), style = line(1.6f))
        }
        WorldGlyph.sparkles -> {
            fun star(cx: Float, cy: Float, r: Float) = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx + r * 0.16f, cy - r * 0.16f, cx + r, cy)
                quadraticTo(cx + r * 0.16f, cy + r * 0.16f, cx, cy + r)
                quadraticTo(cx - r * 0.16f, cy + r * 0.16f, cx - r, cy)
                quadraticTo(cx - r * 0.16f, cy - r * 0.16f, cx, cy - r)
                close()
            }
            drawPath(star(10f, 13f, 7f), tint, style = Fill)
            drawPath(star(18.2f, 5.6f, 3.4f), tint, style = Fill)
            drawPath(star(18.6f, 18.4f, 2.4f), tint, style = Fill)
        }
        WorldGlyph.arrowUturnBackward -> {
            drawPath(Path().apply {
                moveTo(5f, 9.6f)
                lineTo(14.6f, 9.6f)
                cubicTo(17.8f, 9.6f, 20f, 11.8f, 20f, 14.8f)
                cubicTo(20f, 17.8f, 17.8f, 20f, 14.6f, 20f)
                lineTo(10.4f, 20f)
            }, tint, style = line(2f))
            drawPath(path(9.2f, 5.2f, 4.8f, 9.6f, 9.2f, 14f), tint, style = line(2f))
        }
        WorldGlyph.curvePath -> {
            drawCircle(tint, 2.4f, Offset(5f, 5f), style = line(1.7f))
            drawCircle(tint, 2.4f, Offset(19f, 19f), style = line(1.7f))
            drawPath(Path().apply {
                moveTo(7.4f, 5f)
                cubicTo(15f, 5f, 15.4f, 10.6f, 11f, 12.4f)
                cubicTo(6.4f, 14.2f, 7.6f, 19f, 16.6f, 19f)
            }, tint, style = line(1.8f))
        }
        WorldGlyph.circlePath -> {
            drawArc(tint, 200f, 150f, false, topLeft = Offset(4.4f, 4.4f), size = androidx.compose.ui.geometry.Size(15.2f, 15.2f), style = line(1.9f))
            drawArc(tint, 20f, 150f, false, topLeft = Offset(4.4f, 4.4f), size = androidx.compose.ui.geometry.Size(15.2f, 15.2f), style = line(1.9f))
            drawPath(path(16.4f, 3.6f, 18.8f, 6.4f, 15.6f, 7.8f, close = true), tint, style = Fill)
            drawPath(path(7.6f, 20.4f, 5.2f, 17.6f, 8.4f, 16.2f, close = true), tint, style = Fill)
        }
    }
}

/** The house of iOS `house.fill` on the 24 grid: the home pin's glyph and the home card's tile. */
internal fun housePath(): Path = path(5.6f, 10.8f, 5.6f, 19.8f, 10f, 19.8f, 10f, 14.6f, 14f, 14.6f, 14f, 19.8f, 18.4f, 19.8f, 18.4f, 10.8f, 12f, 5.2f, close = true)
