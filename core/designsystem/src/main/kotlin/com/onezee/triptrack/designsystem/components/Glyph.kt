package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The SF Symbols the house components draw. Android has no SF Symbols, and Material icons are
 * another app's look, so each one is redrawn on a 24-unit grid at the optical weight the iOS call
 * site asks for (semibold, the weight of every symbol these components use). The `fill` variants
 * cut their mark out of the disc, as monochrome SF Symbols do, so the surface shows through.
 */
enum class AppGlyph {
    checkmark,
    xmark,
    chevronLeft,
    chevronRight,
    ellipsis,
    checkmarkCircleFill,
    xmarkCircleFill,
    infoCircleFill,
    arrowUturnBackwardCircleFill,
    gearshapeFill,
    moonFill,
    sunMaxFill,
    pencil,
    trash,
    squareAndArrowUp,
    flag,
    characterBubble,

    // The trip screens (GlyphsMore.kt).
    plus,
    lock,
    lockFill,
    globe,
    map,
    mapSlash,
    suitcase,
    suitcaseFill,
    squareAndPencil,
    pencilLine,
    camera,
    cameraFill,
    photo,
    flagFill,
    flagCheckered,
    mappinAndEllipse,
    arrowUpLeftAndArrowDownRight,
    arrowDownRightAndArrowUpLeft,
    arrowClockwise,
    exclamationmarkTriangleFill,
    car,
    tray,
    tablecells,
    squareAndArrowDown,
    magnifyingglass,
    minusCircle,
    plusCircleFill,
    checkmarkCircle,
    circle,
    bubbleRight,
    heart,
    calendar,
    clock,
    speedometer,
    mountain,
    bolt,
    fuelpump,
    roadLanes,
    arrowTriangleSwap,
    pause,
    play,
}

/** iOS `Image(systemName:)` for an [AppGlyph]. `null` description: decoration, hidden from TalkBack. */
@Composable
fun Glyph(
    glyph: AppGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val described = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    val layered = if (glyph.cutsOut) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
    Canvas(modifier.size(size).then(layered).then(described)) {
        scale(this.size.width / GRID, this.size.height / GRID, pivot = Offset.Zero) {
            drawGlyph(glyph, tint)
        }
    }
}

private const val GRID = 24f

private val AppGlyph.cutsOut: Boolean
    get() = when (this) {
        AppGlyph.checkmarkCircleFill, AppGlyph.xmarkCircleFill, AppGlyph.infoCircleFill,
        AppGlyph.arrowUturnBackwardCircleFill, AppGlyph.gearshapeFill,
        AppGlyph.exclamationmarkTriangleFill, AppGlyph.plusCircleFill -> true
        else -> false
    }

private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun polyline(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
}

/** Draws [glyph] in grid units; the caller scales the grid to the glyph's size. */
internal fun DrawScope.drawGlyph(glyph: AppGlyph, tint: Color) {
    val center = Offset(12f, 12f)
    when (glyph) {
        AppGlyph.checkmark -> drawPath(polyline(5.2f, 12.8f, 9.8f, 17.4f, 18.8f, 6.6f), tint, style = stroke(2.6f))
        AppGlyph.xmark -> {
            drawLine(tint, Offset(6.2f, 6.2f), Offset(17.8f, 17.8f), 2.5f, StrokeCap.Round)
            drawLine(tint, Offset(17.8f, 6.2f), Offset(6.2f, 17.8f), 2.5f, StrokeCap.Round)
        }
        AppGlyph.chevronLeft -> drawPath(polyline(15.2f, 4.2f, 7.4f, 12f, 15.2f, 19.8f), tint, style = stroke(2.6f))
        AppGlyph.chevronRight -> drawPath(polyline(8.8f, 4.2f, 16.6f, 12f, 8.8f, 19.8f), tint, style = stroke(2.6f))
        AppGlyph.ellipsis -> listOf(4.8f, 12f, 19.2f).forEach { drawCircle(tint, 2.1f, Offset(it, 12f)) }
        AppGlyph.checkmarkCircleFill -> {
            drawCircle(tint, 10.5f, center)
            drawPath(polyline(7.6f, 12.4f, 10.7f, 15.5f, 16.5f, 8.8f), Color.Black, style = stroke(2.3f), blendMode = BlendMode.DstOut)
        }
        AppGlyph.xmarkCircleFill -> {
            drawCircle(tint, 10.5f, center)
            drawLine(Color.Black, Offset(8.6f, 8.6f), Offset(15.4f, 15.4f), 2.3f, StrokeCap.Round, blendMode = BlendMode.DstOut)
            drawLine(Color.Black, Offset(15.4f, 8.6f), Offset(8.6f, 15.4f), 2.3f, StrokeCap.Round, blendMode = BlendMode.DstOut)
        }
        AppGlyph.infoCircleFill -> {
            drawCircle(tint, 10.5f, center)
            drawCircle(Color.Black, 1.55f, Offset(12f, 7.5f), blendMode = BlendMode.DstOut)
            drawPath(polyline(10.2f, 11f, 12.2f, 11f, 12.2f, 16.6f), Color.Black, style = stroke(2.3f), blendMode = BlendMode.DstOut)
            drawLine(Color.Black, Offset(10.2f, 16.8f), Offset(14.2f, 16.8f), 2.1f, StrokeCap.Round, blendMode = BlendMode.DstOut)
        }
        AppGlyph.arrowUturnBackwardCircleFill -> {
            drawCircle(tint, 10.5f, center)
            val turn = Path().apply {
                moveTo(8.2f, 9.6f)
                lineTo(13.4f, 9.6f)
                arcTo(Rect(Offset(13.4f - 3.3f, 9.6f), Size(6.6f, 6.6f)), -90f, 180f, false)
                lineTo(10.6f, 16.2f)
            }
            drawPath(turn, Color.Black, style = stroke(2.1f), blendMode = BlendMode.DstOut)
            drawPath(polyline(10.8f, 7f, 8.2f, 9.6f, 10.8f, 12.2f), Color.Black, style = stroke(2.1f), blendMode = BlendMode.DstOut)
        }
        AppGlyph.gearshapeFill -> {
            drawCircle(tint, 7.6f, center)
            repeat(8) { tooth ->
                rotate(tooth * 45f, pivot = center) {
                    drawRoundRect(
                        tint,
                        topLeft = Offset(10.15f, 1.6f),
                        size = Size(3.7f, 5.2f),
                        cornerRadius = CornerRadius(1.1f, 1.1f),
                    )
                }
            }
            drawCircle(Color.Black, 3.1f, center, blendMode = BlendMode.DstOut)
        }
        AppGlyph.moonFill -> {
            val disc = Path().apply { addOval(Rect(center = Offset(11.4f, 12.6f), radius = 8.6f)) }
            val bite = Path().apply { addOval(Rect(center = Offset(16.4f, 7.6f), radius = 7f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), tint)
        }
        AppGlyph.sunMaxFill -> {
            drawCircle(tint, 4.7f, center)
            repeat(8) { ray ->
                rotate(ray * 45f, pivot = center) {
                    drawLine(tint, Offset(12f, 1.9f), Offset(12f, 4.4f), 2.1f, StrokeCap.Round)
                }
            }
        }
        AppGlyph.pencil -> {
            val body = Path().apply {
                moveTo(4.6f, 19.4f)
                lineTo(5.5f, 15.4f)
                lineTo(15.6f, 5.3f)
                lineTo(18.7f, 8.4f)
                lineTo(8.6f, 18.5f)
                close()
            }
            drawPath(body, tint, style = stroke(1.9f))
            drawLine(tint, Offset(13.6f, 7.3f), Offset(16.7f, 10.4f), 1.9f, StrokeCap.Round)
        }
        AppGlyph.trash -> {
            drawLine(tint, Offset(4.6f, 6.6f), Offset(19.4f, 6.6f), 1.9f, StrokeCap.Round)
            drawPath(polyline(9.4f, 6.4f, 9.6f, 4.4f, 14.4f, 4.4f, 14.6f, 6.4f), tint, style = stroke(1.9f))
            drawPath(polyline(6.6f, 6.8f, 7.6f, 19.6f, 16.4f, 19.6f, 17.4f, 6.8f), tint, style = stroke(1.9f))
            drawLine(tint, Offset(10.2f, 9.8f), Offset(10.4f, 16.4f), 1.7f, StrokeCap.Round)
            drawLine(tint, Offset(13.8f, 9.8f), Offset(13.6f, 16.4f), 1.7f, StrokeCap.Round)
        }
        AppGlyph.squareAndArrowUp -> {
            drawLine(tint, Offset(12f, 3.6f), Offset(12f, 14.2f), 1.9f, StrokeCap.Round)
            drawPath(polyline(8.4f, 7.2f, 12f, 3.6f, 15.6f, 7.2f), tint, style = stroke(1.9f))
            drawPath(polyline(9f, 9.6f, 6.2f, 9.6f, 6.2f, 20.2f, 17.8f, 20.2f, 17.8f, 9.6f, 15f, 9.6f), tint, style = stroke(1.9f))
        }
        AppGlyph.characterBubble -> {
            val bubble = Path().apply {
                moveTo(7.2f, 17.4f)
                lineTo(6.2f, 20.6f)
                lineTo(10.4f, 17.4f)
                lineTo(16.6f, 17.4f)
                cubicTo(18.8f, 17.4f, 20.4f, 15.8f, 20.4f, 13.6f)
                lineTo(20.4f, 7.4f)
                cubicTo(20.4f, 5.2f, 18.8f, 3.6f, 16.6f, 3.6f)
                lineTo(7.4f, 3.6f)
                cubicTo(5.2f, 3.6f, 3.6f, 5.2f, 3.6f, 7.4f)
                lineTo(3.6f, 13.6f)
                cubicTo(3.6f, 15.8f, 5.2f, 17.4f, 7.2f, 17.4f)
                close()
            }
            drawPath(bubble, tint, style = stroke(1.8f))
            drawPath(polyline(9f, 14.2f, 12f, 6.6f, 15f, 14.2f), tint, style = stroke(1.8f))
            drawLine(tint, Offset(10.2f, 11.4f), Offset(13.8f, 11.4f), 1.6f, StrokeCap.Round)
        }
        AppGlyph.flag -> {
            drawLine(tint, Offset(6.2f, 20.8f), Offset(6.2f, 3.8f), 1.9f, StrokeCap.Round)
            drawPath(polyline(6.2f, 4.4f, 17.6f, 4.4f, 14.8f, 8.6f, 17.6f, 12.8f, 6.2f, 12.8f), tint, style = stroke(1.9f))
        }
        else -> drawMoreGlyph(glyph, tint)
    }
}
