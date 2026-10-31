// iOS: AtlasSheet.swift, SlotGesture.swift (spring and reduced motion)
package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.iosShadow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * iOS SlotGesture.spring, `.spring(response: 0.45, dampingFraction: 0.86)`: stiffness (2π / 0.45)².
 * «Reduce motion» replaces it with a 0.2 s fade-out curve, as on the whole tab.
 */
fun slotSpring(reduceMotion: Boolean): AnimationSpec<Float> =
    if (reduceMotion) tween(200, easing = EaseOut) else spring(dampingRatio = 0.86f, stiffness = 195f)

/**
 * The live top of a bottom slot, in dp from the SCREEN's top edge. The screen owns it, because the map
 * controls, the place card and the attribution follow the same number frame by frame: «one spring for
 * everything at the bottom» (spec principle 5), during the drag and during the settle alike.
 */
@Stable
class SlotMotion(initial: Float) {
    val top = Animatable(initial)
    var dragging by mutableStateOf(false)
        internal set
}

@Composable
fun rememberSlotMotion(initial: Double): SlotMotion = remember { SlotMotion(initial.toFloat()) }

/**
 * iOS: AtlasSheet. The Atlas sheet: one slot at the bottom, three positions, one spring. The house
 * one, not a system sheet: the map under it stays alive and the tab bar stays in place.
 *
 * It moves by OFFSET inside a full-screen box, never by padding or window insets: on iOS a layer
 * pushed out of its container moved the safe area of the whole window (27 September). The sheet
 * reaches the physical bottom at every top, rubber band included.
 *
 * [target] is where the position puts the top; [settleTick] changes on every drag end, so a drag that
 * lands back on the same position still springs back to it. [onDrag] maps a raw finger top to the
 * rubber-banded one, [onSettle] answers where to go (pt and pt/s), and [onTapHandle] is the handle's tap.
 */
@Composable
fun AtlasSheet(
    motion: SlotMotion,
    target: Double,
    screenHeight: Dp,
    accessibilityTitle: String,
    onDrag: (Double) -> Double,
    onSettle: (top: Double, velocity: Double) -> Unit,
    onTapHandle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "atlas_sheet",
    handleTag: String = "atlas_sheet_handle",
    draggable: Boolean = true,
    shadowRadius: Dp = 14.dp,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var settleTick by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableFloatStateOf(0f) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    val latestDrag by rememberUpdatedState(onDrag)
    val latestSettle by rememberUpdatedState(onSettle)

    LaunchedEffect(target, settleTick) {
        if (!motion.dragging) motion.top.animateTo(target.toFloat(), slotSpring(reduceMotion))
    }

    val shape = RoundedCornerShape(topStart = AtlasTheme.sheetRadius, topEnd = AtlasTheme.sheetRadius)
    val shadow = Color.Black.copy(alpha = if (AppTheme.isDark) 0.32f else 0.16f)
    val top = motion.top.value
    Column(
        modifier
            .offset { IntOffset(0, with(density) { top.dp.roundToPx() }) }
            .fillMaxWidth()
            .height((screenHeight.value - top).coerceAtLeast(0f).dp)
            .iosShadow(shape, shadow, radius = shadowRadius, y = (-8).dp)
            .background(AtlasTheme.colors.background, shape)
            // The content is CUT by the frame: a half without a scroll would otherwise spill its
            // extra cards over the tab bar.
            .clipToBounds()
            .then(
                if (!draggable) Modifier else Modifier.draggable(
                    state = rememberDraggableState { delta ->
                        dragTotal += with(density) { delta.toDp().value }
                        val live = latestDrag((dragStart + dragTotal).toDouble()).toFloat()
                        scope.launch { motion.top.snapTo(live) }
                    },
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        motion.dragging = true
                        motion.top.stop()
                        dragStart = motion.top.value
                        dragTotal = 0f
                    },
                    onDragStopped = { velocity ->
                        val landed = latestDrag((dragStart + dragTotal).toDouble())
                        motion.dragging = false
                        latestSettle(landed, with(density) { velocity.toDp().value }.toDouble())
                        settleTick++
                    },
                ),
            )
            .testTag(testTag),
    ) {
        SheetHandle(accessibilityTitle, handleTag, onTapHandle)
        header()
        content()
    }
}

/**
 * The handle and the strip around it: a tap opens the list, as a tap on the block does. A real
 * element: it is pressed, and it is where a test reads the sheet's top.
 */
@Composable
fun SheetHandle(accessibilityTitle: String, testTag: String, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onTap)
            .semantics { contentDescription = accessibilityTitle }
            .testTag(testTag)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 36.dp, height = 5.dp).background(AtlasTheme.colors.handle, CapsuleShape))
    }
}

/** Handle (8 + 5 + 8): where the sheet's own content starts below its top. */
val HANDLE_HEIGHT: Dp = 21.dp
