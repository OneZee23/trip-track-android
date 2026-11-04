package com.onezee.triptrack.feature.journey.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.ActionPopover
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.CardPressResponse
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.components.edgeScrims
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.asPreviewTrip
import com.onezee.triptrack.feature.journey.JourneyDayState
import com.onezee.triptrack.feature.journey.JourneyDetailState
import com.onezee.triptrack.feature.journey.JourneyDetailViewModel
import com.onezee.triptrack.feature.journey.JourneyItemState
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.trip.filmingVersion
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.marker.MapMarker
import com.onezee.triptrack.map.route.MapRoutes
import com.onezee.triptrack.map.route.RouteAppearance
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.logic.coordinate
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.SocialJourneyResponse
import com.onezee.triptrack.ui.DetailSectionHeader
import com.onezee.triptrack.ui.DetailStatCard
import com.onezee.triptrack.ui.DetailTopBar
import com.onezee.triptrack.ui.LightRoutePreview
import com.onezee.triptrack.ui.LoadingMoreRow
import com.onezee.triptrack.ui.MapChromeButton
import com.onezee.triptrack.ui.LiveMaps
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.StatSegment
import com.onezee.triptrack.ui.confirmationAction
import com.onezee.triptrack.ui.confirmationMessage
import com.onezee.triptrack.ui.confirmationTitle
import com.onezee.triptrack.ui.durationSegments
import com.onezee.triptrack.ui.heroProgress
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.toast
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val HeroHeight = 300.dp

/** iOS: JourneyDetailView(journeyId:). One's own journey, read from the store by id so edits show. */
@Composable
fun JourneyDetailScreen(id: UUID, feature: TripsFeature, navigator: DestinationNavigator<TripsDestination>) {
    val vm = entryViewModel("journey:$id") { feature.journeyDetail(id) }
    JourneyDetailContent(vm, feature, navigator, social = null)
}

/**
 * iOS: PublicJourneyView.swift. Someone else's journey: the whole response is loaded first (guests
 * may read), then shown without a single local write; hidden, deleted or failed all read the same.
 */
@Composable
fun SocialJourneyScreen(id: UUID, feature: TripsFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val response by produceState<Result<SocialJourneyResponse>?>(null, id) {
        value = runCatching { withContext(feature.worker) { feature.social.journey(id) } }
    }
    HideAppTabBar()
    when (val loaded = response) {
        null -> Box(Modifier.fillMaxSize().background(AppTheme.colors.bg), contentAlignment = Alignment.Center) { LoadingMoreRow() }
        else -> loaded.fold(
            onSuccess = { social ->
                val vm = entryViewModel("social-journey:$id") { feature.journeyDetail(id, social) }
                JourneyDetailContent(vm, feature, navigator, social)
            },
            onFailure = {
                Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp).testTag("public_journey")) {
                    Box(Modifier.padding(start = 4.dp)) {
                        MapChromeButton(AppGlyph.chevronLeft, strings.back(), { navigator.pop() }, Modifier.testTag("public_journey_back"), progress = 1f)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(strings.journeyUnavailable(), style = inter(13f), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp))
                    Spacer(Modifier.weight(2f))
                }
            },
        )
    }
}

@Composable
private fun JourneyDetailContent(vm: JourneyDetailViewModel, feature: TripsFeature, navigator: DestinationNavigator<TripsDestination>, social: SocialJourneyResponse?) {
    val rawState by vm.state.collectAsState()
    val filming by feature.filmingMode.collectAsState()
    val state = remember(rawState, filming, feature.defaultHiddenEdgeMetres()) {
        if (!filming || rawState.isSocial) rawState else {
            val home = feature.homeZone()?.let { it.centre to it.radius }
            fun shown(trip: TripSummary) = trip.filmingVersion(feature.defaultHiddenEdgeMetres(), home)
            val shownTrips = rawState.trips.map(::shown)
            rawState.copy(
                trips = shownTrips,
                days = rawState.days.map { day -> day.copy(items = day.items.map { item -> item.copy(trips = item.trips.map(::shown)) }) },
                coordinates = shownTrips.flatMap { it.coordinates },
                cover = rawState.cover?.takeIf { cover -> shownTrips.any { trip -> trip.photos.any { it.id == cover.id } } },
                totalMetres = shownTrips.sumOf { it.distance },
                firstStart = shownTrips.minByOrNull { it.startDate }?.trackPoints?.firstOrNull()?.coordinate ?: rawState.firstStart,
                farthestEnd = shownTrips.maxByOrNull { it.distance }?.trackPoints?.lastOrNull()?.coordinate ?: rawState.farthestEnd,
            )
        }
    }
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val progress = heroProgress(with(density) { scroll.value.toDp().value }, 240f)
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var mapFullscreen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Journey?>(null) }
    var editOpenings by remember { mutableIntStateOf(0) }

    HideAppTabBar()
    LaunchedEffect(state.message) { state.message?.let { toast = strings.toast(it); vm.consumeMessage() } }
    LaunchedEffect(state.deleted) { if (state.deleted) navigator.pop() }

    val journey = state.journey
    val socialFeature = com.onezee.triptrack.feature.social.LocalSocial.current
    val end = journey?.endDate ?: state.trips.lastOrNull()?.endDate ?: journey?.startDate
    val dateRange = if (journey != null && end != null) JourneyFormat.dateRange(journey.startDate, end, strings) else ""
    val title = JourneyFormat.title(journey?.title, state.startName, state.farthestName, dateRange)

    Box(Modifier.fillMaxSize().background(colors.bg).testTag("journey_detail")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            if (social != null && state.author != null) AuthorRow(state, dateRange) { state.author?.let { navigator.push(TripsDestination.PublicProfile(it.id)) } }
            JourneyHero(state, title, dateRange, onExpand = { mapFullscreen = true })
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (!state.isSocial && state.loaded && state.trips.isEmpty()) {
                    EmptyCard { haptics.tap(); scope.launch { editing = withContext(feature.worker) { feature.journey(vm.journeyId) }; editOpenings++ } }
                } else if (state.loaded) {
                    Totals(state)
                    if (journey != null && !journey.isPrivate && socialFeature != null) {
                        com.onezee.triptrack.feature.social.ui.SocialShareAction(socialFeature, journey.id, true, title)
                    }
                    JourneyDaysList(
                        days = state.days,
                        localNames = state.localNames,
                        onOpenTrip = { trip ->
                            val leg = social?.legs?.firstOrNull { it.id == trip.id }
                            navigator.push(TripsDestination.TripDetail(trip.id, preview = trip.asPreviewTrip(), social = leg))
                        },
                        onRemoveLeg = if (state.isSocial) null else ({ vm.requestRemoveLeg(it.id) }),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 28.dp).edgeScrims(top = true))
        DetailTopBar(
            progress = progress,
            title = title,
            onBack = { navigator.pop() },
            actions = if (state.isSocial || journey == null) null else ({
                Box {
                    MapChromeButton(AppGlyph.ellipsis, strings.moreActions(), { haptics.tap(); showActions = true }, Modifier.testTag("detail_actions"), progress)
                    ActionPopover(showActions, { showActions = false }, buildList {
                        if (journey.isPrivate) add(ActionPopoverItem(strings.journeyPublish(), AppGlyph.globe, testTag = "journey_action_publish") { vm.requestPublish() })
                        else add(ActionPopoverItem(strings.journeyHide(), AppGlyph.lock, testTag = "journey_action_hide") { vm.requestHide() })
                        add(ActionPopoverItem(strings.journeyEdit(), AppGlyph.pencil, testTag = "journey_action_edit") {
                            scope.launch { editing = withContext(feature.worker) { feature.journey(vm.journeyId) }; editOpenings++ }
                        })
                        add(ActionPopoverItem(strings.journeyDelete(), AppGlyph.trash, isDestructive = true, testTag = "journey_action_delete") { vm.requestDelete() })
                    })
                }
            }),
        )
        if (mapFullscreen) JourneyMapFullscreen(state, onClose = { mapFullscreen = false })
        ToastHost(toast, onDismiss = { toast = null })
    }

    editing?.let { domain ->
        val edit = entryViewModel("journey-edit:${domain.id}:$editOpenings") { feature.journeyEdit(domain) }
        JourneyEditSheet(
            vm = edit,
            photos = state.trips.flatMap { it.photos },
            onFinished = { vm.reload() },
            onDismissRequest = { editing = null },
        )
    }
    state.publish?.let { publish ->
        JourneyPublishSheet(title, publish, onConfirm = {
            scope.launch {
                vm.publish()?.join()
                if (vm.state.value.message == null) toast = ToastItem(ToastType.success, strings.journeyPublished())
            }
        }, onDismissRequest = vm::cancelPublish)
    }
    AppConfirm(
        item = state.confirmation,
        onDismissRequest = vm::cancelConfirmation,
        title = { strings.confirmationTitle(it) },
        message = { strings.confirmationMessage(it) },
        actions = { c -> listOf(AppDialogAction(strings.confirmationAction(c), AppDialogAction.Kind.destructive, testTag = "journey_confirm") { vm.acceptConfirmation(c.id) }) },
    )
}

/** iOS: authorRow. Whose journey, its dates and length, above the hero (someone else's only). */
@Composable
private fun AuthorRow(state: JourneyDetailState, dateRange: String, onClick: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val author = state.author ?: return
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = top + 68.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            Text(author.avatarEmoji ?: "🚗", style = inter(18f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(author.displayName ?: strings.blockedListUser(), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
            Text("$dateRange · ${state.calendarDays} ${strings.nounDays(state.calendarDays)}", style = inter(12f, InterWeight.medium), color = colors.textSecondary, maxLines = 1)
        }
    }
}

/**
 * iOS: JourneyDetailView.hero. 300 high: the cover photo when it is among the legs' photos, else the
 * route of every leg, under a dark gradient carrying the title and «12–17 СЕНТ. · КРАСНОДАР. КРАЙ».
 */
@Composable
private fun JourneyHero(state: JourneyDetailState, title: String, dateRange: String, onExpand: () -> Unit) {
    val strings = appStrings
    val regions = state.trips.mapNotNull { RegionDisplay.localized(it.region, strings.language)?.takeIf(String::isNotEmpty) }.distinct()
    val caption = (listOfNotNull(dateRange.takeIf { title != it && it.isNotEmpty() }) + regions.joinToString(", ").takeIf { it.isNotEmpty() }.let(::listOfNotNull))
        .joinToString(" · ").uppercased(strings.language)
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        val cover = state.cover
        when {
            cover != null -> PhotoImage(PhotoSource.Local(cover.filename), maxPixels = 1200, modifier = Modifier.fillMaxSize().testTag("journey_hero_cover"))
            // iOS fits the legs' map clear of the buttons above and the title below; so does the stand-in.
            state.coordinates.size > 1 -> JourneyRouteMap(state, Modifier.fillMaxSize(), fullscreen = false)
            else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.map, contentDescription = null, tint = Color.White.copy(alpha = 0.35f), size = 34.dp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 68.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = inter(26f, InterWeight.bold), letterSpacing = points(-0.5f), color = Color.White, maxLines = 2, modifier = Modifier.testTag("journey_title"))
            if (caption.isNotEmpty()) Text(caption, style = inter(10f, InterWeight.bold), letterSpacing = points(0.4f), color = Color.White.copy(alpha = 0.78f), maxLines = 2)
        }
        if (state.coordinates.size > 1) MapChromeButton(AppGlyph.arrowUpLeftAndArrowDownRight, strings.openRouteMapA11y(), onExpand,
            Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).testTag("journey_map_expand"))
    }
}

@Composable
private fun JourneyMapFullscreen(state: JourneyDetailState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("journey_fullscreen_map")) {
        JourneyRouteMap(state, Modifier.fillMaxSize(), fullscreen = true)
        MapChromeButton(AppGlyph.xmark, appStrings.closeSheet(), onClose,
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 12.dp, top = 8.dp).testTag("journey_map_close"), progress = 1f)
    }
}

@Composable
private fun JourneyRouteMap(state: JourneyDetailState, modifier: Modifier, fullscreen: Boolean) {
    val mapState = remember { MapHostState() }
    // The camera frames every leg; the map draws each leg on its own. One line through all of them
    // would join the end of one leg to the start of the next with a straight chord across country
    // (iOS draws legs, and the journey map has no replay for the same reason).
    val route = remember(state.coordinates) { RouteGeometry.build(state.coordinates, preview = true) }
    val legRoutes = remember(state.trips) {
        state.trips.map { it.coordinates }.filter { it.size > 1 }.map { RouteGeometry.build(it, preview = true) }
    }
    val markers = remember(state.checkpoints) { journeyCheckpointMarkers(state.checkpoints) }
    val dark = AppTheme.isDark
    val style = remember(dark) { LiveMaps.style(dark) }
    LaunchedEffect(route.bounds, fullscreen) {
        route.bounds?.let { bounds -> mapState.execute(MapCameraCommand.FitRoute(bounds,
            if (fullscreen) MapInsets.all(40.0) else MapInsets(top = 64.0, bottom = 96.0, left = 24.0, right = 24.0), animated = false)) }
    }
    if (LocalLiveMaps.current) MapHost(mapState, modifier, routes = MapRoutes(legRoutes, RouteAppearance.plain), markers = markers,
        systemInsets = if (fullscreen) WindowInsets.safeDrawing else WindowInsets(0), styleUri = style,
        gesturesEnabled = fullscreen, textureMode = true)
    else Box(modifier.background(AppTheme.colors.cardAlt)) {
        LightRoutePreview(state.trips.map { it.coordinates }.filter { it.size > 1 }, Modifier.fillMaxSize(), accent = AppTheme.accent)
    }
}

internal fun journeyCheckpointMarkers(checkpoints: List<com.onezee.triptrack.feature.trip.CheckpointState>): List<MapMarker> =
    checkpoints.mapNotNull { checkpoint -> checkpoint.coordinate?.let {
        MapMarker("journey_checkpoint:${checkpoint.id}", it, MapMarker.Kind.CHECKPOINT)
    } }

/** iOS: totals. Days, kilometres, legs (public legs on someone else's), and time on the road (own only). */
@Composable
private fun Totals(state: JourneyDetailState) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val distance = Measure.distanceParts(state.totalMetres, unit, strings.language.code, strings, Measure.Style.grouped)
    val tiles = buildList {
        add(Triple(listOf(StatSegment("${state.calendarDays}", strings.nounDays(state.calendarDays))), strings.journeyDurationLabel(), AppTheme.accent))
        add(Triple(listOf(StatSegment(distance.value, distance.unit)), strings.journeyDistanceLabel(), AppTheme.colors.distance))
        add(Triple(listOf(StatSegment("${state.legCount}", strings.nounTrips(state.legCount))),
            if (state.isSocial) strings.journeyPublicLegsLabel() else strings.journeyLegsLabel(), AppTheme.blue))
        if (!state.isSocial) add(Triple(durationSegments(state.drivingSeconds, strings), strings.journeyDrivingLabel(), AppTheme.accent))
    }
    Column(Modifier.testTag("journey_totals"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (segments, label, color) -> DetailStatCard(segments, label, color, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** iOS: emptyCard. «Пока пусто» and the way to the dates that would fill it. */
@Composable
private fun EmptyCard(onEdit: () -> Unit) {
    val strings = appStrings
    PressableCard(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("journey_empty")) {
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(strings.journeyEmptyTitle(), style = inter(17f, InterWeight.bold), color = AppTheme.colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.journeyEdit(), style = inter(13f, InterWeight.semibold), color = AppTheme.accent)
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = AppTheme.accent, size = 12.dp)
            }
        }
    }
}

/**
 * iOS: JourneyDaysList.swift. A card per day: «ДЕНЬ 2» and the date, then its road legs and its
 * stays, where the trips around one town fold into «Тбилиси · 5 поездок по городу» and open on a tap.
 * A hold on one's own leg offers «Убрать из путешествия».
 */
@Composable
private fun JourneyDaysList(days: List<JourneyDayState>, localNames: Map<UUID, String>, onOpenTrip: (TripSummary) -> Unit, onRemoveLeg: ((TripSummary) -> Unit)?) {
    val strings = appStrings
    var expanded by remember { mutableStateOf(emptySet<UUID>()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailSectionHeader(strings.journeyByDays(), Modifier.padding(bottom = 2.dp))
        days.forEach { day ->
            Column(Modifier.fillMaxWidth().surfaceCard().padding(horizontal = 14.dp, vertical = 12.dp).animateContentSize(spring(dampingRatio = 0.85f, stiffness = 440f))
                .testTag("journey_day")) {
                DayHeader(day)
                day.items.forEachIndexed { index, item ->
                    if (index > 0) Hairline(0.dp)
                    if (item.localAnchor == null) {
                        item.trips.firstOrNull()?.let { trip -> LegRow(trip, onOpenTrip, onRemoveLeg) }
                    } else {
                        val key = item.trips.firstOrNull()?.id ?: return@forEachIndexed
                        StayRow(day, item, localNames[key], key in expanded,
                            onToggle = { expanded = if (key in expanded) expanded - key else expanded + key }, onOpenTrip = onOpenTrip, onRemoveLeg = onRemoveLeg)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: JourneyDayState) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.journeyDay(day.number).uppercased(strings.language), style = inter(10f, InterWeight.bold), letterSpacing = points(0.3f), color = colors.bg,
            modifier = Modifier.background(colors.text, CapsuleShape).padding(horizontal = 8.dp, vertical = 4.dp))
        Text(JourneyFormat.dayDate(day.date, strings), style = inter(12f, InterWeight.semibold), color = colors.textSecondary)
    }
}

@Composable
private fun Hairline(inset: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset, top = 9.dp, bottom = 9.dp).height(0.5.dp).background(AppTheme.colors.border))
}

/** «2 ч 41 мин · 177 км · 2 отметки». */
@Composable
private fun legMeta(trip: TripSummary, strings: AppStrings): String {
    val unit = LocalDistanceUnit.current
    return listOf(JourneyFormat.duration(trip.duration, strings), Measure.distance(trip.distance, unit, strings.language.code, strings, Measure.Style.grouped))
        .joinToString(" · ")
}

@Composable
private fun TripButton(trip: TripSummary, onOpenTrip: (TripSummary) -> Unit, onRemoveLeg: ((TripSummary) -> Unit)?, tag: String, content: @Composable () -> Unit) {
    val strings = appStrings
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    Box {
        PressableCard(
            onClick = { if (!menu) { haptics.tap(); onOpenTrip(trip) } },
            response = if (onRemoveLeg != null) CardPressResponse.hold else CardPressResponse.tap,
            onLongClick = onRemoveLeg?.let { { haptics.action(); menu = true } },
            onLongClickLabel = if (onRemoveLeg != null) strings.journeyRemoveLeg() else null,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) { content() }
        if (onRemoveLeg != null) {
            ActionPopover(menu, { menu = false }, listOf(ActionPopoverItem(strings.journeyRemoveLeg(), AppGlyph.minusCircle, isDestructive = true,
                testTag = "journey_remove_leg") { onRemoveLeg(trip) }))
        }
    }
}

@Composable
private fun LegRow(trip: TripSummary, onOpenTrip: (TripSummary) -> Unit, onRemoveLeg: ((TripSummary) -> Unit)?) {
    val strings = appStrings
    val colors = AppTheme.colors
    TripButton(trip, onOpenTrip, onRemoveLeg, "journey_leg_row") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(JourneyFormat.tripTitle(trip, strings), style = inter(16f, InterWeight.bold), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f))
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 15.dp)
            }
            Text(legMeta(trip, strings), style = inter(12f, InterWeight.medium), color = colors.textTertiary)
            MomentsLine(trip)
        }
    }
}

/** iOS: the moments line of a leg: the first photo small, «N фото». */
@Composable
private fun MomentsLine(trip: TripSummary) {
    val strings = appStrings
    if (trip.photos.isEmpty()) return
    Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        PhotoImage(PhotoSource.Local(trip.photos.first().filename), maxPixels = 96, modifier = Modifier.size(22.dp).background(Color.Transparent, RoundedCornerShape(6.dp)))
        Text("${trip.photos.size} ${strings.nounPhotos(trip.photos.size)}", style = inter(12f, InterWeight.medium), color = AppTheme.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun StayRow(
    day: JourneyDayState,
    item: JourneyItemState,
    name: String?,
    open: Boolean,
    onToggle: () -> Unit,
    onOpenTrip: (TripSummary) -> Unit,
    onRemoveLeg: ((TripSummary) -> Unit)?,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val unit = LocalDistanceUnit.current
    val count = item.trips.size
    val photos = item.trips.sumOf { it.photos.size }
    val meta = listOfNotNull(
        "$count ${strings.nounTrips(count)} ${strings.journeyAroundTown()}",
        Measure.distance(item.trips.sumOf { it.distance }, unit, strings.language.code, strings, Measure.Style.grouped),
        JourneyFormat.duration(item.trips.sumOf { it.duration }, strings),
        if (photos > 0) "$photos ${strings.nounPhotos(photos)}" else null,
    ).joinToString(" · ")
    Column {
        PressableCard(onClick = { haptics.selection(); onToggle() }, modifier = Modifier.fillMaxWidth().semantics { selected = open }.testTag("journey_local_group")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(26.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.houseFill, contentDescription = null, tint = colors.textSecondary, size = 14.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name ?: strings.journeyStayFallback(), style = inter(15f, InterWeight.bold), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        val last = item.lastDayNumber
                        if (last != null && last > day.number) {
                            Text(strings.journeyDays(day.number, last).uppercased(strings.language), style = inter(10f, InterWeight.bold), letterSpacing = points(0.3f),
                                color = colors.textSecondary, modifier = Modifier.background(colors.cardAlt, CapsuleShape).padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        Glyph(if (open) AppGlyph.chevronUp else AppGlyph.chevronDown, contentDescription = null, tint = colors.textTertiary, size = 15.dp)
                    }
                    Text(meta, style = inter(12f, InterWeight.medium), color = colors.textTertiary)
                }
            }
        }
        if (open) {
            item.trips.forEach { trip ->
                Hairline(44.dp)
                TripButton(trip, onOpenTrip, onRemoveLeg, "journey_local_trip_row") {
                    Row(Modifier.padding(start = 44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(JourneyFormat.time(trip.startDate, strings), style = inter(12f, InterWeight.semibold), color = colors.textTertiary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(JourneyFormat.tripTitle(trip, strings), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
                            Text(legMeta(trip, strings), style = inter(12f, InterWeight.medium), color = colors.textTertiary)
                            MomentsLine(trip)
                        }
                        Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 15.dp)
                    }
                }
            }
        }
    }
}
