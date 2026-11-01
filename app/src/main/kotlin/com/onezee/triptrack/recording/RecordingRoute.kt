// iOS: TrackingView.swift, MapViewModel.swift. The platform subscriptions live only while visible.
package com.onezee.triptrack.recording

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.me.MeTab
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.location.permissions.*
import com.onezee.triptrack.location.provider.PlatformLocationSource
import com.onezee.triptrack.location.provider.TrackingMode
import com.onezee.triptrack.location.recording.StartRefusal
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun RecordingRoute(graph: AppGraph, resumeTick: Int, onClose: () -> Unit) {
    val presenter = graph.recording
    val state by presenter.state.collectAsState()
    val settings by graph.settingsManager.state.collectAsState()
    val garage by graph.me.garage.state.collectAsState()
    val outcome by graph.location.recorder.completion.collectAsState()
    val strings = appStrings
    val context = LocalContext.current
    var permissionRevision by remember { mutableIntStateOf(0) }
    val precise = remember(resumeTick, permissionRevision) { graph.location.hasLocationPermission() }
    var picking by remember { mutableStateOf(false) }
    var healthOpen by remember { mutableStateOf(false) }
    var permissionOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var garageOpen by remember { mutableStateOf(false) }
    var pickingBusy by remember { mutableStateOf(false) }
    var pickingFailed by remember { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Instant.now()) }
    val map = remember { MapHostState() }
    var cameraMode by remember { mutableStateOf(TrackingCameraMode.follow) }
    var hudHeight by remember { mutableDoubleStateOf(0.0) }
    val selected = if (state.recorder.isRecording) state.recorder.vehicleId else settings.selectedVehicleId
    val vehicles = garage.vehicles.map { it.vehicle }
    val vehicle = vehicles.firstOrNull { it.id == selected && (state.recorder.isRecording || (!it.isArchived && it.soldAt == null)) }
    val health = remember(resumeTick, permissionRevision, settings.autoRecordMode) { RecordingHealth.read(context, settings.autoRecordMode != AutoRecordMode.off) }
    var fog by remember { mutableStateOf<RevealedLayer?>(null) }
    LaunchedEffect(graph) {
        RoomWorldChanges(graph.database).events.collect { change ->
            if (change == WorldChange.revealed || change == WorldChange.sync) fog = graph.revealedLayer.layer()
        }
    }
    LaunchedEffect(Unit) { presenter.refresh(); fog = graph.revealedLayer.layer() }
    LaunchedEffect(state.recorder.isRecording) {
        if (state.recorder.isRecording) cameraMode = TrackingCameraMode.follow
        while (state.recorder.isRecording) { now = Instant.now(); delay(1000) }
    }
    val haptics = rememberHaptics()
    LaunchedEffect(state.refusal) {
        state.refusal?.let { haptics.error(); delay(if (it == StartRefusal.noFix) 8000 else 4000); presenter.clearRefusal() }
    }
    RecordingLocationEffect(graph, precise, resumeTick)
    val discarded = (outcome as? StopOutcome.Discarded)?.let {
        if (it.reason == com.onezee.triptrack.model.TripJunkClassifier.Reason.noDrivingSpeed) strings.recordingDiscardedSlow() else strings.junkTripDiscarded()
    }
    fun openLocation() {
        if (RecordingPermission.fineLocation in graph.location.permissionLedger.asked()) {
            HealthSettings.open(context, HealthSettings.targets(context, HealthIssue.noPreciseLocation, health.oem))
        } else permissionOpen = true
    }
    TrackingScreen(state, vehicle, !precise, TrackingActions(
        back = onClose,
        start = { if (precise) presenter.start() else openLocation() }, blocked = presenter::blockedStart,
        startAnyway = { presenter.start(force = true) }, pause = presenter::pause, checkpoint = presenter::checkpoint,
        finish = presenter::askFinish, vehicle = { picking = true }, transfer = presenter::setTransfer,
        health = { healthOpen = true }, locate = { cameraMode = cameraMode.next() },
        zoom = { zoomIn -> map.camera?.let { camera -> map.execute(MapCameraCommand.Move(camera.centre, (camera.zoom + if (zoomIn) 1 else -1).coerceIn(1.0, 19.0), true)) } },
    ), now, healthHint = health.issues.firstOrNull()?.message?.let(strings::tr), cameraMode = cameraMode, discarded = discarded,
        onHudHeightChanged = { hudHeight = it },
        map = { TrackingMap(state, vehicle, fog, map, cameraMode, { cameraMode = TrackingCameraMode.none }, hudHeight + 24.0, Modifier.fillMaxSize()) })
    state.stopPreview?.let { preview ->
        StopConfirmationSheet(preview, state.recorder.isPaused, state.busy, presenter::finish, presenter::pauseInstead, presenter::cancelFinish)
    }
    if (picking) VehiclePickerSheet(vehicles, selected, if (state.recorder.isRecording) state.recorder.isTransfer else state.pendingTransfer,
        onPick = { id ->
            if (!pickingBusy) {
                pickingBusy = true
                screenScope.launch {
                    try {
                        if (graph.settingsManager.selectRecordingVehicle(id)) {
                            withContext(graph.location.scope.coroutineContext.minusKey(kotlinx.coroutines.Job)) { graph.location.recorder.assignVehicle(id) }
                            presenter.setTransfer(false)
                            picking = false
                        } else pickingFailed = true
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { pickingFailed = true }
                    finally { pickingBusy = false }
                }
            }
        }, onTransfer = {
            if (!pickingBusy) {
                pickingBusy = true
                screenScope.launch {
                    try {
                        if (state.recorder.isRecording) withContext(graph.location.scope.coroutineContext.minusKey(kotlinx.coroutines.Job)) { graph.location.recorder.assignVehicle(null, transfer = true) }
                        else presenter.setTransfer(true)
                        picking = false
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { pickingFailed = true }
                    finally { pickingBusy = false }
                }
            }
        }, onGarage = { if (!pickingBusy) { picking = false; garageOpen = true } }, onDismiss = { if (!pickingBusy) picking = false }, busy = pickingBusy, failed = pickingFailed)
    if (garageOpen) {
        HideAppTabBar()
        Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { MeTab(graph, resumeTick, garageOnly = true, onGarageBack = { garageOpen = false }) }
    }
    if (permissionOpen) RecordingPermissionSheet(graph.location, resumeTick, onComplete = { permissionOpen = false; permissionRevision++ })
    if (healthOpen) RecordingHealthSheet(health, onDismiss = { healthOpen = false }, onPermission = { healthOpen = false; permissionOpen = true },
        onStereo = { healthOpen = false; graph.settingsManager.openSheet(SettingsSheet.autoRecord); settingsOpen = true })
    if (settingsOpen) SettingsRoute(graph) { settingsOpen = false }
    if (state.failed || pickingFailed) ToastHost(remember(state.failed, pickingFailed) { ToastItem(ToastType.error, strings.manualFailedDbTitle()) }, { presenter.clearError(); pickingFailed = false })
}

@Composable
private fun RecordingLocationEffect(graph: AppGraph, allowed: Boolean, resumeTick: Int) {
    val recording by graph.location.recorder.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    DisposableEffect(graph, allowed, recording.isRecording, resumeTick, lifecycle) {
        val source = PlatformLocationSource(context)
        fun update() {
            if (allowed && !recording.isRecording && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                source.start(TrackingMode.idle) { fix, age -> graph.location.scope.launch { graph.location.recorder.onProviderFix(fix, age) } }
            } else source.stop()
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer)
        update()
        onDispose { lifecycle.removeObserver(observer); source.stop() }
    }
}
