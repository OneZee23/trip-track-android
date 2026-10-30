package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppRadius
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.iosShadow

// iOS: AppTheme.swift, SurfaceCard. The card of Лента and Я: the card colour, and in light a
// shadow just strong enough to lift it off the warm background. Dark has none: there the card
// is already lighter than the ground.
fun Modifier.surfaceCard(cornerRadius: Dp = AppRadius.card): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    val shadow = if (AppTheme.isDark) Color.Transparent else Color.Black.copy(alpha = 0.03f)
    iosShadow(shape, shadow, radius = 2.dp, y = 1.dp).background(AppTheme.colors.card, shape)
}

// iOS: AppTheme.swift, GlassBackground. iOS fills it with `.ultraThinMaterial`, which blurs what
// lies behind. Compose cannot blur another view's pixels on minSdk 29 without a third-party
// renderer, so the fill is the theme's glass tint, the same translucency without the blur
// (docs/divergences.md). The hairline is the theme border, as on iOS.
fun Modifier.glassBackground(cornerRadius: Dp = AppRadius.glass): Modifier = composed {
    val shape = RoundedCornerShape(cornerRadius)
    background(AppTheme.colors.glass, shape).border(1.dp, AppTheme.colors.border, shape)
}

/**
 * iOS: AppTheme.swift, GlassPill. A capsule chip: accent with white ink when active, a card with
 * secondary ink otherwise. iOS sets the font and ink for whatever it wraps; so does this.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = AppTheme.colors
    Box(
        modifier
            .background(if (isActive) AppTheme.accent else colors.card, CapsuleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides AppType.chip,
            LocalContentColor provides if (isActive) Color.White else colors.textSecondary,
            content = content,
        )
    }
}

@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, isActive: Boolean = false) {
    GlassPill(modifier, isActive) { Text(text, maxLines = 1) }
}
