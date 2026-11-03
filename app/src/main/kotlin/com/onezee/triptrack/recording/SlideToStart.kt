// iOS: SlideToStartView.swift.
package com.onezee.triptrack.recording

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.progressSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

// iOS: SlideToStartView.threshold, thumbSize, trackHeight, horizontalInset.
/**
 * iOS ProgressView(.circular): eight rounded spokes round a gap, the brightest one the head that steps
 * clockwise eight times a second and the tail fading behind it to a floor, measured on the iPhone's
 * render: over a grey of 78 the head reads .63 of white, then .52, .41, .29 and .23 for the rest. Still
 * under «Remove animations».
 */
@Composable
internal fun SpokeSpinner(color: Color, modifier: Modifier = Modifier) {
    val step = if (LocalReduceMotion.current) 7 else rememberInfiniteTransition(label = "spokes")
        .animateFloat(0f, 8f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spokeStep").value.toInt().coerceIn(0, 7)
    Canvas(modifier.progressSemantics()) {
        val unit = size.minDimension / 20f
        val fades = floatArrayOf(.79f, .65f, .51f, .36f, .29f, .29f, .29f, .29f)
        for (index in 0 until 8) {
            // Spoke 0 stands at twelve o'clock; the head is spoke [step] and the tail trails counter-clockwise.
            val behind = (step - index + 8) % 8
            val angle = Math.toRadians(index * 45.0 - 90.0)
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            val centre = Offset(size.width / 2, size.height / 2)
            drawLine(color.copy(alpha = color.alpha / .8f * fades[behind]), centre + Offset(dx, dy) * (3.3f * unit + 1.3f * unit),
                centre + Offset(dx, dy) * (10f * unit - 1.3f * unit), 2.6f * unit, StrokeCap.Round)
        }
    }
}

object SlideToStartPolicy {
    const val threshold = .66f
    fun commits(offset: Float, travel: Float): Boolean = travel > 0 && offset / travel >= threshold
    fun offset(raw: Float, travel: Float): Float = when {
        raw < 0 -> 0f
        raw <= travel -> raw
        else -> travel + sqrt(raw - travel) * 2
    }
}

@Composable
fun SlideToStart(
    blocked: Boolean,
    onStart: () -> Unit,
    onBlockedAttempt: () -> Unit,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
) {
    val strings = appStrings
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    var halfway by remember { mutableStateOf(false) }
    var near by remember { mutableStateOf(false) }
    val start by rememberUpdatedState(onStart)
    val explain by rememberUpdatedState(onBlockedAttempt)
    fun complete() {
        if (completed) return
        completed = true
        haptics.action()
        start()
    }
    LaunchedEffect(completed) {
        if (completed) {
            delay(500)
            offset.snapTo(0f)
            raw = 0f; halfway = false; near = false; completed = false
        }
    }
    LaunchedEffect(blocked) { if (blocked) { offset.snapTo(0f); raw = 0f } }
    val label = if (blocked) strings.slideWaitingForGPS() else labelOverride ?: strings.slideToStart()
    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp).testTag("slide_to_start")
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
            onClick { if (blocked) explain() else complete(); true }
        }) {
        val travel = with(density) { (maxWidth - 64.dp).toPx().coerceAtLeast(0f) }
        val progress = if (travel > 0) (offset.value / travel).coerceIn(0f, 1f) else 0f
        // Not clipped: the live thumb's glow reaches past the track, as on the iPhone.
        Box(Modifier.fillMaxSize()
            .background(if (blocked) Color.White.copy(alpha = .07f) else AppTheme.accent.copy(alpha = .15f), CircleShape)
            .then(if (blocked) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { explain(); tryAwaitRelease() }) } else Modifier)
            .graphicsLayer { alpha = if (completed) 0f else 1f; scaleX = if (completed) .96f else 1f; scaleY = scaleX }) {
            if (!blocked) Box(Modifier.width(with(density) { offset.value.toDp() } + 64.dp).fillMaxHeight()
                .background(AppTheme.accent.copy(alpha = .35f), CircleShape))
            val phase = if (reduceMotion || blocked) .5f else {
                val loop = rememberInfiniteTransition(label = "slide_hint")
                loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "shimmer").value
            }
            val hint = Modifier.align(Alignment.Center).fillMaxWidth()
                .padding(start = when { blocked -> 80.dp; label.length > 12 -> 100.dp; else -> 16.dp }, end = 16.dp)
                .graphicsLayer { alpha = if (blocked) 1f else (1 - progress / .3f).coerceAtLeast(0f) }
            val hintStyle = inter(17f, InterWeight.semibold).copy(textAlign = TextAlign.Center)
            val autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(inter(12.75f).fontSize, inter(17f).fontSize)
            Text(label, hint, style = hintStyle,
                color = Color.White.copy(alpha = when { blocked -> .5f; reduceMotion -> .7f; else -> .25f }),
                maxLines = 1, autoSize = autoSize)
            if (!blocked && !reduceMotion) BasicText(label, hint.clearAndSetSemantics {},
                style = hintStyle.copy(brush = Brush.horizontalGradient(
                    (phase - .2f).coerceAtLeast(0f) to Color.Transparent,
                    phase to Color.White.copy(alpha = .85f),
                    (phase + .2f).coerceAtMost(1f) to Color.Transparent)),
                maxLines = 1, autoSize = autoSize)
            if (!blocked && progress < .15f) Row(Modifier.align(Alignment.CenterStart)
                .padding(start = 70.dp + with(density) { offset.value.toDp() })
                .graphicsLayer { alpha = (1 - progress / .15f).coerceAtLeast(0f) }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) { index ->
                    val local = (phase * 2 + index * .2f) % 1
                    val alpha = when {
                        local < .3f -> local / .3f * .7f
                        local < .7f -> .7f - (local - .3f) / .4f * .5f
                        else -> (.2f - (local - .7f) / .3f * .2f).coerceAtLeast(0f)
                    }
                    Glyph(AppGlyph.chevronRight, null, tint = Color.White.copy(alpha = alpha), size = 14.dp)
                }
            }
            Box(Modifier.offset { IntOffset(offset.value.roundToInt() + with(density) { 4.dp.roundToPx() }, 0) }
                .align(Alignment.CenterStart).size(56.dp)
                // iOS: the live thumb glows in accent at .4, radius 8; the held one casts nothing.
                .iosShadow(CircleShape, if (blocked) Color.Transparent else AppTheme.accent.copy(alpha = .4f), 8.dp)
                .background(if (blocked) Color(0xFF525252) else AppTheme.accent, CircleShape)
                .then(if (!blocked) Modifier.pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragStart = { raw = offset.value; halfway = false; near = false },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            if (!completed) {
                                raw += amount
                                val next = SlideToStartPolicy.offset(raw, travel)
                                scope.launch { offset.snapTo(next) }
                                if (travel > 0 && next / travel >= .5 && !halfway) { halfway = true; haptics.selection() }
                                if (travel > 0 && next / travel >= .8 && !near) { near = true; haptics.selection() }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 1f)) } },
                        onDragEnd = {
                            if (SlideToStartPolicy.commits(offset.value, travel)) complete()
                            else scope.launch { offset.animateTo(0f, if (reduceMotion) tween(0) else spring(dampingRatio = .7f)) }
                        },
                    )
                } else Modifier), contentAlignment = Alignment.Center) {
                // The held thumb says «not your turn» with the system's own spoke spinner (iOS ProgressView, white at .8).
                if (blocked) SpokeSpinner(Color.White.copy(alpha = .8f), Modifier.size(20.dp))
                // play.fill, 21 bold.
                else RecordingShape(RecordingMark.play, 16.85f, 18.7f, Color.White)
            }
        }
    }
}
