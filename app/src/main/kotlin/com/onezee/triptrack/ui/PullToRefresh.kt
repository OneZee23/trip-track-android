package com.onezee.triptrack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * iOS `.refreshable` (UIRefreshControl): pulling the list down past its top reveals the system
 * spinner, letting go past the threshold refreshes, and the list waits pushed down until the work
 * ends. The spinner is drawn the iOS way, twelve fading spokes, not a Material circle.
 *
 * [refreshing] is the caller's truth (the store's loading flag); the pull only asks.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val rest = with(density) { 52.dp.toPx() }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val currentRefresh by rememberUpdatedState(onRefresh)
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) offset.animateTo(rest, spring(stiffness = 500f)) else offset.animateTo(0f, tween(250))
    }

    val connection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Scrolling back up while the spinner shows: the pull gives way first.
                if (available.y < 0 && offset.value > 0 && source == NestedScrollSource.UserInput && !refreshing) {
                    val next = (offset.value + available.y).coerceAtLeast(0f)
                    val used = next - offset.value
                    scope.launch { offset.snapTo(next) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput && !refreshing) {
                    // Rubber band: the further it goes, the harder it pulls.
                    val next = offset.value + available.y * 0.5f
                    scope.launch { offset.snapTo(next) }
                    if (!armed && next >= threshold) {
                        armed = true
                        haptics.selection()
                    } else if (armed && next < threshold) {
                        armed = false
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (refreshing || offset.value <= 0f) return Velocity.Zero
                if (offset.value >= threshold) {
                    armed = false
                    currentRefresh()
                    offset.animateTo(rest, spring(stiffness = 500f))
                } else {
                    offset.animateTo(0f, tween(220))
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        Box(Modifier.graphicsLayer { translationY = offset.value }) { content() }
        val shown = offset.value
        if (shown > 0f || refreshing) {
            ActivitySpinner(
                progress = (shown / threshold).coerceIn(0f, 1f),
                spinning = refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = shown / 2f - with(density) { 14.dp.toPx() } }
                    .testTag("pull_to_refresh"),
            )
        }
    }
}

/**
 * iOS UIActivityIndicatorView (medium): twelve spokes, the lead one darkest. While pulling the
 * spokes appear one by one with [progress]; while [spinning] the shade runs round.
 */
@Composable
fun ActivitySpinner(progress: Float, spinning: Boolean, modifier: Modifier = Modifier, color: Color = AppTheme.colors.textSecondary) {
    val reduceMotion = LocalReduceMotion.current
    val turn = if (spinning && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "spinner")
        transition.animateFloat(0f, 12f, infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "spin").value
    } else 0f
    Canvas(modifier.size(28.dp)) {
        val centre = Offset(size.width / 2, size.height / 2)
        val inner = size.minDimension * 0.22f
        val outer = size.minDimension * 0.44f
        val width = size.minDimension * 0.085f
        val visible = if (spinning) 12 else (progress * 12).toInt()
        val lead = turn.toInt()
        for (i in 0 until visible) {
            val angle = (i / 12f) * 2 * PI - PI / 2
            val alpha = if (spinning) 1f - ((lead - i + 12) % 12) / 12f * 0.85f else 0.25f + 0.75f * (i + 1) / 12f
            drawLine(
                color.copy(alpha = color.alpha * alpha),
                Offset(centre.x + (inner * cos(angle)).toFloat(), centre.y + (inner * sin(angle)).toFloat()),
                Offset(centre.x + (outer * cos(angle)).toFloat(), centre.y + (outer * sin(angle)).toFloat()),
                strokeWidth = width,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A row's spinner while the next page loads (iOS `ProgressView()` with 16 of vertical air). */
@Composable
fun LoadingMoreRow(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ActivitySpinner(progress = 1f, spinning = true, modifier = Modifier.size(24.dp))
    }
}
