package com.onezee.triptrack.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/** iOS `Capsule()`: a pill with half-height ends. */
val CapsuleShape = RoundedCornerShape(percent = 50)

/**
 * iOS `RoundedRectangle(cornerRadius:style: .continuous)`: the corner eases into the edge over
 * 1.53 × radius instead of meeting it in a quarter circle. The iPhone app uses it for the dialog
 * card and its buttons and for the toast; every other rounded rectangle there is circular, and so
 * is `RoundedCornerShape` here.
 *
 * The curve is the published reconstruction of UIKit's continuous rounded rectangle: three cubic
 * segments per corner, symmetric about the corner's diagonal. On a rectangle too small for the
 * full transition the radius shrinks so the two corners of a side still meet.
 */
@Immutable
class ContinuousRoundedRectangle(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(continuousRoundedRect(size, with(density) { radius.toPx() }))

    override fun equals(other: Any?): Boolean = other is ContinuousRoundedRectangle && other.radius == radius
    override fun hashCode(): Int = radius.hashCode()
}

private const val EDGE = 1.52866483f
private const val C1 = 1.08849323f
private const val C2 = 0.86840689f
private const val P1 = 0.66993427f
private const val P2 = 0.06549600f
private const val C3 = 0.43231340f
private const val C4 = 0.17605818f

internal fun continuousRoundedRect(size: Size, radius: Float): Path {
    val w = size.width
    val h = size.height
    val r = min(radius, min(w, h) / 2f / EDGE).coerceAtLeast(0f)
    return Path().apply {
        moveTo(EDGE * r, 0f)
        lineTo(w - EDGE * r, 0f)
        cubicTo(w - C1 * r, 0f, w - C2 * r, 0f, w - P1 * r, P2 * r)
        cubicTo(w - C3 * r, C4 * r, w - C4 * r, C3 * r, w - P2 * r, P1 * r)
        cubicTo(w, C2 * r, w, C1 * r, w, EDGE * r)
        lineTo(w, h - EDGE * r)
        cubicTo(w, h - C1 * r, w, h - C2 * r, w - P2 * r, h - P1 * r)
        cubicTo(w - C4 * r, h - C3 * r, w - C3 * r, h - C4 * r, w - P1 * r, h - P2 * r)
        cubicTo(w - C2 * r, h, w - C1 * r, h, w - EDGE * r, h)
        lineTo(EDGE * r, h)
        cubicTo(C1 * r, h, C2 * r, h, P1 * r, h - P2 * r)
        cubicTo(C3 * r, h - C4 * r, C4 * r, h - C3 * r, P2 * r, h - P1 * r)
        cubicTo(0f, h - C2 * r, 0f, h - C1 * r, 0f, h - EDGE * r)
        lineTo(0f, EDGE * r)
        cubicTo(0f, C1 * r, 0f, C2 * r, P2 * r, P1 * r)
        cubicTo(C4 * r, C3 * r, C3 * r, C4 * r, P1 * r, P2 * r)
        cubicTo(C2 * r, 0f, C1 * r, 0f, EDGE * r, 0f)
        close()
    }
}
