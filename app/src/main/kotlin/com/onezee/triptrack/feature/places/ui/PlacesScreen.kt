// iOS: PlacesView.swift, PlacesPanel.swift, PlacesPanelHeader.swift
package com.onezee.triptrack.feature.places.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.navigation.tabBarTappableInset
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsDestinationScreen
import com.onezee.triptrack.feature.atlas.PlacesDestination
import com.onezee.triptrack.feature.atlas.WorldFeature
import com.onezee.triptrack.feature.atlas.ui.AtlasBetaChip
import com.onezee.triptrack.feature.atlas.ui.AtlasBetaSheet
import com.onezee.triptrack.feature.atlas.ui.AtlasSectionTitle
import com.onezee.triptrack.feature.atlas.ui.AtlasSheet
import com.onezee.triptrack.feature.atlas.ui.BetaSubject
import com.onezee.triptrack.feature.atlas.ui.CircleButton
import com.onezee.triptrack.feature.atlas.ui.SuggestionSkeleton
import com.onezee.triptrack.feature.atlas.ui.WorldGlyph
import com.onezee.triptrack.feature.atlas.ui.WorldGlyphIcon
import com.onezee.triptrack.feature.atlas.ui.rememberSlotMotion
import com.onezee.triptrack.feature.atlas.ui.selectedSemantics
import com.onezee.triptrack.feature.atlas.ui.tabular
import com.onezee.triptrack.feature.places.PlacesEmptyState
import com.onezee.triptrack.feature.places.PlacesPanelStop
import com.onezee.triptrack.feature.places.PlacesSearchOutcome
import com.onezee.triptrack.feature.places.PlacesSlot
import com.onezee.triptrack.feature.places.PlacesTabState
import com.onezee.triptrack.feature.places.PlacesTabViewModel
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.id
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.PlaceLastPass
import com.onezee.triptrack.model.PlacesPresentation
import com.onezee.triptrack.model.PlacesSort
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.asPreviewTrip
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.navigation.TabNavHost
import java.time.Instant
import java.util.UUID

/**
 * The Places tab: its stack (the place screens, iOS `PlacesDest.place`) and a trip opened from it
 * sliding over everything in the trips stack (iOS pushes `.trip` onto the same path; a trip screen
 * pushes journeys onto ITS stack, so it lives in its own).
 */
@Composable
fun PlacesTab(feature: WorldFeature, onOpenRecord: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        TabNavHost(feature.placesNavigator, Modifier.fillMaxSize(), root = { PlacesScreen(feature) }) { dest ->
            when (dest) {
                is PlacesDestination.PlaceDetail -> PlaceDetailScreen(dest.id, feature)
            }
        }
        TabNavHost(feature.placesTrips, Modifier.fillMaxSize(), root = {}) { dest ->
            TripsDestinationScreen(dest, feature.trips, feature.placesTrips)
        }
    }
}

/** Opens a trip from the Places tab over its stack, with what the list already knows of it. */
fun WorldFeature.openPlacesTrip(id: UUID, preview: Trip? = null) {
    placesTrips.push(TripsDestination.TripDetail(id, preview = preview))
}

/**
 * iOS: PlacesView. The map at full height and the panel over it; nothing else on the tab. The title,
 * the switch, the search and the order live INSIDE the panel. The screen grows with the library:
 * empty, a newcomer with suggestions, the list; search, order and groups from `manyPlaces` (8).
 */
@Composable
fun PlacesScreen(feature: WorldFeature, now: () -> Instant = feature.trips.clock) {
    val vm = feature.placesTab
    val state by vm.state.collectAsState()
    val online by feature.online.collectAsState()
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val reduceMotion = LocalReduceMotion.current

    val panel = feature.placesPanel
    var showsBeta by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val mapState = remember { MapHostState() }
    val searching = state.search != null

    BoxWithConstraints(Modifier.fillMaxSize().background(atlas.background).testTag("screen_places")) {
        val screenHeight = maxHeight
        val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val tappable = tabBarTappableInset()
        val slot = PlacesSlot((screenHeight - tappable).value.toDouble(), safeTop.value.toDouble(), safeBottom.value.toDouble())
        // Not loaded yet reads as a library: the empty tab is decided by the first batch (divergences).
        val emptyState = if (!state.loaded) PlacesEmptyState.notEmpty else PlacesEmptyState.resolve(state.items.size, state.recentTrips.size)
        val current = panel.stop ?: emptyState.stop(slot).also { panel.stop = it }
        LaunchedEffect(emptyState, state.loaded) {
            // During a search the panel holds the list: a place arriving by a pull must not drop it
            // from under the finger typing the query.
            if (!state.loaded || searching) return@LaunchedEffect
            if (emptyState.isPinned) panel.stop = emptyState.stop(slot)
            else if (panel.stop?.isDraggable != true && !panel.hasSelection) panel.stop = slot.defaultStop
        }

        fun push(id: UUID) {
            haptics.tap()
            feature.placesNavigator.push(PlacesDestination.PlaceDetail(id))
        }
        fun beginSearch() {
            if (searching) return
            panel.beginSearch()
            vm.openSearch()
        }
        fun endSearch() {
            focusManager.clearFocus()
            keyboard?.hide()
            vm.dismissSearch()
            panel.endSearch()
        }
        LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }

        BackHandler(enabled = searching || panel.hasSelection) {
            if (searching) endSearch() else panel.closeSelection()
        }

        val pins = remember(state.items, state.suggestions) {
            state.items.map { PlacePin(it.place.id, GeoPoint(it.place.latitude, it.place.longitude), name = it.place.name) } +
                state.knownSuggestions.map { PlacePin(it.id, it.coordinate, isSuggested = true) }
        }
        val chromeBottom = slot.mapBottomInset(current) - safeBottom.value
        PlacesMapLayer(
            pins = pins,
            routes = emptyList(),
            selectedId = panel.selectedPlace ?: panel.selectedHint,
            mapState = mapState,
            chrome = MapInsets(bottom = chromeBottom.coerceAtLeast(0.0)),
            strings = strings,
            onPinTap = { haptics.tap(); panel.selectPin(it, state) },
        )

        val motion = rememberSlotMotion(slot.top(current))
        if (!online) {
            // In the MIDDLE of the visible map, not at its top edge: the strip runs from 56 pt to
            // the whole screen, and a line at the edge would read as a system banner.
            val visible = (motion.top.value - safeTop.value).coerceAtLeast(0f)
            Box(Modifier.padding(top = safeTop).fillMaxWidth().height(visible.dp).padding(horizontal = 24.dp).testTag("places_offline_note"),
                contentAlignment = Alignment.Center) {
                Text(strings.placesOfflineMap(), style = AppType.caption, color = atlas.secondary, textAlign = TextAlign.Center)
            }
        }
        if (searching) {
            // The scrim is the second way out, and deaf to TalkBack, like the house dialog's.
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxSize().background(atlas.scrim)
                .clickable(interactionSource = interaction, indication = null) { endSearch() }
                .semantics { hideFromAccessibility() }.testTag("places_search_scrim"))
        }

        AtlasSheet(
            motion = motion,
            target = slot.top(current),
            screenHeight = screenHeight,
            accessibilityTitle = strings.tabPlaces(),
            onDrag = { slot.clamped(it) },
            onSettle = { top, velocity -> panel.stop = slot.settle(top, velocity, current) },
            onTapHandle = {},
            testTag = "places_panel",
            handleTag = "places_panel_handle",
            draggable = !emptyState.isPinned && !searching,
            shadowRadius = 28.dp,
            header = {
                // The tab's title is HIDDEN while searching: one looks for a place, the switch has
                // nowhere to lead.
                if (!searching) PanelHeader(current, onBeta = { showsBeta = true }, onList = { panel.move(PlacesPanelStop.list) }, onMap = { panel.move(PlacesPanelStop.map) })
            },
        ) {
            when {
                searching -> SearchContent(state, vm, searchFocus, onCancel = { haptics.tap(); endSearch() }, onOpen = ::push)
                current == PlacesPanelStop.map -> Unit
                current == PlacesPanelStop.selectedPlace || current == PlacesPanelStop.selectedHint -> SelectionContent(
                    state, panel.selectedPlace, panel.selectedHint,
                    onOpen = ::push, onSave = vm::save, onClose = { haptics.tap(); panel.closeSelection() },
                )
                current == PlacesPanelStop.empty -> EmptyContent(state, vm, now(), onOpenTrip = { feature.openPlacesTrip(it.id, TripSummary.from(it).asPreviewTrip()) })
                current == PlacesPanelStop.half -> Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LastPassSection(state, now()) { vm.markLastPassSeen(); push(it) }
                    PlacesSection(state, onOpen = ::push)
                    SuggestionsSection(state, vm)
                }
                else -> ListContent(state, vm, now(), searchFocus, onFocusSearch = ::beginSearch, onOpen = ::push)
            }
        }
    }

    if (showsBeta) AtlasBetaSheet(BetaSubject.places) { showsBeta = false }
}

/**
 * iOS: PlacesPanelHeader. «Места · Бета» and the «Список · Карта» switch. The switch MOVES the panel,
 * it changes no screen: the active segment mirrors the position (on the half, «Список»).
 */
@Composable
private fun PanelHeader(stop: PlacesPanelStop, onBeta: () -> Unit, onList: () -> Unit, onMap: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = AtlasTheme.sideInset), verticalAlignment = Alignment.CenterVertically) {
        // The chip stands CLOSE to the word (6): a caption of «Места», not a control beside it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.tabPlaces(), style = AppType.title, letterSpacing = AppType.titleTracking, color = atlas.ink, maxLines = 1)
            AtlasBetaChip(onBeta, testTag = "places_beta_chip")
        }
        Spacer(Modifier.weight(1f))
        // A 36 capsule with 30 segments; the finger gets 44.
        Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(36.dp).background(atlas.searchBackground, ContinuousRoundedRectangle(12.dp)).padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Segment(strings.placesViewList(), "places_mode_list", stop != PlacesPanelStop.map, onList)
                    Segment(strings.placesViewMap(), "places_mode_map", stop == PlacesPanelStop.map, onMap)
                }
            }
        }
    }
}

@Composable
private fun Segment(title: String, testTag: String, active: Boolean, onClick: () -> Unit) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val shape = ContinuousRoundedRectangle(9.dp)
    Box(
        Modifier.height(44.dp).clickable(interactionSource = interaction, indication = null, role = Role.Tab) { haptics.tap(); onClick() }
            .selectedSemantics(active).testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.height(30.dp)
                .then(if (active) Modifier.iosShadow(shape, Color.Black.copy(alpha = 0.12f), radius = 3.dp, y = 1.dp).background(atlas.card, shape) else Modifier)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(title, style = if (active) AppType.chipActive else AppType.chip, color = if (active) atlas.ink else atlas.secondary, maxLines = 1)
        }
    }
}

/** iOS listContent: the whole list, scrolling; search and order only from `manyPlaces`. */
@Composable
private fun ColumnScope.ListContent(
    state: PlacesTabState,
    vm: PlacesTabViewModel,
    now: Instant,
    focus: FocusRequester,
    onFocusSearch: () -> Unit,
    onOpen: (UUID) -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = tabBarContentClearance())
            .testTag("places_list_page"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.showsOrganization) {
            SearchRow(query = "", onQuery = {}, focus = focus, onFocused = onFocusSearch, onCancel = null)
            SortChips(state.sort, vm::setSort)
        }
        LastPassSection(state, now) { vm.markLastPassSeen(); onOpen(it) }
        PlacesSection(state, onOpen)
        SuggestionsSection(state, vm)
    }
}

/**
 * iOS searchContent (states 10 and 11): the field with «Отмена» pinned, the matches as rows right
 * under it, no heading and no order chips: the screen answers one question now.
 */
@Composable
private fun ColumnScope.SearchContent(state: PlacesTabState, vm: PlacesTabViewModel, focus: FocusRequester, onCancel: () -> Unit, onOpen: (UUID) -> Unit) {
    val search = state.search ?: return
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchRow(search.query, vm::search, focus, onFocused = {}, onCancel = onCancel)
        when (PlacesSearchOutcome.resolve(search.query, search.results.size)) {
            PlacesSearchOutcome.nothingFound -> NothingFound()
            else -> LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("places_search_results"),
                // The keyboard covers the panel's bottom: the last rows come out from under it.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(search.results, key = { it.place.id }) { item -> PlaceCard(item, compact = true, onTap = { onOpen(item.place.id) }) }
            }
        }
    }
}

/** iOS nothingFoundBlock (state 11): no button at all, «Отмена» already stands by the field. */
@Composable
private fun NothingFound() {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, start = 24.dp, end = 24.dp).semantics(mergeDescendants = true) {}.testTag("places_search_empty"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(56.dp).background(atlas.chip, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.magnifyingglass, contentDescription = null, tint = atlas.secondary, size = 24.dp)
        }
        Text(strings.placesSearchEmpty(), style = AppType.itemTitle, color = atlas.ink, textAlign = TextAlign.Center)
        Text(strings.placesSearchEmptyHint(), style = AppType.body, color = atlas.secondary, textAlign = TextAlign.Center)
    }
}

/**
 * The search field. Focus in it IS the entry to the search; losing it does not end the search (a
 * scroll hides the keyboard, and one careless swipe would wipe the query).
 */
@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, focus: FocusRequester, onFocused: () -> Unit, onCancel: (() -> Unit)?) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).height(44.dp).background(atlas.searchBackground, ContinuousRoundedRectangle(12.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Glyph(AppGlyph.magnifyingglass, contentDescription = null, tint = atlas.secondary, size = 17.dp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(strings.placesSearchPlaceholder(), style = AppType.body, color = atlas.secondary, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) onFocused() }.testTag("places_search"),
                    textStyle = AppType.body.copy(color = atlas.ink),
                    singleLine = true,
                    cursorBrush = SolidColor(atlas.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                )
            }
            if (query.isNotEmpty()) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.size(28.dp).clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onQuery("") }
                    .semantics { contentDescription = strings.calendarClearFilter() }, contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.xmarkCircleFill, contentDescription = null, tint = atlas.secondary, size = 17.dp)
                }
            }
        }
        if (onCancel != null) {
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.height(44.dp).plainPressFeedback(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onCancel)
                .testTag("places_search_cancel"), contentAlignment = Alignment.Center) {
                Text(strings.cancel(), style = AppType.action, color = atlas.accent, maxLines = 1)
            }
        }
    }
}

/** iOS sortChips: the order as CHIPS, not a sheet: the whole choice in view, one tap to change it. */
@Composable
private fun SortChips(sort: PlacesSort, onSort: (PlacesSort) -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (order in PlacesSort.entries) {
            val active = order == sort
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.height(44.dp).clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onSort(order) }
                .selectedSemantics(active).testTag("places_sort_${order.rawValue}"), contentAlignment = Alignment.Center) {
                Box(Modifier.height(32.dp).background(if (active) atlas.ink else atlas.chip, CapsuleShape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(when (order) {
                        PlacesSort.recent -> strings.placesSortRecent()
                        PlacesSort.frequent -> strings.placesSortFrequent()
                        PlacesSort.byName -> strings.placesSortName()
                    }, style = if (active) AppType.chipActive else AppType.chip, color = if (active) atlas.background else atlas.ink, maxLines = 1)
                }
            }
        }
    }
}

/**
 * iOS lastPassSection: «Последний проезд», the tab's answer to «why come back». Rare by the bounds of
 * PlaceLastPass: a find has to stay a find.
 */
@Composable
private fun LastPassSection(state: PlacesTabState, now: Instant, onOpen: (UUID) -> Unit) {
    val reading = state.lastPass ?: return
    val item = state.items.firstOrNull { it.place.id == reading.placeId } ?: return
    val strings = appStrings
    val atlas = AtlasTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.testTag("places_last_pass")) { AtlasSectionTitle(strings.placeLastPassSection()) }
        PressableCard(onClick = { onOpen(reading.placeId) }, modifier = Modifier.fillMaxWidth().testTag("places_last_pass_row")) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).background(atlas.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(atlas.accentSoft, ContinuousRoundedRectangle(16.dp)), contentAlignment = Alignment.Center) {
                    WorldGlyphIcon(WorldGlyph.mappin, contentDescription = null, tint = atlas.accentInk, size = 19.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.place.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed(), style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                    Text(lastPassLine(reading, now, strings), style = AppType.meta, color = atlas.secondary, maxLines = 2)
                }
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
            }
        }
    }
}

/** «Вчера · на 7 мин быстрее обычного». «Slower» with the same words and colour: an observation. */
fun lastPassLine(reading: PlaceLastPass.Reading, now: Instant, strings: AppStrings): String {
    val `when` = RelativeTripDate.string(reading.at, now, strings)
    val delta = CheckpointReading.clock(reading.delta, strings)
    return if (reading.isFaster) strings.placeLastPassFaster(`when`, delta) else strings.placeLastPassSlower(`when`, delta)
}

/** The groups of the library; without places, «Мест пока нет» instead of a heading over nothing. */
@Composable
private fun PlacesSection(state: PlacesTabState, onOpen: (UUID) -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    if (state.groups.isEmpty()) {
        if (state.loaded && state.items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 24.dp).testTag("places_none_yet"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.placesNoneYet(), style = AppType.itemTitle, color = atlas.ink, textAlign = TextAlign.Center)
                Text(strings.placesNoneYetBody(), style = AppType.meta, color = atlas.secondary, textAlign = TextAlign.Center)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("places_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.groups.forEachIndexed { index, group ->
            Row(Modifier.fillMaxWidth().padding(top = if (state.groups.size > 1 && index > 0) 6.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                AtlasSectionTitle(groupTitle(group.kind, strings))
                Spacer(Modifier.weight(1f))
                Text(strings.formattedCount(group.items.size), style = AppType.meta.tabular(), color = atlas.secondary)
            }
            group.items.forEach { item -> PlaceCard(item, compact = state.showsOrganization, onTap = { onOpen(item.place.id) }) }
        }
    }
}

fun groupTitle(kind: PlacesPresentation.Group.Kind, strings: AppStrings): String = when (kind) {
    PlacesPresentation.Group.Kind.all -> strings.placesMineSection()
    // The relative dates come TRANSLATED already: the spec forbids new keys for them.
    PlacesPresentation.Group.Kind.today -> strings.today()
    PlacesPresentation.Group.Kind.thisWeek -> strings.thisWeek()
    PlacesPresentation.Group.Kind.earlier -> strings.earlier()
    PlacesPresentation.Group.Kind.frequent -> strings.placesGroupFrequent()
    PlacesPresentation.Group.Kind.others -> strings.placesGroupOthers()
}

/**
 * iOS suggestionsSection: «Похоже, вы здесь бываете», and a SKELETON while it is counted (state 23):
 * places read at once, suggestions arrive later. Null is «still counting», empty is «nothing found».
 */
@Composable
private fun SuggestionsSection(state: PlacesTabState, vm: PlacesTabViewModel) {
    val strings = appStrings
    if (!state.isComputingSuggestions && state.knownSuggestions.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The id on the HEADING: on the column it would rename every row inside.
        Box(Modifier.testTag("places_suggestions")) { AtlasSectionTitle(strings.placeSuggestSection()) }
        if (state.isComputingSuggestions) SuggestionSkeleton(strings.placesSuggestLoading())
        else state.knownSuggestions.forEach { suggestion -> PlaceSuggestionCard(suggestion, onSave = { vm.save(suggestion) }) }
    }
}

/** iOS selectionContent: the chosen pin as one row or one card, and a cross. */
@Composable
private fun SelectionContent(state: PlacesTabState, placeId: UUID?, hintId: UUID?, onOpen: (UUID) -> Unit, onSave: (com.onezee.triptrack.model.PlaceSuggestion) -> Unit, onClose: () -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val item = placeId?.let { id -> state.items.firstOrNull { it.place.id == id } }
    val hint = hintId?.let { id -> state.knownSuggestions.firstOrNull { it.id == id } }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp), verticalAlignment = if (item != null) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            when {
                item != null -> PlaceCard(item, compact = true, onTap = { onOpen(item.place.id) })
                hint != null -> PlaceSuggestionCard(hint, onSave = { onSave(hint) })
            }
        }
        CircleButton(AppGlyph.xmark, strings.close(), "places_selection_close", tint = atlas.secondary, glyphSize = 15.dp, onClick = onClose)
    }
}

/**
 * iOS emptyContent (states 13 and 14): the suggestions first, then «Мест пока нет» and «Отметить в
 * поездке» with the last three trips: a place is born FROM a checkpoint, set on a trip's screen, so
 * the tab without places must lead there.
 */
@Composable
private fun ColumnScope.EmptyContent(state: PlacesTabState, vm: PlacesTabViewModel, now: Instant, onOpenTrip: (Trip) -> Unit) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val unit = LocalDistanceUnit.current
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = tabBarContentClearance()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SuggestionsSection(state, vm)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(atlas.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius))
            .padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}.testTag("places_no_places_note"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(atlas.chip, CircleShape), contentAlignment = Alignment.Center) {
                WorldGlyphIcon(WorldGlyph.mappinSlash, contentDescription = null, tint = atlas.secondary, size = 19.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.placesNoneYet(), style = AppType.itemTitle, color = atlas.ink)
                Text(strings.placesNoneYetBody(), style = AppType.meta, color = atlas.secondary)
            }
        }
        if (state.recentTrips.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) { AtlasSectionTitle(strings.placesMarkInTrip()) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(atlas.card, ContinuousRoundedRectangle(AtlasTheme.cardRadius))
                .padding(horizontal = 16.dp, vertical = 2.dp)) {
                state.recentTrips.forEachIndexed { index, trip ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(atlas.separator))
                    RecentTripRow(trip, strings, unit) { onOpenTrip(trip) }
                }
            }
        }
    }
}

@Composable
private fun RecentTripRow(trip: Trip, strings: AppStrings, unit: com.onezee.triptrack.model.DistanceUnit, onTap: () -> Unit) {
    val atlas = AtlasTheme.colors
    val haptics = rememberHaptics()
    val date = strings.dayMonth(trip.startDate)
    PressableCard(onClick = { haptics.tap(); onTap() }, modifier = Modifier.fillMaxWidth().testTag("places_trip_row")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trip.title ?: date, style = AppType.itemTitle, color = atlas.ink, maxLines = 1)
                // Without a title the row is named by its date: printing it twice in a row is noise.
                val seconds = ((trip.endDate ?: trip.startDate).epochSecond - trip.startDate.epochSecond).toDouble()
                val parts = listOf(if (trip.title == null) "" else date, CheckpointReading.clock(seconds, strings),
                    Measure.distance(trip.distance, unit, strings.language.code, strings)).filter { it.isNotEmpty() }
                Text(parts.joinToString(" · "), style = AppType.meta, color = atlas.secondary, maxLines = 1)
            }
            Glyph(AppGlyph.chevronRight, contentDescription = null, tint = atlas.secondary.copy(alpha = 0.7f), size = 14.dp)
        }
    }
}
