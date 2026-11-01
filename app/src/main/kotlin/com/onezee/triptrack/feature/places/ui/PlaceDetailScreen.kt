// iOS: PlaceDetailView.swift, PlaceUsuallyLine.swift, PlaceRenameSheet.swift
package com.onezee.triptrack.feature.places.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.NavCircleButton
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.tabBarClearanceAboveSafeArea
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.atlas.WorldFeature
import com.onezee.triptrack.feature.atlas.ui.AtlasSectionTitle
import com.onezee.triptrack.feature.atlas.ui.CircleButton
import com.onezee.triptrack.feature.atlas.ui.SheetButton
import com.onezee.triptrack.feature.atlas.ui.WorldGlyph
import com.onezee.triptrack.feature.atlas.ui.WorldGlyphIcon
import com.onezee.triptrack.feature.places.PlaceDetailState
import com.onezee.triptrack.feature.places.PlaceDetailViewModel
import com.onezee.triptrack.logic.hasUsual
import com.onezee.triptrack.logic.isFirstTime
import com.onezee.triptrack.logic.visiblePasses
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.PlacePass
import com.onezee.triptrack.model.PlaceScreen
import com.onezee.triptrack.model.PlaceStats
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.navigation.entryViewModel
import java.util.UUID
import kotlin.math.roundToInt

/** iOS PlaceDetailView.mapHeight: as much map as stands above the sheet. */
private const val MAP_HEIGHT = 322f

/** iOS headerHeight: the scrolled header the sheet hides under. */
private const val HEADER_HEIGHT = 96f

/**
 * iOS: PlaceDetailView. The map is the screen's GROUND, not a card inside it: a place is a point on
 * the earth, and the first thing to know is where. A paper sheet rides over the map and takes it up
 * when pulled. The tab bar STAYS, as on the garage: the place screen lives inside its tab.
 */
@Composable
fun PlaceDetailScreen(id: UUID, feature: WorldFeature) {
    val vm = entryViewModel("place:$id") { feature.placeDetail(id) }
    val state by vm.state.collectAsState()
    val navigator = feature.placesNavigator
    var shownOnce by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }
    // The place vanished while the screen was open (deleted elsewhere, a stale id): the screen closes
    // itself instead of showing «Без названия» with no map and «0 / — / —» tiles.
    LaunchedEffect(state.loaded, state.place) {
        if (state.place != null) shownOnce = true
        if (state.loaded && state.place == null) navigator.pop()
    }
    PlaceDetailContent(state, vm, feature, onBack = { navigator.pop() })
}

@Composable
private fun PlaceDetailContent(state: PlaceDetailState, vm: PlaceDetailViewModel, feature: WorldFeature, onBack: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val scroll = rememberScrollState()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 0: the sheet down and the map in view; 1: the sheet under the header. QUANTISED to 1/20 before
    // it reaches the state: at full precision every point of the scroll redraws the map's frame.
    val progress by remember {
        derivedStateOf {
            val span = with(density) { (MAP_HEIGHT - HEADER_HEIGHT).dp.toPx() }
            ((scroll.value / span).coerceIn(0f, 1f) * 20).roundToInt() / 20f
        }
    }
    val mapState = remember { MapHostState() }
    val place = state.place

    BoxWithConstraints(Modifier.fillMaxSize().background(atlas.background).testTag("place_detail")) {
        val height = maxHeight
        Box(Modifier.fillMaxWidth().height((MAP_HEIGHT + AtlasTheme.sheetRadius.value).dp)) {
            if (place != null) {
                PlacesMapLayer(
                    pins = listOf(PlacePin(place.id, GeoPoint(place.latitude, place.longitude), name = place.name)),
                    routes = state.routes,
                    selectedId = place.id,
                    mapState = mapState,
                    chrome = MapInsets.Zero,
                    strings = strings,
                    testTag = "place_detail_map",
                )
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // A clear window onto the map: the scroll is caught over the whole screen.
            Spacer(Modifier.fillMaxWidth().height(MAP_HEIGHT.dp))
            Sheet(state, vm, feature, Modifier.heightIn(min = (height.value - MAP_HEIGHT).dp), onRename = { renaming = true }, onDelete = { confirmingDelete = true })
        }
        // The back disc and «23 проезда» live ON the map and go with it.
        Row(Modifier.fillMaxWidth().padding(top = safeTop + 9.dp).padding(horizontal = AtlasTheme.sideInset).alpha((1 - progress * 2).coerceAtLeast(0f)),
            verticalAlignment = Alignment.CenterVertically) {
            NavCircleButton(AppGlyph.chevronLeft, onClick = onBack, contentDescription = strings.back(), modifier = Modifier.testTag("place_back"))
            Spacer(Modifier.weight(1f))
            if (state.stats.passCount > 0) PassesPill(state.stats.passCount, strings)
        }
        FloatingHeader(place?.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed(), progress, safeTop, onBack)
    }

    if (renaming) {
        PlaceRenameSheet(place?.name, state.geocodedName, onSave = { vm.rename(it?.takeIf { name -> name.isNotEmpty() }) }) { renaming = false }
    }
    // A house dialog at the screen root, never inside the scroll.
    AppConfirm(
        isPresented = confirmingDelete,
        onDismissRequest = { confirmingDelete = false },
        title = strings.placeDeleteTitle(),
        message = strings.placeDeleteMessage(),
        // The screen closes when the place is gone: one way, not a second dismissal here.
        actions = listOf(AppDialogAction(strings.placeDelete(), AppDialogAction.Kind.destructive) { vm.delete() }),
    )
}

/** «— 23 проезда»: a stroke of the thread's colour and the count; threads without it read as someone's route. */
@Composable
private fun PassesPill(count: Int, strings: AppStrings) {
    val atlas = AtlasTheme.colors
    Row(
        Modifier.height(32.dp).iosShadow(CapsuleShape, Color.Black.copy(alpha = if (AppTheme.isDark) 0.32f else 0.12f), radius = 4.dp, y = 2.dp)
            .background(atlas.control.copy(alpha = 0.94f), CapsuleShape).padding(horizontal = 12.dp)
            .semantics(mergeDescendants = true) {}.testTag("place_passes_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(atlas.accent, CapsuleShape))
        Text("${strings.formattedCount(count)} ${strings.nounPasses(count)}", style = AppType.meta, color = atlas.secondary, maxLines = 1)
    }
}

/** The header of the scrolled sheet: the same back button and the name in the middle. Transparent, it takes no taps. */
@Composable
private fun FloatingHeader(name: String, progress: Float, safeTop: androidx.compose.ui.unit.Dp, onBack: () -> Unit) {
    val atlas = AtlasTheme.colors
    if (progress <= 0f) return
    Column(Modifier.fillMaxWidth().alpha(progress).background(atlas.background.copy(alpha = 0.97f))) {
        Row(Modifier.fillMaxWidth().padding(top = safeTop + 3.dp, bottom = 8.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (progress > 0.5f) NavCircleButton(AppGlyph.chevronLeft, onClick = onBack, contentDescription = appStrings.back())
            else Spacer(Modifier.size(40.dp))
            Text(name, Modifier.weight(1f), style = AppType.headerTitle, color = atlas.ink, maxLines = 1, textAlign = TextAlign.Center)
            Spacer(Modifier.size(40.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(atlas.separator))
    }
}

@Composable
private fun Sheet(state: PlaceDetailState, vm: PlaceDetailViewModel, feature: WorldFeature, modifier: Modifier, onRename: () -> Unit, onDelete: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    val shape = RoundedCornerShape(topStart = AtlasTheme.sheetRadius, topEnd = AtlasTheme.sheetRadius)
    Column(
        modifier.fillMaxWidth().iosShadow(shape, Color.Black.copy(alpha = if (AppTheme.isDark) 0.5f else 0.16f), radius = 14.dp, y = (-8).dp)
            .background(atlas.background, shape)
            .padding(start = AtlasTheme.sideInset, end = AtlasTheme.sideInset, top = 8.dp, bottom = tabBarClearanceAboveSafeArea() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Box(Modifier.size(width = 36.dp, height = 5.dp).background(atlas.handle, CapsuleShape)) }
        TitleRow(state, onRename)
        HereRow(state.stats, strings)
        UsualCard(state, strings)
        RecentSection(state, strings)
        PassesSection(state, vm, feature, strings)
        DeleteBlock(onDelete)
    }
}

/** The name, its town and the «Rename» disc: a button, not a «…» item; deletion stands at the very bottom. */
@Composable
private fun TitleRow(state: PlaceDetailState, onRename: () -> Unit) {
    val atlas = AtlasTheme.colors
    val strings = appStrings
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.place?.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed(), Modifier.semantics { heading() }, style = AppType.title, color = atlas.ink)
            state.address?.let { Text(it, style = AppType.meta, color = atlas.secondary, maxLines = 2) }
        }
        CircleButton(AppGlyph.pencil, strings.placeRename(), "place_action_rename", diameter = 44.dp, fill = atlas.searchBackground, glyphSize = 19.dp, onClick = onRename)
    }
}

/**
 * «Здесь 23 раза»: the ONLY big number of the screen and its one terracotta. The line comes WHOLE from
 * the translation; only the number we printed ourselves is lit, never digits found in the text.
 */
@Composable
private fun HereRow(stats: PlaceStats, strings: AppStrings) {
    val atlas = AtlasTheme.colors
    val text = strings.placeHereTimes(stats.passCount)
    val number = strings.formattedCount(stats.passCount)
    val unitStyle = AppType.unit
    val display = AppType.display(34f)
    val annotated = buildAnnotatedString {
        val at = text.indexOf(number)
        if (at < 0) {
            withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontFamily = unitStyle.fontFamily, fontWeight = unitStyle.fontWeight)) { append(text) }
        } else {
            withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontFamily = unitStyle.fontFamily, fontWeight = unitStyle.fontWeight)) { append(text.substring(0, at)) }
            withStyle(SpanStyle(fontSize = display.fontSize, fontFamily = display.fontFamily, fontWeight = display.fontWeight, color = atlas.accent)) { append(number) }
            withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontFamily = unitStyle.fontFamily, fontWeight = unitStyle.fontWeight)) { append(text.substring(at + number.length)) }
        }
    }
    Row(Modifier.fillMaxWidth().testTag("place_here_row"), verticalAlignment = Alignment.CenterVertically) {
        Text(annotated, Modifier.weight(1f), color = atlas.secondary)
        when {
            stats.isFrequentGuest -> PlaceChipLabel(strings.placeChipFrequent())
            stats.isFirstTime -> PlaceChipLabel(strings.placeChipFirst())
        }
    }
}

/**
 * iOS: PlaceUsuallyLine.compose. Only what ADDS to the median above it: one pass is «1 проезд»; a
 * spread is shown only when the PRINTED ends differ («от 1 ч 10 мин до 1 ч 30 мин · 3 проезда»).
 */
fun placeUsuallyLine(count: Int, best: Double, worst: Double, strings: AppStrings): String {
    val passes = "${strings.formattedCount(count)} ${strings.nounPasses(count)}"
    val low = CheckpointReading.clock(minOf(best, worst), strings)
    val high = CheckpointReading.clock(maxOf(best, worst), strings)
    if (count <= 1 || low == high) return passes
    return "${strings.placeUsuallyRange(low, high)} · $passes"
}

@Composable
private fun UsualCard(state: PlaceDetailState, strings: AppStrings) {
    val stats = state.stats
    // One number is not «usually»: with one pass «Первый раз здесь» already stands beside.
    if (!stats.hasUsual) return
    if (stats.directions.isNotEmpty()) {
        PaperCard {
            CardHeader(strings.placeUsuallyTitle(), strings.placeUsuallyCaption())
            stats.directions.forEachIndexed { index, direction ->
                if (index > 0) RowSeparator()
                UsualRow(direction.course, state.directionLabels[direction.latestTripId]?.let { strings.placeTowards(it) },
                    placeUsuallyLine(direction.count, direction.best, direction.worst, strings), direction.median, strings)
            }
        }
    } else {
        val median = stats.medianElapsed ?: return
        // Passes without a course: nothing to group by direction, and the fact «usually takes» stands.
        PaperCard {
            CardHeader(strings.placeUsuallyTitle(), strings.placeUsuallyCaption())
            UsualRow(PlacePass.unknownCourse, strings.placeNoDirection(), placeUsuallyLine(stats.passCount, median, median, strings), median, strings)
        }
    }
}

/** Where they go on the left, how long on the right; the compass arrow only where there is no name. */
@Composable
private fun UsualRow(course: Double, label: String?, detail: String, median: Double, strings: AppStrings) {
    val atlas = AtlasTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (label == null) PassCourseGlyph(course)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (label != null) Text(label, style = AppType.body, color = atlas.ink, maxLines = 2)
            Text(detail, style = AppType.meta, color = atlas.secondary, maxLines = 2)
        }
        Text(CheckpointReading.clock(median, strings), style = AppType.itemValue, color = atlas.ink, maxLines = 1)
    }
}

/** Day, month and, only with more than one pass that day, «2 раза». The year never: five tiles share a row. */
@Composable
private fun RecentSection(state: PlaceDetailState, strings: AppStrings) {
    if (state.tiles.isEmpty()) return
    val atlas = AtlasTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AtlasSectionTitle(strings.placeRecentPasses())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tile in state.tiles) {
                Column(Modifier.weight(1f).background(atlas.card, ContinuousRoundedRectangle(14.dp)).padding(vertical = 10.dp, horizontal = 4.dp)
                    .semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LocalizedDateFormatter.templates("d", strings.zone).getValue(strings.language).format(tile.day), style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                    Text(LocalizedDateFormatter.templates("MMM", strings.zone).getValue(strings.language).format(tile.day), style = AppType.meta, color = atlas.secondary, maxLines = 1)
                    if (tile.count > 1) Text("${strings.formattedCount(tile.count)} ${strings.nounTimes(tile.count)}", style = AppType.meta, color = atlas.secondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PassesSection(state: PlaceDetailState, vm: PlaceDetailViewModel, feature: WorldFeature, strings: AppStrings) {
    val scope = rememberCoroutineScope()
    if (state.passes.isEmpty()) return
    val atlas = AtlasTheme.colors
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AtlasSectionTitle(strings.placePassesSection())
            Spacer(Modifier.weight(1f))
            // «от старта поездки» stands ONCE over the list, not as a tail of every row.
            Text(strings.placeUsuallyCaption(), style = AppType.meta, color = atlas.secondary, maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().background(atlas.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius)).padding(horizontal = 16.dp, vertical = 2.dp)) {
            state.visiblePasses.forEachIndexed { index, pass ->
                if (index > 0) RowSeparator()
                val route = state.routeNames[pass.tripId]
                val date = LocalizedDateFormatter.templates("dMMMEEE", strings.zone).getValue(strings.language).format(pass.timestamp)
                val reading = CheckpointReading.text(pass.elapsedFromStart, pass.distanceFromStart, unit, strings)
                PressableCard(onClick = { haptics.tap(); scope.launch {
                    val focus = withContext(feature.worker) { vm.focus(pass.tripId) }
                    feature.openPlacesTrip(pass.tripId, focus = focus)
                } }, modifier = Modifier.fillMaxWidth().testTag("place_pass_row")) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // The hour tells «there and back» on one day apart: one date names both.
                            Text(route ?: date, style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                            Text(if (route != null) "$date · $reading" else reading, style = AppType.meta, color = atlas.secondary, maxLines = 1)
                        }
                        Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
                    }
                }
            }
            if (state.visiblePasses.size < state.passes.size) {
                RowSeparator()
                val interaction = remember { MutableInteractionSource() }
                Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); vm.showAllPasses() }
                    .testTag("place_show_all_passes"), verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.placeShowAllPasses("${strings.formattedCount(state.passes.size)} ${strings.nounPasses(state.passes.size)}"),
                        Modifier.weight(1f), style = AppType.itemTitle, color = atlas.accent, maxLines = 1)
                    Glyph(AppGlyph.chevronDown, contentDescription = null, tint = atlas.accent, size = 14.dp)
                }
            }
        }
    }
}

/** Deletion is irreversible: at the very bottom, under everything, never beside the name. */
@Composable
private fun DeleteBlock(onDelete: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PressableCard(onClick = { haptics.tap(); onDelete() }, modifier = Modifier.fillMaxWidth().testTag("place_action_delete")) {
            Row(Modifier.fillMaxWidth().height(52.dp).background(atlas.card, ContinuousRoundedRectangle(AtlasTheme.statRadius)),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Glyph(AppGlyph.trash, contentDescription = null, tint = AppTheme.red, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(strings.placeDelete(), style = AppType.button, color = AppTheme.red)
            }
        }
        Text(strings.placeDeleteHint(), style = AppType.meta, color = atlas.secondary, textAlign = TextAlign.Center)
    }
}

/** The mock's white card: one radius and one set of fields for «Обычно занимает» and the passes. */
@Composable
private fun PaperCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(AtlasTheme.colors.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius))
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
private fun CardHeader(title: String, caption: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AtlasSectionTitle(title)
        Spacer(Modifier.weight(1f))
        Text(caption, style = AppType.meta, color = AtlasTheme.colors.secondary, maxLines = 1)
    }
}

@Composable
private fun RowSeparator() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AtlasTheme.colors.separator))
}

/**
 * iOS: PlaceRenameSheet. The field, the hint about the address, «Вернуть название по адресу» and two
 * buttons. An empty name is unnamed again: erasing the name is a legitimate answer, so «Save» stays
 * live on an empty field.
 */
@Composable
fun PlaceRenameSheet(name: String?, address: String?, onSave: (String?) -> Unit, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    // The cursor stands at the END of the current name, as the system field puts it on iOS.
    var field by remember { mutableStateOf(TextFieldValue(name.orEmpty(), TextRange(name.orEmpty().length))) }
    val text = field.text
    fun setText(value: String) { field = TextFieldValue(value, TextRange(value.length)) }
    val focus = remember { FocusRequester() }
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = atlas.background) {
        val sheet = this
        fun save() { onSave(text.trim()); sheet.dismiss() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Column(Modifier.fillMaxWidth().padding(start = AtlasTheme.sideInset, end = AtlasTheme.sideInset, top = 8.dp, bottom = 20.dp).testTag("place_rename_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.placeRenameTitle(), Modifier.weight(1f).semantics { heading() }, style = AppType.sheetTitle, color = atlas.ink)
                CircleButton(AppGlyph.xmark, strings.close(), "place_rename_close", diameter = 44.dp, fill = atlas.searchBackground) { sheet.dismiss() }
            }
            Row(Modifier.fillMaxWidth().height(56.dp).background(atlas.card, ContinuousRoundedRectangle(14.dp))
                .border(1.dp, atlas.separator, ContinuousRoundedRectangle(14.dp)).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(strings.placeNamePlaceholder(), style = AppType.unit, color = atlas.secondary, maxLines = 1)
                    BasicTextField(field, { field = it }, Modifier.fillMaxWidth().focusRequester(focus).testTag("place_rename_field"),
                        textStyle = AppType.unit.copy(color = atlas.ink), singleLine = true, cursorBrush = SolidColor(atlas.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }))
                }
                if (text.isNotEmpty()) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(Modifier.size(44.dp).clickable(interactionSource = interaction, indication = null, role = Role.Button) { setText(""); runCatching { focus.requestFocus() } }
                        .semantics { contentDescription = strings.calendarClearFilter() }, contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.xmarkCircleFill, contentDescription = null, tint = atlas.handle, size = 19.dp)
                    }
                }
            }
            Text(if (address == null) strings.placeRenameHint() else strings.placeRenameHintAddress(address), style = AppType.meta, color = atlas.secondary)
            if (address != null && text.trim() != address) {
                val haptics = rememberHaptics()
                PressableCard(onClick = { haptics.tap(); setText(address) }, modifier = Modifier.testTag("place_rename_use_address")) {
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WorldGlyphIcon(WorldGlyph.arrowUturnBackward, contentDescription = null, tint = atlas.ink, size = 17.dp)
                        Text(strings.placeRenameUseAddress(), style = AppType.itemTitle, color = atlas.ink)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SheetButton(strings.cancel(), atlas.searchBackground, atlas.ink, "place_rename_cancel") { sheet.dismiss() } }
                Box(Modifier.weight(1f)) { SheetButton(strings.save(), atlas.accent, Color.White, "place_rename_save") { save() } }
            }
        }
    }
}
