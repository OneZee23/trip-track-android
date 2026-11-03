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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.onezee.triptrack.feature.account.HouseSwitch
import com.onezee.triptrack.feature.me.ProfileSectionLabel
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
    AutoRecordSettingsScreen(state, onBack, manager::setAutoRecordTrigger,
        onMode = { manager.setAutoRecord(it) },
        onTimeout = { manager.setAutoRecord(state.autoRecordMode, it) },
        onUnlink = { manager.removeStereo(it) },
        onStereo = { onStereo?.invoke() ?: manager.openSheet(SettingsSheet.stereo) }, vehicleId = vehicleId)
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
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val on = state.autoRecordMode != AutoRecordMode.off
    val description = if (state.autoRecordTrigger == AutoRecordTrigger.stereoOnly)
        strings.autoRecordStereoOnlyDescription() else strings.autoRecordDescription()
    Column(Modifier.fillMaxSize().background(colors.bg)) {
        CustomNavBar(strings.autoRecord(), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            // iOS toggleCard: the antenna (accent while it is on), the title and a full-size switch over a hairline
            // and the description, 14 inside a card of 16.
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().testTag("auto_record_toggle")
                    .toggleable(on, role = Role.Switch) { onMode(if (it) AutoRecordMode.remind else AutoRecordMode.off) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(AppGlyph.antennaRadiowaves, null, tint = if (on) AppTheme.accent else colors.textTertiary, size = 21.6.dp)
                    Text(strings.autoRecord(), Modifier.weight(1f), style = interScaled(15f, InterWeight.semibold), color = colors.text)
                    Box(Modifier.clearAndSetSemantics {}) { HouseSwitch(on, { onMode(if (it) AutoRecordMode.remind else AutoRecordMode.off) }) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
                Text(description, style = inter(13f), color = colors.textSecondary)
            }
            if (on) {
                AutoRecordSectionLabel(strings.stereoSection())
                Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                    state.stereos.filter { vehicleId == null || it.vehicleId == vehicleId }.forEach { stereo ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 3.dp, bottom = 3.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(AppGlyph.musicNote, null, tint = AppTheme.accent, size = 20.4.dp)
                            Text(stereo.name, Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text, maxLines = 1)
                            LinkedCapsule(strings.linked())
                            Box(Modifier.size(40.dp).clickable(role = Role.Button) { onUnlink(stereo.uuid) }
                                .semantics { contentDescription = strings.delete() }.testTag("auto_record_unlink_${stereo.uuid}"), contentAlignment = Alignment.Center) {
                                Glyph(AppGlyph.xmarkCircleFill, null, tint = colors.textTertiary, size = 20.4.dp)
                            }
                        }
                        Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(colors.cardAlt))
                    }
                    // iOS: an accent text button, «+» and the words, no tile and no chevron.
                    val haptics = rememberHaptics()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { haptics.tap(); onStereo() }.padding(horizontal = 14.dp, vertical = 13.dp).testTag("auto_record_stereo"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(AppGlyph.plus, null, tint = AppTheme.accent, size = 16.8.dp)
                        Text(strings.linkStereo(), style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
                    }
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
            }
        }
    }
}

/** iOS: the heading of a section of the auto-record screen is `GarageSectionLabel`, the heading of «Я»: 16 bold ink. */
@Composable
private fun AutoRecordSectionLabel(text: String) {
    ProfileSectionLabel(text, Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
}

/** iOS: the green «Привязана» / «Добавлена» capsule: 11 bold green on green at 14 %, 8 and 4 of padding. */
@Composable
internal fun LinkedCapsule(text: String) {
    Text(text, Modifier.background(AppTheme.green.copy(alpha = 0.14f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp), style = inter(11f, InterWeight.bold), color = AppTheme.green, maxLines = 1)
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
    // iOS: «Текущий аудиовыход» is what the phone plays through now, «Устройства рядом» what it could pair with.
    val currentIds = outputs.mapTo(mutableSetOf()) { it.uuid.uppercase(java.util.Locale.ROOT) }
    StereoPickerContent(
        // A stereo belongs to one vehicle: from a vehicle's own screen it is that one; from the recording screen
        // the person says which (the iPhone's sheet is always opened for one).
        vehicles = if (vehicleId == null) garage.vehicles.map { it.vehicle }.filter { it.soldAt == null } else null,
        selected = selected, onSelect = { selected = it },
        allowed = allowed, onAllow = { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        outputs = outputs, nearby = devices.filter { it.uuid.uppercase(java.util.Locale.ROOT) !in currentIds },
        isLinked = { device -> state.stereos.any { it.uuid.equals(device.uuid, ignoreCase = true) && it.vehicleId == selected } },
        onLink = { device -> selected?.let { id -> graph.settingsManager.addStereo(device.copy(vehicleId = id)); onBack() } },
        onRefresh = { refresh++ },
        // Android pairs a car stereo in the system's Bluetooth settings; the phone cannot find classic devices itself.
        onOpenSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); refresh++ },
        onBack = onBack,
    )
}

/**
 * iOS BluetoothScanSheet.scanContent: «Привязать магнитолу» 17 bold with the grey close disc (16 from the sides and the top, 8
 * below), then 8 above the cards, 10 between them and 32 under the last. [vehicles] is null on a vehicle's own screen, where the
 * stereo is that vehicle's; elsewhere the person says which. Nothing here reads the phone: the picker above gathers the state.
 */
@Composable
internal fun StereoPickerContent(
    vehicles: List<Vehicle>?, selected: UUID?, onSelect: (UUID) -> Unit,
    allowed: Boolean, onAllow: () -> Unit,
    outputs: List<SavedBluetoothDevice>, nearby: List<SavedBluetoothDevice>,
    isLinked: (SavedBluetoothDevice) -> Boolean, onLink: (SavedBluetoothDevice) -> Unit,
    onRefresh: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().testTag("stereo_picker")) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.linkStereo(), Modifier.weight(1f).semantics { heading() }, style = inter(17f, InterWeight.bold), color = colors.text)
            SheetCloseCircle(onClick = onBack)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (vehicles != null) StereoSection(strings.myVehicle()) {
                vehicles.forEachIndexed { index, vehicle ->
                    if (index > 0) StereoDivider()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { haptics.selection(); onSelect(vehicle.id) }.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vehicle.name, Modifier.weight(1f), style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
                        if (selected == vehicle.id) Glyph(AppGlyph.checkmark, null, tint = AppTheme.accent, size = 18.dp)
                    }
                }
            }
            if (!allowed) MeAction(strings.continueButton(), onAllow)
            if (outputs.isNotEmpty()) StereoSection(strings.currentAudioOutput()) {
                outputs.forEachIndexed { index, device ->
                    if (index > 0) StereoDivider()
                    StereoOutputRow(device, isLinked(device), enabled = selected != null) { onLink(device) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileSectionLabel(strings.nearbyDevices(), Modifier.weight(1f))
                    Box(Modifier.size(24.dp).clickable(role = Role.Button) { haptics.tap(); onRefresh() }.semantics { contentDescription = strings.tryAgain() }, contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.arrowClockwise, null, tint = AppTheme.accent, size = 15.6.dp)
                    }
                }
                StereoCard {
                    if (nearby.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Glyph(AppGlyph.antennaRadiowaves, null, tint = colors.textTertiary, size = 24.dp)
                        Text(strings.noDevicesFound(), style = inter(13f), color = colors.textTertiary)
                    }
                    nearby.forEachIndexed { index, device ->
                        if (index > 0) StereoDivider()
                        NearbyDeviceRow(device, isLinked(device), enabled = selected != null) { onLink(device) }
                    }
                }
            }
            Text(strings.scanHint(), Modifier.fillMaxWidth().padding(top = 4.dp), style = inter(12f), color = colors.textTertiary, textAlign = TextAlign.Center)
            Text(strings.openSettings(), Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenSettings).padding(vertical = 12.dp).testTag("stereo_open_settings"),
                style = inter(14f, InterWeight.semibold), color = AppTheme.accent, textAlign = TextAlign.Center)
        }
    }
}

/** iOS: a labelled card of the stereo sheet. */
@Composable
private fun StereoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProfileSectionLabel(title, Modifier.padding(horizontal = 2.dp))
        StereoCard(content)
    }
}

/** iOS: the card of this sheet is white with a 1 pt border, not the shadowed `surfaceCard` of the settings. */
@Composable
private fun StereoCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().background(AppTheme.colors.card, shape).border(1.dp, AppTheme.colors.border, shape).clip(shape), content = content)
}

/** iOS rowDivider: the card-alt hairline inset 14 from the leading edge. */
@Composable
private fun StereoDivider() = Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.cardAlt))

// iOS: BluetoothScanSheet.swift, audioOutputSection. A Bluetooth output may be headphones.
// The speaker, the name over «Bluetooth-аудио» (or «Android Auto», as CarPlay is named there), and either an accent
// «+» or the green «Добавлена» capsule.
@Composable
internal fun StereoOutputRow(device: SavedBluetoothDevice, linked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().testTag("stereo_${device.name}").alpha(if (enabled) 1f else 0.5f)
        .clickable(enabled = enabled && !linked, role = Role.Button) { haptics.tap(); onClick() }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(AppGlyph.speakerWaveFill, null, tint = AppTheme.accent, size = 19.2.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name, style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
            Text(if (device.uuid == com.onezee.triptrack.location.auto.AutoTripController.ANDROID_AUTO) strings.androidAuto() else strings.bluetoothAudio(), style = inter(12f), color = colors.textTertiary)
        }
        if (linked) LinkedCapsule(strings.added())
        else Glyph(AppGlyph.plus, null, tint = AppTheme.accent, size = 24.dp)
    }
}

/** iOS DeviceRow: a small quiet note, the name, and the green capsule once it is linked. */
@Composable
private fun NearbyDeviceRow(device: SavedBluetoothDevice, saved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth().testTag("stereo_nearby_${device.name}").alpha(if (enabled) 1f else 0.5f)
        .clickable(enabled = enabled && !saved, role = Role.Button) { haptics.tap(); onClick() }.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.musicNote, null, tint = if (saved) AppTheme.green else colors.textSecondary, size = 15.6.dp)
        }
        Text(device.name, Modifier.weight(1f), style = inter(14f, InterWeight.semibold), color = if (saved) colors.textTertiary else colors.text, maxLines = 1)
        if (saved) LinkedCapsule(appStrings.added())
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
