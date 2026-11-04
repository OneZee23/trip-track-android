package com.onezee.triptrack.feature.trip.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.UnitNumber
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.speedParts
import com.onezee.triptrack.map.replay.ReplayBarState
import com.onezee.triptrack.map.replay.TripReplayEngine
import com.onezee.triptrack.map.replay.PlaybackProgress
import com.onezee.triptrack.map.replay.RoutePlaybackController
import com.onezee.triptrack.map.replay.CarHeadingPolicy
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripReplayInput
import java.util.Locale

// iOS: FullscreenMapSheet.swift, cameraToggle (SF Symbols location.fill / map).
@Composable
fun ReplayCameraToggle(followsCar: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val strings = appStrings
    Box(Modifier.size(40.dp).iosShadow(CircleShape, Color.Black.copy(alpha = .18f), radius = 6.dp, y = 2.dp)
        .background(colors.card, CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (followsCar) strings.replayShowWholeRoute() else strings.replayFollowTheCar() }
        .testTag("replay_camera_toggle"), contentAlignment = Alignment.Center) {
        if (followsCar) Glyph(AppGlyph.map, null, tint = colors.text, size = 18.dp)
        else Glyph(AppGlyph.locationFill, null, tint = colors.text, size = 18.dp)
    }
}

// iOS: FullscreenMapSheet.swift, published TripReplayEngine state.
data class TripReplayFrame(
    val phase: ReplayBarState = ReplayBarState.idle,
    val progress: Double = 0.0,
    val distanceFraction: Double = 0.0,
    val rate: Double = 1.0,
    val holdFractions: List<Double> = emptyList(),
    val holdingIndex: Int? = null,
    val speedMS: Double = 0.0,
    val speedKnown: Boolean = true,
    val hasSpeeds: Boolean = false,
    val head: com.onezee.triptrack.model.GeoPoint? = null,
    val trailIndex: Int = -1,
    val course: Double = 0.0,
    val opacity: Double = 1.0,
)

// iOS: FullscreenMapSheet.swift. Compose owns display-link subscription; the engine stays pure JVM.
@Stable
class TripReplayPlayer(val input: TripReplayInput, private val distanceMetres: Double = 0.0) {
    val canReplay = input.coords.size > 1 && input.timestamps.size == input.coords.size
    val canCrawl = !canReplay && input.coords.size > 1
    val canPlay = canReplay || canCrawl
    private val crawl = RoutePlaybackController()
    private val cumulative = PlaybackProgress.cumulative(input.coords)
    private val engine = TripReplayEngine().apply { if (canReplay) configure(input) }
    var frame by mutableStateOf(TripReplayFrame()); private set
    private var resumeAfterScrub = false
    var reduceMotion = false
    private var course: Double? = null
    private var lastCarTick = 0.0
    val litFraction: Double? get() = frame.head?.let { PlaybackProgress.fraction(cumulative, input.coords, it, frame.trailIndex) }

    fun change(action: TripReplayEngine.() -> Unit) {
        engine.action()
        publish()
    }

    private fun publish() {
        val head = if (canReplay) engine.headCoord else crawl.currentCoord
        val trailIndex = if (canReplay) engine.trailIndex else crawl.currentTrailIndex
        if (head != null) {
            val now = now()
            val target = CarHeadingPolicy.courseAlongRoute(head, trailIndex, input.coords)
            course = if (course == null) target else CarHeadingPolicy.smoothed(course!!, target,
                if (lastCarTick > 0) minOf(now - lastCarTick, .5) else 1.0 / 60, reduceMotion)
            lastCarTick = now
        } else { course = null; lastCarTick = 0.0 }
        frame = TripReplayFrame(engine.phase, engine.progress, engine.distanceFraction, engine.rate,
            engine.holdFractions, engine.holdingIndex, engine.currentSpeedMS, engine.currentSpeedKnown,
            engine.hasSpeeds, head, trailIndex, course ?: 0.0, engine.headOpacity)
        if (canCrawl) frame = frame.copy(phase = if (crawl.isPlaying) ReplayBarState.playing else ReplayBarState.idle,
            progress = crawl.progress, distanceFraction = head?.let { PlaybackProgress.fraction(cumulative, input.coords, it, trailIndex) } ?: 0.0)
    }

    fun playPause() {
        if (canReplay) change { togglePlay(now()) }
        else { crawl.toggle(input.coords, distanceMetres, now()); publish() }
    }
    fun stop() { crawl.stop(); change { stop() } }
    fun pause() { if (canReplay) change { pause() } else { crawl.stop(); publish() } }
    fun tick() { if (canReplay) change { tick(now()) } else { crawl.tick(now()); publish() } }
    fun scrubStart() { resumeAfterScrub = engine.isPlaying; change { pause() } }
    fun scrub(progress: Double) = change { seek(progress) }
    fun scrubEnd() { if (resumeAfterScrub && engine.progress < 1) change { play(now()) }; resumeAfterScrub = false }

    companion object { fun now(): Double = System.nanoTime() / 1e9 }
}

@Composable
fun TripReplayClock(player: TripReplayPlayer, active: Boolean) {
    val playing = player.frame.phase == ReplayBarState.playing
    LaunchedEffect(player, active, playing) {
        if (active && playing) while (true) withFrameNanos { player.tick() }
    }
    LaunchedEffect(active) { if (!active) player.stop() }
    DisposableEffect(player) { onDispose { player.stop() } }
}

// iOS: FullscreenMapSheet.swift, timecodePill/readoutText/hmm. Seconds are intentionally not shown.
@Composable
fun ReplayTimecode(frame: TripReplayFrame, input: TripReplayInput, distanceMetres: Double, modifier: Modifier = Modifier) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val span = if (input.timestamps.size >= 2) maxOf(0.0, TripReplayEngine.seconds(input.timestamps.first(), input.timestamps.last())) else 0.0
    fun hmm(seconds: Double): String = seconds.toLong().coerceAtLeast(0).let { String.format(Locale.ROOT, "%02d:%02d", it / 3600, it % 3600 / 60) }
    val text = hmm(span * frame.progress) + " / " + hmm(span) + if (distanceMetres > 0) " · " +
        Measure.distance(distanceMetres * frame.distanceFraction, unit, strings.language.code, strings, Measure.Style.tenths) else ""
    Text(text, style = inter(12f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.text,
        modifier = modifier.glassBackground(cornerRadius = 50.dp).padding(horizontal = 12.dp, vertical = 7.dp).testTag("replay_timecode"), maxLines = 1)
}

// iOS: FullscreenMapSheet.swift, speedBubble. No bubble on an interpolated unknown-speed leg.
@Composable
fun ReplaySpeedBubble(frame: TripReplayFrame, modifier: Modifier = Modifier) {
    if (frame.head == null || !frame.hasSpeeds || !frame.speedKnown) return
    val strings = appStrings
    val speed = Measure.speedParts(frame.speedMS, LocalDistanceUnit.current, strings.language.code, strings)
    Row(modifier.glassBackground(cornerRadius = 50.dp).padding(horizontal = 12.dp, vertical = 7.dp).testTag("replay_speed"),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(speed.value, style = inter(15f, InterWeight.heavy, monospacedDigits = true), color = AppTheme.accent)
        Text(speed.unit, style = inter(10f, InterWeight.medium), color = AppTheme.colors.textSecondary)
    }
}

// iOS: FullscreenMapSheet.swift, transportPlaque/playButton/progressBar/rateButton/stopButton.
@Composable
fun TripReplayControls(
    frame: TripReplayFrame,
    isOwn: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onRate: () -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    crawl: Boolean = false,
    enabled: Boolean = true,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().glassBackground(cornerRadius = 16.dp).padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("replay_controls"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val playing = frame.phase.showsPauseIcon
        Box(Modifier.size(44.dp).background(AppTheme.accent.copy(alpha = .14f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onPlayPause)
            .semantics { contentDescription = if (playing) strings.stop() else if (isOwn) strings.reliveTrip() else strings.watchTrip() }
            .testTag("fullscreen_replay_play"), contentAlignment = Alignment.Center) {
            Glyph(if (playing) AppGlyph.pause else AppGlyph.play, null, tint = AppTheme.accent, size = 16.dp)
        }
        if (crawl) Canvas(Modifier.weight(1f).height(40.dp).testTag("fullscreen_crawl_progress")) {
            drawLine(colors.textTertiary.copy(alpha = colors.textTertiary.alpha * .35f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 5.dp.toPx())
            drawLine(Color(0xFFFC4C02), Offset(0f, size.height / 2), Offset(size.width * frame.progress.toFloat(), size.height / 2), 5.dp.toPx())
        } else ReplayScrubber(frame, onScrubStart, onScrub, onScrubEnd, Modifier.weight(1f), enabled)
        if (!crawl) Box(Modifier.size(40.dp).clickable(enabled = enabled, role = Role.Button, onClick = onRate).testTag("fullscreen_replay_rate"), contentAlignment = Alignment.Center) {
            Text(UnitNumber.upToTenth(frame.rate, strings.language.code) + "×", style = inter(12f, InterWeight.bold, monospacedDigits = true), color = colors.textSecondary)
        }
        if (frame.phase.showsStop) Box(Modifier.size(40.dp).clickable(enabled = enabled, role = Role.Button, onClick = onStop)
            .semantics { contentDescription = strings.fullscreenMapClearPlayback() }.testTag("fullscreen_replay_stop"), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.xmark, null, tint = colors.textSecondary, size = 13.dp)
        }
    }
}

@Composable
private fun ReplayScrubber(frame: TripReplayFrame, onStart: () -> Unit, onSeek: (Double) -> Unit, onEnd: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val colors = AppTheme.colors
    val accent = AppTheme.accent
    val latestStart by rememberUpdatedState(onStart)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestEnd by rememberUpdatedState(onEnd)
    Canvas(modifier.height(44.dp).testTag("fullscreen_replay_progress")
        .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(frame.progress.toFloat(), 0f..1f)
            setProgress { if (enabled) { latestStart(); latestSeek(it.toDouble()); latestEnd(); true } else false }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                latestStart()
                try {
                    latestSeek((down.position.x / size.width.coerceAtLeast(1)).toDouble())
                    drag(down.id) { event ->
                        event.consume()
                        latestSeek((event.position.x / size.width.coerceAtLeast(1)).toDouble())
                    }
                } finally { latestEnd() }
            }
        }) {
        val y = size.height / 2
        val width = size.width.coerceAtLeast(1f)
        val progress = frame.progress.toFloat().coerceIn(0f, 1f) * width
        drawLine(colors.textTertiary.copy(alpha = colors.textTertiary.alpha * .35f), Offset(0f, y), Offset(width, y), 5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(accent, Offset(0f, y), Offset(progress.coerceAtLeast(5.dp.toPx()), y), 5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
        frame.holdFractions.forEach { fraction ->
            val x = (fraction.toFloat() * width).coerceIn(3.5.dp.toPx().coerceAtMost(width / 2), (width - 3.5.dp.toPx()).coerceAtLeast(width / 2))
            drawCircle(accent, 3.5.dp.toPx(), Offset(x, y))
            drawCircle(colors.card, 2.dp.toPx(), Offset(x, y))
        }
        val radius = 6.5.dp.toPx().coerceAtMost(width / 2)
        drawCircle(accent, 6.5.dp.toPx(), Offset(progress.coerceIn(radius, width - radius), y))
    }
}
