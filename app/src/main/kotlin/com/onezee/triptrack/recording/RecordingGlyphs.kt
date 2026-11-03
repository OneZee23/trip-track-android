// iOS: the SF Symbols of TrackingView, IdleHUDView, VehicleChip, GPSIndicatorView, TripCompleteSummaryView
// and TripRevealedBlock that the house set has no drawing for.
package com.onezee.triptrack.recording

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
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.LocalContentColor

/**
 * The symbols of the record screen and the trip summary, drawn once for the weight and size the
 * iPhone sets them in (the SF Symbols are not copied: each was drawn against a render of the
 * symbol, for its ink only).
 *
 * Unlike the house `Glyph`, a drawing here is placed by its INK: the call site passes the width
 * and height the symbol's ink measures on the iPhone at that point size and weight
 * ([RecordingInk]), so a glyph that is 10.0 × 10.6 pt there is 10.0 × 10.6 dp here. The shapes are
 * authored on a grid 100 units tall; [aspect] is their width on that grid over 100.
 */
internal enum class RecordingGlyph(val aspect: Float) {
    /** location, medium: the outline of the map's arrow. */
    location(1.0f),

    /** location.fill, medium. */
    locationFill(1.054f),

    /** location.north.line.fill, medium: the arrow of the heading mode with its short north line. */
    locationNorthLineFill(0.565f),

    /** person.fill, semibold. */
    personFill(0.942f),

    /** chevron.up.chevron.down, bold: the two-way chevron of a chip that opens a choice. */
    chevronUpChevronDown(0.708f),

    /** point.topleft.down.curvedto.point.bottomright.up: the distance tile's icon. */
    route(0.812f),

    /** mountain.2: the altitude tile's icon. */
    mountainTwo(1.99f),

    /** antenna.radiowaves.left.and.right. */
    antenna(1.185f),

    /** antenna.radiowaves.left.and.right.slash. */
    antennaSlash(1.053f),

    /** exclamationmark.circle.fill. */
    exclamationCircleFill(1f),

    /** text.alignleft. */
    textAlignLeft(1.164f),

    /** flame.fill. */
    flameFill(0.77f),

    /** arrow.triangle.2.circlepath. */
    circlePath(1.197f),

    /** seal.fill. */
    sealFill(1f),
}

/** The ink boxes of the symbols at the sizes TripTrack sets them in, in points (= dp), measured once on macOS. */
internal object RecordingInk {
    val locationMedium18 = Ink(17.12f, 17.12f)
    val locationFillMedium18 = Ink(17.12f, 16.25f)
    val locationNorthLineFillMedium18 = Ink(13.12f, 21.0f)
    val personFillSemibold12 = Ink(10.0f, 10.62f)
    val chevronUpChevronDownBold10 = Ink(7.38f, 10.5f)
    val routeRegular13 = Ink(11.5f, 14.0f)
    val mountainTwoRegular13 = Ink(22.38f, 11.25f)
    val antennaSemibold14 = Ink(15.98f, 12.95f)
    val antennaRegular14 = Ink(13.75f, 12.75f)
    val antennaSlashSemibold15 = Ink(16.5f, 15.75f)
    val exclamationCircleFillSemibold14 = Ink(14.25f, 14.25f)
    val textAlignLeftRegular15 = Ink(14.75f, 12.75f)
    val flameFillRegular14 = Ink(11.38f, 14.88f)
    val circlePathRegular14 = Ink(16.75f, 14.0f)
    val sealFillSemibold14 = Ink(15.75f, 15.75f)

    data class Ink(val width: Float, val height: Float)
}

@Composable
internal fun RecordingGlyphIcon(
    glyph: RecordingGlyph,
    ink: RecordingInk.Ink,
    tint: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    // Offscreen: the knock-outs (the slash's gap, the flame's hollow, the mark of the «!») clear this glyph's own pixels only.
    Canvas(modifier.size(ink.width.dp, ink.height.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        scale(size.width / (glyph.aspect * GRID), size.height / GRID, pivot = Offset.Zero) { drawRecordingGlyph(glyph, tint) }
    }
}

/** The two solid marks of the controls, drawn by size: `pause.fill` and `play.fill` at 12 and 19 bold are not the house glyphs' proportions. */
internal enum class RecordingMark { pause, play }

/** iOS pause.fill / play.fill: two bars, or a rounded triangle, of exactly [width] × [height] dp. */
@Composable
internal fun RecordingShape(mark: RecordingMark, width: Float, height: Float, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width.dp, height.dp)) {
        when (mark) {
            RecordingMark.pause -> {
                // Two bars of 41 % with 18 % between, their corners about a quarter of a bar.
                val bar = size.width * 0.41f
                val radius = CornerRadius(bar * 0.27f)
                drawRoundRect(tint, Offset.Zero, Size(bar, size.height), radius)
                drawRoundRect(tint, Offset(size.width - bar, 0f), Size(bar, size.height), radius)
            }
            RecordingMark.play -> {
                // The triangle's corners are rounded by a round-joined stroke of its own ink.
                val round = size.height * 0.07f
                val triangle = Path().apply {
                    moveTo(round, round); lineTo(size.width - round, size.height / 2); lineTo(round, size.height - round); close()
                }
                drawPath(triangle, tint, style = Fill)
                drawPath(triangle, tint, style = Stroke(round * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private const val GRID = 100f

private fun line(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun polygon(vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    var i = 2
    while (i < points.size) {
        lineTo(points[i], points[i + 1])
        i += 2
    }
    close()
}

private fun DrawScope.drawRecordingGlyph(glyph: RecordingGlyph, tint: Color) {
    when (glyph) {
        RecordingGlyph.location -> drawPath(polygon(94.75f, 5.25f, 5.25f, 47f, 50f, 50f, 53f, 94.75f), tint, style = line(10.5f))

        RecordingGlyph.locationFill -> {
            // Filled, and rounded at the corners by a round-joined stroke of the same ink.
            val arrow = polygon(100.4f, 5f, 5f, 49f, 50.5f, 52f, 56f, 95f)
            drawPath(arrow, tint, style = Fill)
            drawPath(arrow, tint, style = line(10f))
        }

        RecordingGlyph.locationNorthLineFill -> {
            drawRoundRect(tint, Offset(24.55f, 0f), Size(7.4f, 24.5f), CornerRadius(3.7f))
            val arrow = polygon(28.25f, 35f, 51.5f, 95.5f, 28.25f, 79f, 5f, 95.5f)
            drawPath(arrow, tint, style = Fill)
            drawPath(arrow, tint, style = line(9f))
        }

        RecordingGlyph.personFill -> {
            drawOval(tint, Offset(24.9f, 0f), Size(44.4f, 48.6f))
            drawPath(Path().apply {
                moveTo(0f, 92f)
                cubicTo(0f, 69f, 22f, 58.3f, 47.1f, 58.3f)
                cubicTo(72.2f, 58.3f, 94.2f, 69f, 94.2f, 92f)
                quadraticTo(94.2f, 100f, 86f, 100f)
                lineTo(8.2f, 100f)
                quadraticTo(0f, 100f, 0f, 92f)
                close()
            }, tint)
        }

        RecordingGlyph.chevronUpChevronDown -> {
            drawPath(Path().apply { moveTo(5.9f, 35f); lineTo(35.4f, 5.9f); lineTo(64.9f, 35f) }, tint, style = line(11.8f))
            drawPath(Path().apply { moveTo(5.9f, 65f); lineTo(35.4f, 94.1f); lineTo(64.9f, 65f) }, tint, style = line(11.8f))
        }

        RecordingGlyph.route -> {
            drawCircle(tint, 11f, Offset(14.6f, 15.3f), style = Stroke(7.5f))
            drawCircle(tint, 11f, Offset(66.6f, 84.7f), style = Stroke(7.5f))
            drawPath(Path().apply {
                moveTo(14.6f, 26.3f); lineTo(14.6f, 36f)
                cubicTo(14.6f, 47f, 22f, 50f, 36f, 54f)
                cubicTo(52f, 58.5f, 66.6f, 58f, 66.6f, 73.7f)
            }, tint, style = line(6.8f))
        }

        RecordingGlyph.mountainTwo -> {
            val outline = line(9f)
            // The big peak, then the small one in front of its slope, sharing the base.
            drawPath(polygon(57f, 96f, 128f, 4f, 195f, 96f), tint, style = outline)
            drawPath(Path().apply { moveTo(4f, 96f); lineTo(52f, 26f); lineTo(81f, 63f) }, tint, style = outline)
            drawPath(Path().apply { moveTo(4f, 96f); lineTo(57f, 96f) }, tint, style = outline)
            // The snow lines.
            val snow = line(6f)
            drawPath(Path().apply {
                moveTo(34f, 54f); quadraticTo(40f, 47f, 46f, 54f); quadraticTo(52f, 61f, 58f, 54f)
            }, tint, style = snow)
            drawPath(Path().apply {
                moveTo(101f, 47f); quadraticTo(108f, 39f, 115f, 47f); quadraticTo(122f, 55f, 129f, 47f); quadraticTo(136f, 39f, 143f, 47f)
            }, tint, style = snow)
        }

        RecordingGlyph.antenna -> drawAntenna(tint, 118.5f, stroke = 8.5f)

        RecordingGlyph.antennaSlash -> {
            // The waves and the mast, a gap cut along the slash, then the slash itself.
            drawAntenna(tint, 105.3f, stroke = 8.5f, cy = 40f, mastBottom = 100f, offset = Offset(0f, 0f), narrow = true)
            val from = Offset(5f, 3f)
            val to = Offset(96.5f, 93f)
            drawLine(Color.Black, from, to, 22f, StrokeCap.Butt, blendMode = BlendMode.Clear)
            drawLine(tint, from, to, 8.5f, StrokeCap.Round)
        }

        RecordingGlyph.exclamationCircleFill -> {
            drawCircle(tint, 50f, Offset(50f, 50f))
            drawRoundRect(Color.Black, Offset(45.5f, 21f), Size(9f, 38f), CornerRadius(4.5f), blendMode = BlendMode.Clear)
            drawCircle(Color.Black, 5.4f, Offset(50f, 73f), blendMode = BlendMode.Clear)
        }

        RecordingGlyph.textAlignLeft -> {
            val width = 116.4f
            for ((index, length) in listOf(width, width * 0.63f, width, width * 0.63f).withIndex()) {
                val y = 4.5f + index * 30.33f
                drawRoundRect(tint, Offset(0f, y - 4.5f), Size(length, 9f), CornerRadius(4.5f))
            }
        }

        RecordingGlyph.flameFill -> {
            // The tip curls to the upper left, a small tongue stands on the left, and the hollow is a flame of its own.
            drawPath(Path().apply {
                moveTo(13f, 0f)
                cubicTo(30f, 5f, 77f, 20f, 77f, 62f)
                cubicTo(77f, 85f, 60f, 100f, 38.5f, 100f)
                cubicTo(17f, 100f, 0f, 85f, 0f, 66f)
                cubicTo(0f, 52f, 6f, 47f, 6f, 37f)
                cubicTo(6f, 30f, 9f, 27f, 12.5f, 27.5f)
                cubicTo(17f, 28f, 20f, 33f, 22f, 38f)
                cubicTo(24f, 30f, 22f, 12f, 13f, 0f)
                close()
            }, tint)
            drawPath(Path().apply {
                moveTo(39f, 42f)
                cubicTo(44f, 52f, 56f, 57f, 56f, 72f)
                cubicTo(56f, 83f, 48f, 88f, 40f, 88f)
                cubicTo(29f, 88f, 22f, 82f, 22f, 73f)
                cubicTo(22f, 66f, 28f, 62f, 30f, 55f)
                cubicTo(31f, 60f, 34f, 62f, 36f, 60f)
                cubicTo(38f, 56f, 36f, 48f, 39f, 42f)
                close()
            }, Color.Black, blendMode = BlendMode.Clear)
        }

        RecordingGlyph.circlePath -> {
            val arcs = line(8f)
            val topLeft = Offset(59.85f - 46f, 4f)
            val box = Size(92f, 92f)
            drawArc(tint, 215f, 140f, false, topLeft, box, style = arcs)
            drawArc(tint, 35f, 140f, false, topLeft, box, style = arcs)
            // The heads: one at the end of the upper arc pointing down, one at the end of the lower pointing up.
            drawPath(polygon(91.5f, 45f, 119.7f, 45f, 105.5f, 70f), tint, style = Fill)
            drawPath(polygon(28.2f, 55f, 0f, 55f, 14.2f, 30f), tint, style = Fill)
        }

        RecordingGlyph.sealFill -> {
            // Eight soft lobes: the radius swings between 50 at a lobe and 43 in the notch between two.
            drawPath(Path().apply {
                for (step in 0..360) {
                    val angle = Math.toRadians(step.toDouble())
                    val radius = 50f - 3.5f * (1f - kotlin.math.cos(8 * angle).toFloat())
                    val x = 50f + radius * kotlin.math.cos(angle).toFloat()
                    val y = 50f + radius * kotlin.math.sin(angle).toFloat()
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }, tint)
        }
    }
}

/**
 * The mast and the four waves of the antenna. [narrow] is the slash's variant: the same drawing on a
 * narrower grid, its waves a little tighter, so the slash lies across them the way the symbol's does.
 */
private fun DrawScope.drawAntenna(tint: Color, width: Float, stroke: Float, cy: Float = 37.7f, mastBottom: Float = 100f, offset: Offset = Offset.Zero, narrow: Boolean = false) {
    val cx = width / 2 + offset.x
    val inner = if (narrow) 29f else 32f
    val outer = if (narrow) 48f else 55f
    val waves = line(stroke)
    drawCircle(tint, 10f, Offset(cx, cy + offset.y))
    drawLine(tint, Offset(cx, cy + offset.y), Offset(cx, mastBottom - stroke / 2), stroke, StrokeCap.Round)
    for (radius in listOf(inner, outer)) {
        val box = Size(radius * 2, radius * 2)
        val topLeft = Offset(cx - radius, cy + offset.y - radius)
        drawArc(tint, 142f, 76f, false, topLeft, box, style = waves)
        drawArc(tint, -38f, 76f, false, topLeft, box, style = waves)
    }
}
