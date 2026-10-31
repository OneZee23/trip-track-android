package com.onezee.triptrack.recording

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.R as Words
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.permissions.HealthSettings
import com.onezee.triptrack.location.permissions.HealthIssue
import com.onezee.triptrack.location.permissions.Oem
import com.onezee.triptrack.location.permissions.PermissionFlow
import com.onezee.triptrack.location.permissions.PermissionPlan
import com.onezee.triptrack.location.permissions.PermissionStep
import com.onezee.triptrack.location.permissions.RecordingHealth
import com.onezee.triptrack.location.permissions.RecordingPermission
import com.onezee.triptrack.location.permissions.RequestMethod
import com.onezee.triptrack.location.recording.RecorderState
import com.onezee.triptrack.location.service.formattedDuration
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.speed
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.SavedBluetoothDevice
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The minimum to drive the recording on a phone: start, pause, resume, finish, the recovery
 * question, the permission sequence, the recording health and the car stereo. Plain foundation
 * widgets on purpose; the designed screens replace these with the design system.
 */

private enum class Page { record, permissions, health, stereo }

@Composable
fun RecordingRoute(graph: LocationGraph, strings: AppStrings, resumeTick: Int) {
    var page by remember { mutableStateOf(Page.record) }
    val back = { page = Page.record }
    when (page) {
        Page.record -> RecordScreen(graph, strings, resumeTick) { page = it }
        Page.permissions -> PermissionScreen(graph, strings, resumeTick, back)
        Page.health -> HealthScreen(graph, strings, resumeTick, back)
        Page.stereo -> StereoScreen(graph, strings, back)
    }
}

@Composable
private fun RecordScreen(graph: LocationGraph, strings: AppStrings, resumeTick: Int, open: (Page) -> Unit) {
    val context = LocalContext.current
    val precise = remember(resumeTick) { graph.hasLocationPermission() }
    val state by graph.recorder.state.collectAsState()
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(state.isRecording) {
        while (state.isRecording) {
            now = Instant.now()
            delay(1_000)
        }
    }
    Screen {
        Title(if (state.isPaused) strings.live_paused() else if (state.isRecording) strings.live_recording() else strings.record())
        if (state.isDraft) Line(strings.draftRecordingLabel())
        Numbers(state, strings, graph, now)
        Line(gpsLabel(state, strings))
        state.recovery?.let {
            Title(strings.recoveryTitle())
            Line(strings.recoveryBody())
            Button(strings.recoveryContinue()) { scope.launch { graph.continueRecovered() } }
            Button(strings.recoveryFinish()) { scope.launch { graph.finishRecovered() } }
        }
        if (state.isRecording) {
            Button(if (state.isPaused) strings.resumeAction() else strings.pauseAction()) { scope.launch { graph.togglePause() } }
            Button(strings.live_finish()) { scope.launch { graph.stopRecording() } }
        } else if (state.recovery == null && !state.isFinishing) {
            if (precise) Button(strings.recordTripCta()) { scope.launch { graph.startRecording() } }
            else {
                Line(strings.tr(Words.string.android_precise_location_body))
                val asked = RecordingPermission.fineLocation in graph.permissionLedger.asked()
                Button(if (asked) strings.tr(Words.string.android_health_open_settings) else strings.onboardingContinue()) {
                    if (RecordingPermission.fineLocation in graph.permissionLedger.asked()) {
                        HealthSettings.open(context, HealthSettings.targets(context, HealthIssue.noPreciseLocation, Oem.other))
                    } else open(Page.permissions)
                }
            }
        }
        Spacer()
        Link(strings.onboardingLocation()) { open(Page.permissions) }
        Link(strings.tr(Words.string.android_health_title)) { open(Page.health) }
        Link(strings.linkStereo()) { open(Page.stereo) }
    }
}

@Composable
private fun Numbers(state: RecorderState, strings: AppStrings, graph: LocationGraph, now: Instant) {
    val lang = strings.language.code
    val unit = graph.distanceUnit
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Stat(strings.live_distanceCaption(), Measure.distance(state.distance, unit, lang, strings))
        Stat(strings.live_speedCaption(), Measure.speed(state.speed, unit, lang, strings))
        Stat(strings.live_timeCaption(), formattedDuration(state.elapsedSeconds(now)))
    }
}

/** iOS: GPSIndicatorView. Zero accuracy means no fix has been accepted yet. */
private fun gpsLabel(state: RecorderState, strings: AppStrings): String = when {
    state.gpsAccuracy <= 0 -> strings.gpsSearching()
    state.isRecording && state.gpsStale -> strings.gpsLost()
    state.gpsAccuracy <= 10 -> strings.gpsAccurate()
    state.gpsAccuracy <= 35 -> strings.gpsMedium()
    else -> strings.gpsWeak()
}

/**
 * iOS: the onboarding permission pages. One explanation, one Continue, then the system's own
 * question; the next page appears only after the answer, whatever it was. Nothing is asked twice.
 */
@Composable
private fun PermissionScreen(graph: LocationGraph, strings: AppStrings, resumeTick: Int, back: () -> Unit) {
    val context = LocalContext.current
    val flow = remember { PermissionFlow(graph.permissionLedger) }
    var refresh by remember { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        flow.complete(token)
        refresh++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        flow.complete(token)
        refresh++
    }
    val step = remember(refresh, resumeTick) {
        val granted = PermissionPlan.effectiveGrants(Build.VERSION.SDK_INT) {
            context.checkSelfPermission(it.manifestName) == PackageManager.PERMISSION_GRANTED
        }
        PermissionPlan.next(Build.VERSION.SDK_INT, granted, graph.permissionLedger.asked())
    }
    val approximate = remember(refresh, resumeTick) {
        PermissionPlan.hasApproximateLocation { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
    Screen {
        if (approximate) {
            Line(strings.tr(Words.string.android_precise_location_body))
            Button(strings.tr(Words.string.android_health_open_settings)) {
                HealthSettings.open(context, HealthSettings.targets(context, HealthIssue.noPreciseLocation, Oem.other))
            }
        }
        when (step) {
            is PermissionStep.Explain -> {
                val (title, body) = explanation(step.permission, strings)
                Title(title)
                Line(body)
                Button(strings.onboardingContinue()) {
                    flow.begin(step.permission)?.let {
                        token = it
                        if (step.method == RequestMethod.settingsPage) {
                            settingsLauncher.launch(HealthSettings.targets(context, HealthIssue.noBackgroundLocation, Oem.other).first())
                        } else launcher.launch(PermissionPlan.manifestNames(step.permission))
                    }
                }
            }
            PermissionStep.Done -> {
                // The answers may have opened activity recognition: register the transitions now.
                LaunchedEffect(Unit) { graph.rearmAutoRecord() }
                Title(strings.done())
            }
        }
        Link(strings.back(), back)
    }
}

private fun explanation(permission: RecordingPermission, strings: AppStrings): Pair<String, String> = when (permission) {
    RecordingPermission.fineLocation -> strings.onboardingLocation() to strings.onboardingLocationSub()
    RecordingPermission.backgroundLocation -> strings.onboardingBackgroundTitle() to
        (strings.tr(Words.string.permission_NSLocationAlwaysAndWhenInUseUsageDescription) +
            if (Build.VERSION.SDK_INT >= 30) "\n\n" + strings.tr(Words.string.android_permission_background_hint) else "")
    RecordingPermission.activityRecognition -> strings.onboardingBackgroundTitle() to strings.tr(Words.string.permission_NSMotionUsageDescription)
    RecordingPermission.notifications -> strings.onboardingNotificationsTitle() to strings.onboardingNotificationsSub()
    RecordingPermission.bluetoothConnect -> strings.autoRecord() to strings.tr(Words.string.permission_NSBluetoothAlwaysUsageDescription)
}

@Composable
private fun HealthScreen(graph: LocationGraph, strings: AppStrings, resumeTick: Int, back: () -> Unit) {
    val context = LocalContext.current
    val health = remember(resumeTick) { RecordingHealth.read(context, graph.autoRecord.mode != AutoRecordMode.off) }
    Screen {
        Title(strings.tr(Words.string.android_health_title))
        if (health.issues.isEmpty()) Line(strings.tr(Words.string.android_health_ok))
        for (issue in health.issues) {
            Line(strings.tr(issue.message))
            Button(strings.tr(Words.string.android_health_open_settings)) {
                HealthSettings.open(context, HealthSettings.targets(context, issue, health.oem))
            }
        }
        health.oemSteps?.let { steps ->
            Line(strings.tr(steps))
            Button(strings.tr(Words.string.android_health_open_settings)) {
                HealthSettings.open(context, HealthSettings.targets(context, null, health.oem))
            }
        }
        Link(strings.back(), back)
    }
}

/** The auto-record mode and the paired car stereo whose connection starts a trip. */
@Composable
private fun StereoScreen(graph: LocationGraph, strings: AppStrings, back: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(graph.autoRecord.mode) }
    var saved by remember { mutableStateOf(graph.autoRecord.savedDevices) }
    LaunchedEffect(graph.autoRecord) {
        graph.autoRecord.changes.collect {
            mode = graph.autoRecord.mode
            saved = graph.autoRecord.savedDevices
        }
    }
    val paired = remember { pairedAudioDevices(context) }
    Screen {
        Title(strings.autoRecord())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, label) in listOf(AutoRecordMode.off to strings.autoRecordOff(), AutoRecordMode.remind to strings.autoRecordRemind(), AutoRecordMode.auto to strings.autoRecordAuto())) {
                Button(if (value == mode) strings.tr(Words.string.android_selected_item, label) else label) {
                    graph.autoRecord.mode = value
                    mode = value
                    graph.rearmAutoRecord()
                }
            }
        }
        Title(strings.stereoSection())
        Line(if (saved.isEmpty()) strings.stereoNotLinkedTitle() else strings.stereoLinkedTitle())
        if (paired == null) Line(strings.bluetoothRequired())
        for (device in paired.orEmpty()) {
            val isSaved = saved.any { it.uuid.equals(device.uuid, ignoreCase = true) }
            Button(if (isSaved) strings.tr(Words.string.android_selected_item, device.name) else device.name) {
                if (isSaved) graph.autoRecord.removeDevice(device.uuid) else graph.autoRecord.addDevice(device)
                saved = graph.autoRecord.savedDevices
            }
        }
        Link(strings.back(), back)
    }
}

/** Paired audio devices (car kits first), or null without the Bluetooth permission. */
@SuppressLint("MissingPermission")
private fun pairedAudioDevices(context: Context): List<SavedBluetoothDevice>? {
    if (Build.VERSION.SDK_INT >= 31 &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return null
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    val bonded = runCatching { adapter.bondedDevices.toList() }.getOrDefault(emptyList())
    val audio = bonded.filter { it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
    return (audio.ifEmpty { bonded })
        .sortedByDescending { it.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO }
        .map { SavedBluetoothDevice(uuid = it.address, name = it.name ?: it.address) }
}

// MARK: Bare widgets until the design system arrives

@Composable
private fun Screen(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun Title(text: String) = BasicText(text, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))

@Composable
private fun Line(text: String) = BasicText(text, style = TextStyle(fontSize = 15.sp, color = Color(0xFF555555)))

@Composable
private fun Stat(caption: String, value: String) {
    Column {
        BasicText(caption, style = TextStyle(fontSize = 11.sp, color = Color(0xFF888888)))
        BasicText(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun Button(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        modifier = Modifier.fillMaxWidth().background(Color(0xFFEFEAE4), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(16.dp),
        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    BasicText(label, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp), style = TextStyle(fontSize = 15.sp, color = Color(0xFFB4532A)))
}

@Composable
private fun Spacer() = androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
