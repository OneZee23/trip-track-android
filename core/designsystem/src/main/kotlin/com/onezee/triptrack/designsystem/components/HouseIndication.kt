// iOS: AppTheme.swift (PressableCardStyle), TapOnlyButtonStyle.swift: what a press looks like.
package com.onezee.triptrack.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * iOS `.buttonStyle(.plain)`: no decoration at rest, the label dims under the finger. The theme
 * provides it as `LocalIndication`, so a `clickable`, `selectable` or `toggleable` that names no
 * indication of its own still answers the finger the way the app does, instead of drawing the
 * foundation's default: a 30 % black rectangle over the whole node that ignores rounded corners
 * (the 3 November audit, SH-06, counted 84 of them).
 *
 * Cards that iOS gives `PressableCardStyle` (a slight shrink and fade) use [PressableCard] or
 * [cardPressFeedback]; this is the answer for everything else. The dim is the one of
 * [plainPressFeedback]: instant on the press, 160 ms back on the release.
 */
object HouseIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PlainPressNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === HouseIndication
    override fun hashCode(): Int = -1
}

private class PlainPressNode(private val interactionSource: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(1f)

    override fun onAttach() {
        coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses += interaction
                    is PressInteraction.Release -> presses -= interaction.press
                    is PressInteraction.Cancel -> presses -= interaction.press
                }
                val pressed = presses.isNotEmpty()
                launch { alpha.animateTo(if (pressed) PLAIN_ALPHA else 1f, tween(if (pressed) 0 else 160)) { invalidateDraw() } }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val current = alpha.value
        if (current >= 1f) {
            drawContent()
            return
        }
        // The content dims as one piece, the way a label does, not each of its layers apart.
        drawIntoCanvas { canvas ->
            val paint = Paint().apply { this.alpha = current }
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
}
