// iOS: ReactionIconView.swift
package com.onezee.triptrack.ui.reactions

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.model.ReactionEmoji

/** The two drawings of one reaction: the outline at rest, the filled one once it is yours. */
data class ReactionDrawables(@param:DrawableRes val outline: Int, @param:DrawableRes val fill: Int)

/**
 * iOS: ReactionIconView.swift (assetBases). The owner's ReactionIcon component (Figma Components →
 * ReactionIcon): 24×24 template drawings `reaction_<name>_<outline|fill>`, keyed by the emoji the
 * server stores. Legacy prod emoji resolve through [ReactionEmoji.canonical], so every known key
 * has a drawing; only a key a future server introduces before the app learns it falls back to the
 * emoji itself.
 */
object ReactionArt {
    /** Canonical emoji → asset base name. Palette order lives in [ReactionEmoji.all]. */
    val bases: Map<String, String> = mapOf(
        "🔥" to "fire", "🤯" to "wow", "🏁" to "finish",
        "🛣️" to "pass", "🌅" to "frame", "👍" to "like",
    )

    fun base(emoji: String): String? = bases[ReactionEmoji.canonical(emoji)]

    fun of(emoji: String): ReactionDrawables? = when (base(emoji)) {
        "fire" -> ReactionDrawables(R.drawable.reaction_fire_outline, R.drawable.reaction_fire_fill)
        "wow" -> ReactionDrawables(R.drawable.reaction_wow_outline, R.drawable.reaction_wow_fill)
        "finish" -> ReactionDrawables(R.drawable.reaction_finish_outline, R.drawable.reaction_finish_fill)
        "pass" -> ReactionDrawables(R.drawable.reaction_pass_outline, R.drawable.reaction_pass_fill)
        "frame" -> ReactionDrawables(R.drawable.reaction_frame_outline, R.drawable.reaction_frame_fill)
        "like" -> ReactionDrawables(R.drawable.reaction_like_outline, R.drawable.reaction_like_fill)
        else -> null
    }
}

/**
 * iOS: ReactionIconView.swift. The drawn reaction icon. Both variants stay mounted and cross-fade
 * (0.22 s, ease out): swapping one image for the other is a hard cut, and the outline ↔ filled
 * morph is the main visual signal that a reaction went on or came off. The drawings are templates:
 * a SrcIn filter keeps only their alpha, as UIKit's template rendering does. Default tint: filled →
 * accent (the Figma fill variant), outline → the theme's ink. TalkBack reads the emoji's name.
 */
@Composable
fun ReactionIcon(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    filled: Boolean = false,
    tint: Color? = null,
) {
    val art = ReactionArt.of(emoji)
    if (art == null) {
        Text(emoji, modifier, style = inter(size.value))
        return
    }
    val fill by animateFloatAsState(if (filled) 1f else 0f, tween(220, easing = EaseOut), label = "reactionFill")
    val ink by animateColorAsState(tint ?: if (filled) AppTheme.accent else AppTheme.colors.text, tween(220, easing = EaseOut), label = "reactionTint")
    val filter = ColorFilter.tint(ink)
    Box(modifier.size(size).semantics { contentDescription = emoji }) {
        Image(painterResource(art.outline), null, Modifier.matchParentSize().graphicsLayer { alpha = 1f - fill }, colorFilter = filter)
        Image(painterResource(art.fill), null, Modifier.matchParentSize().graphicsLayer { alpha = fill }, colorFilter = filter)
    }
}
