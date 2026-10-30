package com.onezee.triptrack.designsystem.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import kotlin.math.max

// iOS: AppTheme.swift, CardPressResponse. What a card promises the finger.
enum class CardPressResponse {
    /** A tap opens: a quick press with a slight fade. */
    tap,

    /**
     * A tap opens, and a hold opens something ELSE: the press runs for the whole hold, so it is
     * visible that the finger has to stay.
     */
    hold,
}

// iOS: AppTheme.swift, HoldableCardStyle.holdDuration.
object HoldableCard {
    /**
     * How long the press runs, and exactly as long as the hold gesture waits. Style and gesture
     * are two halves of one promise: a hold that fires earlier opens its menu on a half-pressed
     * card. On Android the user can make long presses slower in the accessibility settings;
     * then both halves follow that longer delay.
     */
    const val holdDurationMillis = 500L

    fun holdMillis(systemLongPressMillis: Long): Long = max(holdDurationMillis, systemLongPressMillis)
}

private const val TAP_SCALE = 0.98f
private const val TAP_ALPHA = 0.92f
private const val HOLD_SCALE = 0.945f

/**
 * The visual half of iOS `PressableCardStyle` / `HoldableCardStyle` for content that handles its
 * own gestures. [PressableCard] wires both halves.
 */
fun Modifier.cardPressFeedback(
    interactionSource: InteractionSource,
    response: CardPressResponse,
    holdMillis: Long = HoldableCard.holdDurationMillis,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val (scale, alpha) = when (response) {
        CardPressResponse.tap -> {
            // «Reduce motion» keeps the fade and drops the shrink and its animation, as on iOS.
            val spec = if (reduceMotion) snap() else tween<Float>(160, easing = EaseOut)
            val s by animateFloatAsState(if (pressed && !reduceMotion) TAP_SCALE else 1f, spec, label = "pressScale")
            val a by animateFloatAsState(if (pressed) TAP_ALPHA else 1f, spec, label = "pressAlpha")
            s to a
        }
        CardPressResponse.hold -> {
            val spec = tween<Float>(if (pressed) holdMillis.toInt() else 180, easing = EaseOut)
            val s by animateFloatAsState(if (pressed) HOLD_SCALE else 1f, spec, label = "holdScale")
            s to 1f
        }
    }
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

/**
 * A card that answers the finger the moment it lands (CLAUDE.md «Нажатие обязано отвечать»).
 * [onLongClick] belongs with [CardPressResponse.hold]: the hold waits as long as the press runs.
 */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    response: CardPressResponse = CardPressResponse.tap,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    role: Role? = Role.Button,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val system = LocalViewConfiguration.current
    val holdMillis = HoldableCard.holdMillis(system.longPressTimeoutMillis)
    val holdConfiguration = remember(system, holdMillis) { HoldViewConfiguration(system, holdMillis) }
    CompositionLocalProvider(LocalViewConfiguration provides holdConfiguration) {
        Box(
            modifier
                .cardPressFeedback(interaction, response, holdMillis)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        ) {
            // The card's own children keep the system's timing.
            CompositionLocalProvider(LocalViewConfiguration provides system, content = content)
        }
    }
}

private class HoldViewConfiguration(
    private val base: ViewConfiguration,
    override val longPressTimeoutMillis: Long,
) : ViewConfiguration by base

/**
 * iOS `.buttonStyle(.plain)`: no decoration at rest, the label dims under the finger. Rows of
 * lists, sheets and dialogs; cards use [cardPressFeedback].
 */
fun Modifier.plainPressFeedback(interactionSource: InteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) PLAIN_ALPHA else 1f, tween(if (pressed) 0 else 160), label = "plainPress")
    graphicsLayer { this.alpha = alpha }
}

private const val PLAIN_ALPHA = 0.55f
