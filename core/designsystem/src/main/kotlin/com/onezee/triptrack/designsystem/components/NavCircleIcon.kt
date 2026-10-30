package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.iosShadow

/**
 * iOS: NavCircleIcon.swift (profile 117:944 / 117:948). The nav control: a card-filled circle with
 * a glyph and a soft shadow lifting it off the warm background, so «назад» and «…» read as one
 * pair. 40 dp, not the artboard's 34: canon was drawn on a 360 pt frame, and the same circle on a
 * fifth more width read as a small control floating in a thin bar.
 *
 * The tap target grows to Android's 48 dp by itself: Compose extends small clickable nodes to the
 * minimum touch target without moving the layout, which is what iOS does by hand with padding.
 *
 * Never hang a menu off this button; use [ActionPopover] for an action list and the house dialog
 * for a question.
 */
@Composable
fun NavCircleIcon(glyph: AppGlyph, modifier: Modifier = Modifier, glyphSize: Dp = NavCircleGlyphSize) {
    val colors = AppTheme.colors
    val shadow = Color.Black.copy(alpha = if (AppTheme.isDark) 0.40f else 0.07f)
    Box(
        modifier
            .size(NavCircleDiameter)
            .iosShadow(CircleShape, shadow, radius = 5.dp, y = 2.dp)
            .background(colors.card, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, contentDescription = null, tint = colors.text, size = glyphSize)
    }
}

/** [NavCircleIcon] as a button. [contentDescription] is what TalkBack says: «Назад», «Ещё». */
@Composable
fun NavCircleButton(
    glyph: AppGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    ) {
        NavCircleIcon(glyph)
    }
}

/** The visible circle. It also sets the height of the bar it sits in. */
val NavCircleDiameter = 40.dp

/** iOS: an 18 pt semibold symbol. */
val NavCircleGlyphSize = 22.dp
