// iOS: EmptyStateIllustration.swift, IdleRing.swift
package com.onezee.triptrack.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle

/**
 * iOS: EmptyStateIllustration.swift. The drawn scene an empty screen leads with. The scenes carry
 * their own navy backdrop, so the plate IS the artwork: a 22 pt continuous corner is all the framing,
 * and on the light theme's cream it reads as a deliberate accent rather than a rendering fault.
 * Nearest-neighbour, like every pixel-art call site; decoration only, the heading under it carries
 * the meaning. Sizing lives here so the empty screens stay the size of each other: 156 by default,
 * the call sites pass what iOS passes there.
 */
@Composable
fun EmptyStateIllustration(@DrawableRes image: Int, modifier: Modifier = Modifier, size: Dp = 156.dp) {
    Image(
        ImageBitmap.imageResource(image),
        contentDescription = null,
        modifier = modifier.size(size).clip(ContinuousRoundedRectangle(22.dp)).semantics { hideFromAccessibility() },
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
    )
}

/**
 * iOS: IdleRing.swift (Figma 114:151). The empty-state hero of the feed and of the statistics: the
 * pixel car on the sprite plate, every vehicle's ground, inside an accent ring, with a pale accent
 * ring around that. The car box follows the cropped sprite's proportions.
 */
@Composable
fun IdleRing(modifier: Modifier = Modifier) {
    Box(modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(100.dp).border(2.dp, AppTheme.accent.copy(alpha = 0.25f), CircleShape))
        Box(
            Modifier
                .size(82.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(AppTheme.spritePlateTop, AppTheme.spritePlateBottom)))
                .border(2.5.dp, AppTheme.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageBitmap.imageResource(R.drawable.pixel_car), contentDescription = null, modifier = Modifier.size(width = 52.dp, height = 32.dp),
                contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        }
    }
}
