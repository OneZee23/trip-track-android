// iOS: ReactionTallyPill.swift, TripDetailSections.swift (ReactionCountChip)
package com.onezee.triptrack.ui.reactions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

/**
 * iOS: ReactionTallyPill.swift. The interactive reaction pill (Figma ReactionPills 114:141): the drawn
 * icon and the count in a capsule. It owns the feel of toggling, and the two directions are
 * deliberately asymmetric so taking a reaction back reads as an undo rather than another vote:
 * landing — a crisp click, the pill overshoots and springs back, the icon morphs outline → filled
 * accent, the count rolls up; removing — a muted thud, the pill dips and settles, the icon fades back
 * to outline, the count rolls down.
 *
 * [isEnabled] false: the owner's pill, which renders but does not toggle (Strava rule). [onLongPress]
 * is the peek at who reacted, with this pill's emoji; unwired, no hold gesture is attached. A hold
 * never also counts as a tap.
 */
@Composable
fun ReactionTallyPill(
    emoji: String,
    count: Int,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onLongPress: ((String) -> Unit)? = null,
    onTap: () -> Unit,
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val scale = toggleBounce(isMine)
    val background by animateColorAsState(if (isMine) AppTheme.orangeDim else colors.cardAlt, tween(220, easing = EaseOut), label = "pillFill")
    // A clear ring rather than none, as on iOS: the capsule never changes size between the states.
    val ring by animateColorAsState(if (isMine) AppTheme.accent else Color.Transparent, tween(220, easing = EaseOut), label = "pillRing")
    val digits by animateColorAsState(if (isMine) AppTheme.accent else colors.textSecondary, tween(220, easing = EaseOut), label = "pillCount")
    HoldTimeout(PILL_HOLD_MILLIS) {
        Row(
            modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(background, CapsuleShape)
                .border(1.5.dp, ring, CapsuleShape)
                .combinedClickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onLongClick = onLongPress?.let { peek -> { haptics.action(); peek(emoji) } },
                ) {
                    if (!isEnabled) return@combinedClickable
                    if (isMine) haptics.reactionOff() else haptics.reactionOn()
                    onTap()
                }
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) {}
                .testTag("reaction_pill"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReactionIcon(emoji, size = 14.dp, filled = isMine, tint = if (isMine) AppTheme.accent else colors.text)
            RollingCount(count, countsDown = !isMine, style = inter(12f, InterWeight.bold, monospacedDigits = true), color = digits)
        }
    }
}

/** iOS: TripDetailSections.swift, ReactionCountChip.Style. */
enum class ReactionChipStyle {
    /** The owner's read-out of their own trip. */
    breakdown,

    /** Someone else's trip, a reaction that is not ours. */
    unselected,

    /** Someone else's trip, the reaction we left. */
    mine,
}

/**
 * iOS: TripDetailSections.swift, ReactionCountChip. The chip row of the trip screen's «Реакции · N»
 * card: the same capsule, icon, rolling count and bounce as [ReactionTallyPill], in three styles. The
 * gestures belong to the caller on iOS; here they are parameters, attached only when given (a hold
 * waits half a second, the iOS default).
 */
@Composable
fun ReactionCountChip(
    emoji: String,
    count: Int,
    modifier: Modifier = Modifier,
    style: ReactionChipStyle = ReactionChipStyle.breakdown,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dark = AppTheme.isDark
    val mine = style == ReactionChipStyle.mine
    val scale = toggleBounce(mine)
    val (fill, ink) = when (style) {
        ReactionChipStyle.breakdown -> colors.cardAlt to colors.text
        ReactionChipStyle.unselected -> (if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)) to colors.textSecondary
        ReactionChipStyle.mine -> AppTheme.orangeDim to AppTheme.accent
    }
    val background by animateColorAsState(fill, tween(220, easing = EaseOut), label = "chipFill")
    val ring by animateColorAsState(if (mine) AppTheme.accent else Color.Transparent, tween(220, easing = EaseOut), label = "chipRing")
    val digits by animateColorAsState(ink, tween(220, easing = EaseOut), label = "chipCount")
    val gestures = if (onTap != null || onLongPress != null) Modifier.combinedClickable(
        interactionSource = null,
        indication = null,
        role = Role.Button,
        onLongClick = onLongPress,
        onClick = { onTap?.invoke() },
    ) else Modifier
    HoldTimeout(CHIP_HOLD_MILLIS) {
        Row(
            modifier
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(background, CapsuleShape)
                .border(1.5.dp, ring, CapsuleShape)
                .then(gestures)
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) {}
                .testTag("reaction_chip"),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReactionIcon(emoji, size = 14.dp, filled = mine, tint = if (mine) AppTheme.accent else colors.text)
            RollingCount(count, countsDown = !mine, style = inter(12f, InterWeight.bold, monospacedDigits = true), color = digits)
        }
    }
}

/**
 * iOS `.contentTransition(.numericText(countsDown:))` with `.snappy(duration: 0.26)`: the digits roll
 * in the direction of the change, up as a reaction lands, down as it is taken back.
 */
@Composable
internal fun RollingCount(count: Int, countsDown: Boolean, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val slide = spring<IntOffset>(dampingRatio = SNAPPY_DAMPING, stiffness = SNAPPY_STIFFNESS)
                val fade = spring<Float>(dampingRatio = SNAPPY_DAMPING, stiffness = SNAPPY_STIFFNESS)
                // Rolling up: the new number comes in from below and the old one leaves upwards.
                val from = if (countsDown) -1 else 1
                (slideInVertically(slide) { it * from } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { -it * from } + fadeOut(fade))
            }.using(SizeTransform(clip = true))
        },
        label = "rollingCount",
    ) { value ->
        Text("$value", style = style, color = color, maxLines = 1)
    }
}

/**
 * iOS: the keyframe track of the pill and the chip — SpringKeyframe(1.16 or 0.88, 0.13 s, .snappy),
 * then SpringKeyframe(1.0, 0.3 s, .bouncy). Each segment runs its spring for exactly its duration and
 * hands its velocity on, which is what a SpringKeyframe with a duration does; the first frame and
 * «Remove animations» stay still.
 */
@Composable
private fun toggleBounce(on: Boolean): Animatable<Float, AnimationVector1D> {
    val reduceMotion = LocalReduceMotion.current
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(on) }
    LaunchedEffect(on) {
        if (on == shown) return@LaunchedEffect
        shown = on
        if (reduceMotion) return@LaunchedEffect
        withTimeoutOrNull(130) { scale.animateTo(if (on) 1.16f else 0.88f, spring(dampingRatio = SNAPPY_DAMPING, stiffness = DEFAULT_SPRING_STIFFNESS)) }
        withTimeoutOrNull(300) { scale.animateTo(1f, spring(dampingRatio = BOUNCY_DAMPING, stiffness = DEFAULT_SPRING_STIFFNESS)) }
        scale.snapTo(1f)
    }
    return scale
}

/**
 * The hold before a long press counts, as on iOS, unless the person made long presses slower in the
 * accessibility settings: then the system's longer delay wins (the [PressableCard] rule).
 */
@Composable
private fun HoldTimeout(millis: Long, content: @Composable () -> Unit) {
    val system = LocalViewConfiguration.current
    val hold = remember(system, millis) { HoldConfiguration(system, max(millis, system.longPressTimeoutMillis)) }
    CompositionLocalProvider(LocalViewConfiguration provides hold, content = content)
}

private class HoldConfiguration(base: ViewConfiguration, override val longPressTimeoutMillis: Long) : ViewConfiguration by base

/** iOS `onLongPressGesture(minimumDuration: 0.35)` on the pill. */
private const val PILL_HOLD_MILLIS = 350L

/** iOS `onLongPressGesture` default, the chip's hold. */
private const val CHIP_HOLD_MILLIS = 500L

// SwiftUI springs as Compose springs: stiffness = (2π / response)², damping ratio = 1 − bounce.
// .snappy is bounce 0.15; .bouncy is bounce 0.3; both have a 0.5 s response by default.
private const val SNAPPY_DAMPING = 0.85f
private const val BOUNCY_DAMPING = 0.7f
private const val DEFAULT_SPRING_STIFFNESS = 157.9f

/** `.snappy(duration: 0.26)`: (2π / 0.26)². */
private const val SNAPPY_STIFFNESS = 584f
