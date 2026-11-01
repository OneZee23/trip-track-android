// iOS: TrackingView.stopConfirmSheet, VehiclePickerSheet.swift, RecoveryPromptSheet.swift.
package com.onezee.triptrack.recording

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.garage.VehicleSpritePlate
import com.onezee.triptrack.location.recording.RecoverableRecording
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.location.service.formattedDuration
import java.util.UUID

@Composable
fun StopConfirmationSheet(preview: RecordingFinishPreview, paused: Boolean, busy: Boolean, onFinish: () -> Unit, onPause: () -> Unit, onDismiss: () -> Unit) {
    Dialog({ if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PlainModalWindow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).clickable { if (!busy) onDismiss() }.clearAndSetSemantics {})
            Box(Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                StopConfirmationContent(preview, paused, busy, onFinish, onPause, onDismiss)
            }
        }
    }
}

@Composable
fun StopConfirmationContent(preview: RecordingFinishPreview, paused: Boolean, busy: Boolean, onFinish: () -> Unit, onPause: () -> Unit, onDismiss: () -> Unit) {
    val strings = appStrings
    val reason = preview.discardReason
    val title = if (reason == null) strings.stopConfirmTitle() else strings.recordingDiscardTitle()
    val unit = LocalDistanceUnit.current
    Column(Modifier.fillMaxWidth().background(Color(0xFF1C1C1E), RoundedCornerShape(26.dp))
        .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(26.dp)).verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp).padding(top = 22.dp, bottom = 10.dp).semantics { paneTitle = title }.testTag("stop_confirmation"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, Modifier.testTag("stop_confirm_title"), style = interScaled(20f, InterWeight.heavy), color = Color.White, textAlign = TextAlign.Center)
            Text(reason?.let(strings::recordingDiscardReason) ?: strings.stopConfirmBody(), Modifier.testTag("stop_confirm_explanation"),
                style = interScaled(13.5f), color = Color.White.copy(alpha = .75f), textAlign = TextAlign.Center)
        }
        StatusPill("${Measure.distance(preview.distance, unit, strings.language.code, strings, Measure.Style.tenths)} · ${formattedDuration(preview.duration)}", fill = Color.White.copy(alpha = .08f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TrackingButton(if (reason == null) strings.stopConfirmAction() else strings.recordingDiscardAction(),
                if (reason == null) AppGlyph.checkmark else AppGlyph.trash, onFinish, "stop_confirm_finish", fill = AppTheme.red, enabled = !busy)
            if (!paused) TrackingButton(strings.stopConfirmPause(), AppGlyph.pause, onPause, "stop_confirm_pause", fill = Color.White.copy(alpha = .1f), enabled = !busy)
            TrackingButton(strings.recordingReturn(), null, onDismiss, "stop_confirm_cancel", fill = Color.Transparent, ink = Color.White.copy(alpha = .75f), height = 50.dp, enabled = !busy)
        }
    }
}

@Composable
fun VehiclePickerSheet(vehicles: List<Vehicle>, selected: UUID?, transfer: Boolean, onPick: (UUID?) -> Unit, onTransfer: () -> Unit, onGarage: () -> Unit, onDismiss: () -> Unit, busy: Boolean = false, failed: Boolean = false) {
    ContentSizedSheet(onDismiss, background = AppTheme.colors.bg, dismissible = !busy) {
        VehiclePickerContent(vehicles, selected, transfer, onPick, onTransfer, onGarage, busy, failed)
    }
}

@Composable
fun VehiclePickerContent(vehicles: List<Vehicle>, selected: UUID?, transfer: Boolean, onPick: (UUID?) -> Unit, onTransfer: () -> Unit, onGarage: () -> Unit, busy: Boolean = false, failed: Boolean = false) {
    val strings = appStrings
    val listed = vehicles.filter { !it.isArchived && it.soldAt == null }
    val unit = LocalDistanceUnit.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 8.dp, bottom = 14.dp).size(34.dp, 5.dp).background(AppTheme.colors.text.copy(alpha = .18f), CircleShape))
        Text(strings.vehiclePickerTitle(), Modifier.padding(bottom = 16.dp), style = inter(18f, InterWeight.bold), color = AppTheme.colors.text)
        if (busy) RecordingSpinner(AppTheme.accent, Modifier.size(20.dp))
        if (failed) Text(strings.manualFailedDbTitle(), style = inter(13f), color = AppTheme.red)
        VehiclePickerRow(strings.noVehicleOption(), null, !transfer && listed.none { it.id == selected }, "vehicle_picker_none", AppGlyph.minusCircle, { onPick(null) })
        VehiclePickerRow(strings.tripTransferTitle(), strings.tripTransferHint(), transfer, "vehicle_picker_transfer", AppGlyph.car, onTransfer)
        listed.forEach { vehicle ->
            val odometer = Measure.odometer(vehicle.displayOdometerKm, vehicle.dashboardUnit(unit), strings.language.code, strings)
            VehiclePickerRow(vehicle.name, odometer, !transfer && selected == vehicle.id, "vehicle_picker_${vehicle.id}", null, { onPick(vehicle.id) }, vehicle)
        }
        if (listed.isEmpty()) Text(strings.garageEmptyPickerHint(), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = inter(12f), color = AppTheme.colors.textTertiary, textAlign = TextAlign.Center)
        Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(AppTheme.colors.border))
        TrackingButton(if (vehicles.isEmpty()) strings.addVehicleTitle() else strings.manageInGarage(), AppGlyph.plus, onGarage, "vehicle_picker_garage",
            fill = Color.Transparent, ink = AppTheme.accent)
    }
}

@Composable
private fun VehiclePickerRow(title: String, subtitle: String?, checked: Boolean, tag: String, glyph: AppGlyph?, onClick: () -> Unit, vehicle: Vehicle? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().background(if (checked) AppTheme.accent.copy(alpha = .08f) else Color.Transparent, shape)
        .border(1.5.dp, if (checked) AppTheme.accent else Color.Transparent, shape)
        .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = checked }.padding(10.dp).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(AppTheme.colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            if (vehicle != null) VehicleSpritePlate(vehicle, 38.dp)
            else glyph?.let { Glyph(it, null, tint = if (checked) AppTheme.accent else AppTheme.colors.textTertiary, size = 18.dp) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = inter(15f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 1)
            subtitle?.let { Text(it, style = inter(11.5f), color = AppTheme.colors.textTertiary) }
        }
        if (checked) Glyph(AppGlyph.checkmark, null, tint = AppTheme.accent, size = 16.dp)
    }
}

@Composable
fun RecoveryPromptSheet(recovery: RecoverableRecording, busy: Boolean, onContinue: () -> Unit, onFinish: () -> Unit, failed: Boolean = false) {
    ContentSizedSheet({}, background = AppTheme.colors.bg, dismissible = false) {
        RecoveryPromptContent(recovery, busy, onContinue, onFinish)
        if (failed) Text(appStrings.manualFailedDbTitle(), Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = inter(13f), color = AppTheme.red)
    }
}

@Composable
fun RecoveryPromptContent(recovery: RecoverableRecording, busy: Boolean, onContinue: () -> Unit, onFinish: () -> Unit) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val totalMinutes = recovery.durationSeconds.toInt().coerceAtLeast(0) / 60
    val time = "%d:%02d".format(java.util.Locale.ROOT, totalMinutes / 60, totalMinutes % 60)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 24.dp)
        .semantics { paneTitle = strings.recoveryTitle() }.testTag("recovery_prompt"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp).background(AppTheme.accent.copy(alpha = .08f), CircleShape), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.arrowClockwise, null, tint = AppTheme.accent, size = 30.dp)
        }
        Text(strings.recoveryTitle(), Modifier.padding(top = 16.dp, bottom = 8.dp), style = inter(19f, InterWeight.bold), color = AppTheme.colors.text, textAlign = TextAlign.Center)
        Text(strings.recoveryBody(), Modifier.padding(horizontal = 8.dp).padding(bottom = 14.dp), style = inter(14f), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
        StatusPill("${strings.recoveryChip()} · ${Measure.distance(recovery.orphan.distance, unit, strings.language.code, strings, Measure.Style.tenths)} · $time",
            fill = AppTheme.accent.copy(alpha = .1f), ink = AppTheme.accent)
        Spacer(Modifier.height(22.dp))
        TrackingButton(strings.recoveryContinue(), null, onContinue, "recovery_continue", enabled = !busy)
        Spacer(Modifier.height(10.dp))
        TrackingButton(strings.recoveryFinish(), null, onFinish, "recovery_finish", fill = AppTheme.colors.cardAlt, ink = AppTheme.colors.text, height = 48.dp, enabled = !busy)
    }
}
