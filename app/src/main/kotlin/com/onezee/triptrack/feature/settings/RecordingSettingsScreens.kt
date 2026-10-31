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
    val strings = appStrings
    var modes by remember { mutableStateOf(false) }
    SettingsDetail(strings.autoRecord(), onBack) {
        MeRow(strings.autoRecordMode(), autoRecordLabel(state.autoRecordMode, strings), tag = "auto_record_mode", onClick = { modes = true })
        MeRow(strings.autoRecordDescription())
        MeRow(strings.autoStopTimeout(), strings.autoStopMinutes(state.autoStopTimeout))
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MeAction("−", { manager.setAutoRecord(state.autoRecordMode, (state.autoStopTimeout - 1).coerceAtLeast(1)) }, Modifier.weight(1f), state.autoStopTimeout > 1)
            MeAction("+", { manager.setAutoRecord(state.autoRecordMode, (state.autoStopTimeout + 1).coerceAtMost(10)) }, Modifier.weight(1f), state.autoStopTimeout < 10)
        }
        MeRow(strings.autoStopDescription())
        state.stereos.filter { vehicleId == null || it.vehicleId == vehicleId }.forEach { stereo ->
            MeRow(stereo.name, strings.linked(), onClick = { manager.removeStereo(stereo.uuid) })
        }
        MeRow(strings.linkStereo(), tag = "auto_record_stereo", onClick = { onStereo?.invoke() ?: manager.openSheet(SettingsSheet.stereo) })
        val context = LocalContext.current
        MeRow(strings.openSettings(), onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) })
    }
    if (modes) SettingsOptionPicker(strings.autoRecordMode(), AutoRecordMode.entries, state.autoRecordMode, strings.autoRecordDescription(),
        label = { autoRecordLabel(it, strings) }, onSelect = { manager.setAutoRecord(it) }, onDismissRequest = { modes = false }, testTagPrefix = "auto_record_mode")
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
    SettingsDetail(strings.linkStereo(), onBack) {
        garage.vehicles.filter { it.vehicle.soldAt == null }.forEach { item ->
            MeRow(item.vehicle.name, if (selected == item.vehicle.id) "✓" else "", onClick = { selected = item.vehicle.id })
        }
        if (!allowed) MeAction(strings.continueButton(), { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) })
        else {
            if (devices.isEmpty()) MeRow(strings.noDevicesFound(), subtitle = strings.scanHint())
            devices.forEach { device -> MeRow(device.name, if (state.stereos.any { it.uuid == device.uuid && it.vehicleId == selected }) strings.linked() else "",
                enabled = selected != null, tag = "stereo_${device.name}", onClick = { selected?.let { id -> graph.settingsManager.addStereo(device.copy(vehicleId = id)); onBack() } }) }
            MeRow(strings.openSettings(), onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); refresh++ })
        }
    }
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
            MapHost(map, Modifier.fillMaxSize())
            Text("⌖", Modifier.align(Alignment.Center), style = inter(38f), color = AppTheme.accent)
        }
        Text(strings.homePlaceHint(), Modifier.padding(16.dp), style = AppType.meta, color = AppTheme.colors.textSecondary)
        MeAction(strings.save(), { map.camera?.centre?.let { onChange(home.copy(coordinate = it)); choosing = false } }, Modifier.padding(16.dp), enabled = map.camera != null)
    }
    if (removing) AppConfirmDialog(strings.homeRemoveTitle(), strings.homeRemoveBody(),
        actions = listOf(AppDialogAction(strings.homeRemove(), AppDialogAction.Kind.destructive) { onChange(home.copy(coordinate = null, trimsPublicTracks = false)) }),
        cancelTitle = strings.cancel(), onDismiss = { removing = false })
}
