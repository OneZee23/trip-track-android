// iOS: CarLoadingView.swift
package com.onezee.triptrack.feature.atlas.ui

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings

/**
 * iOS: CarLoadingView (standard). The car drives left and right over a dashed road while three dots
 * blink; still under «Remove animations». One element for TalkBack: «Загружаем».
 */
@Composable
fun CarLoading(modifier: Modifier = Modifier) {
    val still = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "car")
    val drive = if (still) 0f else transition.animateFloat(-24f, 24f, infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), label = "drive").value
    val rock = if (still) 0f else transition.animateFloat(-3f, 3f, infiniteRepeatable(tween(400, easing = EaseInOut), RepeatMode.Reverse), label = "rock").value
    val label = appStrings.loadingGeneric()
    Box(modifier.size(width = 96.dp, height = 56.dp).semantics(mergeDescendants = true) { contentDescription = label }.testTag("atlas_loading"),
        contentAlignment = Alignment.Center) {
        Box(Modifier.offset(y = 15.dp).size(width = 80.dp, height = 2.dp).background(AppTheme.colors.textTertiary.copy(alpha = 0.3f), RoundedCornerShape(1.dp)))
        Box(Modifier.offset(y = 15.dp)) { CarLoadingDots(AppTheme.colors.textTertiary) }
        Glyph(AppGlyph.car, contentDescription = null, tint = AppTheme.accent, size = 30.dp,
            modifier = Modifier.offset(x = drive.dp).rotate(rock))
    }
}

/** The three dots, two phases a step apart (iOS: 0.8 / 0.3 opacity, 0.6 s each way). */
@Composable
fun CarLoadingDots(colour: Color) {
    val still = LocalReduceMotion.current
    val phase = if (still) 0f else rememberInfiniteTransition(label = "dots")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(600, easing = EaseInOut), RepeatMode.Reverse), label = "dotPhase").value
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val even = i % 2 == 0
            val alpha = if (even) 0.3f + 0.5f * phase else 0.8f - 0.5f * phase
            Box(Modifier.size(4.dp).alpha(alpha).background(colour, CircleShape))
        }
    }
}
