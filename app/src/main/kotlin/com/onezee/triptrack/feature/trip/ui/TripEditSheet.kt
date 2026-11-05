package com.onezee.triptrack.feature.trip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.Vehicle
import com.onezee.triptrack.ui.cards.hasDisplayableName
import java.util.UUID

// iOS: TripEditSheet.swift.
@Composable
fun TripEditSheet(
    trip: Trip,
    vehicles: List<Vehicle>,
    onSave: (String, String, UUID?, Boolean, Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    showHiddenPartsEntry: Boolean = false,
    onEditHidden: () -> Unit = {},
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    var title by remember(trip.id) { mutableStateOf(if (com.onezee.triptrack.feature.trip.TripSummary.from(trip).hasDisplayableName(strings)) trip.title.orEmpty() else "") }
    var notes by remember(trip.id) { mutableStateOf(trip.tripDescription.orEmpty()) }
    var vehicleId by remember(trip.id) { mutableStateOf(trip.vehicleId) }
    var transfer by remember(trip.id) { mutableStateOf(trip.isTransfer) }
    var private by remember(trip.id) { mutableStateOf(trip.isPrivate) }
    val notesRefused = com.onezee.triptrack.logic.CommentContentRules.containsObjectionable(
        com.onezee.triptrack.logic.CommentContentRules.sanitize(notes),
    )
    var pickingVehicle by remember { mutableStateOf(false) }
    var pickingAccess by remember { mutableStateOf(false) }
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = colors.bg) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("trip_edit_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.cancel(), style = inter(16f, InterWeight.medium), color = colors.textSecondary,
                    modifier = Modifier.clickable(role = Role.Button) { haptics.tap(); sheet.dismiss() }.testTag("trip_edit_cancel"))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(strings.editTripTitle(), style = inter(16f, InterWeight.bold), color = colors.text, maxLines = 1)
                }
                Text(strings.done(), style = inter(16f, InterWeight.bold), color = AppTheme.accent,
                    modifier = Modifier.clickable(role = Role.Button) {
                        haptics.action()
                        if (!notesRefused) {
                            onSave(title.trim(), notes.trim(), vehicleId, private, transfer)
                            sheet.dismiss()
                        }
                    }.testTag("trip_edit_done"))
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                EditField(strings.tripTitleLabel(), "trip_edit_title") {
                    BasicTextField(title, { title = it }, singleLine = true,
                        textStyle = inter(16f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent),
                        modifier = Modifier.fillMaxWidth().testTag("trip_edit_title"),
                        decorationBox = { field -> Box { if (title.isEmpty()) Text(strings.tripTitlePlaceholder(), style = inter(16f), color = colors.textTertiary); field() } })
                }
                EditField(strings.descriptionSection(), "trip_edit_description_section") {
                    Column {
                        BasicTextField(notes, { notes = graphemePrefix(it, MAX_NOTES_LENGTH) },
                            textStyle = inter(16f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent),
                            modifier = Modifier.fillMaxWidth().height(108.dp).testTag("trip_edit_description"),
                            decorationBox = { field -> Box { if (notes.isEmpty()) Text(strings.describeTripPlaceholder(), style = inter(16f), color = colors.textTertiary); field() } })
                        Text("${graphemeCount(notes)} / $MAX_NOTES_LENGTH", Modifier.align(Alignment.End), style = inter(11f, InterWeight.medium), color = colors.textTertiary)
                        if (notesRefused) Text(strings.contentFilterContainsInappropriateLanguage(), style = inter(12f), color = AppTheme.red, modifier = Modifier.testTag("description_refused"))
                    }
                }
                EditField(strings.vehicleSectionLabel(), "trip_edit_vehicle") {
                    PressableCard(onClick = { haptics.tap(); pickingVehicle = true }, modifier = Modifier.testTag("trip_edit_vehicle_row")) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(CircleShape).background(colors.cardAlt), contentAlignment = Alignment.Center) {
                                Text(if (transfer) "🚶" else vehicles.firstOrNull { it.id == vehicleId }?.avatarEmoji ?: "🚗", style = inter(17f))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(if (transfer) strings.tripTransferTitle() else vehicles.firstOrNull { it.id == vehicleId }?.name ?: strings.noVehicle(),
                                Modifier.weight(1f), style = inter(16f, InterWeight.semibold), color = colors.text)
                            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 13.dp)
                        }
                    }
                }
                EditField(strings.accessSectionLabel(), "trip_edit_access") {
                    PressableCard(onClick = { haptics.tap(); pickingAccess = true }, modifier = Modifier.testTag("trip_edit_access_row")) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(if (private) AppGlyph.lock else AppGlyph.globe, null, tint = if (private) colors.textSecondary else AppTheme.accent, size = 18.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(if (private) strings.privacyOnlyMe() else strings.privacyPublic(), style = inter(16f, InterWeight.semibold), color = colors.text)
                                Text(if (private) strings.privacyOnlyMeHint() else strings.privacyPublicHint(), style = inter(12.5f), color = colors.textTertiary)
                            }
                            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 13.dp)
                        }
                    }
                }
                if (showHiddenPartsEntry) Text(strings.hiddenEditorTitle(),
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEditHidden).testTag("trip_edit_hidden_parts")
                        .padding(vertical = 10.dp), style = inter(14f, InterWeight.medium), color = AppTheme.accent)
            }
        }
    }
    if (pickingVehicle) ContentSizedSheet(onDismissRequest = { pickingVehicle = false }, background = colors.bg) {
        val picker = this
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.vehicleSectionLabel(), style = inter(19f, InterWeight.bold), color = colors.text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
            (listOf(null) + vehicles.map { it.id }).forEach { id ->
                val car = vehicles.firstOrNull { it.id == id }
                PressableCard(onClick = { vehicleId = id; transfer = false; pickingVehicle = false; picker.dismiss() }, modifier = Modifier.testTag("trip_edit_vehicle_option_${id ?: "none"}")) {
                    Row(Modifier.fillMaxWidth().surfaceCard().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(car?.avatarEmoji ?: "🚗", style = inter(17f)); Spacer(Modifier.width(12.dp))
                        Text(car?.name ?: strings.noVehicle(), Modifier.weight(1f), style = inter(16f, InterWeight.semibold), color = colors.text)
                        if (vehicleId == id && !transfer) Glyph(AppGlyph.checkmark, null, tint = AppTheme.accent, size = 20.dp)
                    }
                }
            }
            PressableCard(onClick = { transfer = true; vehicleId = null; pickingVehicle = false; picker.dismiss() }, modifier = Modifier.testTag("trip_edit_vehicle_transfer")) {
                Row(Modifier.fillMaxWidth().surfaceCard().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🚶", style = inter(17f)); Spacer(Modifier.width(12.dp)); Text(strings.tripTransferTitle(), style = inter(16f, InterWeight.semibold), color = colors.text)
                }
            }
        }
    }
    if (pickingAccess) ContentSizedSheet(onDismissRequest = { pickingAccess = false }, background = colors.bg) {
        val picker = this
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(strings.accessSectionLabel(), style = inter(19f, InterWeight.bold), color = colors.text, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
            listOf(true, false).forEach { isPrivate ->
                val selected = private == isPrivate
                PressableCard(onClick = { private = isPrivate; pickingAccess = false; picker.dismiss() }, modifier = Modifier.testTag(if (isPrivate) "trip_edit_access_private" else "trip_edit_access_public")) {
                    Row(Modifier.fillMaxWidth().surfaceCard().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (isPrivate) AppGlyph.lock else AppGlyph.globe, null, tint = if (isPrivate) colors.textSecondary else AppTheme.accent, size = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (isPrivate) strings.privacyOnlyMe() else strings.privacyPublic(), style = inter(16f, InterWeight.semibold), color = colors.text)
                            Text(if (isPrivate) strings.privacyOnlyMeHint() else strings.privacyPublicHint(), style = inter(12.5f), color = colors.textTertiary)
                        }
                        if (selected) Glyph(AppGlyph.checkmarkCircleFill, null, tint = AppTheme.accent, size = 20.dp)
                    }
                }
            }
            Text(strings.tripEditAPublicTrip(), style = inter(12.5f), color = colors.textTertiary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun EditField(label: String, tag: String, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(label.uppercase(), style = inter(11f, InterWeight.semibold), color = colors.textTertiary)
        Column(Modifier.fillMaxWidth().surfaceCard().padding(14.dp)) { content() }
    }
}
