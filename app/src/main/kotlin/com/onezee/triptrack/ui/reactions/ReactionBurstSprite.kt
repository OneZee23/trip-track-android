// iOS: ReactionBurstSprite.swift
package com.onezee.triptrack.ui.reactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * iOS: ReactionBurstSprite.swift. The small floating icon of a reaction going from off to on: the
 * filled drawing in accent rises 36 pt while it grows (0.6 → 1.5) and fades out, 0.7 s, ease out.
 * Purely presentational; the parent owns its lifetime and removes it when the burst is over, and
 * every new sprite starts from zero. [initialPhase] freezes a frame for screenshots.
 *
 * The iPhone app defines the sprite but mounts it on no screen (2 Nov 2026), so neither does Android.
 */
@Composable
fun ReactionBurstSprite(emoji: String, modifier: Modifier = Modifier, initialPhase: Float? = null) {
    val phase = remember { Animatable(initialPhase ?: 0f) }
    LaunchedEffect(Unit) { if (initialPhase == null) phase.animateTo(1f, tween(700, easing = EaseOut)) }
    ReactionIcon(
        emoji,
        modifier.graphicsLayer {
            val p = phase.value
            scaleX = 0.6f + p * 0.9f
            scaleY = 0.6f + p * 0.9f
            alpha = 1f - p
            translationY = -p * 36.dp.toPx()
        },
        size = 30.dp,
        filled = true,
    )
}
