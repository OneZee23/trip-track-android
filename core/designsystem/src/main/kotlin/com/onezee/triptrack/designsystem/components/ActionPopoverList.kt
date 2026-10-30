package com.onezee.triptrack.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics

// iOS: ActionPopoverList.swift, ActionPopoverList.Item.
@Immutable
class ActionPopoverItem(
    val title: String,
    val glyph: AppGlyph,
    val isDestructive: Boolean = false,
    /** Named, so tests tap «Редактировать» by name instead of «the first button on screen». */
    val testTag: String? = null,
    val action: () -> Unit,
)

/**
 * iOS: ActionPopoverList.swift. The rows of the small popover behind a «…» button: a symbol and a
 * word, destructive ones in red, hairlines between them that start past the symbol.
 */
@Composable
fun ActionPopoverList(
    items: List<ActionPopoverItem>,
    onItemClick: (ActionPopoverItem) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 232.dp,
) {
    val colors = AppTheme.colors
    Column(modifier.width(width)) {
        items.forEachIndexed { index, item ->
            val interaction = remember { MutableInteractionSource() }
            val ink = if (item.isDestructive) AppTheme.red else colors.text
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onItemClick(item) }
                    .plainPressFeedback(interaction)
                    .padding(horizontal = 16.dp, vertical = 13.dp)
                    .then(if (item.testTag != null) Modifier.testTag(item.testTag) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    Glyph(item.glyph, contentDescription = null, tint = ink, size = 18.dp)
                }
                Text(item.title, style = inter(15f, InterWeight.medium), color = ink, maxLines = 2)
            }
            if (index < items.lastIndex) {
                Box(
                    Modifier
                        .padding(start = 48.dp)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.border),
                )
            }
        }
    }
}

/**
 * The popover itself, anchored to the composable it is declared in, the way iOS anchors
 * `.popover(isPresented:arrowEdge: .top)` to its button: below it, the arrow pointing at its
 * centre, kept inside the screen. Not a menu: a context menu leaves a square plate blinking
 * around a round button (see NavCircleIcon), and an action sheet loses the anchor.
 *
 * Choosing a row closes the popover first, then runs the row's action, the same order as the
 * house dialog, so the action may open a dialog or a screen straight away. iOS call sites close
 * it by hand and wait out UIKit's 260 ms dismissal; nothing here needs that wait.
 */
@Composable
fun ActionPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ActionPopoverItem>,
    width: Dp = 232.dp,
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = expanded
    if (!visibility.currentState && !visibility.targetState) return

    val density = LocalDensity.current
    val placement = remember { mutableStateOf(PopoverPlacement()) }
    val provider = remember(density) { AnchoredPopoverPosition(density, placement) }
    val haptics = rememberHaptics()
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        val current by placement
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.86f, stiffness = 700f), initialScale = 0.88f, transformOrigin = current.origin),
            exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.94f, transformOrigin = current.origin),
        ) {
            PopoverSurface(arrowX = current.arrowX, arrowOnTop = current.arrowOnTop) {
                ActionPopoverList(items, width = width, onItemClick = { item ->
                    haptics.tap()
                    onDismissRequest()
                    item.action()
                })
            }
        }
    }
}

@Immutable
private data class PopoverPlacement(val arrowX: Float = Float.NaN, val arrowOnTop: Boolean = true) {
    val origin: TransformOrigin get() = TransformOrigin(0.5f, if (arrowOnTop) 0f else 1f)
}

private val ArrowWidth = 26.dp
private val ArrowHeight = 12.dp
private val PopoverRadius = 13.dp

@Composable
private fun PopoverSurface(arrowX: Float, arrowOnTop: Boolean, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    val shape = remember(arrowX, arrowOnTop) { PopoverShape(arrowX, arrowOnTop) }
    val shadow = Color.Black.copy(alpha = if (AppTheme.isDark) 0.45f else 0.14f)
    Box(
        Modifier
            .padding(PopoverShadowRoom)
            .iosShadow(shape, shadow, radius = 18.dp, y = 6.dp)
            .background(colors.surfaceElevated, shape)
            .padding(top = if (arrowOnTop) ArrowHeight else 0.dp, bottom = if (arrowOnTop) 0.dp else ArrowHeight)
            .testTag("action_popover"),
    ) {
        content()
    }
}

/** Room for the shadow inside the popup window, which clips whatever it draws past its edge. */
private val PopoverShadowRoom = 24.dp

/**
 * The bubble with its arrow: a rounded rectangle and a small rounded triangle on the edge that
 * faces the anchor. [arrowX] is measured from the bubble's left edge; NaN centres it.
 */
@Immutable
private class PopoverShape(private val arrowX: Float, private val arrowOnTop: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val arrowHeight = with(density) { ArrowHeight.toPx() }
        val half = with(density) { ArrowWidth.toPx() } / 2f
        val top = if (arrowOnTop) arrowHeight else 0f
        val bottom = if (arrowOnTop) size.height else size.height - arrowHeight
        val r = with(density) { PopoverRadius.toPx() }.coerceAtMost((bottom - top) / 2f)
        val centre = if (arrowX.isNaN()) size.width / 2f else arrowX
        val x = centre.coerceIn(r + half, (size.width - r - half).coerceAtLeast(r + half))
        val tip = with(density) { 2.dp.toPx() }
        val path = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, bottom, r, r))
            val base = if (arrowOnTop) top else bottom
            val point = if (arrowOnTop) 0f else size.height
            val toward = if (arrowOnTop) -1f else 1f
            val arrow = Path().apply {
                moveTo(x - half, base - toward * 0.5f)
                quadraticTo(x - half * 0.35f, base + toward * arrowHeight * 0.2f, x - tip, point - toward * tip * 0.75f)
                quadraticTo(x, point, x + tip, point - toward * tip * 0.75f)
                quadraticTo(x + half * 0.35f, base + toward * arrowHeight * 0.2f, x + half, base - toward * 0.5f)
                close()
            }
            addPath(arrow)
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is PopoverShape && other.arrowOnTop == arrowOnTop && (other.arrowX == arrowX || (other.arrowX.isNaN() && arrowX.isNaN()))

    override fun hashCode(): Int = 31 * arrowX.hashCode() + arrowOnTop.hashCode()
}

/**
 * Below the anchor and centred on it, kept 8 dp inside the window; above it when there is no room
 * below. Reports where the arrow has to point, since the clamp can move the bubble off-centre.
 */
private class AnchoredPopoverPosition(
    private val density: Density,
    private val placement: MutableState<PopoverPlacement>,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val room = with(density) { PopoverShadowRoom.roundToPx() }
        val margin = with(density) { 8.dp.roundToPx() }
        val gap = with(density) { 4.dp.roundToPx() }
        val bubbleWidth = popupContentSize.width - 2 * room
        val bubbleHeight = popupContentSize.height - 2 * room
        val centre = anchorBounds.center.x
        val left = (centre - bubbleWidth / 2).coerceIn(margin, (windowSize.width - bubbleWidth - margin).coerceAtLeast(margin))
        val below = anchorBounds.bottom + gap
        val fitsBelow = below + bubbleHeight <= windowSize.height - margin
        val top = if (fitsBelow) below else (anchorBounds.top - gap - bubbleHeight).coerceAtLeast(margin)
        val next = PopoverPlacement(arrowX = (centre - left).toFloat(), arrowOnTop = fitsBelow)
        if (placement.value != next) placement.value = next
        return IntOffset(left - room, top - room)
    }
}
