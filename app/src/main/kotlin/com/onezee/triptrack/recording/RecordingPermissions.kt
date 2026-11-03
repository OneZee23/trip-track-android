// iOS: OnboardingPermissionCoordinator.swift, TrackingView.swift (inline location hint).
package com.onezee.triptrack.recording

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.R as Words
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.settings.*
import com.onezee.triptrack.location.LocationGraph
import com.onezee.triptrack.location.permissions.*

@Composable
internal fun RecordingPermissionSheet(graph: LocationGraph, resumeTick: Int, onComplete: () -> Unit) {
    val context = LocalContext.current
    val strings = appStrings
    val flow = remember { PermissionFlow(graph.permissionLedger) }
    var revision by remember { mutableIntStateOf(0) }
    var token by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { flow.complete(token); revision++ }
    val step = remember(revision, resumeTick) {
        val granted = PermissionPlan.effectiveGrants(Build.VERSION.SDK_INT) { context.checkSelfPermission(it.manifestName) == PackageManager.PERMISSION_GRANTED }
        PermissionPlan.next(Build.VERSION.SDK_INT, granted, graph.permissionLedger.asked())
    }
    if (step == PermissionStep.Done) {
        LaunchedEffect(Unit) { graph.rearmAutoRecord(); onComplete() }
        return
    }
    val explanation = step as PermissionStep.Explain
    val (title, body) = when (explanation.permission) {
        RecordingPermission.fineLocation -> strings.onboardingLocation() to strings.onboardingLocationSub()
        RecordingPermission.backgroundLocation -> strings.onboardingBackgroundTitle() to
            (strings.tr(Words.string.permission_NSLocationAlwaysAndWhenInUseUsageDescription) +
                if (Build.VERSION.SDK_INT >= 30) "\n\n" + strings.tr(Words.string.android_permission_background_hint) else "")
        RecordingPermission.activityRecognition -> strings.onboardingBackgroundTitle() to strings.tr(Words.string.permission_NSMotionUsageDescription)
        RecordingPermission.notifications -> strings.onboardingNotificationsTitle() to strings.onboardingNotificationsSub()
        RecordingPermission.bluetoothConnect -> strings.linkStereo() to strings.tr(Words.string.permission_NSBluetoothAlwaysUsageDescription)
    }
    ContentSizedSheet({}, background = AppTheme.colors.bg, dismissible = false) {
        Column(Modifier.fillMaxWidth().padding(24.dp).testTag("recording_permission_explanation"), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(title, style = interScaled(22f, InterWeight.bold), color = AppTheme.colors.text)
            Text(body, style = interScaled(14f), color = AppTheme.colors.textSecondary)
            MeAction(strings.onboardingContinue(), {
                flow.begin(explanation.permission)?.let {
                    token = it
                    // From Android 11 the system shows the app's location page for background location.
                    launcher.launch(PermissionPlan.manifestNames(explanation.permission))
                }
            }, enabled = !flow.isRequesting)
        }
    }
}

// iOS: AutoRecordSettingsView.swift; Android's health issues stay in core:location.
@Composable
internal fun RecordingHealthSheet(health: RecordingHealth, onDismiss: () -> Unit, onPermission: () -> Unit, onStereo: () -> Unit) {
    val context = LocalContext.current
    val strings = appStrings
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg) {
        MeSheetHeader(strings.tr(Words.string.android_health_title)) { dismiss() }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (health.issues.isEmpty()) Text(strings.tr(Words.string.android_health_ok), style = AppType.body, color = AppTheme.colors.textSecondary)
            health.issues.forEach { issue ->
                MeRow(strings.tr(issue.message), subtitle = strings.tr(Words.string.android_health_open_settings), onClick = {
                    HealthSettings.open(context, HealthSettings.targets(context, issue, health.oem))
                })
            }
            health.oemSteps?.let { steps -> MeRow(strings.tr(steps), subtitle = strings.tr(Words.string.android_health_open_settings), onClick = {
                HealthSettings.open(context, HealthSettings.targets(context, null, health.oem))
            }) }
            MeRow(strings.onboardingLocation(), onClick = onPermission)
            MeRow(strings.autoRecord(), subtitle = strings.linkStereo(), onClick = onStereo)
        }
    }
}
