package com.onezee.triptrack.feature.drafts.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.navigation.TabBarMetrics
import com.onezee.triptrack.designsystem.navigation.tabBarTappableInset
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.drafts.DraftBucket
import com.onezee.triptrack.feature.drafts.DraftsLayout
import com.onezee.triptrack.feature.drafts.DraftsViewModel
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.clockTime
import com.onezee.triptrack.ui.confirmationAction
import com.onezee.triptrack.ui.confirmationMessage
import com.onezee.triptrack.ui.confirmationTitle
import com.onezee.triptrack.ui.toast
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * iOS: DraftsListView.swift. The trips auto-tracking recorded, grouped Сегодня / Вчера / Ранее, each
 * group one card. A swipe right says «Моя», a swipe left asks to delete; «Выбрать» decides many at
 * once. Status is set by the screen, never by a badge on the card. Opening the list marks the drafts
 * seen, and the dot on Я goes out.
 */
@Composable
fun DraftsListScreen(vm: DraftsViewModel, onOpenTrip: (TripSummary) -> Unit, onBack: () -> Unit, now: () -> Instant = Instant::now) {
    val state by vm.state.collectAsState()
    val strings = appStrings
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val tappable = tabBarTappableInset()
    // DraftsLayout: the button stands 12 above the tab bar, the list clears both.
    val buttonBottom = tappable + TabBarMetrics.bottomGap + TabBarMetrics.pillHeight + 12.dp
    val listBottom = tappable + 68.dp + 22.dp + 12.dp + 52.dp + 12.dp
    var toast by remember { mutableStateOf<ToastItem?>(null) }

    BackHandler(enabled = state.selecting) { vm.closeSelection() }

    LaunchedEffect(vm) { vm.opened() }
    LaunchedEffect(state.message) { state.message?.let { toast = strings.toast(it); vm.consumeMessage() } }
    HideAppTabBar(state.confirmation != null)

    Box(Modifier.fillMaxSize().background(AppTheme.colors.bg).testTag("drafts_list")) {
        if (state.drafts.isEmpty() && !state.isLoading) {
            DraftsEmpty(windowHeight)
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = safeTop + 50.dp, bottom = listBottom),
            ) {
                state.groups.forEach { group ->
                    item(key = "h-${group.bucket}") {
                        Text(
                            when (group.bucket) { DraftBucket.today -> strings.today(); DraftBucket.yesterday -> strings.yesterday(); DraftBucket.earlier -> strings.earlier() },
                            style = AppType.section, letterSpacing = AppType.sectionTracking, color = AtlasTheme.colors.ink,
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp).semantics { heading() },
                        )
                    }
                    items(group.trips, key = { it.id }) { trip ->
                        val index = group.trips.indexOf(trip)
                        DraftRow(
                            trip = trip,
                            first = index == 0,
                            last = index == group.trips.lastIndex,
                            selecting = state.selecting,
                            selected = trip.id in state.selected,
                            now = now(),
                            onTap = {
                                haptics.tap()
                                if (state.selecting) vm.toggle(trip.id) else onOpenTrip(trip)
                            },
                            onConfirm = { vm.confirm(listOf(trip.id)) },
                            onDelete = { vm.askDelete(listOf(trip.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        Header(
            selecting = state.selecting,
            selectedCount = state.selected.size,
            hasDrafts = state.drafts.isNotEmpty(),
            onBack = onBack,
            onSelectAll = { haptics.tap(); if (state.selected.size == state.drafts.size) vm.setSelecting(true) else vm.selectAll() },
            onSelect = { haptics.tap(); vm.setSelecting(true) },
            onDone = { haptics.tap(); vm.setSelecting(false) },
        )
        if (state.drafts.isNotEmpty()) {
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = buttonBottom)) {
                val rowWidth = maxWidth
                if (state.selecting) {
                    val none = state.selected.isEmpty()
                    Row(Modifier.fillMaxWidth().alpha(if (none) 0.4f else 1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WideButton(strings.delete(), AppTheme.red, AtlasTheme.colors.chip, enabled = !none, modifier = Modifier.width(rowWidth * 0.4f).testTag("drafts_delete_selected")) {
                            haptics.tap(); vm.askDelete()
                        }
                        WideButton(strings.draftsMineCount(state.selected.size), Color.White, AtlasTheme.colors.accent, enabled = !none,
                            modifier = Modifier.weight(1f).testTag("drafts_mine_selected")) { haptics.tap(); vm.confirm() }
                    }
                } else {
                    WideButton(if (state.drafts.size == 1) strings.draftConfirm() else strings.draftsAllMine(state.drafts.size), Color.White, AtlasTheme.colors.accent,
                        enabled = true, modifier = Modifier.fillMaxWidth().testTag("drafts_all_mine")) { haptics.tap(); vm.confirm(state.drafts.map { it.id }) }
                }
            }
        }
        ToastHost(toast, onDismiss = { toast = null })
    }
    AppConfirm(
        item = state.confirmation,
        onDismissRequest = vm::cancelDelete,
        title = { strings.confirmationTitle(it) },
        message = { strings.confirmationMessage(it) },
        actions = { c -> listOf(AppDialogAction(strings.confirmationAction(c), AppDialogAction.Kind.destructive, testTag = "drafts_delete_confirm") { vm.acceptDelete(c.id) }) },
    )
}

/**
 * The header: glass that runs under the clock, a 46 row that does not (the row stays reachable).
 * «Назад» or «Все» on the left, the title (or «Выбрано N»), «Выбрать» or «Готово» on the right.
 */
@Composable
private fun Header(
    selecting: Boolean,
    selectedCount: Int,
    hasDrafts: Boolean,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onSelect: () -> Unit,
    onDone: () -> Unit,
) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().background(headerGlass()).testTag("drafts_header")) {
        Row(
            Modifier.fillMaxWidth().padding(top = safeTop).height(46.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                HeaderText(strings.draftsSelectAll(), "drafts_select_all", onSelectAll)
            } else {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(44.dp).plainPressFeedback(interaction)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = strings.back() }
                        .testTag("drafts_back"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(36.dp).background(atlas.chip, CircleShape), contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.chevronLeft, contentDescription = null, tint = atlas.ink, size = 18.dp)
                    }
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(if (selecting) strings.draftsSelectedCount(selectedCount) else strings.draftsTitle(), style = AppType.navTitle, color = atlas.ink, maxLines = 1,
                    modifier = Modifier.semantics { heading() }.testTag("drafts_title"))
            }
            when {
                selecting -> HeaderText(strings.done(), "drafts_done", onDone)
                hasDrafts -> HeaderText(strings.draftsSelect(), "drafts_select", onSelect)
                else -> Spacer(Modifier.size(44.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(atlas.separator))
    }
}

/**
 * iOS: `.ultraThinMaterial` under the header. Over the page it measures 241, 240, 237 on 248, 246, 242 in
 * light (a shade DARKER than the page) and 40, 39, 41 on 18, 18, 20 in dark; the house glass tint is
 * lighter than the page in light (254, 253, 252). The tint here is the colour that gives those
 * numbers at the alpha of a thin material, so the list still shows through a little.
 */
@Composable
internal fun headerGlass(): Color = if (AppTheme.isDark) Color(46, 44, 46).copy(alpha = 0.8f) else Color(239, 239, 236).copy(alpha = 0.8f)

@Composable
private fun HeaderText(text: String, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.widthIn(min = 44.dp).height(44.dp).plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppType.action, color = AtlasTheme.colors.accent, maxLines = 1) }
}

/** The swipe plates: a word, not an icon (one of the actions cannot be undone). */
private class SwipePlate(val label: String, val fill: Color, val tag: String, val onTrigger: () -> Unit)

/**
 * iOS: the draft row. 72 high: the selection circle while selecting, the route thumbnail, the
 * title and «17,6 км · 32 мин · 18:01», a chevron when a tap opens the trip. The group's card is
 * built from its rows: rounded at the group's top and bottom only.
 */
@Composable
private fun DraftRow(
    trip: TripSummary,
    first: Boolean,
    last: Boolean,
    selecting: Boolean,
    selected: Boolean,
    now: Instant,
    onTap: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val unit = LocalDistanceUnit.current
    val shape = RoundedCornerShape(topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp)
    val title = TripAutoTitle.localized(trip.title, trip.startDate, strings) ?: strings.tripTitle()
    val subtitle = listOf(
        Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.tenths),
        CheckpointReading.clock(trip.duration, strings),
        strings.clockTime(trip.startDate),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    SwipeRow(
        leading = SwipePlate(strings.draftConfirm(), AppTheme.accent, "draft_swipe_mine", onConfirm),
        trailing = SwipePlate(strings.delete(), Color(0xFFFF3B30), "draft_swipe_delete", onDelete),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth().background(atlas.card, shape)) {
            if (!first) Box(Modifier.padding(start = 84.dp).fillMaxWidth().height(1.dp).background(atlas.separator))
            PressableCard(onClick = onTap, modifier = Modifier.semantics {
                contentDescription = "$title, ${subtitle.replace(" · ", ", ")}, ${strings.nounDrafts(1)}"
            }.testTag("draft_row")) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selecting) SelectionCircle(selected)
                    DraftRouteThumb(trip.coordinates)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                        Text(subtitle, style = AppType.meta, color = atlas.secondary, maxLines = 1)
                    }
                    if (!selecting) Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 16.dp)
                }
            }
        }
    }
}

/** The plate of a swipe action: 74 wide, and 8 from the row (iOS's system swipe actions measure both). */
private val SwipePlateWidth = 74.dp
private val SwipePlateGap = 8.dp

/**
 * The system swipe actions iOS lists have, which Compose lists lack. Drag the row aside and its
 * plate appears at the edge, detached: 74 wide, its own rounded corners, 8 from the row, so the row
 * stands 82 from where it began. Let go past that to keep it open, past half the row to act at once
 * (full swipe, the plate then grows with the row). The row slides under the list's margin, not over
 * it: what leaves the row's own bounds is clipped, as the list clips it on iOS.
 */
@Composable
private fun SwipeRow(leading: SwipePlate, trailing: SwipePlate, modifier: Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val open = with(density) { (SwipePlateWidth + SwipePlateGap).toPx() }
    val gap = with(density) { SwipePlateGap.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val width = with(density) { maxWidth.toPx() }
        val full = width * 0.55f
        fun settle(velocity: Float) = scope.launch {
            val value = offset.value
            when {
                value > full || (value > open && velocity > 2500f) -> { haptics.action(); offset.animateTo(0f, spring()); leading.onTrigger() }
                value < -full || (value < -open && velocity < -2500f) -> { offset.animateTo(0f, spring()); trailing.onTrigger() }
                value > open / 2 -> offset.animateTo(open, spring(stiffness = 600f))
                value < -open / 2 -> offset.animateTo(-open, spring(stiffness = 600f))
                else -> offset.animateTo(0f, spring(stiffness = 600f))
            }
        }
        // The plates stand as tall as the row: the box matches the size the row gives the whole.
        if (offset.value > gap) Box(Modifier.matchParentSize()) {
            PlateButton(leading, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(with(density) { (offset.value - gap).toDp() })) {
                scope.launch { offset.animateTo(0f) }; leading.onTrigger()
            }
        } else if (offset.value < -gap) Box(Modifier.matchParentSize()) {
            PlateButton(trailing, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(with(density) { (-offset.value - gap).toDp() })) {
                scope.launch { offset.animateTo(0f) }; trailing.onTrigger()
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(-width, width)) } },
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) { content() }
    }
}

@Composable
private fun PlateButton(plate: SwipePlate, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(plate.fill).clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick).testTag(plate.tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(plate.label, style = inter(13f, InterWeight.semibold), color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** iOS: the selection circle: a hairline ring, or the accent disc with a check. */
@Composable
private fun SelectionCircle(selected: Boolean) {
    val atlas = AtlasTheme.colors
    if (selected) {
        Box(Modifier.size(24.dp).background(atlas.accent, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.checkmark, contentDescription = null, tint = Color.White, size = 14.dp)
        }
    } else {
        Box(Modifier.size(24.dp).border(1.5.dp, atlas.handle, CircleShape))
    }
}

/**
 * iOS: DraftRouteThumb.swift. The route of a draft in a 56 tile: the poster's equirectangular
 * projection (longitude scaled by the cosine of the middle latitude, never below 0.15), a smoothed
 * line in the poster's green → yellow → orange → red, the start dot green, the finish white.
 */
@Composable
fun DraftRouteThumb(coordinates: List<GeoPoint>, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val colors = AppTheme.colors
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(colors.cardAlt), contentAlignment = Alignment.Center) {
        if (coordinates.size < 2) {
            Glyph(AppGlyph.roadLanes, contentDescription = null, tint = colors.textTertiary, size = size * 0.32f)
            return@Box
        }
        Canvas(Modifier.size(size)) {
            val inset = 8.dp.toPx()
            val minLat = coordinates.minOf { it.latitude }; val maxLat = coordinates.maxOf { it.latitude }
            val minLon = coordinates.minOf { it.longitude }; val maxLon = coordinates.maxOf { it.longitude }
            val midLat = (minLat + maxLat) / 2
            val lonScale = maxOf(0.15, cos(Math.toRadians(midLat)))
            val spanX = (maxLon - minLon) * lonScale
            val spanY = maxLat - minLat
            val avail = minOf(size.toPx() - inset * 2, size.toPx() - inset * 2)
            val scale = if (maxOf(spanX, spanY) > 0) avail / maxOf(spanX, spanY) else 1.0
            val cx = this.size.width / 2; val cy = this.size.height / 2
            fun point(p: GeoPoint) = Offset(
                (cx + ((p.longitude - (minLon + maxLon) / 2) * lonScale * scale)).toFloat(),
                (cy - ((p.latitude - midLat) * scale)).toFloat(),
            )
            val points = coordinates.map(::point)
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size - 1) {
                    val mid = Offset((points[i].x + points[i + 1].x) / 2, (points[i].y + points[i + 1].y) / 2)
                    quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
                }
                lineTo(points.last().x, points.last().y)
            }
            val brush = Brush.linearGradient(
                colorStops = arrayOf(0f to Color(0xFF7AC828), 0.45f to Color(0xFFE8C41E), 0.75f to Color(0xFFF07B1E), 1f to Color(0xFFE03B2C)),
                start = points.first(), end = points.last().let { if (it == points.first()) it + Offset(1f, 1f) else it },
            )
            drawPath(path, Color.Black.copy(alpha = 0.45f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, brush, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val dot = 1.5.dp.toPx()
            drawCircle(Color(0xFF5AC83C), dot, points.first())
            drawCircle(Color.Black.copy(alpha = 0.25f), dot, points.first(), style = Stroke(0.32.dp.toPx()))
            drawCircle(Color.White, dot, points.last())
            drawCircle(Color.Black.copy(alpha = 0.25f), dot, points.last(), style = Stroke(0.32.dp.toPx()))
        }
    }
}

/** iOS: wideButton. 52 high, a 16 radius, the house button type, and a soft shadow under it. */
@Composable
private fun WideButton(text: String, ink: Color, fill: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    PressableCard(onClick = onClick, enabled = enabled, modifier = modifier.iosShadow(shape, Color.Black.copy(alpha = 0.16f), radius = 28.dp, y = 8.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).background(fill, shape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, style = AppType.button, color = ink, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * iOS: empty, placed by `DraftsLayout.emptyTop − safeTop` INSIDE the safe area, which is 30 % of the
 * screen's height from the screen's top. This screen is edge to edge, so the block's top is the
 * 30 % itself: taking the status bar off it again set the block a status-bar height too high on a
 * phone (DR-05; a render with no insets could not show it).
 */
@Composable
internal fun DraftsEmpty(windowHeight: androidx.compose.ui.unit.Dp) {
    EmptyState(Modifier.padding(top = DraftsLayout(windowHeight.value.toDouble(), 0.0, 0.0).emptyTop.dp).padding(horizontal = 24.dp))
}

/** iOS: emptyState. The tray in its circle, «Черновиков нет» and what will show up here; its top at 30 %. */
@Composable
private fun EmptyState(modifier: Modifier) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(modifier.widthIn(max = 300.dp).testTag("drafts_empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp).background(atlas.chip, CircleShape), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.tray, contentDescription = null, tint = atlas.secondary, size = 26.dp)
            }
            Text(strings.draftsEmptyTitle(), style = AppType.itemTitle, color = atlas.ink)
            Text(strings.draftsEmptyBody(), style = AppType.body, color = atlas.secondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * iOS: ProfileDraftsRow.swift with its section label. On Я above the history, only while there are
 * drafts: «3 поездки», when the newest one was recorded, the way into the list.
 *
 * The subtitle says the day once («Последняя вчера, 18:01»): iOS composes the relative date (which
 * already carries the time) into the same template and prints the time twice.
 */
@Composable
fun ProfileDraftsRow(drafts: List<TripSummary>, now: Instant, onTap: () -> Unit, modifier: Modifier = Modifier) {
    if (drafts.isEmpty()) return
    ProfileDraftsRow(drafts.size, drafts.maxOf { it.startDate }, now, onTap, modifier)
}

/** The same card from what it needs: how many drafts there are and when the newest was recorded. */
@Composable
fun ProfileDraftsRow(count: Int, last: Instant, now: Instant, onTap: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val day = when (ChronoUnit.DAYS.between(last.atZone(strings.zone).toLocalDate(), now.atZone(strings.zone).toLocalDate())) {
        0L -> strings.today().lowercase(strings.language.locale)
        1L -> strings.yesterday().lowercase(strings.language.locale)
        else -> RelativeTripDate.string(last, now, strings)
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(strings.draftsTitle(), style = AppType.section, letterSpacing = AppType.sectionTracking, color = colors.text,
            modifier = Modifier.padding(top = 4.dp).semantics { heading() }.testTag("profile_drafts_header"))
        PressableCard(onClick = { haptics.tap(); onTap() }, modifier = Modifier.fillMaxWidth().testTag("profile_drafts_row")) {
            Row(
                Modifier.fillMaxWidth().surfaceCard().height(68.dp).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(AppTheme.accentBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.trayFull, contentDescription = null, tint = AppTheme.accent, size = 21.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${strings.formattedCount(count)} ${strings.nounTrips(count)}", style = AppType.itemValue, color = colors.text, maxLines = 1)
                    Text(strings.draftsCardLast(day, strings.clockTime(last)), style = AppType.meta, color = colors.textSecondary, maxLines = 1)
                }
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 16.dp)
            }
        }
    }
}
