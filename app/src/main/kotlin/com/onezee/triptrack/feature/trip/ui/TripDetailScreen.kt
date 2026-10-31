package com.onezee.triptrack.feature.trip.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.map.replay.MapPlayback
import com.onezee.triptrack.map.replay.CarHeadingPolicy
import com.onezee.triptrack.map.MapPoints
import com.onezee.triptrack.feature.garage.VehicleAvatar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.ActionPopover
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.components.edgeScrims
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.asPreviewTrip
import com.onezee.triptrack.feature.trip.CheckpointState
import com.onezee.triptrack.feature.trip.DetailPhotoState
import com.onezee.triptrack.feature.trip.SegmentState
import com.onezee.triptrack.feature.trip.TripDetailState
import com.onezee.triptrack.feature.trip.TripDetailViewModel
import com.onezee.triptrack.feature.trip.TripExportFormat
import com.onezee.triptrack.feature.journey.ui.JourneyComposerSheet
import com.onezee.triptrack.logic.elevation
import com.onezee.triptrack.logic.speedValue
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.map.camera.MapInsets
import com.onezee.triptrack.map.route.RouteGeometry
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.ui.CheckpointReading
import com.onezee.triptrack.ui.DetailTopBar
import com.onezee.triptrack.ui.JourneyLinkRow
import com.onezee.triptrack.ui.LocalLiveMaps
import com.onezee.triptrack.ui.LocalTripPhotoImages
import com.onezee.triptrack.ui.MapChromeButton
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoImport
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.PhotoViewer
import com.onezee.triptrack.ui.TripExportShare
import com.onezee.triptrack.ui.ViewerPage
import com.onezee.triptrack.ui.confirmationAction
import com.onezee.triptrack.ui.confirmationMessage
import com.onezee.triptrack.ui.confirmationTitle
import com.onezee.triptrack.ui.heroProgress
import com.onezee.triptrack.ui.navigation.LocalScreenAppeared
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import com.onezee.triptrack.ui.toast
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS: TripDetailView.swift. One trip: the map hero (only this trip, flat, no fog), the title and
 * its chips, the numbers, the moments of one's own trip with their checkpoints and segments, the
 * charts, the description and the photos. The first frame comes from what the list already knew;
 * Back works before the full read lands.
 */
@Composable
fun TripDetailScreen(dest: TripsDestination.TripDetail, feature: TripsFeature, navigator: DestinationNavigator<TripsDestination>) {
    val vm = entryViewModel("trip:${dest.id}") { feature.tripDetail(dest.id, dest.preview, dest.social) }
    LaunchedEffect(vm) { vm.load() }
    val state by vm.state.collectAsState()
    TripDetailContent(state, vm, feature, navigator, dest.social)
}

@Composable
private fun TripDetailContent(
    state: TripDetailState,
    vm: TripDetailViewModel,
    feature: TripsFeature,
    navigator: DestinationNavigator<TripsDestination>,
    social: com.onezee.triptrack.model.SocialFeedTrip?,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val images = LocalTripPhotoImages.current
    val trip = state.trip
    val signedIn = feature.signedIn()

    HideAppTabBar()

    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val posterHeight = windowHeight * 0.45f
    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()
    val progress = heroProgress(with(density) { scroll.value.toDp().value }, (posterHeight - safeTop - 52.dp).value)

    var toast by remember { mutableStateOf<ToastItem?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var showComposer by remember { mutableStateOf(false) }
    var selectedCheckpoint by remember { mutableStateOf<CheckpointState?>(null) }
    var selectedSegment by remember { mutableStateOf<SegmentState?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var photoToDelete by remember { mutableStateOf<DetailPhotoState?>(null) }
    var journeyTick by remember { mutableIntStateOf(0) }
    var composerOpenings by remember { mutableIntStateOf(0) }

    // Effects of the view model: a toast per message, the share sheet per export, leaving per delete.
    LaunchedEffect(state.message) { state.message?.let { toast = strings.toast(it); vm.consumeMessage() } }
    LaunchedEffect(state.exported) {
        val export = state.exported ?: return@LaunchedEffect
        vm.consumeExport()
        runCatching { TripExportShare.write(context, export) }
            .onSuccess { TripExportShare.share(context, it, export.format, export.filename) }
            .onFailure { toast = ToastItem(ToastType.error, strings.exportFailed()) }
    }
    LaunchedEffect(state.deleted, state.draftResolved) {
        if (state.deleted || state.draftResolved) navigator.pop()
    }

    val vehicle by produceState<Vehicle?>(null, trip?.vehicleId) { value = trip?.vehicleId?.let { withContext(feature.worker) { feature.vehicle(it) } } }
    var vehicleKnown by remember { mutableStateOf(false) }
    LaunchedEffect(vehicle, trip?.vehicleId) { vehicleKnown = true }
    val journey by produceState<Journey?>(null, trip?.id, journeyTick, state.isOwn) {
        value = if (trip != null && state.isOwn) withContext(feature.worker) { feature.journeyContaining(trip.id) } else null
    }

    // The map: the route shape is built off the main thread, once per geometry revision.
    val route by produceState(RouteGeometry.build(emptyList()), state.geometryRevision, state.coordinates) {
        val coordinates = state.coordinates
        val speeds = state.speeds
        val preview = state.isPreviewRoute
        value = withContext(Dispatchers.Default) { RouteGeometry.build(coordinates, speeds, preview) }
    }
    val pinBitmaps by produceState(emptyMap<UUID, Bitmap>(), state.photoPins, images) {
        value = state.photoPins.mapNotNull { pin -> images?.load(PhotoSource.Local(pin.filename), 120)?.let { pin.id to it.asAndroidBitmap() } }.toMap()
    }
    val replay = remember(state.replay) { TripReplayPlayer(state.replay, state.trip?.distance ?: 0.0) }
    val reduceMotion = LocalReduceMotion.current
    SideEffect { replay.reduceMotion = reduceMotion }
    val mapState = remember { MapHostState() }
    val expansion = remember { TripMapExpansion() }
    var selectedMarker by remember { mutableStateOf<String?>(null) }
    TripReplayClock(replay, expansion.state == MapExpansion.expanded)
    val holdMarkers = remember(state.checkpoints, state.replay) {
        val times = state.replay.timestamps
        state.checkpoints.filter { it.coordinate != null && times.size >= 2 && it.timestamp > times.first() && it.timestamp < times.last() }.sortedBy { it.timestamp }
    }
    LaunchedEffect(replay, holdMarkers) { replay.change { setHolds(holdMarkers.map { it.timestamp }) } }
    LaunchedEffect(replay.frame.holdingIndex) { if (replay.frame.holdingIndex != null) haptics.selection() }
    var followsCar by remember { mutableStateOf(false) }
    var collapsedCar by remember { mutableStateOf(false) }
    LaunchedEffect(mapState.camera?.metresPerPoint) {
        collapsedCar = CarHeadingPolicy.collapsed(mapState.camera?.metresPerPoint ?: 0.0, collapsedCar)
    }
    val shownMarker = selectedMarker ?: replay.frame.holdingIndex?.let { holdMarkers.getOrNull(it) }?.let { "cp:${it.id}" }
    val playback = replay.frame.head?.takeIf { expansion.state.presented }?.let {
        MapPlayback(it, replay.frame.course, replayCarColor(vehicle?.avatarEmoji), collapsedCar)
    }
    val mapInput = TripMapInput(route.takeIf { it.lines.isNotEmpty() }, state.checkpoints, state.photoPins, pinBitmaps,
        playback, if (playback != null) replay.litFraction else null)
    val currentInput by rememberUpdatedState(mapInput)
    val dark = AppTheme.isDark
    val mapContent = remember {
        movableContentOf { modifier: Modifier, fullscreen: Boolean, insets: MapInsets, onTap: (String) -> Unit ->
            TripLiveMap(mapState, currentInput, fullscreen, dark, insets, { onTap(it.id) }, modifier)
        }
    }
    val live = LocalLiveMaps.current && LocalScreenAppeared.current && state.coordinates.size > 1
    val rendered = mapState.camera != null
    var heroFitted by remember { mutableStateOf(false) }
    LaunchedEffect(live, route) {
        if (live && route.bounds != null && expansion.state == MapExpansion.collapsed) {
            heroFit(mapInput.route)?.let(mapState::execute)
            heroFitted = true
        }
    }
    val cardHeight = when {
        shownMarker?.startsWith("photo:") == true -> 132.0
        shownMarker?.startsWith("cp:") == true -> 92.0
        else -> 0.0
    }
    TripMapExpansionEffects(
        expansion,
        onExpanded = { fullscreenFit(mapInput.route, cardHeight, replay.canPlay)?.let(mapState::execute) },
        onCollapsed = { selectedMarker = null; followsCar = false; replay.stop(); heroFit(mapInput.route)?.let(mapState::execute) },
    )
    var followingSince by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(followsCar, replay.frame.head, expansion.state) {
        val head = replay.frame.head
        if (followsCar && head != null && expansion.state == MapExpansion.expanded) {
            val now = TripReplayPlayer.now()
            if (followingSince == null) {
                followingSince = now
                val scale = MapPoints.metresPerMapPoint(head.latitude) * minOf(windowHeight.value, windowWidth.value) / 1400.0
                mapState.execute(MapCameraCommand.Move(head, MapPoints.mapLibreZoom(scale), animated = !reduceMotion, durationMillis = 450))
            } else if (now - followingSince!! >= .45 || reduceMotion) {
                mapState.camera?.let { mapState.execute(MapCameraCommand.Move(head, it.zoom, animated = false)) }
            }
        } else if (!followsCar && followingSince != null) {
            followingSince = null
            fullscreenFit(mapInput.route, cardHeight, replay.canPlay)?.let(mapState::execute)
        }
    }
    BackHandler(enabled = expansion.state == MapExpansion.expanded) { expansion.collapse() }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty() || trip == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = PhotoImport.import(context, uris, trip.id, feature.repository)
            if (added > 0) { vm.load(); toast = ToastItem(ToastType.success, strings.photosAdded(added)) }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg).testTag("trip_detail")) {
        if (trip == null) {
            if (state.loadFailed) LoadFailedState(onRetry = { vm.load() }, onBack = { navigator.pop() })
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("detail_content")) {
                Hero(
                    height = posterHeight,
                    state = state,
                    expansionPresented = expansion.state.presented,
                    live = live,
                    rendered = rendered,
                    map = { mapContent(Modifier.fillMaxSize(), false, MapInsets.Zero) {} },
                    onExpand = { haptics.tap(); expansion.expand() },
                )
                InfoPanel(
                    now = feature.clock(),
                    state = state,
                    foreignVehicle = social?.vehicle,
                    vehicle = vehicle,
                    vehicleKnown = vehicleKnown,
                    journey = journey,
                    signedIn = signedIn,
                    safeBottom = safeBottom,
                    onOpenJourney = { navigator.push(TripsDestination.JourneyDetail(it)) },
                    onConfirmDraft = { vm.confirmDraft() },
                    onDiscardDraft = { vm.requestDelete() },
                    onEnergyMode = { vm.setEnergyMode(it) },
                    onSelectCheckpoint = { selectedCheckpoint = it },
                    onSelectSegment = { selectedSegment = it },
                    onOpenPhoto = { id -> state.photos.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { viewerIndex = it } },
                    onOpenPhotoAt = { viewerIndex = it },
                    onDeletePhoto = { photoToDelete = it },
                    onAddPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
            }
            Box(Modifier.fillMaxWidth().height(safeTop + 28.dp).edgeScrims(top = true))
            DetailTopBar(
                progress = progress,
                title = trip.detailTitle(strings),
                onBack = { navigator.pop() },
                backTag = if (state.isLoading) "detail_loading_back" else "detail_back",
                pill = social?.author?.takeIf { !state.isOwn }?.let { author -> { AuthorPill(author) } },
                actions = if (state.isOwn && !state.isLoading) ({
                    Box {
                        MapChromeButton(AppGlyph.ellipsis, strings.moreActions(), { haptics.tap(); showActions = true }, Modifier.testTag("detail_actions"), progress)
                        ActionPopover(showActions, { showActions = false }, ownerActions(state, journey, vm, navigator, onCombine = { composerOpenings++; showComposer = true }))
                    }
                }) else null,
            )
        }
        if (expansion.state.presented) {
            FullscreenLayer(expansion.opacity.value) {
                if (live) mapContent(Modifier.fillMaxSize(), true, MapInsets(top = 64.0, bottom = (if (replay.canPlay) 190.0 else 36.0) + cardHeight)) { id ->
                    haptics.selection()
                    replay.pause()
                    val photoId = id.removePrefix("photo:").takeIf { id.startsWith("photo:") }
                    if (photoId != null && selectedMarker == id) {
                        state.photos.indexOfFirst { it.id.toString() == photoId }.takeIf { it >= 0 }?.let { viewerIndex = it }
                    } else selectedMarker = id
                } else RoutePlaceholder(state.coordinates, Modifier.fillMaxSize())
                FullscreenChrome(
                    state = state,
                    selectedMarker = shownMarker,
                    replay = replay,
                    followsCar = followsCar,
                    onFollow = { followsCar = !followsCar },
                    onStopReplay = { followsCar = false; selectedMarker = null; replay.stop(); fullscreenFit(mapInput.route, 0.0, replay.canPlay)?.let(mapState::execute) },
                    visible = expansion.state.showsChrome,
                    onClose = { haptics.tap(); replay.stop(); expansion.collapse() },
                    onZoom = { delta -> mapState.camera?.let { mapState.execute(MapCameraCommand.Move(it.centre, it.zoom + delta)) } },
                    onCloseCard = { selectedMarker = null; replay.pause() },
                    onOpenPhoto = { id -> state.photos.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { viewerIndex = it } },
                )
            }
        }
        viewerIndex?.let { index ->
            PhotoViewer(
                pages = state.photos.map { ViewerPage(it.id, it.source(thumbnail = false), it.source(thumbnail = true), it.timestamp) },
                initialIndex = index,
                region = com.onezee.triptrack.ui.RegionDisplay.localized(trip?.region, strings.language),
                onDismiss = { viewerIndex = null },
                onDelete = if (state.isOwn) ({ id -> deletePhoto(feature, vm, state, id, scope, { toast = it }, strings.photoDeleted()) }) else null,
            )
        }
        ToastHost(toast, onDismiss = { toast = null })
    }

    // Sheets and dialogs live at the screen's root, never inside the scroll.
    selectedCheckpoint?.let { checkpoint ->
        val trip0 = state.trip ?: return@let
        CheckpointEditorSheet(
            checkpoint = checkpoint,
            tripPhotos = trip0.photos.filterNot { photo -> checkpoint.photos.any { it.id == photo.id } },
            otherCheckpoints = state.checkpoints.filter { it.id != checkpoint.id },
            canCreateSegment = state.canAddSegment,
            onSave = { name, edited, cover, photoIds -> vm.updateCheckpoint(checkpoint.id, if (edited) name else checkpoint.name, cover, photoIds) },
            onDelete = { vm.deleteCheckpoint(checkpoint.id) },
            onCreateSegment = { other ->
                vm.addSegment(checkpoint.id, other)
                toast = ToastItem(ToastType.success, strings.segmentCreated())
            },
            onDismissRequest = { selectedCheckpoint = null },
        )
    }
    selectedSegment?.let { segment ->
        SegmentEditorSheet(
            segment = segment,
            title = segment.displayName(state.checkpoints, strings),
            onSave = { vm.renameSegment(segment.id, it) },
            onDelete = { vm.deleteSegment(segment.id) },
            onDismissRequest = { selectedSegment = null },
        )
    }
    if (showComposer && trip != null) {
        // A fresh composer per opening: a created journey must not close the next one at once.
        val composer = entryViewModel("composer:${trip.id}:$composerOpenings") { feature.journeyComposer(trip.asPreviewTrip()) }
        JourneyComposerSheet(
            vm = composer,
            onCreated = { journeyTick++; toast = ToastItem(ToastType.success, strings.journeyCreated()) },
            onDismissRequest = { showComposer = false },
        )
    }
    if (state.publishRequested) {
        PublishTripSheet(cloudSyncEnabled = feature.cloudSyncEnabled(), onPublish = { vm.publish() }, onDismissRequest = { vm.cancelPublish() })
    }
    AppConfirm(
        item = state.confirmation,
        onDismissRequest = vm::cancelConfirmation,
        title = { strings.confirmationTitle(it) },
        message = { strings.confirmationMessage(it) },
        actions = { c -> listOf(AppDialogAction(strings.confirmationAction(c), AppDialogAction.Kind.destructive, testTag = "detail_confirm") { vm.acceptConfirmation(c.id) }) },
    )
    AppConfirm(
        item = photoToDelete,
        onDismissRequest = { photoToDelete = null },
        title = { strings.deletePhoto() },
        actions = { photo -> listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive, testTag = "detail_photo_delete_confirm") {
            deletePhoto(feature, vm, state, photo.id, scope, { toast = it }, strings.photoDeleted())
        }) },
    )
}

/** iOS: deleteOwnPhoto. The row and the file go, every cached size of it too; «Фото удалено». */
private fun deletePhoto(feature: TripsFeature, vm: TripDetailViewModel, state: TripDetailState, id: UUID, scope: kotlinx.coroutines.CoroutineScope, show: (ToastItem) -> Unit, message: String) {
    val trip = state.trip ?: return
    val local = state.photos.firstOrNull { it.id == id }?.filename
    scope.launch {
        runCatching { withContext(feature.worker) { feature.deletePhoto(id, trip.id) } }
            .onSuccess {
                local?.let { feature.photos.forget(PhotoSource.Local(it)) }
                vm.load()
                show(ToastItem(ToastType.success, message))
            }
    }
}

/**
 * iOS: TripDetailView.ownerActions. Publish or make private, open the journey or combine one,
 * the two exports, delete. Edit and «pin to profile» belong to screens not ported yet and are not
 * offered (a row that does nothing is worse than none).
 */
@Composable
private fun ownerActions(
    state: TripDetailState,
    journey: Journey?,
    vm: TripDetailViewModel,
    navigator: DestinationNavigator<TripsDestination>,
    onCombine: () -> Unit,
): List<ActionPopoverItem> {
    val strings = appStrings
    val trip = state.trip ?: return emptyList()
    return listOf(
        if (trip.isPrivate) ActionPopoverItem(strings.publishAction(), AppGlyph.globe, testTag = "detail_action_publish") { vm.requestPublish() }
        else ActionPopoverItem(strings.makePrivateAction(), AppGlyph.lock, testTag = "detail_action_publish") { vm.requestHide() },
        if (journey != null) ActionPopoverItem(strings.journeyOpen(), AppGlyph.suitcase, testTag = "detail_action_journey") { navigator.push(TripsDestination.JourneyDetail(journey.id)) }
        else ActionPopoverItem(strings.journeyCombine(), AppGlyph.suitcase, testTag = "detail_action_journey", action = onCombine),
        ActionPopoverItem(strings.exportGPX(), AppGlyph.squareAndArrowDown, testTag = "detail_action_export_gpx") { vm.export(TripExportFormat.gpx) },
        ActionPopoverItem(strings.exportCSV(), AppGlyph.tablecells, testTag = "detail_action_export_csv") { vm.export(TripExportFormat.csv) },
        ActionPopoverItem(strings.deleteTripAction(), AppGlyph.trash, isDestructive = true, testTag = "detail_action_delete") { vm.requestDelete() },
    )
}

/**
 * The hero: the live map once the push has landed and its first fitted camera settled, the static
 * route until then; while the fullscreen layer holds the map, the still route stands in for it.
 */
@Composable
private fun Hero(
    height: androidx.compose.ui.unit.Dp,
    state: TripDetailState,
    expansionPresented: Boolean,
    live: Boolean,
    rendered: Boolean,
    map: @Composable () -> Unit,
    onExpand: () -> Unit,
) {
    val strings = appStrings
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(0.dp))) {
        val coordinates = state.coordinates.ifEmpty { state.trip?.coordinates.orEmpty() }
        if (coordinates.size > 1) {
            if (!expansionPresented && live) {
                Box(Modifier.fillMaxSize()) { map() }
                Box(Modifier.fillMaxSize().mapTouchShield())
            }
            val reveal = rememberRevealOpacity(rendered && live)
            if (expansionPresented || reveal < 1f) RoutePlaceholder(coordinates, Modifier.fillMaxSize().alpha(if (expansionPresented) 1f else 1f - reveal))
            MapChromeButton(AppGlyph.arrowUpLeftAndArrowDownRight, strings.openRouteMapA11y(), onExpand,
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).testTag("detail_map_expand"))
        } else {
            RoutePlaceholder(coordinates, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun InfoPanel(
    now: java.time.Instant,
    state: TripDetailState,
    foreignVehicle: com.onezee.triptrack.model.SocialFeedVehicle?,
    vehicle: Vehicle?,
    vehicleKnown: Boolean,
    journey: Journey?,
    signedIn: Boolean,
    safeBottom: androidx.compose.ui.unit.Dp,
    onOpenJourney: (UUID) -> Unit,
    onConfirmDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onEnergyMode: (com.onezee.triptrack.model.TripEnergyMode) -> Unit,
    onSelectCheckpoint: (CheckpointState) -> Unit,
    onSelectSegment: (SegmentState) -> Unit,
    onOpenPhoto: (UUID) -> Unit,
    onOpenPhotoAt: (Int) -> Unit,
    onDeletePhoto: (DetailPhotoState) -> Unit,
    onAddPhotos: () -> Unit,
) {
    val trip = state.trip ?: return
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val lang = strings.language.code
    val loaded = !state.isLoading
    Column(
        Modifier.fillMaxWidth().background(AppTheme.colors.bg).padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = safeBottom + 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TripTitleBlock(trip)
            TripChipsRow(trip, state.isOwn, signedIn, vehicle, vehicleKnown, foreignVehicle = foreignVehicle)
            if (journey != null) {
                JourneyLinkRow(journey.title, journey.startDate, journey.endDate, onTap = { onOpenJourney(journey.id) }, modifier = Modifier.testTag("detail_journey_row"), now = now)
            }
        }
        if (state.isOwn && trip.confirmation == TripConfirmation.draft && loaded) DraftTripBanner(onConfirmDraft, onDiscardDraft)
        DetailSection(strings.detailsSection()) {
            if (loaded || state.coordinates.isNotEmpty()) TripStatsGrid(state, vehicle) else TripStatsSkeleton(trip)
            if (state.canChooseEnergyMode) EnergyModeRow(state.energyMode, state.energyEstimate?.electricMetres ?: 0.0, onEnergyMode)
        }
        if (state.showsMoments && loaded) {
            DetailSection(strings.tripMomentsTitle()) {
                TripMomentsTimeline(
                    moments = state.moments,
                    checkpoints = state.checkpoints,
                    segments = state.segments,
                    placeChips = state.placeChips,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    totalElapsed = trip.duration,
                    totalMetres = trip.distance,
                    onSelectCheckpoint = onSelectCheckpoint,
                    onOpenPhoto = { onOpenPhoto(it.id) },
                    onSelectSegment = onSelectSegment,
                )
            }
        }
        if (state.elevationSeries.size > 1) {
            DetailSection(strings.elevationProfile()) {
                ElevationChartCard(
                    state.elevationSeries,
                    summary = strings.chartMaxElev(Measure.elevation(state.maxAltitude, unit, lang, strings), Measure.elevation(state.elevationGain, unit, lang, strings)),
                    // iOS prints the raw column; it is in the phone's language there, the seeds' names are not.
                    leftLabel = com.onezee.triptrack.ui.RegionDisplay.localized(trip.region, strings.language).orEmpty(),
                    rightLabel = groupedDistance(trip.distance),
                )
            }
        }
        if (state.speedSeries.size > 1 && trip.source == TripOrigin.recorded) {
            val average = if (trip.averageSpeed > 0) trip.averageSpeed else if (trip.duration > 0) trip.distance / trip.duration else 0.0
            DetailSection(strings.speedSection()) {
                SpeedChartCard(state.speedSeries, strings.chartMaxAvg(Measure.speedValue(trip.maxSpeed, unit, lang), Measure.speedValue(average, unit, lang)))
            }
        }
        if (trip.source == TripOrigin.recorded && state.drivingTime + state.stoppedTime > 0) {
            DetailSection(strings.movingAndStops()) { MovingStopsCard(state.drivingTime, state.stoppedTime) }
        }
        trip.description?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
            DetailSection(strings.descriptionSection()) { DescriptionCard(text) }
        }
        if (loaded) TripPhotosSection(state.photos, state.isOwn, onOpenPhotoAt, onDeletePhoto, if (state.isOwn) onAddPhotos else null)
    }
}

/** iOS: TripDetailView.loadErrorState. Back, the rings, «Не удалось загрузить поездку», retry. */
@Composable
private fun LoadFailedState(onRetry: () -> Unit, onBack: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).testTag("trip_load_failed")) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp)) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier.size(34.dp).plainPressFeedback(interaction).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onBack() }
                    .semantics { contentDescription = strings.back() }
                    .testTag("detail_back"),
                contentAlignment = Alignment.Center,
            ) { Glyph(AppGlyph.chevronLeft, contentDescription = null, tint = Color.White, size = 17.dp) }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(108.dp).padding(bottom = 26.dp))
            Text(strings.tripLoadFailed(), style = inter(20f, InterWeight.bold), color = colors.text, textAlign = TextAlign.Center)
            Text(strings.tripLoadFailedBody(), style = inter(14f), color = colors.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp))
            Row(
                Modifier.padding(top = 22.dp).background(AppTheme.accent, RoundedCornerShape(50))
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.tap(); onRetry() }
                    .padding(horizontal = 22.dp, vertical = 13.dp)
                    .testTag("trip_load_retry"),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(AppGlyph.magnifyingglass, contentDescription = null, tint = Color.White, size = 16.dp)
                Text(strings.tryAgain(), style = inter(15f, InterWeight.bold), color = Color.White)
            }
        }
    }
}

/**
 * iOS: FullscreenMapSheet's chrome as the trip screen configures it: close at the top, zoom at the
 * bottom right, and the card of the marker that was tapped (a checkpoint's name and reading, or a
 * photo that opens on a second tap).
 */
@Composable
internal fun FullscreenChrome(
    state: TripDetailState,
    selectedMarker: String?,
    replay: TripReplayPlayer,
    followsCar: Boolean,
    onFollow: () -> Unit,
    onStopReplay: () -> Unit,
    visible: Boolean,
    onClose: () -> Unit,
    onZoom: (Double) -> Unit,
    onCloseCard: () -> Unit,
    onOpenPhoto: (UUID) -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxSize().alpha(if (visible) 1f else 0f).edgeScrims(top = true)) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                MapCircle(AppGlyph.xmark, strings.closeSheet(), "fullscreen_map_close", enabled = visible, onClick = onClose)
                Spacer(Modifier.weight(1f))
                if (replay.canPlay) ReplayCameraToggle(followsCar, visible) { haptics.tap(); onFollow() }
                }
                if (replay.canReplay) ReplayTimecode(replay.frame, replay.input, state.trip?.distance ?: 0.0, Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.iosShadow(RoundedCornerShape(12.dp), Color.Black.copy(alpha = 0.18f), radius = 6.dp, y = 2.dp)
                        .background(colors.card, RoundedCornerShape(12.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ZoomButton(AppGlyph.plus, strings.fullscreenMapZoomIn(), "fullscreen_map_plus", visible) { onZoom(1.0) }
                    Box(Modifier.width(26.dp).height(1.dp).background(colors.textTertiary.copy(alpha = colors.textTertiary.alpha * 0.25f)))
                    ZoomButton(AppGlyph.minus, strings.fullscreenMapZoomOut(), "fullscreen_map_minus", visible) { onZoom(-1.0) }
                }
            }
            val checkpoint = selectedMarker?.takeIf { it.startsWith("cp:") }?.removePrefix("cp:")?.let { id -> state.checkpoints.firstOrNull { it.id.toString() == id } }
            val pin = selectedMarker?.takeIf { it.startsWith("photo:") }?.removePrefix("photo:")?.let { id -> state.photoPins.firstOrNull { it.id.toString() == id } }
            if (checkpoint != null) {
                MarkerCard(Modifier.padding(top = 12.dp), onClose = onCloseCard) {
                    val cover = checkpoint.photos.firstOrNull()
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(AppTheme.accent), contentAlignment = Alignment.Center) {
                        if (cover != null) PhotoImage(PhotoSource.Local(cover.filename), maxPixels = 200, modifier = Modifier.size(56.dp))
                        else Glyph(AppGlyph.flagFill, contentDescription = null, tint = Color.White, size = 22.dp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(checkpoint.displayName(strings), style = inter(16f, InterWeight.bold), color = colors.text, maxLines = 1)
                        Text(CheckpointReading.text(checkpoint.elapsed, checkpoint.metres, unit, strings), style = inter(13f, InterWeight.semibold, monospacedDigits = true),
                            color = colors.textSecondary, maxLines = 1)
                    }
                }
            } else if (pin != null) {
                PressableCard(onClick = { onOpenPhoto(pin.id) }, modifier = Modifier.padding(top = 12.dp).testTag("map_photo_preview")) {
                    MarkerCard(Modifier, onClose = onCloseCard) {
                        PhotoImage(PhotoSource.Local(pin.filename), maxPixels = 600, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (pin.elapsed != null && pin.metres != null) {
                                Text(CheckpointReading.text(pin.elapsed, pin.metres, unit, strings), style = inter(15f, InterWeight.bold, monospacedDigits = true),
                                    color = colors.text, maxLines = 1)
                            }
                            Text(strings.openPhotoAction(), style = inter(13f, InterWeight.semibold), color = AppTheme.accent)
                        }
                    }
                }
            }
            if (replay.canPlay) TripReplayControls(replay.frame, state.isOwn,
                onPlayPause = { haptics.tap(); replay.playPause() }, onStop = { haptics.tap(); onStopReplay() },
                onRate = { haptics.selection(); replay.change { cycleRate() } }, onScrubStart = replay::scrubStart,
                onScrub = replay::scrub, onScrubEnd = replay::scrubEnd, modifier = Modifier.padding(top = 12.dp), crawl = replay.canCrawl, enabled = visible)
        }
        if (followsCar) ReplaySpeedBubble(replay.frame, Modifier.align(Alignment.Center).offset(y = (-46).dp))
    }
}

@Composable
private fun MarkerCard(modifier: Modifier, onClose: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val close = appStrings.closeSheet()
    Row(
        modifier.fillMaxWidth().iosShadow(shape, Color.Black.copy(alpha = 0.22f), radius = 14.dp, y = 4.dp).background(colors.card, shape).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Box(
            Modifier.size(32.dp).background(colors.cardAlt, CircleShape)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClose)
                .semantics { contentDescription = close },
            contentAlignment = Alignment.Center,
        ) { Glyph(AppGlyph.xmark, contentDescription = null, tint = colors.textSecondary, size = 14.dp) }
    }
}

@Composable
private fun MapCircle(glyph: AppGlyph, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(40.dp).plainPressFeedback(interaction)
            .iosShadow(CircleShape, Color.Black.copy(alpha = 0.18f), radius = 6.dp, y = 2.dp)
            .background(AppTheme.colors.card, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, contentDescription = null, tint = AppTheme.colors.text, size = 18.dp) }
}

@Composable
private fun ZoomButton(glyph: AppGlyph, label: String, tag: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(44.dp, 42.dp).plainPressFeedback(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, contentDescription = null, tint = AppTheme.colors.text, size = 18.dp) }
}

/**
 * iOS: PublishTripSheet (TripDetailSections.swift). «Опубликовать поездку?», what publishing means
 * (and that the trip leaves the phone even with Cloud Sync off), Cancel and Publish.
 */
@Composable
private fun PublishTripSheet(cloudSyncEnabled: Boolean, onPublish: () -> Unit, onDismissRequest: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.card) {
        val sheet = this
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 20.dp).testTag("publish_sheet"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings.publishTripTitle(), style = inter(19f, InterWeight.bold), color = colors.text)
            Text(strings.publishConfirmBody() + if (!cloudSyncEnabled) "\n\n" + strings.publishConfirmCloudOff() else "", style = inter(14f), color = colors.textSecondary)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    strings.cancel(), style = inter(15f, InterWeight.bold), color = colors.text,
                    modifier = Modifier.background(colors.cardAlt, RoundedCornerShape(50))
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.tap(); sheet.dismiss() }
                        .padding(horizontal = 22.dp, vertical = 15.dp).testTag("publish_cancel"),
                )
                Box(
                    Modifier.weight(1f).background(AppTheme.accent, RoundedCornerShape(50))
                        .clickable(interactionSource = null, indication = null, role = Role.Button) { haptics.action(); onPublish(); sheet.dismiss() }
                        .padding(vertical = 15.dp).testTag("publish_confirm"),
                    contentAlignment = Alignment.Center,
                ) { Text(strings.publishAction(), style = inter(15f, InterWeight.bold), color = Color.White) }
            }
        }
    }
}

// iOS: TripTrackShared/VehicleAvatar.swift, swatch; MapCarMarker.paint clamps brightness later.
private fun replayCarColor(avatar: String?): Int = (when (avatar?.let(VehicleAvatar::drawnColor)) {
    "white" -> 0xD4D9E3
    "black" -> 0x2A2C33
    "silver" -> 0xA2A8B4
    "gray" -> 0x707580
    "red" -> 0xE03B2C
    "blue" -> 0x3B7DD8
    "yellow" -> 0xF2C41B
    "green" -> 0x4CAF50
    else -> 0xEB571E
}) or 0xFF000000.toInt()
