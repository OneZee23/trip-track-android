package com.onezee.triptrack.designsystem.theme

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * iOS `.shadow(color:radius:x:y:)` drawn under [shape].
 *
 * Core Animation's shadow radius is the Gaussian sigma. Skia's shadow layer takes a radius that it
 * turns into sigma as `0.57735 × radius + 0.5`, so the radius is converted back: the same number
 * gives the same softness as on the iPhone. Only the part outside the shape is drawn, so a
 * translucent fill does not show a dark slab through itself.
 */
fun Modifier.iosShadow(shape: Shape, color: Color, radius: Dp, x: Dp = 0.dp, y: Dp = 0.dp): Modifier =
    if (color.alpha == 0f) this else this then IosShadowElement(shape, color, radius, x, y)

private data class IosShadowElement(
    val shape: Shape,
    val color: Color,
    val radius: Dp,
    val x: Dp,
    val y: Dp,
) : ModifierNodeElement<IosShadowNode>() {
    override fun create() = IosShadowNode(shape, color, radius, x, y)
    override fun update(node: IosShadowNode) {
        node.shape = shape
        node.color = color
        node.radius = radius
        node.x = x
        node.y = y
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "iosShadow"
        properties["color"] = color
        properties["radius"] = radius
    }
}

private class IosShadowNode(
    var shape: Shape,
    var color: Color,
    var radius: Dp,
    var x: Dp,
    var y: Dp,
) : Modifier.Node(), DrawModifierNode {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun ContentDrawScope.draw() {
        val path = when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }
        val sigma = radius.toPx()
        val skiaRadius = if (sigma <= 0.5f) 0.01f else (sigma - 0.5f) / 0.57735f
        // Opaque paint so the shadow takes the alpha of its own colour (Paint.setShadowLayer).
        paint.color = android.graphics.Color.BLACK
        paint.setShadowLayer(skiaRadius, x.toPx(), y.toPx(), color.toArgb())
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val shapePath = path.asAndroidPath()
            native.save()
            native.clipOutPath(shapePath)
            native.drawPath(shapePath, paint)
            native.restore()
        }
        drawContent()
    }
}
