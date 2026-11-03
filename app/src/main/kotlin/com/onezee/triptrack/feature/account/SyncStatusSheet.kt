// iOS: SyncStatusSheetView.swift
package com.onezee.triptrack.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.SheetCloseCircle
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.sync.SyncOperation

/**
 * iOS: SyncStatusSheetView, «Синхронизация» per item. The category card counts what is in the cloud
 * against what is only here; below it the live queue appears when something is actually in flight,
 * and failed operations get «Retry now», which also reopens their automatic retries.
 */
@Composable
fun SyncStatusSheet(state: AccountScreenState, onRetry: () -> Unit, onDismissRequest: () -> Unit) {
    // iOS: .presentationDetents([.medium, .large]): at least half the screen, taller with a long queue.
    val half = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } / 2
    // iOS .presentationCornerRadius(22).
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = AppTheme.colors.bg, minimumHeight = half, cornerRadius = 22.dp) {
        SyncStatusContent(state, onRetry)
    }
}

@Composable
fun SheetScope.SyncStatusContent(state: AccountScreenState, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val strings = appStrings
    val sheet = this
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.syncSheetTitle(), style = inter(17f, InterWeight.heavy), letterSpacing = points(-0.2f), color = colors.text, modifier = Modifier.weight(1f))
        SheetCloseCircle(onClick = { sheet.dismiss() }, modifier = Modifier.testTag("sync_status_close"))
    }
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 28.dp)
            .testTag("sync_status_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CategoryCard(state.counts)
        state.lastSyncedAt?.let { last ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                AccountIcon(AccountGlyph.clock, colors.textTertiary, size = 15.dp)
                Text(strings.syncLastAt(RelativeTripDate.string(last, strings)), style = inter(13f), color = colors.textTertiary)
            }
        }
        state.queue.currentOperation?.let { op ->
            QueueSection(strings.syncStatusNowLabel()) { OperationRow(op, isFailed = false, highlight = true) }
        }
        if (state.queue.pending.isNotEmpty()) {
            QueueSection("${strings.syncStatusPendingHeader()}  ${state.queue.pending.size}") {
                state.queue.pending.forEach { OperationRow(it, isFailed = false) }
            }
        }
        if (state.queue.failed.isNotEmpty()) {
            QueueSection("${strings.syncStatusFailedHeader()}  ${state.queue.failed.size}") {
                state.queue.failed.forEach { OperationRow(it, isFailed = true) }
            }
            RetryButton(state.isRetrying, onRetry)
        }
    }
}

/** No «Achievements» row: level and XP ride profile-update and have no per-item sync state. */
@Composable
private fun CategoryCard(counts: SyncCounts) {
    val strings = appStrings
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
        CategoryRow(AccountGlyph.flag, strings.tripsTab(), counts.tripsSynced, counts.tripsSynced + counts.tripsPending, 0, true, "sync_row_trips")
        CategoryRow(AccountGlyph.camera, strings.photos(), counts.photosUploaded, counts.photosTotal, counts.photosFailed, true, "sync_row_photos")
        ProfileRow(counts)
        CategoryRow(AccountGlyph.car, strings.garage(), counts.vehiclesSynced, counts.vehiclesSynced + counts.vehiclesPending, 0, false, "sync_row_garage")
    }
}

@Composable
private fun CategoryRow(icon: AccountGlyph, label: String, synced: Int, total: Int, failed: Int, showsDivider: Boolean, tag: String) {
    val colors = AppTheme.colors
    val strings = appStrings
    Column(Modifier.testTag(tag)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { AccountIcon(icon, colors.textSecondary, size = 21.dp) }
            Text(label, style = inter(15f, InterWeight.medium), color = colors.text, modifier = Modifier.weight(1f))
            // The failed share is a red suffix; the details are in the failed list below the card.
            val counter = buildAnnotatedString {
                append("$synced / $total")
                if (failed > 0) withStyle(SpanStyle(color = AppTheme.red)) { append(" · " + strings.syncFailedCount(failed)) }
            }
            Text(counter, style = inter(12f, InterWeight.medium, monospacedDigits = true), color = colors.textTertiary, maxLines = 1)
            if (total == 0 || synced >= total) Checkmark() else ProgressCapsule(synced.toFloat() / total)
        }
        if (showsDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

/** «Profile»: the settings row of this install, one per person, so no counter. */
@Composable
private fun ProfileRow(counts: SyncCounts) {
    val colors = AppTheme.colors
    val strings = appStrings
    Column(Modifier.testTag("sync_row_profile")) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { AccountIcon(AccountGlyph.person, colors.textSecondary, size = 21.dp) }
            Text(strings.profile(), style = inter(15f, InterWeight.medium), color = colors.text, modifier = Modifier.weight(1f))
            when {
                !counts.settingsExists -> Text("—", style = inter(12f, InterWeight.medium), color = colors.textTertiary)
                counts.settingsSynced -> Checkmark()
                // A pending dot: an upload of the profile is queued.
                else -> Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Box(Modifier.size(6.dp).background(AppTheme.accent, CircleShape)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
    }
}

@Composable
private fun Checkmark() {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { Glyph(AppGlyph.checkmark, contentDescription = null, tint = AppTheme.green, size = 20.dp) }
}

/**
 * The track has its own contrast, not a surface token: on the white card `cardAlt` sat at about
 * 1.06:1, and a half-done sync read as finished.
 */
@Composable
private fun ProgressCapsule(fraction: Float) {
    val track = if (AppTheme.isDark) Color.White.copy(alpha = 0.18f) else Color(0xFFD9D9DE)
    Box(Modifier.size(width = 44.dp, height = 6.dp).background(track, CircleShape)) {
        Box(Modifier.size(width = maxOf(4f, 44f * fraction.coerceIn(0f, 1f)).dp, height = 6.dp).background(AppTheme.accent, CircleShape))
    }
}

@Composable
private fun QueueSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountSectionLabel(title, Modifier.padding(start = 4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun OperationRow(op: SyncOperation, isFailed: Boolean, highlight: Boolean = false) {
    val colors = AppTheme.colors
    val strings = appStrings
    val disc = when {
        isFailed -> AppTheme.red.copy(alpha = 0.15f)
        highlight -> AppTheme.accent.copy(alpha = 0.18f)
        else -> colors.cardAlt
    }
    val ink = when {
        isFailed -> AppTheme.red
        highlight -> AppTheme.accent
        else -> colors.textSecondary
    }
    Row(Modifier.fillMaxWidth().surfaceCard(12.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(disc, CircleShape), contentAlignment = Alignment.Center) {
            AccountIcon(operationGlyph(op.entityType), ink, size = 17.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.entityLabel(op.entityType.rawValue), style = inter(14f, InterWeight.semibold), color = colors.text, maxLines = 1)
                Text("·", style = inter(14f), color = colors.textTertiary)
                Text(strings.actionLabel(op.action.rawValue), style = inter(13f), color = colors.textSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                // The least informative part: it truncates before the entity and the action.
                Text(op.entityId.toString().take(8).lowercase(), style = inter(11f, InterWeight.medium, monospacedDigits = true), color = colors.textTertiary, maxLines = 1)
            }
            if (op.retryCount > 0 || isFailed) {
                Text(strings.syncAttemptsLabel(maxOf(op.retryCount, 1)), style = inter(11f, InterWeight.medium), color = colors.textTertiary)
            }
            op.lastError?.takeIf { it.isNotEmpty() }?.let { error ->
                Text(error, style = inter(12f), color = AppTheme.red.copy(alpha = 0.85f), maxLines = 3)
            }
        }
    }
}

private fun operationGlyph(type: SyncEntityType): AccountGlyph = when (type) {
    SyncEntityType.trip -> AccountGlyph.flagFill
    SyncEntityType.vehicle -> AccountGlyph.car
    SyncEntityType.photo -> AccountGlyph.photoFill
    SyncEntityType.vehiclePhoto -> AccountGlyph.carCircleFill
    SyncEntityType.settings -> AccountGlyph.gearFill
    SyncEntityType.journey -> AccountGlyph.mapFill
    SyncEntityType.discovery -> AccountGlyph.sealFill
}

@Composable
private fun RetryButton(isRetrying: Boolean, onRetry: () -> Unit) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .plainPressFeedback(interaction)
            .background(AppTheme.accent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = !isRetrying, role = Role.Button) {
                haptics.tap()
                onRetry()
            }
            .padding(vertical = 12.dp)
            .testTag("sync_retry_button"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRetrying) Spinner(Color.White, size = 15.dp) else AccountIcon(AccountGlyph.arrowClockwise, Color.White, size = 17.dp)
        Text(strings.syncStatusRetry(), style = inter(15f, InterWeight.semibold), color = Color.White)
    }
}
