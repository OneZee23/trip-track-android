package com.onezee.triptrack.designsystem.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

// The SF Symbols of the trip screens (feed, trip, journey, drafts), redrawn on the same 24-unit grid
// and at the same semibold weight as Glyph.kt. Outline symbols are strokes; `fill` symbols are
// solid, and the ones that carry a mark (the triangle, the plus disc) cut it out of the solid.

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun path(vararg points: Float, close: Boolean = false): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    if (close) close()
}

internal fun DrawScope.drawMoreGlyph(glyph: AppGlyph, tint: Color) {
    val w = 1.9f
    when (glyph) {
        AppGlyph.plus -> {
            drawLine(tint, Offset(12f, 5f), Offset(12f, 19f), 2.3f, StrokeCap.Round)
            drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), 2.3f, StrokeCap.Round)
        }
        AppGlyph.lock, AppGlyph.lockFill -> {
            val shackle = Path().apply {
                moveTo(8.2f, 10.6f)
                lineTo(8.2f, 7.6f)
                cubicTo(8.2f, 5.4f, 9.8f, 3.8f, 12f, 3.8f)
                cubicTo(14.2f, 3.8f, 15.8f, 5.4f, 15.8f, 7.6f)
                lineTo(15.8f, 10.6f)
            }
            drawPath(shackle, tint, style = line(w))
            val body = Rect(5.6f, 10.4f, 18.4f, 20.4f)
            if (glyph == AppGlyph.lockFill) {
                drawRoundRect(tint, body.topLeft, body.size, CornerRadius(2.4f, 2.4f))
            } else {
                drawRoundRect(tint, body.topLeft, body.size, CornerRadius(2.4f, 2.4f), style = line(w))
            }
        }
        AppGlyph.globe -> {
            drawCircle(tint, 8.6f, Offset(12f, 12f), style = line(w))
            drawOval(tint, Offset(8.4f, 3.4f), Size(7.2f, 17.2f), style = line(1.6f))
            drawLine(tint, Offset(3.6f, 12f), Offset(20.4f, 12f), 1.6f, StrokeCap.Round)
            drawLine(tint, Offset(5.2f, 7.9f), Offset(18.8f, 7.9f), 1.4f, StrokeCap.Round)
            drawLine(tint, Offset(5.2f, 16.1f), Offset(18.8f, 16.1f), 1.4f, StrokeCap.Round)
        }
        AppGlyph.map, AppGlyph.mapSlash -> {
            drawPath(path(3.4f, 6.6f, 9f, 4f, 15f, 6.6f, 20.6f, 4f, 20.6f, 17.4f, 15f, 20f, 9f, 17.4f, 3.4f, 20f, close = true), tint, style = line(w))
            drawLine(tint, Offset(9f, 4f), Offset(9f, 17.4f), 1.6f, StrokeCap.Round)
            drawLine(tint, Offset(15f, 6.6f), Offset(15f, 20f), 1.6f, StrokeCap.Round)
            if (glyph == AppGlyph.mapSlash) drawLine(tint, Offset(3.4f, 3.4f), Offset(20.6f, 20.6f), 2.1f, StrokeCap.Round)
        }
        AppGlyph.suitcase, AppGlyph.suitcaseFill -> {
            val handle = path(9.2f, 7.2f, 9.2f, 5.2f, 14.8f, 5.2f, 14.8f, 7.2f)
            drawPath(handle, tint, style = line(w))
            val body = Rect(3.8f, 7.4f, 20.2f, 19.6f)
            if (glyph == AppGlyph.suitcaseFill) {
                drawRoundRect(tint, body.topLeft, body.size, CornerRadius(2.6f, 2.6f))
            } else {
                drawRoundRect(tint, body.topLeft, body.size, CornerRadius(2.6f, 2.6f), style = line(w))
                drawLine(tint, Offset(8.6f, 7.6f), Offset(8.6f, 19.4f), 1.5f, StrokeCap.Round)
                drawLine(tint, Offset(15.4f, 7.6f), Offset(15.4f, 19.4f), 1.5f, StrokeCap.Round)
            }
        }
        AppGlyph.squareAndPencil -> {
            drawPath(path(11.4f, 5.2f, 6.2f, 5.2f, 4.4f, 7f, 4.4f, 17.8f, 6.2f, 19.6f, 17f, 19.6f, 18.8f, 17.8f, 18.8f, 12.6f), tint, style = line(w))
            drawPath(path(10.2f, 14f, 10.8f, 11.2f, 17.6f, 4.4f, 19.6f, 6.4f, 12.8f, 13.2f, close = true), tint, style = line(1.7f))
        }
        AppGlyph.pencilLine -> {
            drawPath(path(4.8f, 16.4f, 5.6f, 13.2f, 14.4f, 4.4f, 17.6f, 7.6f, 8.8f, 16.4f, close = true), tint, style = line(w))
            drawLine(tint, Offset(12.4f, 6.4f), Offset(15.6f, 9.6f), 1.7f, StrokeCap.Round)
            drawLine(tint, Offset(4.2f, 20.2f), Offset(19.8f, 20.2f), 1.9f, StrokeCap.Round)
        }
        AppGlyph.camera, AppGlyph.cameraFill -> {
            val body = Path().apply {
                moveTo(3.6f, 9f)
                cubicTo(3.6f, 7.9f, 4.5f, 7f, 5.6f, 7f)
                lineTo(8.2f, 7f)
                lineTo(9.6f, 4.8f)
                lineTo(14.4f, 4.8f)
                lineTo(15.8f, 7f)
                lineTo(18.4f, 7f)
                cubicTo(19.5f, 7f, 20.4f, 7.9f, 20.4f, 9f)
                lineTo(20.4f, 17.4f)
                cubicTo(20.4f, 18.5f, 19.5f, 19.4f, 18.4f, 19.4f)
                lineTo(5.6f, 19.4f)
                cubicTo(4.5f, 19.4f, 3.6f, 18.5f, 3.6f, 17.4f)
                close()
            }
            if (glyph == AppGlyph.cameraFill) {
                drawPath(body, tint)
                drawCircle(Color.Black, 3.6f, Offset(12f, 12.8f), blendMode = BlendMode.DstOut)
                drawCircle(tint, 2.3f, Offset(12f, 12.8f))
            } else {
                drawPath(body, tint, style = line(w))
                drawCircle(tint, 3.5f, Offset(12f, 12.8f), style = line(w))
            }
        }
        AppGlyph.photo -> {
            drawRoundRect(tint, Offset(3.6f, 5f), Size(16.8f, 14f), CornerRadius(2.4f, 2.4f), style = line(w))
            drawCircle(tint, 1.6f, Offset(8.6f, 9.4f))
            drawPath(path(4.4f, 17.2f, 9.6f, 12.2f, 12.6f, 15f, 15.4f, 12.4f, 19.6f, 16.4f), tint, style = line(1.7f))
        }
        AppGlyph.flagFill -> {
            drawLine(tint, Offset(6.2f, 20.8f), Offset(6.2f, 3.8f), 1.9f, StrokeCap.Round)
            drawPath(path(6.2f, 4.2f, 18.2f, 4.2f, 15.2f, 8.6f, 18.2f, 13f, 6.2f, 13f, close = true), tint, style = Fill)
        }
        AppGlyph.flagCheckered -> {
            drawLine(tint, Offset(5.8f, 20.8f), Offset(5.8f, 3.8f), 1.9f, StrokeCap.Round)
            drawRect(tint, Offset(5.8f, 4.2f), Size(13f, 9f), style = line(1.6f))
            for (row in 0 until 2) for (col in 0 until 3) {
                if ((row + col) % 2 == 0) drawRect(tint, Offset(5.8f + col * 4.33f, 4.2f + row * 4.5f), Size(4.33f, 4.5f))
            }
        }
        AppGlyph.mappinAndEllipse -> {
            drawOval(tint, Offset(5f, 16.4f), Size(14f, 4.6f), style = line(1.6f))
            val pin = Path().apply {
                moveTo(12f, 18.6f)
                lineTo(12f, 11.8f)
            }
            drawPath(pin, tint, style = line(w))
            drawCircle(tint, 4.2f, Offset(12f, 7.4f))
        }
        AppGlyph.arrowUpLeftAndArrowDownRight -> {
            drawPath(path(4.6f, 10.4f, 4.6f, 4.6f, 10.4f, 4.6f), tint, style = line(w))
            drawLine(tint, Offset(4.8f, 4.8f), Offset(10.2f, 10.2f), w, StrokeCap.Round)
            drawPath(path(19.4f, 13.6f, 19.4f, 19.4f, 13.6f, 19.4f), tint, style = line(w))
            drawLine(tint, Offset(19.2f, 19.2f), Offset(13.8f, 13.8f), w, StrokeCap.Round)
        }
        AppGlyph.arrowDownRightAndArrowUpLeft -> {
            drawPath(path(10.4f, 4.6f, 10.4f, 10.4f, 4.6f, 10.4f), tint, style = line(w))
            drawLine(tint, Offset(10.2f, 10.2f), Offset(4.8f, 4.8f), w, StrokeCap.Round)
            drawPath(path(13.6f, 19.4f, 13.6f, 13.6f, 19.4f, 13.6f), tint, style = line(w))
            drawLine(tint, Offset(13.8f, 13.8f), Offset(19.2f, 19.2f), w, StrokeCap.Round)
        }
        AppGlyph.arrowClockwise -> {
            val arc = Path().apply { arcTo(Rect(Offset(12f, 12.6f), 7.4f), -60f, 300f, true) }
            drawPath(arc, tint, style = line(w))
            drawPath(path(15.4f, 3.6f, 15.9f, 6.3f, 13.2f, 7.4f), tint, style = line(w))
        }
        AppGlyph.exclamationmarkTriangleFill -> {
            val triangle = Path().apply {
                moveTo(10.3f, 3.9f)
                cubicTo(11.1f, 2.6f, 12.9f, 2.6f, 13.7f, 3.9f)
                lineTo(21.2f, 17.4f)
                cubicTo(21.9f, 18.7f, 21f, 20.2f, 19.5f, 20.2f)
                lineTo(4.5f, 20.2f)
                cubicTo(3f, 20.2f, 2.1f, 18.7f, 2.8f, 17.4f)
                close()
            }
            drawPath(triangle, tint)
            drawLine(Color.Black, Offset(12f, 8.4f), Offset(12f, 13.4f), 2.2f, StrokeCap.Round, blendMode = BlendMode.DstOut)
            drawCircle(Color.Black, 1.3f, Offset(12f, 16.8f), blendMode = BlendMode.DstOut)
        }
        AppGlyph.car -> {
            val body = Path().apply {
                moveTo(3.6f, 15.8f)
                lineTo(3.6f, 12.6f)
                cubicTo(3.6f, 11.6f, 4.2f, 10.9f, 5f, 10.6f)
                lineTo(6.8f, 6.6f)
                cubicTo(7.2f, 5.8f, 7.9f, 5.4f, 8.8f, 5.4f)
                lineTo(15.2f, 5.4f)
                cubicTo(16.1f, 5.4f, 16.8f, 5.8f, 17.2f, 6.6f)
                lineTo(19f, 10.6f)
                cubicTo(19.8f, 10.9f, 20.4f, 11.6f, 20.4f, 12.6f)
                lineTo(20.4f, 15.8f)
                close()
            }
            drawPath(body, tint, style = line(w))
            drawLine(tint, Offset(5.4f, 10.6f), Offset(18.6f, 10.6f), 1.6f, StrokeCap.Round)
            drawLine(tint, Offset(6.4f, 15.8f), Offset(6.4f, 18.4f), 2.4f, StrokeCap.Round)
            drawLine(tint, Offset(17.6f, 15.8f), Offset(17.6f, 18.4f), 2.4f, StrokeCap.Round)
            drawCircle(tint, 1.1f, Offset(7.4f, 13.2f))
            drawCircle(tint, 1.1f, Offset(16.6f, 13.2f))
        }
        AppGlyph.tray -> {
            drawPath(path(3.6f, 13f, 6.6f, 5.4f, 17.4f, 5.4f, 20.4f, 13f, 20.4f, 18.6f, 3.6f, 18.6f, close = true), tint, style = line(w))
            drawPath(path(3.8f, 13f, 8.6f, 13f, 9.8f, 15.2f, 14.2f, 15.2f, 15.4f, 13f, 20.2f, 13f), tint, style = line(1.7f))
        }
        AppGlyph.tablecells -> {
            drawRoundRect(tint, Offset(3.6f, 4.8f), Size(16.8f, 14.4f), CornerRadius(2.2f, 2.2f), style = line(w))
            drawLine(tint, Offset(3.8f, 9.6f), Offset(20.2f, 9.6f), 1.5f)
            drawLine(tint, Offset(3.8f, 14.4f), Offset(20.2f, 14.4f), 1.5f)
            drawLine(tint, Offset(9.2f, 9.6f), Offset(9.2f, 19f), 1.5f)
            drawLine(tint, Offset(14.8f, 9.6f), Offset(14.8f, 19f), 1.5f)
        }
        AppGlyph.squareAndArrowDown -> {
            drawLine(tint, Offset(12f, 3.6f), Offset(12f, 14.2f), w, StrokeCap.Round)
            drawPath(path(8.4f, 10.6f, 12f, 14.2f, 15.6f, 10.6f), tint, style = line(w))
            drawPath(path(9f, 8.2f, 6.2f, 8.2f, 6.2f, 20.2f, 17.8f, 20.2f, 17.8f, 8.2f, 15f, 8.2f), tint, style = line(w))
        }
        AppGlyph.magnifyingglass -> {
            drawCircle(tint, 6f, Offset(10.4f, 10.4f), style = line(2.1f))
            drawLine(tint, Offset(14.8f, 14.8f), Offset(19.8f, 19.8f), 2.4f, StrokeCap.Round)
        }
        AppGlyph.minusCircle -> {
            drawCircle(tint, 8.8f, Offset(12f, 12f), style = line(w))
            drawLine(tint, Offset(8f, 12f), Offset(16f, 12f), 2f, StrokeCap.Round)
        }
        AppGlyph.plusCircleFill -> {
            drawCircle(tint, 10.5f, Offset(12f, 12f))
            drawLine(Color.Black, Offset(12f, 7.4f), Offset(12f, 16.6f), 2.3f, StrokeCap.Round, blendMode = BlendMode.DstOut)
            drawLine(Color.Black, Offset(7.4f, 12f), Offset(16.6f, 12f), 2.3f, StrokeCap.Round, blendMode = BlendMode.DstOut)
        }
        AppGlyph.checkmarkCircle -> {
            drawCircle(tint, 9f, Offset(12f, 12f), style = line(w))
            drawPath(path(7.8f, 12.4f, 10.8f, 15.2f, 16.2f, 9f), tint, style = line(2f))
        }
        AppGlyph.circle -> drawCircle(tint, 9f, Offset(12f, 12f), style = line(w))
        AppGlyph.bubbleRight -> {
            val bubble = Path().apply {
                moveTo(12f, 4f)
                cubicTo(16.9f, 4f, 20.6f, 7.2f, 20.6f, 11.2f)
                cubicTo(20.6f, 15.2f, 16.9f, 18.4f, 12f, 18.4f)
                cubicTo(11f, 18.4f, 10.1f, 18.3f, 9.2f, 18f)
                lineTo(5f, 20.2f)
                lineTo(5.8f, 16.2f)
                cubicTo(4.2f, 14.9f, 3.4f, 13.1f, 3.4f, 11.2f)
                cubicTo(3.4f, 7.2f, 7.1f, 4f, 12f, 4f)
                close()
            }
            drawPath(bubble, tint, style = line(w))
        }
        AppGlyph.heart -> {
            val heart = Path().apply {
                moveTo(12f, 19.6f)
                cubicTo(6.4f, 15.6f, 3.6f, 12.4f, 3.6f, 9.2f)
                cubicTo(3.6f, 6.6f, 5.6f, 4.6f, 8.1f, 4.6f)
                cubicTo(9.8f, 4.6f, 11.2f, 5.5f, 12f, 6.9f)
                cubicTo(12.8f, 5.5f, 14.2f, 4.6f, 15.9f, 4.6f)
                cubicTo(18.4f, 4.6f, 20.4f, 6.6f, 20.4f, 9.2f)
                cubicTo(20.4f, 12.4f, 17.6f, 15.6f, 12f, 19.6f)
                close()
            }
            drawPath(heart, tint, style = line(w))
        }
        AppGlyph.calendar -> {
            drawRoundRect(tint, Offset(4f, 5.4f), Size(16f, 14.6f), CornerRadius(2.6f, 2.6f), style = line(w))
            drawLine(tint, Offset(4.2f, 9.8f), Offset(19.8f, 9.8f), 1.7f)
            drawLine(tint, Offset(8.4f, 3.6f), Offset(8.4f, 7f), w, StrokeCap.Round)
            drawLine(tint, Offset(15.6f, 3.6f), Offset(15.6f, 7f), w, StrokeCap.Round)
        }
        AppGlyph.clock -> {
            drawCircle(tint, 8.8f, Offset(12f, 12f), style = line(w))
            drawPath(path(12f, 7f, 12f, 12f, 15.4f, 14.2f), tint, style = line(w))
        }
        AppGlyph.speedometer -> {
            val dial = Path().apply { arcTo(Rect(Offset(12f, 14f), 8.6f), 160f, 220f, true) }
            drawPath(dial, tint, style = line(w))
            drawLine(tint, Offset(12f, 14f), Offset(16f, 9.4f), 2f, StrokeCap.Round)
            drawCircle(tint, 1.6f, Offset(12f, 14f))
        }
        AppGlyph.mountain -> {
            drawPath(path(2.8f, 19.2f, 9.2f, 8.4f, 12.8f, 13.6f, 15.4f, 10.2f, 21.2f, 19.2f, close = true), tint, style = line(w))
        }
        AppGlyph.bolt -> {
            drawPath(path(13.4f, 3f, 5.8f, 13.4f, 11.4f, 13.4f, 10.4f, 21f, 18.2f, 10.4f, 12.6f, 10.4f, close = true), tint, style = Fill)
        }
        AppGlyph.fuelpump -> {
            drawRoundRect(tint, Offset(4.4f, 4f), Size(9.6f, 16.2f), CornerRadius(1.8f, 1.8f), style = line(w))
            drawRect(tint, Offset(6.4f, 6.2f), Size(5.6f, 4f))
            drawPath(path(14f, 9f, 16.6f, 9f, 17.6f, 10f, 17.6f, 16.2f, 18.6f, 17.2f, 19.6f, 16.2f, 19.6f, 8.4f, 17.6f, 6.4f), tint, style = line(1.7f))
        }
        AppGlyph.roadLanes -> {
            drawLine(tint, Offset(7.2f, 3.8f), Offset(4.4f, 20.2f), w, StrokeCap.Round)
            drawLine(tint, Offset(16.8f, 3.8f), Offset(19.6f, 20.2f), w, StrokeCap.Round)
            drawLine(tint, Offset(12f, 4.4f), Offset(12f, 7.6f), 1.7f, StrokeCap.Round)
            drawLine(tint, Offset(12f, 10.4f), Offset(12f, 13.6f), 1.7f, StrokeCap.Round)
            drawLine(tint, Offset(12f, 16.4f), Offset(12f, 19.6f), 1.7f, StrokeCap.Round)
        }
        AppGlyph.arrowTriangleSwap -> {
            drawPath(path(4.6f, 8.6f, 17.6f, 8.6f), tint, style = line(w))
            drawPath(path(14.6f, 5.4f, 17.8f, 8.6f, 14.6f, 11.8f), tint, style = line(w))
            drawPath(path(19.4f, 15.4f, 6.4f, 15.4f), tint, style = line(w))
            drawPath(path(9.4f, 12.2f, 6.2f, 15.4f, 9.4f, 18.6f), tint, style = line(w))
        }
        AppGlyph.pause -> {
            drawRoundRect(tint, Offset(6.6f, 5f), Size(3.6f, 14f), CornerRadius(1.2f, 1.2f))
            drawRoundRect(tint, Offset(13.8f, 5f), Size(3.6f, 14f), CornerRadius(1.2f, 1.2f))
        }
        AppGlyph.play -> {
            val triangle = Path().apply {
                moveTo(7.6f, 5.4f)
                lineTo(18.6f, 12f)
                lineTo(7.6f, 18.6f)
                close()
            }
            drawPath(triangle, tint)
            drawPath(triangle, tint, style = line(1.6f))
        }
        AppGlyph.airplane -> {
            val plane = Path().apply {
                moveTo(12f, 2.6f)
                cubicTo(12.9f, 2.6f, 13.4f, 3.6f, 13.4f, 4.8f)
                lineTo(13.4f, 9.6f)
                lineTo(21f, 14f)
                lineTo(21f, 16f)
                lineTo(13.4f, 13.6f)
                lineTo(13.4f, 18.2f)
                lineTo(15.8f, 19.9f)
                lineTo(15.8f, 21.4f)
                lineTo(12f, 20.4f)
                lineTo(8.2f, 21.4f)
                lineTo(8.2f, 19.9f)
                lineTo(10.6f, 18.2f)
                lineTo(10.6f, 13.6f)
                lineTo(3f, 16f)
                lineTo(3f, 14f)
                lineTo(10.6f, 9.6f)
                lineTo(10.6f, 4.8f)
                cubicTo(10.6f, 3.6f, 11.1f, 2.6f, 12f, 2.6f)
                close()
            }
            drawPath(plane, tint)
        }
        else -> Unit
    }
}
