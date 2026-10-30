package com.onezee.triptrack.designsystem.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalWindowIsDark

/**
 * iOS: EdgeScrims.swift. A haze under the status bar (and, optionally, the navigation bar) on
 * screens whose map or photo runs edge to edge: the status bar has no ground of its own, and its
 * glyphs vanish over busy streets.
 *
 * The haze follows the WINDOW's colours, that is the status bar glyphs: dark glyphs in light need
 * a light haze, white glyphs in dark a dark one. [dark] forces it, only where the glyphs are
 * forced too. White needs more opacity than black: dark text on a light ground loses contrast
 * faster. Drawn over the content, it takes no touches, and its height is the system bar's inset
 * plus a tail, so the gradient never ends in a hard line across the map.
 *
 * Place it below the screen's own header: buttons and titles stay crisp, what is under them dims.
 */
fun Modifier.edgeScrims(
    top: Boolean = true,
    bottom: Boolean = false,
    dark: Boolean? = null,
    topStrength: Float = 0.42f,
    bottomStrength: Float = 0.30f,
): Modifier = composed {
    val density = LocalDensity.current
    val windowDark = LocalWindowIsDark.current
    val isDark = dark ?: (windowDark ?: AppTheme.isDark)
    val haze = if (isDark) Color.Black else Color.White
    val topAlpha = if (isDark) topStrength else LightTop
    val bottomAlpha = if (isDark) bottomStrength else LightBottom
    val statusInset = WindowInsets.statusBars.getTop(density)
    val navigationInset = WindowInsets.navigationBars.getBottom(density)
    drawWithContent {
        drawContent()
        if (top) {
            val height = statusInset + 28.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(haze.copy(alpha = topAlpha), haze.copy(alpha = 0f)), startY = 0f, endY = height),
                topLeft = Offset.Zero,
                size = Size(size.width, height),
            )
        }
        if (bottom) {
            val height = navigationInset + 24.dp.toPx()
            drawRect(
                Brush.verticalGradient(listOf(haze.copy(alpha = 0f), haze.copy(alpha = bottomAlpha)), startY = size.height - height, endY = size.height),
                topLeft = Offset(0f, size.height - height),
                size = Size(size.width, height),
            )
        }
    }
}

/** Opacity of the light haze: worked out separately, white and black dim a map differently. */
private const val LightTop = 0.72f
private const val LightBottom = 0.55f
