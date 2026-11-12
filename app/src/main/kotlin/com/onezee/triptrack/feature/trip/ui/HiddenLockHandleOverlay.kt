package com.onezee.triptrack.feature.trip.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.logic.HiddenLockHandleMath
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TrackPoint
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun HiddenLockHandleOverlay(
    points: List<TrackPoint>,
    hidden: List<Boolean>,
    handles: List<HiddenLockHandleMath.HandleIndex>,
    mapState: MapHostState,
    bottomLimitPx: Float,
    draggingKey: String?,
    bubbleKey: String?,
    bubbleText: Map<String, Pair<String, String>>,
    labels: Map<String, String>,
    forwardLabel: String,
    backLabel: String,
    pulseKey: String?,
    pulseToken: Int,
    nudgeKey: String?,
    reduceMotion: Boolean,
    onDragStart: (String) -> Unit,
    onDrag: (String, HiddenLockHandleMath.ScreenPoint) -> Unit,
    onDragEnd: (String) -> Unit,
    onLongPress: ((Int) -> String?)?,
    onAccessibilityMove: (String, Boolean) -> Unit,
    projectedPoints: Map<Int, HiddenLockHandleMath.ScreenPoint>? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val colors = AppTheme.colors
    val projectionRevision = mapState.projectionRevision
    val positions = remember(points, handles, projectionRevision, mapState, projectedPoints) {
        handles.associateNotNull { handle ->
            val screen = if (projectedPoints != null) {
                projectedPoints[handle.index]?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
            } else {
                val point = points.getOrNull(handle.index) ?: return@associateNotNull null
                mapState.screenPosition(GeoPoint(point.latitude, point.longitude))?.let { Offset(it.x, it.y) }
            } ?: return@associateNotNull null
            handle.key to screen
        }
    }
    val liftedKey = draggingKey
    val currentPositions = remember(positions, nudgeKey, points, mapState) { positions }
    val currentPositionsForInput by rememberUpdatedState(currentPositions)
    var pulseProgress by remember { mutableFloatStateOf(0f) }
    var pulseAlpha by remember { mutableFloatStateOf(0f) }
    var nudgeProgress by remember { mutableFloatStateOf(0f) }
    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pulseToken, reduceMotion) {
        pulseProgress = 0f
        pulseAlpha = if (pulseKey == null || reduceMotion) 0f else 0.38f
        if (pulseKey != null && !reduceMotion) {
            val started = withFrameNanos { it }
            while (true) {
                val fraction = ((withFrameNanos { it } - started) / 450_000_000f).coerceIn(0f, 1f)
                pulseProgress = fraction
                pulseAlpha = .38f * (1f - fraction)
                if (fraction >= 1f) break
            }
        }
    }
    LaunchedEffect(nudgeKey, reduceMotion) {
        nudgeProgress = 0f
        if (nudgeKey != null && !reduceMotion) {
            kotlinx.coroutines.delay(600)
            val started = withFrameNanos { it }
            while (true) {
                val fraction = ((withFrameNanos { it } - started) / 300_000_000f).coerceIn(0f, 1f)
                nudgeProgress = fraction
                if (fraction >= 1f) break
            }
            val returned = withFrameNanos { it }
            while (true) {
                val fraction = ((withFrameNanos { it } - returned) / 300_000_000f).coerceIn(0f, 1f)
                nudgeProgress = 1f - fraction
                if (fraction >= 1f) break
            }
        }
    }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnAccessibilityMove by rememberUpdatedState(onAccessibilityMove)
    val currentMapState by rememberUpdatedState(mapState)
    val currentProjectedPoints by rememberUpdatedState(projectedPoints)
    val currentHandles by rememberUpdatedState(handles)
    val currentHidden by rememberUpdatedState(hidden)
    val currentPoints by rememberUpdatedState(points)

    BoxWithConstraints(
        modifier.fillMaxSize().testTag("hidden_lock_overlay").pointerInput(points, mapState) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.position.y >= bottomLimitPx) return@awaitEachGesture
                val hit = currentHandles.mapNotNull { handle ->
                    val point = currentPositionsForInput[handle.key] ?: return@mapNotNull null
                    val distance = hypot(point.x - down.position.x, point.y - down.position.y)
                    if (distance <= 22.dp.toPx()) handle to distance else null
                }.minByOrNull { it.second }?.first
                if (hit != null) {
                    down.consume()
                    currentOnDragStart(hit.key)
                    trackDrag(down.id, down.position, hit.key, currentOnDrag, currentOnDragEnd)
                    return@awaitEachGesture
                }

                val onLongPressCallback = currentOnLongPress ?: return@awaitEachGesture
                val nearest = nearestVisibleTrackPoint(
                    currentPoints,
                    currentHidden,
                    currentMapState,
                    HiddenLockHandleMath.ScreenPoint(down.position.x.toDouble(), down.position.y.toDouble()),
                    currentProjectedPoints,
                ) ?: return@awaitEachGesture
                if (nearest.distance > 16.dp.toPx()) return@awaitEachGesture

                var releasedDuringWait = false
                val held = withTimeoutOrNull<Boolean>(450) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        val distance = (change.position - down.position).getDistance()
                        if (distance > 6.dp.toPx()) return@withTimeoutOrNull false
                        if (!change.pressed) {
                            releasedDuringWait = true
                            return@withTimeoutOrNull change.uptimeMillis - down.uptimeMillis >= 450L
                        }
                    }
                    false
                } ?: true
                if (!held) return@awaitEachGesture
                down.consume()
                val key = onLongPressCallback(nearest.index) ?: return@awaitEachGesture
                currentOnDragStart(key)
                if (releasedDuringWait) currentOnDragEnd(key)
                else trackDrag(down.id, down.position, key, currentOnDrag, currentOnDragEnd)
            }
        },
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val nudgeOffset = remember(nudgeKey, nudgeProgress, positions, points, mapState, projectedPoints) {
            val origin = nudgeKey?.let(positions::get) ?: return@remember Offset.Zero
            val handle = handles.firstOrNull { it.key == nudgeKey } ?: return@remember Offset.Zero
            val deltaIndex = if (handle.key == "start") 1 else -1
            val neighbour = points.getOrNull(handle.index + deltaIndex) ?: return@remember Offset.Zero
            val neighbourScreen = (projectedPoints?.get(handle.index + deltaIndex)?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
                ?: if (projectedPoints == null) mapState.screenPosition(GeoPoint(neighbour.latitude, neighbour.longitude))?.let { Offset(it.x, it.y) }
                else null) ?: return@remember Offset.Zero
            val dx = neighbourScreen.x - origin.x
            val dy = neighbourScreen.y - origin.y
            val length = hypot(dx.toDouble(), dy.toDouble()).coerceAtLeast(1.0).toFloat()
            Offset(dx / length * with(density) { 25.dp.toPx() } * nudgeProgress, dy / length * with(density) { 25.dp.toPx() } * nudgeProgress)
        }
        val visualPositions = currentPositions.mapValues { (key, position) ->
            if (key == nudgeKey) position + nudgeOffset else position
        }

        Canvas(Modifier.fillMaxSize()) {
            visualPositions.forEach { (key, center) ->
                val handle = handles.firstOrNull { it.key == key } ?: return@forEach
                val lifted = key == liftedKey
                val scale = if (lifted) 1.2f else 1f
                val radius = 10.5.dp.toPx() * scale
                val ink = if (handle.ghost) LockInk.copy(alpha = .58f) else LockInk
                drawCircle(Color.Black.copy(alpha = if (lifted) .26f else .12f), radius = radius, center = center + Offset(0f, if (lifted) 3.dp.toPx() else 1.5.dp.toPx()))
                if (key == pulseKey && pulseAlpha > 0f) {
                    drawCircle(
                        AppTheme.accent.copy(alpha = pulseAlpha),
                        radius = radius + 2.dp.toPx() + 10.dp.toPx() * pulseProgress,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
                drawCircle(Color.White.copy(alpha = if (handle.ghost) .9f else 1f), radius, center)
                if (handle.ghost) {
                    drawCircle(
                        ink,
                        radius,
                        center,
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.4.dp.toPx()))),
                    )
                }
                val bodySize = Size(8.2.dp.toPx(), 6.2.dp.toPx())
                drawRoundRect(
                    ink,
                    topLeft = center + Offset(-bodySize.width / 2, -0.5.dp.toPx()),
                    size = bodySize,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.4.dp.toPx()),
                )
                val shackle = androidx.compose.ui.geometry.Rect(
                    center.x - 2.8.dp.toPx(),
                    center.y - 5.dp.toPx(),
                    center.x + 2.8.dp.toPx(),
                    center.y + 0.5.dp.toPx(),
                )
                drawArc(
                    ink,
                    startAngle = if (handle.ghost) 200f else 180f,
                    sweepAngle = if (handle.ghost) 140f else 180f,
                    useCenter = false,
                    topLeft = Offset(shackle.left, shackle.top),
                    size = Size(shackle.width, shackle.height),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        handles.forEach { handle ->
            val center = visualPositions[handle.key] ?: return@forEach
            val customActions = listOf(
                CustomAccessibilityAction(forwardLabel) {
                    currentOnAccessibilityMove(handle.key, true)
                    true
                },
                CustomAccessibilityAction(backLabel) {
                    currentOnAccessibilityMove(handle.key, false)
                    true
                },
            )
            Box(
                Modifier
                    .offset { IntOffset((center.x - 22.dp.toPx()).roundToInt(), (center.y - 22.dp.toPx()).roundToInt()) }
                    .size(44.dp)
                    .semantics {
                        contentDescription = labels[handle.key].orEmpty()
                        stateDescription = bubbleText[handle.key]?.let { "${it.first}, ${it.second}" }.orEmpty()
                        this.customActions = customActions
                    }
                    .testTag("hidden_handle_${handle.key.replace(':', '_')}"),
            )
        }

        val bubble = bubbleText[bubbleKey]
        AnimatedVisibility(
            visible = bubbleKey != null && bubble != null && visualPositions[bubbleKey] != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val center = visualPositions[bubbleKey] ?: Offset.Zero
            if (bubble != null) {
                val bubbleTop = with(density) {
                    minOf(center.y - 64.dp.toPx(), bottomLimitPx - bubbleSize.height - 8.dp.toPx())
                        .coerceIn(8.dp.toPx(), heightPx - bubbleSize.height - 8.dp.toPx())
                }
                val bubbleLeft = (center.x - bubbleSize.width / 2f).coerceIn(
                    with(density) { 8.dp.toPx() },
                    widthPx - bubbleSize.width - with(density) { 8.dp.toPx() },
                )
                Box(
                    Modifier
                        .offset { IntOffset(bubbleLeft.roundToInt(), bubbleTop.roundToInt()) }
                        .onSizeChanged { bubbleSize = it }
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .background(BubbleInk, RoundedCornerShape(12.dp))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                ) {
                    Column {
                        Text(bubble.first, style = inter(14f, InterWeight.semibold), color = Color.White)
                        Text(bubble.second, style = inter(11f), color = Color.White.copy(alpha = .74f))
                    }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    initialPosition: Offset,
    key: String,
    onDrag: (String, HiddenLockHandleMath.ScreenPoint) -> Unit,
    onDragEnd: (String) -> Unit,
) {
    var lastPosition = initialPosition
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
        if (change.position.x != lastPosition.x || change.position.y != lastPosition.y) {
            lastPosition = change.position
            onDrag(key, HiddenLockHandleMath.ScreenPoint(change.position.x.toDouble(), change.position.y.toDouble()))
        }
        if (!change.pressed) {
            change.consume()
            onDragEnd(key)
            return
        }
        change.consume()
    }
}

private fun nearestVisibleTrackPoint(
    points: List<TrackPoint>,
    hidden: List<Boolean>,
    mapState: MapHostState,
    finger: HiddenLockHandleMath.ScreenPoint,
    projectedPoints: Map<Int, HiddenLockHandleMath.ScreenPoint>?,
): HiddenLockHandleMath.NearestPoint? {
    if (projectedPoints != null) {
        val stride = (points.size / 300).coerceAtLeast(1)
        var coarseIndex: Int? = null
        var coarseDistance = Double.MAX_VALUE
        for (index in points.indices step stride) {
            if (hidden.getOrNull(index) == true) continue
            val screen = projectedPoints[index] ?: continue
            val distance = hypot(screen.x - finger.x, screen.y - finger.y)
            if (distance < coarseDistance) {
                coarseDistance = distance
                coarseIndex = index
            }
        }
        val centre = coarseIndex ?: return null
        var best: HiddenLockHandleMath.NearestPoint? = null
        for (index in (centre - stride..centre + stride).filter { it in points.indices && hidden.getOrNull(it) != true }) {
            val screen = projectedPoints[index] ?: continue
            val distance = hypot(screen.x - finger.x, screen.y - finger.y)
            if (best == null || distance < best.distance) best = HiddenLockHandleMath.NearestPoint(index, distance)
        }
        return best
    }
    val coordinate = mapState.coordinateAt(finger.x.toFloat(), finger.y.toFloat()) ?: return null
    val stride = (points.size / 300).coerceAtLeast(1)
    var coarseIndex: Int? = null
    var coarseDistance = Double.MAX_VALUE
    for (index in points.indices step stride) {
        if (hidden.getOrNull(index) == true) continue
        val point = points[index]
        val distance = com.onezee.triptrack.logic.GeometryUtils.haversineDistance(coordinate, GeoPoint(point.latitude, point.longitude))
        if (distance < coarseDistance) {
            coarseDistance = distance
            coarseIndex = index
        }
    }
    val centre = coarseIndex ?: return null
    var best: HiddenLockHandleMath.NearestPoint? = null
    for (index in (centre - stride..centre + stride).filter { it in points.indices }) {
        if (hidden.getOrNull(index) == true) continue
        val point = points[index]
        val screen = mapState.screenPosition(GeoPoint(point.latitude, point.longitude)) ?: continue
        val distance = hypot(screen.x - finger.x.toFloat(), screen.y - finger.y.toFloat())
        if (best == null || distance < best.distance) best = HiddenLockHandleMath.NearestPoint(index, distance.toDouble())
    }
    return best
}

private inline fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for (item in this) transform(item)?.let { result[it.first] = it.second }
    return result
}

/** The lock glyph's ink, the same as the trip map's lock pins ([hiddenLockBitmap]). The theme's text
 * colour is near white in the dark theme, which drew a white lock on a white disc. */
private val LockInk = Color(0xFF5A5B65)

/** The bubble stays dark in both themes, as on iOS: on `colors.text` the dark theme got white text
 * on a white plate. */
private val BubbleInk = Color(0xF21E1E1E)
