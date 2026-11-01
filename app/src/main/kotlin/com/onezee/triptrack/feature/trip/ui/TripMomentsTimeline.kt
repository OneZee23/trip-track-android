package com.onezee.triptrack.feature.trip.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.feature.trip.MomentState
import com.onezee.triptrack.feature.trip.PhotoState
import com.onezee.triptrack.feature.trip.SegmentState
import com.onezee.triptrack.logic.SegmentHistory
import com.onezee.triptrack.model.PlaceChip
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import java.time.Instant
import java.util.UUID

/** iOS: the default name of a checkpoint is composed at display, from its number in time. */
fun CheckpointState.displayName(strings: AppStrings): String = name?.takeIf { it.isNotEmpty() } ?: strings.checkpointDefaultName(number)

/** iOS: TripSegmentName.text. The hand-given name, else «A → B» from the checkpoints' names. */
fun SegmentState.displayName(checkpoints: List<CheckpointState>, strings: AppStrings): String {
    name?.takeIf { it.isNotEmpty() }?.let { return it }
    val pair = resolved(checkpoints) ?: return strings.checkpointLeg()
    return "${pair.first.displayName(strings)} → ${pair.second.displayName(strings)}"
}

/** iOS: TripSegmentMetrics.resolve's order: the earlier checkpoint first, whatever pull brought. */
fun SegmentState.resolved(checkpoints: List<CheckpointState>): Pair<CheckpointState, CheckpointState>? {
    val a = checkpoints.firstOrNull { it.id == fromId } ?: return null
    val b = checkpoints.firstOrNull { it.id == toId } ?: return null
    return if (a.elapsed <= b.elapsed) a to b else b to a
}

/**
 * iOS: TripMomentsTimeline.swift. The road of one's own trip: Start, then the checkpoints and the
 * stacks of loose photos in time order, then Finish. Between two nodes the time and distance
 * covered («+1 ч 40 мин · 100 км»), except where a segment spans exactly those two nodes (the same
 * number twice side by side asks to be compared). A segment's block sits under its earlier
 * checkpoint. Shown on every own trip, even an empty one: Start → Finish is an owner decision.
 */
@Composable
fun TripMomentsTimeline(
    moments: List<MomentState>,
    checkpoints: List<CheckpointState>,
    segments: List<SegmentState>,
    placeChips: Map<UUID, PlaceChip>,
    startDate: Instant,
    endDate: Instant?,
    totalElapsed: Double,
    totalMetres: Double,
    onSelectCheckpoint: ((CheckpointState) -> Unit)?,
    onOpenPhoto: (PhotoState) -> Unit,
    onSelectSegment: ((SegmentState) -> Unit)?,
    modifier: Modifier = Modifier,
    highlightedId: UUID? = null,
    focusId: UUID? = null,
    onOpenPlace: ((UUID) -> Unit)? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val metrics = TimelineMetrics.current()
    var railTop by remember { mutableFloatStateOf(Float.NaN) }
    var railBottom by remember { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    val railX = with(density) { (14.dp + metrics.time + 12.dp + metrics.node / 2).toPx() }
    val railColor = colors.border
    Column(
        modifier
            .fillMaxWidth()
            .surfaceCard()
            .drawBehind {
                if (!railTop.isNaN() && !railBottom.isNaN() && railBottom > railTop) {
                    drawLine(railColor, Offset(railX, railTop), Offset(railX, railBottom), 2.dp.toPx())
                }
            }
            .padding(vertical = 6.dp)
            .testTag("trip_moments"),
    ) {
        val byCheckpoint = checkpoints.associateBy { it.id }
        NodeRow(time = startDate, metrics = metrics, node = { StartNode(metrics.node) }, onNodePositioned = { y -> railTop = y }) {
            TitleLine(strings.momentStart(), metrics, chevron = false)
        }
        moments.forEachIndexed { index, moment ->
            Connector(index, moments, segments, checkpoints, totalElapsed, totalMetres, metrics)
            val highlight by animateColorAsState(if (moment.id == highlightedId) AppTheme.accent.copy(alpha = 0.12f) else Color.Transparent, tween(400), label = "momentHighlight")
            val target = remember(moment.id) { BringIntoViewRequester() }
            LaunchedEffect(focusId == moment.id) {
                if (focusId == moment.id) {
                    withFrameNanos { }
                    target.bringIntoView()
                    onFocusConsumed()
                }
            }
            Column(Modifier.bringIntoViewRequester(target).background(highlight)) {
                val checkpoint = moment.checkpointId?.let(byCheckpoint::get)
                if (checkpoint != null) {
                    CheckpointRow(checkpoint, moment, metrics, onSelectCheckpoint, onOpenPhoto)
                    placeChips[checkpoint.id]?.let { PlaceChipRow(it, metrics, onOpenPlace) }
                    segments.filter { it.resolved(checkpoints)?.first?.id == checkpoint.id }.forEach { segment ->
                        SegmentBlock(segment, checkpoints, metrics, onSelectSegment)
                    }
                } else {
                    PhotoStackRow(moment, startDate, metrics, onOpenPhoto)
                }
            }
        }
        Connector(moments.size, moments, segments, checkpoints, totalElapsed, totalMetres, metrics)
        NodeRow(time = endDate, metrics = metrics, node = { FinishNode(metrics.node) }, onNodePositioned = { y -> railBottom = y }) {
            TitleLine(strings.momentFinish(), metrics, chevron = false)
            Measure(strings.checkpointFromStart(), CheckpointReading.text(totalElapsed, totalMetres, LocalDistanceUnit.current, strings))
        }
    }
}

/** iOS: the timeline's metrics at the current text size: node 28 (≤ 40), time column 40 (≤ 76). */
private class TimelineMetrics(val node: Dp, val time: Dp) {
    val contentInset: Dp get() = 14.dp + time + node + 24.dp

    companion object {
        @Composable
        fun current(): TimelineMetrics {
            val scale = LocalDensity.current.fontScale
            return TimelineMetrics(min(28.dp * scale, 40.dp), min(40.dp * scale, 76.dp))
        }
    }
}

@Composable
private fun NodeRow(
    time: Instant?,
    metrics: TimelineMetrics,
    node: @Composable () -> Unit,
    onNodePositioned: ((Float) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val strings = appStrings
    var rowY by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowY = it.positionInParent().y }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(metrics.time).height(metrics.node), contentAlignment = Alignment.CenterEnd) {
            Text(time?.let { LocalizedDateFormatter.patterns("HH:mm", strings.zone).getValue(strings.language).format(it) } ?: "",
                style = interScaled(13f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.textSecondary, maxLines = 1)
        }
        Box(
            Modifier
                .size(metrics.node)
                .onGloballyPositioned { coordinates ->
                    // The rail runs between the first and last node centres, in the card's space.
                    onNodePositioned?.invoke(rowY + coordinates.boundsInParent().center.y)
                },
            contentAlignment = Alignment.Center,
        ) { node() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun StartNode(size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(16.dp).background(AppTheme.colors.card, CircleShape).border(3.dp, AppTheme.accent, CircleShape))
    }
}

@Composable
private fun FinishNode(size: Dp) {
    Box(Modifier.size(size).background(AppTheme.colors.text, CircleShape), contentAlignment = Alignment.Center) {
        Glyph(AppGlyph.flagCheckered, contentDescription = null, tint = AppTheme.colors.card, size = 15.dp)
    }
}

@Composable
private fun CameraNode(size: Dp) {
    Box(Modifier.size(size).background(AppTheme.colors.card, CircleShape).border(1.5.dp, AppTheme.colors.border, CircleShape), contentAlignment = Alignment.Center) {
        Glyph(AppGlyph.cameraFill, contentDescription = null, tint = AppTheme.colors.textSecondary, size = 14.dp)
    }
}

@Composable
private fun NumberNode(number: Int, size: Dp) {
    Box(Modifier.size(size).background(AppTheme.accent, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", style = inter(13f, InterWeight.bold, monospacedDigits = true), color = Color.White)
    }
}

@Composable
private fun TitleLine(title: String, metrics: TimelineMetrics, chevron: Boolean) {
    Row(Modifier.heightIn(min = metrics.node), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = interScaled(15f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 2, modifier = Modifier.weight(1f))
        if (chevron) Glyph(AppGlyph.chevronRight, contentDescription = null, tint = AppTheme.colors.textTertiary, size = 15.dp)
    }
}

/** iOS: measure(label, value). «От старта  1 ч 40 мин · 100 км». */
@Composable
private fun Measure(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = interScaled(11f, InterWeight.semibold), color = AppTheme.colors.textTertiary, maxLines = 1)
        Text(value, style = interScaled(12f, InterWeight.bold, monospacedDigits = true), color = AppTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun CheckpointRow(
    checkpoint: CheckpointState,
    moment: MomentState,
    metrics: TimelineMetrics,
    onSelect: ((CheckpointState) -> Unit)?,
    onOpenPhoto: (PhotoState) -> Unit,
) {
    val strings = appStrings
    val haptics = rememberHaptics()
    PressableCard(
        onClick = { haptics.tap(); onSelect?.invoke(checkpoint) },
        enabled = onSelect != null,
        modifier = Modifier.testTag("moment_checkpoint"),
    ) {
        NodeRow(time = checkpoint.timestamp, metrics = metrics, node = { NumberNode(checkpoint.number, metrics.node) }) {
            TitleLine(checkpoint.displayName(strings), metrics, chevron = onSelect != null)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    Measure(strings.checkpointFromStart(), CheckpointReading.text(checkpoint.elapsed, checkpoint.metres, LocalDistanceUnit.current, strings))
                }
                if (moment.photos.isNotEmpty()) PhotoStack(moment.photos)
            }
        }
    }
}

@Composable
private fun PhotoStackRow(moment: MomentState, startDate: Instant, metrics: TimelineMetrics, onOpenPhoto: (PhotoState) -> Unit) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val count = moment.photos.size
    NodeRow(time = startDate.plusMillis((moment.elapsed * 1000).toLong()), metrics = metrics, node = { CameraNode(metrics.node) }) {
        TitleLine("$count ${strings.nounPhotos(count)}", metrics, chevron = false)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                Measure(strings.checkpointFromStart(), CheckpointReading.text(moment.elapsed, moment.metres, LocalDistanceUnit.current, strings))
            }
            PressableCard(
                onClick = { haptics.tap(); moment.photos.firstOrNull()?.let(onOpenPhoto) },
                modifier = Modifier.semantics { contentDescription = strings.nounPhotos(count) }.testTag("moment_photos"),
            ) { PhotoStack(moment.photos) }
        }
    }
}

/** iOS: photoStack. The first two thumbnails overlapping, the first on top, «+N» for the rest. */
@Composable
private fun PhotoStack(photos: List<PhotoState>) {
    val colors = AppTheme.colors
    Box {
        Row(horizontalArrangement = Arrangement.spacedBy((-9).dp)) {
            photos.take(2).forEachIndexed { index, photo ->
                Box(
                    Modifier
                        .zIndex((2 - index).toFloat())
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .border(2.dp, colors.card, RoundedCornerShape(7.dp)),
                ) {
                    PhotoImage(PhotoSource.Local(photo.filename), maxPixels = 120, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (photos.size > 2) {
            Text(
                "+${photos.size - 2}",
                style = inter(9f, InterWeight.bold, monospacedDigits = true),
                color = colors.card,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(4.dp, 4.dp)
                    .zIndex(3f)
                    .background(colors.text, CapsuleShape)
                    .border(1.5.dp, colors.card, CapsuleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * iOS: the place chip under a checkpoint that became a live place. «Первый раз здесь», or «Здесь 11
 * раз · обычно 2 ч 14 мин». A sibling below the row, never a button inside the row's button.
 */
@Composable
private fun PlaceChipRow(chip: PlaceChip, metrics: TimelineMetrics, onOpenPlace: ((UUID) -> Unit)?) {
    val strings = appStrings
    val text = if (chip.count <= 1) strings.placeChipFirst() else strings.placeHereTimes(chip.count) +
        (chip.usual?.let { " · " + strings.placeChipUsually(CheckpointReading.clock(it, strings)) } ?: "")
    Row(
        Modifier
            .padding(start = metrics.contentInset, end = 14.dp)
            .offset(y = (-4).dp)
            .background(AppTheme.accentBg, CapsuleShape)
            .then(if (onOpenPlace != null) Modifier.clickable { onOpenPlace(chip.placeId) } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("checkpoint_place_chip"),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(AppGlyph.mappinAndEllipse, contentDescription = null, tint = AppTheme.accent, size = 13.dp)
        Text(text, style = interScaled(12f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1)
    }
}

/**
 * iOS: segmentBlock. Under the segment's earlier checkpoint: an accent bar, «ОТРЕЗОК · A → B», the
 * time and distance between the two, and from two passes on the history of this road.
 */
@Composable
private fun SegmentBlock(segment: SegmentState, checkpoints: List<CheckpointState>, metrics: TimelineMetrics, onSelect: ((SegmentState) -> Unit)?) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val unit = LocalDistanceUnit.current
    PressableCard(
        onClick = { haptics.tap(); onSelect?.invoke(segment) },
        enabled = onSelect != null,
        modifier = Modifier.padding(start = metrics.contentInset, end = 14.dp, top = 2.dp, bottom = 4.dp).testTag("moment_segment"),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(AppTheme.accent, RoundedCornerShape(1.5.dp)))
            Column(Modifier.weight(1f).padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${strings.checkpointLeg().uppercased(strings.language)} · ${segment.displayName(checkpoints, strings)}",
                    style = inter(12f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1)
                if (segment.elapsed != null && segment.metres != null) {
                    Text(CheckpointReading.text(segment.elapsed, segment.metres, unit, strings), style = inter(17f, InterWeight.bold, monospacedDigits = true),
                        color = AppTheme.colors.text, maxLines = 1)
                }
                if (segment.history.size >= 2) {
                    Text(strings.segmentHistory(segment.history.size, segment.history.joinToString(" · ") { SegmentHistory.clock(it) }),
                        style = inter(13f), color = AppTheme.colors.textSecondary, maxLines = 2, modifier = Modifier.testTag("moment_segment_history"))
                }
            }
        }
    }
}

/** iOS: connector(before:). The time and distance between two nodes, at the content's inset. */
@Composable
private fun Connector(
    index: Int,
    moments: List<MomentState>,
    segments: List<SegmentState>,
    checkpoints: List<CheckpointState>,
    totalElapsed: Double,
    totalMetres: Double,
    metrics: TimelineMetrics,
) {
    if (index == 0) return
    val previous = moments[index - 1]
    val nextElapsed = moments.getOrNull(index)?.elapsed ?: totalElapsed
    val nextMetres = moments.getOrNull(index)?.metres ?: totalMetres
    val dt = nextElapsed - previous.elapsed
    val dm = nextMetres - previous.metres
    if (dt <= 0 && dm <= 0) return
    if (index < moments.size) {
        val pair = setOfNotNull(previous.checkpointId, moments[index].checkpointId)
        val spanned = pair.size == 2 && segments.any { segment ->
            segment.resolved(checkpoints)?.let { setOf(it.first.id, it.second.id) == pair } == true
        }
        if (spanned) return
    }
    val strings = appStrings
    Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.width(metrics.time + 12.dp + metrics.node))
        Text("+" + CheckpointReading.text(maxOf(0.0, dt), maxOf(0.0, dm), LocalDistanceUnit.current, strings),
            style = interScaled(12f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.textTertiary, maxLines = 1,
            modifier = Modifier.testTag("moment_connector"))
    }
}
