// iOS: MyMapView.swift
package com.onezee.triptrack.feature.atlas.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.navigation.TabBarMetrics
import com.onezee.triptrack.designsystem.navigation.tabBarTappableInset
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsDestinationScreen
import com.onezee.triptrack.feature.atlas.AndroidAtlasLocation
import com.onezee.triptrack.feature.atlas.AtlasCameraAction
import com.onezee.triptrack.feature.atlas.AtlasLocationPermission
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.feature.atlas.AtlasSheetDetent
import com.onezee.triptrack.feature.atlas.AtlasSlot
import com.onezee.triptrack.feature.atlas.AtlasSummaryVariant
import com.onezee.triptrack.feature.atlas.WorldFeature
import com.onezee.triptrack.logic.distanceKm
import com.onezee.triptrack.logic.encodePolyline
import com.onezee.triptrack.logic.isEmpty
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.MapTripPin
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.navigation.TabNavHost
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The Atlas tab: the screen, and a trip opened from it sliding over it in its own stack (iOS presents
 * TripDetailView over the Atlas with `fullScreenCover`).
 */
@Composable
fun AtlasTab(feature: WorldFeature, resumeTick: Int) {
    Box(Modifier.fillMaxSize()) {
        AtlasScreen(feature, resumeTick)
        TabNavHost(feature.atlasTrips, Modifier.fillMaxSize(), root = {}) { dest ->
            TripsDestinationScreen(dest, feature.trips, feature.atlasTrips)
        }
    }
}

/**
 * iOS: MyMapView. The live roads under the paper map, the appearance controls and the permanent
 * sheet. Everything that stands in screen coordinates moves by OFFSET inside one full-screen box: on
 * iOS, layers pushed out of their container shifted the safe area of the whole window.
 */
@Composable
fun AtlasScreen(feature: WorldFeature, resumeTick: Int = 0, now: () -> Instant = feature.trips.clock) {
    val vm = feature.atlas
    val state by vm.state.collectAsState()
    val online by feature.online.collectAsState()
    val strings = appStrings
    val atlas = AtlasTheme.colors
    val unit = LocalDistanceUnit.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) { vm.loadIfNeeded() }
    // The answer to «is location allowed» changes in the system settings; read it on every return.
    LaunchedEffect(resumeTick) { (feature.location as? AndroidAtlasLocation)?.refresh() }

    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showHome by rememberSaveable { mutableStateOf(false) }
    var showBeta by rememberSaveable { mutableStateOf(false) }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    // «Позже» does not ask again until the next launch: a refusal for now, not a setting.
    var locationAskDismissed by rememberSaveable { mutableStateOf(false) }
    var roadExpanded by rememberSaveable { mutableStateOf(false) }
    var renderingPoster by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    val mapState = remember { MapHostState() }
    var framed by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(atlas.background).testTag("screen_atlas")) {
        val screenHeight = maxHeight
        val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // The slot is counted from the TOP of the tab bar's capsule; the capsule clears a tappable
        // navigation bar, so the slot is counted on the height above it.
        val tappable = tabBarTappableInset()
        val slot = AtlasSlot((screenHeight - tappable).value.toDouble(), safeTop.value.toDouble(), safeBottom.value.toDouble())
        val region = state.selectedRegion
        val variant = if (region != null) AtlasSummaryVariant.region else AtlasSummaryVariant.summary
        val detent = state.detent
        val target = slot.top(detent, variant)
        val motion = rememberSlotMotion(target)
        val sheetTop = motion.top.value.toDouble()
        val placeAnchor = if (slot.showsPeekRow) slot.peekTop else slot.tabBarTop
        val cardShown = state.selectedPlace != null || (state.homeSelected && state.home.mapPin != null)
        // The slot's top, followed by the map controls: the card when one holds the slot.
        val slotTop = if (cardShown) placeAnchor - 12 - PLACE_CARD_HEIGHT.value else sheetTop
        val selectionPanel = state.selection != null && region == null

        // Back on Android closes what is open before it leaves the tab (Android's back guidance).
        BackHandler(enabled = state.selection != null || state.selectedPlaceId != null || state.homeSelected || detent == AtlasSheetDetent.expanded) {
            when {
                region != null -> if (detent == AtlasSheetDetent.expanded) vm.setDetent(AtlasSheetDetent.collapsed) else vm.backFromSubstate()
                state.selection != null || state.selectedPlaceId != null || state.homeSelected -> vm.closeSelection()
                else -> vm.setDetent(AtlasSheetDetent.collapsed)
            }
        }

        // The camera fits the trips' midpoints once the first data is there (iOS applyInitialCameraIfNeeded).
        LaunchedEffect(state.exploration) {
            if (framed || state.exploration.isEmpty) return@LaunchedEffect
            bounds(state.exploration.trips.map { it.coordinate })?.let {
                framed = true
                mapState.execute(MapCameraCommand.Fit(it, MapCameraCommand.Padding.overview, animated = false))
            }
        }
        // One-off camera commands from the view model, acknowledged by revision.
        LaunchedEffect(state.cameraRevision) {
            val action = state.camera ?: return@LaunchedEffect
            framed = true
            when (action) {
                is AtlasCameraAction.Command -> mapState.execute(action.command)
                AtlasCameraAction.UserLocation -> if (!mapState.locateUser()) vm.fitAll()
            }
            vm.acknowledgeCamera(state.cameraRevision)
        }
        LaunchedEffect(state.selection) { roadExpanded = false }

        // The attribution and the camera's bottom stay over the slot, but NEVER over the list: at the
        // list's top the inset would be 748 of an 844 map, and the camera fits by the sum of insets.
        val chromeBottom = slot.attributionInset(if (detent == AtlasSheetDetent.peek) AtlasSheetDetent.peek else AtlasSheetDetent.collapsed, variant) -
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val latitudeSpan = mapState.camera?.latitudeSpan ?: 0.0
        val routes = remember(state.exploration) { RouteIndex.build(state.exploration.trips) }
        AtlasMapLayer(
            state = state,
            mapState = mapState,
            chrome = MapInsets(bottom = chromeBottom.coerceAtLeast(0.0)),
            latitudeSpan = latitudeSpan,
            strings = strings,
            onMarker = { marker ->
                haptics.tap()
                when {
                    marker.id == HOME_MARKER -> vm.selectHome()
                    else -> placeIdOf(marker)?.let(vm::selectPlace)
                }
            },
            onTap = { tap ->
                // SECOND LINE of the strip's rule: while the list is open a tap on the map picks
                // nothing, it collapses (spec §4).
                if (detent == AtlasSheetDetent.expanded) { vm.setDetent(AtlasSheetDetent.collapsed); return@AtlasMapLayer }
                when (val meaning = AtlasMapTap.resolve(tap, mapState.camera?.latitudeSpan ?: latitudeSpan, routes)) {
                    is AtlasMapTap.Road -> { haptics.tap(); vm.selectRoad(meaning.tripIds) }
                    is AtlasMapTap.Ground -> vm.tapMap(meaning.point, meaning.zoomToRegion)
                }
            },
        )

        // Paper fading over the map keeps the clock readable.
        Box(Modifier.fillMaxWidth().height(safeTop + 110.dp).background(Brush.verticalGradient(listOf(atlas.background.copy(alpha = 0.8f), atlas.background.copy(alpha = 0f)))))

        val controlsOpacity = AtlasSlot.controlsOpacity(slotTop).toFloat()
        if (!selectionPanel) {
            AtlasMapControls(
                showsLayers = !state.isEmpty,
                locationDenied = state.locationPermission == AtlasLocationPermission.denied || state.locationPermission == AtlasLocationPermission.restricted,
                onLayers = { showAppearance = true },
                onLocate = {
                    if (state.locationPermission == AtlasLocationPermission.denied || state.locationPermission == AtlasLocationPermission.restricted) {
                        // After «Позже» the button still helps: the whole atlas (spec: works without
                        // location, it just does not centre).
                        if (locationAskDismissed) vm.fitAll() else vm.locateUser()
                    } else vm.locateUser()
                },
                enabled = controlsOpacity > 0.5f,
                modifier = Modifier.align(Alignment.TopEnd).anchorBottom(slotTop - AtlasSlot.controlsGap, density).padding(end = 16.dp)
                    .alpha(controlsOpacity),
            )
        }

        if (state.isEmpty) EmptyAtlas(Modifier.align(Alignment.Center))
        if (state.isLoading || state.isFiltering) CarLoading(Modifier.align(Alignment.Center))

        // The strip of map over the open list IS «collapse» (spec §4): aimed at closing the list, the
        // finger used to open whatever region lay under it.
        if (detent == AtlasSheetDetent.expanded) {
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxWidth().height(slot.expandedTop.dp)
                .clickable(interactionSource = interaction, indication = null) { vm.setDetent(AtlasSheetDetent.collapsed) }
                .semantics { contentDescription = strings.close() }.testTag("atlas_collapse_strip"))
        }

        if (selectionPanel) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                AtlasSelectionPanel(
                    trip = state.selectedTrip,
                    road = state.selectedRoad,
                    expanded = roadExpanded,
                    maxHeight = screenHeight.value,
                    now = now(),
                    onExpand = { roadExpanded = it },
                    onSelectTrip = { vm.select(AtlasSelection.Trip(it)) },
                    onOpenTrip = { openTrip(feature, it) },
                    onClose = { vm.closeSelection() },
                )
            }
        } else {
            AtlasSheet(
                motion = motion,
                target = target,
                screenHeight = screenHeight,
                accessibilityTitle = if (region != null) region.localizedName(strings) else strings.atlasExplored(),
                onDrag = { raw ->
                    when {
                        region != null -> slot.substateTop(raw)
                        // In the peek the peek itself is the lower bound, or the place card would jump.
                        else -> slot.clamped(raw, if (detent == AtlasSheetDetent.peek) AtlasSheetDetent.peek else AtlasSheetDetent.collapsed, variant)
                    }
                },
                onSettle = { top, velocity ->
                    // The peek is reached only by choosing a place; a gesture on it means «drop the
                    // choice and show the list», or it stays.
                    if (detent == AtlasSheetDetent.peek) {
                        if (AtlasSlot.settle(top, velocity, slot, variant) == AtlasSheetDetent.expanded) vm.expand()
                    } else vm.settleSheet(top, velocity, slot, variant)
                },
                onTapHandle = {
                    if (region != null) vm.setDetent(if (detent == AtlasSheetDetent.expanded) AtlasSheetDetent.collapsed else AtlasSheetDetent.expanded)
                    else vm.toggleSheet()
                },
                testTag = if (region != null) "atlas_region_sheet" else "atlas_sheet",
                header = {
                    if (region != null) {
                        AtlasSubstateHeader(region.localizedName(strings), regionSubtitle(region, strings),
                            onBack = { vm.backFromSubstate() }, onClose = { vm.closeSelection() })
                    } else {
                        AtlasSheetTitleRow(strings.myMapTitle()) { AtlasBetaChip({ showBeta = true }) }
                    }
                },
            ) {
                if (region != null) {
                    AtlasRegionContent(state, region, now(), onOpenTrip = { openTrip(feature, it) }, onFocusCity = { vm.focusCity(it) })
                } else {
                    AtlasSummaryContent(
                        state = state,
                        detent = detent,
                        isOffline = !online,
                        onShare = {
                            if (vm.requestShare() && !renderingPoster && !state.isFiltering) {
                                renderingPoster = true
                                scope.launch {
                                    val shared = AtlasSharePoster.share(context, state, strings, unit)
                                    renderingPoster = false
                                    if (!shared) toast = ToastItem(ToastType.error, strings.posterFailed())
                                }
                            }
                        },
                        onShareUnavailable = { toast = ToastItem(ToastType.info, strings.atlasOffline()) },
                        onExplain = { showExplanation = true },
                        onOpenRegion = { id -> vm.select(AtlasSelection.Region(id)) },
                    )
                }
            }
        }

        // The place card stands over the slot, 12 above the peek row (12 above the tab bar under
        // 700 pt, where there is no peek row). By offset, not by padding.
        AnimatedVisibility(
            visible = state.selectedPlace != null,
            modifier = Modifier.align(Alignment.TopStart).anchorBottom(placeAnchor - 12, density).padding(horizontal = AtlasTheme.sideInset),
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(160)),
        ) {
            val place = state.selectedPlace
            if (place != null) {
                AtlasPlaceCard(
                    tile = PlaceCardTile.place,
                    name = place.name?.takeIf { it.isNotEmpty() } ?: strings.placeUnnamed(),
                    line = placeCardLine(place.passCount, place.lastAt, strings),
                    actionTitle = strings.atlasOpenPlace(),
                    onOpen = { haptics.tap(); feature.openPlace(place.id) },
                    onClose = { vm.closeSelection() },
                )
            }
        }
        AnimatedVisibility(
            visible = state.homeSelected && state.home.mapPin != null,
            modifier = Modifier.align(Alignment.TopStart).anchorBottom(placeAnchor - 12, density).padding(horizontal = AtlasTheme.sideInset),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            AtlasPlaceCard(
                tile = PlaceCardTile.home,
                name = strings.atlasHome(),
                line = strings.atlasTripsFromHome("${strings.formattedCount(state.homeTripCount)} ${strings.nounTrips(state.homeTripCount)}"),
                actionTitle = strings.atlasHomeConfigure(),
                onOpen = { haptics.tap(); vm.closeSelection(); showHome = true },
                onClose = { vm.closeSelection() },
                testTag = "atlas_home_card",
            )
        }

        if (renderingPoster) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = (screenHeight.value - slot.collapsedTop(variant).toFloat() + 8).dp)
                    .background(Color.Black.copy(alpha = 0.55f), CapsuleShape).padding(horizontal = 14.dp, vertical = 10.dp).testTag("mymap_share_progress"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CarLoadingDots(Color.White)
                Text(strings.shareRendering(), style = inter(13f, InterWeight.semibold), color = Color.White)
            }
        }

        ToastHost(toast, onDismiss = { toast = null })
    }

    if (showAppearance) {
        AtlasAppearanceSheet(
            appearance = state.appearance,
            home = state.home,
            onChange = vm::setAppearance,
            // A sheet over a sheet would take the lower one away: close first, then open home.
            onOpenHome = { showAppearance = false; showHome = true },
            onDismissRequest = { showAppearance = false },
        )
    }
    if (showHome) {
        HomeSheet(state.home, state.exploration.trips.firstOrNull()?.coordinate, onChange = feature::setHome, onDismissRequest = { showHome = false })
    }
    if (showBeta) AtlasBetaSheet(BetaSubject.atlas) { showBeta = false }
    if (showExplanation) AtlasExplanationSheet { showExplanation = false }
    // A house dialog at the screen root; the tab bar stays: the question is not destructive.
    AppConfirm(
        isPresented = state.showsLocationDialog,
        onDismissRequest = { locationAskDismissed = true; vm.dismissLocationDialog() },
        title = strings.atlasLocationDeniedTitle(),
        message = strings.atlasLocationDeniedBody(),
        actions = listOf(AppDialogAction(strings.openSettings()) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }),
        cancelTitle = strings.onboardingSkipForNow(),
    )
}

/** A trip from a region row, a road or the trip card: the trip screen over the Atlas. */
private fun openTrip(feature: WorldFeature, trip: MapTripPin) {
    feature.atlasTrips.push(TripsDestination.TripDetail(trip.id, preview = trip.previewTrip()))
}

/** What the Atlas already knows of a trip: the trip screen's first frame has its numbers at once. */
fun MapTripPin.previewTrip(): com.onezee.triptrack.model.Trip = com.onezee.triptrack.model.Trip(
    id = id, startDate = startDate, endDate = startDate.plusSeconds(duration.toLong()), distance = distance,
    maxSpeed = maxSpeedMS, averageSpeed = avgSpeedMS, title = title,
    previewPolyline = route.takeIf { it.size > 1 }?.let { com.onezee.triptrack.model.Trip.encodePolyline(it) },
)

/** «Здесь 12 раз · 20 сент.»: how many times and when last (iOS placeCardLine). */
fun placeCardLine(passCount: Int, lastAt: Instant?, strings: AppStrings): String {
    val here = strings.placeHereTimes(passCount)
    val last = lastAt ?: return here
    return "$here · ${strings.placeLastPass(LocalizedDateFormatter.templates("dMMM", strings.zone).getValue(strings.language).format(last))}"
}

/** iOS: emptyState. The drawing, «Карта пока пустая» and its line, above the sheet's middle. */
@Composable
private fun EmptyAtlas(modifier: Modifier) {
    val strings = appStrings
    val atlas = AtlasTheme.colors
    Column(modifier.widthIn(max = 280.dp).padding(bottom = (EMPTY_SHEET_HEIGHT / 2).dp).testTag("atlas_empty"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.empty_map), contentDescription = null, modifier = Modifier.size(120.dp))
        Text(strings.emptyMapTitle(), style = inter(18f, InterWeight.semibold), color = atlas.ink, textAlign = TextAlign.Center)
        Text(strings.emptyMapSubtitle(), style = inter(13f), color = atlas.secondary, textAlign = TextAlign.Center)
    }
}

/** iOS MyMapSheet.collapsedHeight: 166 + the tab bar clearance + 2. */
private val EMPTY_SHEET_HEIGHT: Float = 166f + TabBarMetrics.clearance.value + 2f

/** Puts the bottom edge of the content at [y] (dp from the screen's top) by OFFSET, never by padding. */
fun Modifier.anchorBottom(y: Double, density: androidx.compose.ui.unit.Density): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place(0, with(density) { y.toFloat().dp.roundToPx() } - placeable.height)
    }
}

private fun bounds(points: List<com.onezee.triptrack.model.GeoPoint>): GeoBounds? = if (points.isEmpty()) null else GeoBounds(
    points.minOf { it.latitude }, points.maxOf { it.latitude }, points.minOf { it.longitude }, points.maxOf { it.longitude })

private fun com.onezee.triptrack.model.MapRegionStat.localizedName(strings: AppStrings): String =
    if (strings.language.code == "ru") nameRu else nameEn
