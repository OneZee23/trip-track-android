package com.onezee.triptrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch

/** One page of the viewer: the photo's bytes and when it entered the trip. */
data class ViewerPage(val id: UUID, val source: PhotoSource?, val preview: PhotoSource?, val timestamp: Instant)

/**
 * iOS: PhotoFullScreenView.swift with ZoomableImageView. The trip's photos paged sideways on
 * black: pinch and double tap zoom (paging stops while zoomed), a one-finger drag down at rest
 * dismisses, a tap hides the chrome. Deleting asks the house question and pages to a neighbour.
 *
 * A layer at the screen's root, not a dialog: it lies above the fullscreen map and fades in.
 */
@Composable
fun PhotoViewer(
    pages: List<ViewerPage>,
    initialIndex: Int,
    region: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((UUID) -> Unit)? = null,
) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var live by remember { mutableStateOf(pages) }
    val showsCounter = remember { pages.size > 1 }
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) { live.size }
    var chromeHidden by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val drag = remember { Animatable(0f) }
    var confirmingDelete by remember { mutableStateOf<ViewerPage?>(null) }
    val density = LocalDensity.current
    val dismissProgress = (maxOf(drag.value, 0f) / with(density) { 260.dp.toPx() }).coerceIn(0f, 1f)
    val chromeAlpha by animateFloatAsState(if (!chromeHidden && !zoomed) 1f - dismissProgress else 0f, tween(180), label = "viewerChrome")

    HideAppTabBar()
    BackHandler(onBack = onDismiss)

    Box(modifier.fillMaxSize().testTag("photo_viewer")) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - 0.55f * dismissProgress)))
        HorizontalPager(
            pager,
            Modifier.fillMaxSize().graphicsLayer {
                translationY = drag.value
                val scale = 1f - 0.12f * dismissProgress
                scaleX = scale; scaleY = scale
            },
            userScrollEnabled = !zoomed,
            key = { live.getOrNull(it)?.id ?: it },
        ) { index ->
            val page = live.getOrNull(index) ?: return@HorizontalPager
            ZoomablePage(
                page = page,
                current = index == pager.currentPage,
                onZoomChanged = { if (index == pager.currentPage) zoomed = it },
                onTap = { if (!zoomed) chromeHidden = !chromeHidden },
                onDrag = { dy -> scope.launch { drag.snapTo(if (dy >= 0) dy else dy / 6f) } },
                onDragEnd = { translation, velocity ->
                    val predicted = translation + velocity * 0.3f
                    if (translation > with(density) { 120.dp.toPx() } || predicted > with(density) { 300.dp.toPx() }) onDismiss()
                    else scope.launch { drag.animateTo(0f, spring(dampingRatio = 1f, stiffness = 440f)) }
                },
            )
        }
        Column(Modifier.fillMaxSize().alpha(chromeAlpha)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    ViewerButton(AppGlyph.trash, strings.delete(), "photo_viewer_delete", enabled = chromeAlpha > 0.5f) {
                        live.getOrNull(pager.currentPage)?.let { confirmingDelete = it }
                    }
                }
                ViewerButton(AppGlyph.xmark, strings.closeSheet(), "photo_viewer_close", enabled = chromeAlpha > 0.5f, onClick = onDismiss)
            }
            Spacer(Modifier.weight(1f))
            val page = live.getOrNull(pager.currentPage)
            val caption = page?.let { caption(it.timestamp, region) }
            if (showsCounter || caption != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showsCounter) {
                        Text("${pager.currentPage + 1} / ${live.size}", style = inter(17f, InterWeight.bold, monospacedDigits = true), color = Color.White,
                            modifier = Modifier.testTag("photo_viewer_counter"))
                    }
                    if (caption != null) Text(caption, style = inter(13f), color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                }
            }
        }
    }

    AppConfirm(
        item = confirmingDelete,
        onDismissRequest = { confirmingDelete = null },
        title = { strings.deletePhoto() },
        actions = { page ->
            listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive, testTag = "photo_viewer_delete_confirm") {
                haptics.action()
                val remaining = live.filterNot { it.id == page.id }
                if (remaining.isEmpty()) {
                    onDelete?.invoke(page.id)
                    onDismiss()
                } else {
                    live = remaining
                    onDelete?.invoke(page.id)
                }
            })
        },
    )
}

@Composable
private fun caption(timestamp: Instant, region: String?): String? {
    val strings = appStrings
    val date = if (timestamp.epochSecond == 0L) "" else LocalizedDateFormatter.patterns("d MMM, HH:mm", strings.zone).getValue(strings.language).format(timestamp)
    val line = date + if (!region.isNullOrEmpty()) " · $region" else ""
    return line.ifEmpty { null }
}

@Composable
private fun ViewerButton(glyph: AppGlyph, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(40.dp)
            .plainPressFeedback(interaction)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, contentDescription = null, tint = Color.White, size = 20.dp)
    }
}

/**
 * iOS: ZoomableImageView. The photo fills the screen when filling crops at most a fifth of it
 * (fill ≤ 1.2 × fit), otherwise it fits. Zoom runs to four times the resting scale; a double tap
 * goes to 2.5 at the finger and back. At rest a vertical one-finger drag is the dismiss gesture;
 * a sideways one is left to the pager.
 */
@Composable
private fun ZoomablePage(
    page: ViewerPage,
    current: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (translation: Float, velocity: Float) -> Unit,
) {
    val images = LocalTripPhotoImages.current
    val image by produceState<ImageBitmap?>(page.preview?.let { images?.cached(it, 600) }, page, images) {
        if (images == null) return@produceState
        if (value == null) page.preview?.let { value = images.load(it, 600) }
        page.source?.let { source -> images.load(source, 2400)?.let { value = it } }
    }
    var scale by remember(page.id) { mutableFloatStateOf(1f) }
    var offset by remember(page.id) { mutableStateOf(Offset.Zero) }
    val zoomAnimation = rememberCoroutineScope()
    if (!current && scale != 1f) { scale = 1f; offset = Offset.Zero }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bitmap = image
        if (bitmap == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onTap)) {
                Glyph(AppGlyph.photo, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), size = 32.dp)
                Text(if (page.source == null) "—" else "", style = inter(13f), color = Color.White.copy(alpha = 0.5f))
            }
            return@BoxWithConstraints
        }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val fit = minOf(w / bitmap.width, h / bitmap.height)
        val fill = maxOf(w / bitmap.width, h / bitmap.height)
        val contentScale = if (fill <= fit * 1.2f) ContentScale.Crop else ContentScale.Fit
        Image(
            bitmap,
            contentDescription = null,
            contentScale = contentScale,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(page.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { at ->
                            if (scale > 1.01f) {
                                scale = 1f; offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                // Zoom towards the finger: keep the tapped point where it was.
                                offset = Offset((w / 2 - at.x) * 1.5f, (h / 2 - at.y) * 1.5f)
                            }
                            onZoomChanged(scale > 1.01f)
                        },
                    )
                }
                .pointerInput(page.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dismissing = false
                        var total = Offset.Zero
                        var lastTime = down.uptimeMillis
                        var velocity = 0f
                        val slop = viewConfiguration.touchSlop
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val zoom = event.calculateZoom()
                                scale = (scale * zoom).coerceIn(0.85f, 4f)
                                offset += event.calculatePan()
                                onZoomChanged(scale > 1.01f)
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1) {
                                val change = pressed.first()
                                val delta = change.positionChange()
                                if (scale > 1.01f) {
                                    offset += delta
                                    change.consume()
                                } else {
                                    total += delta
                                    if (!dismissing && abs(total.y) > slop && abs(total.y) > 1.6f * abs(total.x)) dismissing = true
                                    if (dismissing) {
                                        val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1)
                                        velocity = delta.y / dt * 1000f
                                        lastTime = change.uptimeMillis
                                        onDrag(total.y)
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale < 1f) zoomAnimation.launch { scale = 1f; offset = Offset.Zero; onZoomChanged(false) }
                        if (dismissing) onDragEnd(total.y, velocity)
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
