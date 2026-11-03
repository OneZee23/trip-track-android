// iOS: SF Symbols of the Я tab: gearshape, photo.badge.plus, square.grid.2x2.fill, line.3.horizontal,
// person.2.fill, car.side.fill, car.2.fill, plus (regular)
package com.onezee.triptrack.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.LocalContentColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The SF Symbols of «Я» the house set has no drawing for, on the same 24-unit grid as the design
 * system's glyphs (see SocialGlyph for the pattern). The artwork spans what its symbol spans at
 * `box / 1.2`, the convention of the call sites, so a call site passes the box it would pass for
 * the iPhone's point size × 1.2.
 */
internal enum class MeGlyph { gearshape, photoBadgePlus, squareGrid, lines3, personTwoFill, carSideFill, carTwoFill, plus }

@Composable
internal fun MeGlyphIcon(glyph: MeGlyph, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = 22.dp) {
    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    // Offscreen: the knock-outs (the badge's plus, the windows) clear this glyph's own pixels only.
    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.then(semantics)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) { drawMeGlyph(glyph, tint) }
    }
}

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun smooth(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** An eight-toothed ring: the tooth profile is a smoothed cosine, so the tips are flat and the roots round. */
private fun gearOutline(): Path {
    val teeth = 8
    val tip = 10.5f
    val root = 8.2f
    val steps = 960
    return Path().apply {
        for (i in 0..steps) {
            val angle = i * 2.0 * PI / steps
            val profile = smooth((cos(teeth * angle).toFloat() - 0.05f) / 0.6f)
            val radius = root + (tip - root) * profile
            val x = 12f + radius * cos(angle - PI / 2).toFloat()
            val y = 12f + radius * sin(angle - PI / 2).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private val gear = gearOutline()

private fun DrawScope.drawMeGlyph(glyph: MeGlyph, tint: Color) {
    when (glyph) {
        MeGlyph.gearshape -> {
            drawPath(gear, tint, style = line(2f))
            drawCircle(tint, 3.7f, Offset(12f, 12f), style = line(2f))
        }
        MeGlyph.photoBadgePlus -> {
            // `photo.badge.plus` at 14 pt, drawn for a 20.3 pt box (one unit is .846 pt): a 19 × 14 pt picture, a
            // frame with a filled sun and filled hills, and a filled badge on the lower corner with a gap round it.
            drawRoundRect(tint, Offset(2.075f, 4.425f), Size(17.85f, 13.8f), CornerRadius(2.125f), style = line(1.35f))
            drawCircle(tint, 2f, Offset(7.3f, 9.42f))
            val hills = Path().apply {
                moveTo(2.8f, 15.3f); lineTo(6.1f, 13.4f); lineTo(8.7f, 15.1f); lineTo(13.7f, 11.3f); lineTo(16.3f, 13f)
                lineTo(16.3f, 17.9f); lineTo(2.8f, 17.9f); close()
            }
            drawPath(hills, tint, style = Fill)
            drawPath(hills, tint, style = line(1.2f))
            drawCircle(Color.Black, 5f, Offset(19.7f, 16.3f), blendMode = BlendMode.Clear)
            drawCircle(tint, 4.2f, Offset(19.7f, 16.3f))
            drawLine(Color.Black, Offset(17.2f, 16.3f), Offset(22.2f, 16.3f), 1.5f, StrokeCap.Butt, blendMode = BlendMode.Clear)
            drawLine(Color.Black, Offset(19.7f, 13.8f), Offset(19.7f, 18.8f), 1.5f, StrokeCap.Butt, blendMode = BlendMode.Clear)
        }
        MeGlyph.squareGrid -> for (x in listOf(2.7f, 12.7f)) for (y in listOf(2.7f, 12.7f)) {
            drawRoundRect(tint, Offset(x, y), Size(8.6f, 8.6f), CornerRadius(2.3f), style = Fill)
        }
        MeGlyph.lines3 -> for (y in listOf(5.6f, 12f, 18.4f)) {
            drawLine(tint, Offset(3.6f, y), Offset(20.4f, y), 2.2f, StrokeCap.Round)
        }
        MeGlyph.personTwoFill -> {
            // The one behind, then a gap round the one in front so the two read apart.
            drawCircle(tint, 2.9f, Offset(17f, 7.9f))
            val behind = Path().apply {
                moveTo(14.6f, 13.1f); cubicTo(18.9f, 12.4f, 22.6f, 14.6f, 22.6f, 18.4f); cubicTo(22.6f, 19.4f, 22f, 20f, 21f, 20f)
                lineTo(16.8f, 20f); close()
            }
            drawPath(behind, tint, style = Fill)
            drawCircle(Color.Black, 4.9f, Offset(9.4f, 8.2f), blendMode = BlendMode.Clear)
            val frontBody = Path().apply {
                moveTo(1.4f, 19f); cubicTo(1.4f, 15.2f, 4.6f, 12.8f, 9.4f, 12.8f); cubicTo(14.2f, 12.8f, 17.4f, 15.2f, 17.4f, 19f)
                cubicTo(17.4f, 20.2f, 16.6f, 21f, 15.4f, 21f); lineTo(3.4f, 21f); cubicTo(2.2f, 21f, 1.4f, 20.2f, 1.4f, 19f); close()
            }
            drawPath(frontBody, Color.Black, style = Stroke(width = 3.2f, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            drawCircle(tint, 3.7f, Offset(9.4f, 8.2f))
            drawPath(frontBody, tint, style = Fill)
        }
        MeGlyph.carSideFill -> drawCarSide(tint, 1f, Offset.Zero)
        MeGlyph.carTwoFill -> {
            // A second car behind the first, up and to the left, with a gap round the one in front.
            drawCarSide(tint, .72f, Offset(-1.9f, -4.2f))
            drawPath(carBody(.86f, Offset(2.4f, 3.3f)), Color.Black, style = Stroke(width = 2.6f, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
            drawCarSide(tint, .86f, Offset(2.4f, 3.3f))
        }
        MeGlyph.plus -> {
            drawLine(tint, Offset(3.7f, 12f), Offset(20.3f, 12f), 1.6f, StrokeCap.Round)
            drawLine(tint, Offset(12f, 3.7f), Offset(12f, 20.3f), 1.6f, StrokeCap.Round)
        }
    }
}

private fun carBody(scale: Float, shift: Offset): Path {
    fun x(value: Float) = 12f + (value - 12f) * scale + shift.x
    fun y(value: Float) = 12f + (value - 12f) * scale + shift.y
    return Path().apply {
        moveTo(x(2.2f), y(16.2f)); lineTo(x(2.2f), y(12.9f)); cubicTo(x(2.2f), y(11.8f), x(2.9f), y(11.1f), x(3.9f), y(10.9f))
        lineTo(x(6.7f), y(10.3f)); lineTo(x(8.9f), y(6.8f)); cubicTo(x(9.4f), y(6.1f), x(10.1f), y(5.7f), x(10.9f), y(5.7f))
        lineTo(x(15.5f), y(5.7f)); cubicTo(x(16.3f), y(5.7f), x(17f), y(6.1f), x(17.5f), y(6.8f)); lineTo(x(19.2f), y(10.2f))
        lineTo(x(20.4f), y(10.6f)); cubicTo(x(21.4f), y(10.9f), x(21.9f), y(11.6f), x(21.9f), y(12.7f)); lineTo(x(21.9f), y(16.2f)); close()
    }
}

private fun DrawScope.drawCarSide(tint: Color, scale: Float, shift: Offset) {
    fun x(value: Float) = 12f + (value - 12f) * scale + shift.x
    fun y(value: Float) = 12f + (value - 12f) * scale + shift.y
    drawPath(carBody(scale, shift), tint, style = Fill)
    // The wheels sit in arches cut a little wider than the tyre.
    drawCircle(Color.Black, 3.7f * scale, Offset(x(7.4f), y(16.4f)), blendMode = BlendMode.Clear)
    drawCircle(Color.Black, 3.7f * scale, Offset(x(16.8f), y(16.4f)), blendMode = BlendMode.Clear)
    drawCircle(tint, 2.55f * scale, Offset(x(7.4f), y(16.4f)))
    drawCircle(tint, 2.55f * scale, Offset(x(16.8f), y(16.4f)))
    val glass = Path().apply {
        moveTo(x(8.6f), y(10.2f)); lineTo(x(10.2f), y(7.5f)); lineTo(x(11.8f), y(7.5f)); lineTo(x(11.8f), y(10.2f)); close()
        moveTo(x(13f), y(7.5f)); lineTo(x(15.2f), y(7.5f)); lineTo(x(16.7f), y(10.2f)); lineTo(x(13f), y(10.2f)); close()
    }
    drawPath(glass, Color.Black, style = Fill, blendMode = BlendMode.Clear)
}
