// iOS: AutoRecordSettingsView.swift, BluetoothScanSheet.swift, HomeSettingsView.swift
package com.onezee.triptrack.feature.settings

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.map.MapHost
import com.onezee.triptrack.map.MapHostState
import com.onezee.triptrack.map.camera.MapCameraCommand
import com.onezee.triptrack.model.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AutoRecordSettingsContent(graph: AppGraph, state: SettingsState, onBack: () -> Unit, vehicleId: UUID? = null, onStereo: (() -> Unit)? = null) {
    val manager = graph.settingsManager
    val context = LocalContext.current
    AutoRecordSettingsScreen(state, onBack, manager::setAutoRecordTrigger,
        onMode = { manager.setAutoRecord(it) },
        onTimeout = { manager.setAutoRecord(state.autoRecordMode, it) },
        onUnlink = { manager.removeStereo(it) },
        onStereo = { onStereo?.invoke() ?: manager.openSheet(SettingsSheet.stereo) }, vehicleId = vehicleId,
        onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) })
}

// iOS: AutoRecordSettingsView.swift. State and callbacks also drive the screenshot fixtures.
@Composable
internal fun AutoRecordSettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onTrigger: (AutoRecordTrigger) -> Unit,
    onMode: (AutoRecordMode) -> Unit,
    onTimeout: (Int) -> Unit,
    onUnlink: (String) -> Unit,
    onStereo: () -> Unit,
    vehicleId: UUID? = null,
    onSettings: (() -> Unit)? = null,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val description = if (state.autoRecordTrigger == AutoRecordTrigger.stereoOnly)
        strings.autoRecordStereoOnlyDescription() else strings.autoRecordDescription()
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        CustomNavBar(strings.autoRecord(), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                MeToggle(strings.autoRecord(), state.autoRecordMode != AutoRecordMode.off,
                    { onMode(if (it) AutoRecordMode.remind else AutoRecordMode.off) }, tag = "auto_record_toggle")
                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(colors.border))
                Text(description, Modifier.padding(14.dp), style = inter(13f), color = colors.textSecondary)
            }
            if (state.autoRecordMode != AutoRecordMode.off) {
                AutoRecordSectionLabel(strings.stereoSection())
                Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                    state.stereos.filter { vehicleId == null || it.vehicleId == vehicleId }.forEach { stereo ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stereo.name, Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text, maxLines = 1)
                            Text(strings.linked(), Modifier.padding(horizontal = 8.dp), style = inter(11f, InterWeight.bold), color = AppTheme.green)
                            Box(Modifier.size(40.dp).clickable(role = Role.Button) { onUnlink(stereo.uuid) }
                                .semantics { contentDescription = strings.delete() }.testTag("auto_record_unlink_${stereo.uuid}"), contentAlignment = Alignment.Center) {
                                Glyph(AppGlyph.xmarkCircleFill, null, tint = colors.textTertiary, size = 17.dp)
                            }
                        }
                        Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(colors.cardAlt))
                    }
                    MeRow(strings.linkStereo(), tag = "auto_record_stereo", glyph = AppGlyph.plus, onClick = onStereo)
                }
                AutoRecordSectionLabel(strings.autoRecordTriggerSection())
                Row(Modifier.fillMaxWidth().selectableGroup().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AutoRecordSegment(strings.autoRecordTriggerMotion(), state.autoRecordTrigger == AutoRecordTrigger.motionOrStereo,
                        true, "auto_record_trigger_motion", Modifier.weight(1f)) { onTrigger(AutoRecordTrigger.motionOrStereo) }
                    // Unlinking must leave the selected mode visibly selected; the caption explains why it cannot start.
                    AutoRecordSegment(strings.autoRecordTriggerStereo(), state.autoRecordTrigger == AutoRecordTrigger.stereoOnly,
                        state.stereos.isNotEmpty() || state.autoRecordTrigger == AutoRecordTrigger.stereoOnly,
                        "auto_record_trigger_stereo", Modifier.weight(1f)) { onTrigger(AutoRecordTrigger.stereoOnly) }
                }
                val caption = when (state.autoRecordTrigger) {
                    AutoRecordTrigger.motionOrStereo -> if (state.stereos.isEmpty()) strings.autoRecordTriggerNeedsStereo() else strings.autoRecordTriggerMotionCaption()
                    AutoRecordTrigger.stereoOnly -> if (state.stereos.isEmpty()) strings.autoRecordTriggerStereoMissing() else strings.autoRecordTriggerStereoCaption()
                }
                AutoRecordCaption(caption)
                AutoRecordSectionLabel(strings.autoRecordMode())
                Row(Modifier.fillMaxWidth().selectableGroup().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AutoRecordSegment(strings.autoRecordRemind(), state.autoRecordMode == AutoRecordMode.remind,
                        true, "auto_record_mode_remind", Modifier.weight(1f)) { onMode(AutoRecordMode.remind) }
                    AutoRecordSegment(strings.autoRecordAuto(), state.autoRecordMode == AutoRecordMode.auto,
                        true, "auto_record_mode_auto", Modifier.weight(1f)) { onMode(AutoRecordMode.auto) }
                }
                AutoRecordCaption(if (state.autoRecordMode == AutoRecordMode.auto) strings.autoModeDescription() else strings.remindModeDescription())
                AutoRecordSectionLabel(strings.autoStopTimeout())
                Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.autoStopRowLabel(), Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text)
                    AutoRecordStep(false, state.autoStopTimeout > 1, strings.autoStopMinutes((state.autoStopTimeout - 1).coerceAtLeast(1))) { onTimeout((state.autoStopTimeout - 1).coerceAtLeast(1)) }
                    Text(strings.autoStopMinutes(state.autoStopTimeout), Modifier.width(54.dp), style = inter(15f, InterWeight.bold).copy(fontFeatureSettings = "tnum"), color = colors.text, textAlign = TextAlign.Center)
                    AutoRecordStep(true, state.autoStopTimeout < 10, strings.autoStopMinutes((state.autoStopTimeout + 1).coerceAtMost(10))) { onTimeout((state.autoStopTimeout + 1).coerceAtMost(10)) }
                }
                AutoRecordCaption(strings.autoStopDescription())
                if (onSettings != null) MeRow(strings.openSettings(), onClick = onSettings)
            }
        }
    }
}

@Composable
private fun AutoRecordSectionLabel(text: String) {
    Text(text, Modifier.padding(top = 6.dp, start = 2.dp), style = inter(12f, InterWeight.semibold), color = AppTheme.colors.textSecondary)
}

@Composable
private fun AutoRecordCaption(text: String) {
    Text(text, Modifier.padding(horizontal = 2.dp), style = inter(12f), color = AppTheme.colors.textTertiary)
}

@Composable
private fun AutoRecordSegment(title: String, selected: Boolean, enabled: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Box(modifier.alpha(if (enabled) 1f else .45f).testTag(tag)
        .then(if (selected) Modifier.surfaceCard(9.dp) else Modifier)
        .selectable(selected, enabled = enabled, role = Role.RadioButton) { haptics.selection(); onClick() }
        .padding(horizontal = 8.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
        Text(title, style = inter(14f, if (selected) InterWeight.bold else InterWeight.medium),
            color = if (selected) colors.text else colors.textSecondary, maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = inter(10.5f).fontSize, maxFontSize = inter(14f).fontSize))
    }
}

@Composable
private fun AutoRecordStep(plus: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Box(Modifier.size(32.dp).background(colors.cardAlt, CircleShape).clickable(enabled = enabled, role = Role.Button) { haptics.tap(); onClick() }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(15.dp)) {
            val ink = if (enabled) colors.text else colors.textTertiary
            drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            if (plus) drawLine(ink, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2.dp.toPx())
        }
    }
}

@Composable
internal fun StereoPicker(graph: AppGraph, onBack: () -> Unit, vehicleId: UUID? = null) {
    val context = LocalContext.current
    val strings = appStrings
    val state by graph.settingsManager.state.collectAsState()
    val garage by graph.me.garage.state.collectAsState()
    var selected by remember { mutableStateOf(vehicleId ?: state.selectedVehicleId) }
    var allowed by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            refresh++
        } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it; refresh++ }
    val devices by produceState<List<SavedBluetoothDevice>>(emptyList(), allowed, refresh) {
        value = if (allowed) withContext(Dispatchers.IO) {
            try { context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .filter { it.bluetoothClass?.majorDeviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO }
                .map { SavedBluetoothDevice(it.address, it.name.orEmpty()) }.filter { it.name.isNotBlank() }.sortedBy { it.name } }
            catch (_: SecurityException) { emptyList() }
        } else emptyList()
    }
    val outputs by produceState<List<SavedBluetoothDevice>>(emptyList(), graph) {
        graph.location.carStereos.changes.collect {
            value = withContext(Dispatchers.IO) { graph.location.carStereos.outputs() }
        }
    }
    val linkable = (outputs + devices).distinctBy { it.uuid.uppercase(java.util.Locale.ROOT) }
    SettingsDetail(strings.linkStereo(), onBack) {
        garage.vehicles.filter { it.vehicle.soldAt == null }.forEach { item ->
            MeRow(item.vehicle.name, if (selected == item.vehicle.id) "✓" else "", onClick = { selected = item.vehicle.id })
        }
        if (!allowed) MeAction(strings.continueButton(), { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) })
        if (linkable.isEmpty()) MeRow(strings.noDevicesFound(), subtitle = strings.scanHint())
        linkable.forEach { device ->
            StereoOutputRow(device, state.stereos.any { it.uuid.equals(device.uuid, ignoreCase = true) && it.vehicleId == selected },
                enabled = selected != null) {
                selected?.let { id -> graph.settingsManager.addStereo(device.copy(vehicleId = id)); onBack() }
            }
        }
        MeRow(strings.openSettings(), onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); refresh++ })
    }
}

// iOS: BluetoothScanSheet.swift, audioOutputSection. A Bluetooth output may be headphones.
@Composable
internal fun StereoOutputRow(device: SavedBluetoothDevice, linked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val strings = appStrings
    MeRow(device.name, if (linked) strings.linked() else "",
        subtitle = if (device.uuid == com.onezee.triptrack.location.auto.AutoTripController.ANDROID_AUTO) strings.androidAuto() else strings.bluetoothAudio(),
        enabled = enabled, tag = "stereo_${device.name}", onClick = onClick)
}

@Composable
internal fun HomeSettingsContent(home: HomeState, unit: DistanceUnit, onBack: () -> Unit, onChange: (HomeState) -> Unit) {
    val strings = appStrings
    var choosing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    SettingsDetail(strings.homeSheetTitle(), onBack) {
        MeRow(strings.homeRow(), if (home.coordinate == null) strings.homeNotSet() else strings.homeVisibleOnlyToYou(), onClick = { choosing = true })
        MeToggle(strings.homeShowOnMap(), home.showsOnMap, { onChange(home.copy(showsOnMap = it)) }, strings.homeVisibleOnlyToYou(), enabled = home.coordinate != null)
        MeToggle(strings.homeZoneToggle(), home.trimsPublicTracks, { onChange(home.copy(trimsPublicTracks = it)) }, strings.homeZoneNote(), enabled = home.coordinate != null)
        HomeSettings.Radius.entries.forEach { radius ->
            MeRow(Measure.radius(radius.rawValue.toDouble(), unit, strings.language.code, strings), if (radius == home.radius) "✓" else "", tag = "home_radius_${radius.rawValue}", onClick = { onChange(home.copy(radius = radius)) })
        }
        if (home.coordinate != null) MeRow(strings.homeRemove(), destructive = true, tag = "home_remove", onClick = { removing = true })
    }
    if (choosing) ContentSizedSheet({ choosing = false }, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        MeSheetHeader(strings.homeSheetTitle()) { dismiss() }
        val map = remember { MapHostState() }
        LaunchedEffect(Unit) { map.execute(MapCameraCommand.Move(home.coordinate ?: GeoPoint(0.0, 0.0), if (home.coordinate == null) 2.0 else 14.0, false)) }
        Box(Modifier.weight(1f)) {
            MapHost(map, Modifier.fillMaxSize(), systemInsets = WindowInsets(0))
            Text("⌖", Modifier.align(Alignment.Center), style = inter(38f), color = AppTheme.accent)
        }
        Text(strings.homePlaceHint(), Modifier.padding(16.dp), style = AppType.meta, color = AppTheme.colors.textSecondary)
        MeAction(strings.save(), { map.camera?.centre?.let { onChange(home.copy(coordinate = it)); choosing = false } }, Modifier.padding(16.dp), enabled = map.camera != null)
    }
    if (removing) AppConfirmDialog(strings.homeRemoveTitle(), strings.homeRemoveBody(),
        actions = listOf(AppDialogAction(strings.homeRemove(), AppDialogAction.Kind.destructive) { onChange(home.copy(coordinate = null, trimsPublicTracks = false)) }),
        cancelTitle = strings.cancel(), onDismiss = { removing = false })
}
