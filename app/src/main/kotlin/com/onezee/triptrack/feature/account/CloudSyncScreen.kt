// iOS: CloudSyncView.swift
package com.onezee.triptrack.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.AppConfirmDialog
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.NavCircleButton
import com.onezee.triptrack.designsystem.components.SheetDetent
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics

/** «Account & Sync» with its view model; a house sheet from the settings row that opens it. */
@Composable
fun CloudSyncRoute(viewModel: AccountViewModel, onDismissRequest: () -> Unit, onOpenBlocked: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberHaptics()
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        val sheet = this
        LaunchedEffect(state.closed) { if (state.closed) sheet.dismiss() }
        CloudSyncContent(
            state = state,
            onClose = { sheet.dismiss() },
            onCloudSync = viewModel::setCloudSync,
            onOpenStatus = { viewModel.showStatus(true) },
            onPublicProfile = { viewModel.setPublicProfile(it) { haptics.error() } },
            onOpenBlocked = onOpenBlocked,
            onSignOut = viewModel::requestSignOut,
            onWipeServer = viewModel::requestWipeServer,
            onDeleteAccount = viewModel::requestDeleteAccount,
        )
    }
    AccountDialogs(state.dialog, viewModel, onDeleteFailed = { haptics.error() })
    if (state.showsStatus) {
        SyncStatusSheet(state, onRetry = viewModel::retryFailedNow, onDismissRequest = { viewModel.showStatus(false) })
    }
}

/** iOS: the four `.appConfirm` of CloudSyncView, one at a time. */
@Composable
private fun AccountDialogs(dialog: AccountDialog?, viewModel: AccountViewModel, onDeleteFailed: () -> Unit) {
    val strings = appStrings
    when (dialog) {
        null -> Unit
        AccountDialog.EnableCloudSync -> AppConfirmDialog(
            title = strings.syncEnableConfirmTitle(),
            message = strings.syncEnableConfirmBody(),
            actions = listOf(AppDialogAction(strings.syncEnableConfirmAction(), handler = viewModel::confirmEnableCloudSync)),
            onDismiss = viewModel::dismissDialog,
        )
        is AccountDialog.SignOut -> AppConfirmDialog(
            title = if (dialog.publishedTrips > 0) strings.signOutPublishedTitle() else strings.signOutConfirmTitle(),
            message = if (dialog.publishedTrips > 0) strings.publishedTripsSignOutMessage(dialog.publishedTrips) else strings.signOutConfirmMessage(),
            actions = if (dialog.publishedTrips > 0) listOf(
                AppDialogAction(strings.signOutHidePublic(), AppDialogAction.Kind.destructive, "account_signout_hide") { viewModel.signOut(hidePublic = true) },
                AppDialogAction(strings.signOutKeepPublic(), AppDialogAction.Kind.plain, "account_signout_keep") { viewModel.signOut(hidePublic = false) },
            ) else listOf(
                AppDialogAction(strings.signOut(), AppDialogAction.Kind.destructive, "account_signout_confirm") { viewModel.signOut(hidePublic = false) },
            ),
            onDismiss = viewModel::dismissDialog,
        )
        // Play's account deletion rule, like Apple 5.1.1(v): closable from inside the app, in as
        // few taps as opening this screen took, with copy naming everything that goes.
        AccountDialog.DeleteAccount -> AppConfirmDialog(
            title = strings.deleteAccountConfirmTitle(),
            message = strings.deleteAccountConfirmBody(),
            actions = listOf(AppDialogAction(strings.deleteAccountConfirmAction(), AppDialogAction.Kind.destructive, "account_delete_confirm") {
                viewModel.deleteAccount(onDeleteFailed)
            }),
            onDismiss = viewModel::dismissDialog,
        )
        AccountDialog.WipeServer -> AppConfirmDialog(
            title = strings.wipeServerConfirmTitle(),
            message = strings.wipeServerConfirmBody(),
            actions = listOf(AppDialogAction(strings.wipeServerConfirmAction(), AppDialogAction.Kind.destructive, "account_wipe_confirm", viewModel::wipeServer)),
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** The screen without its sheet and model, for previews and screenshots. */
@Composable
fun SheetScope.CloudSyncContent(
    state: AccountScreenState,
    onClose: () -> Unit,
    onCloudSync: (Boolean) -> Unit,
    onOpenStatus: () -> Unit,
    onPublicProfile: (Boolean) -> Unit,
    onOpenBlocked: () -> Unit,
    onSignOut: () -> Unit,
    onWipeServer: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val colors = AppTheme.colors
    val strings = appStrings
    NavRow(strings.accountSyncTitle(), onClose)
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(top = 4.dp, bottom = 96.dp)
            .testTag("account_sync_screen"),
    ) {
        AccountCard(state)
        SectionLabel(strings.sectionSyncLabel(), top = 14)
        Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
            AccountSettingsRow(
                icon = AccountGlyph.globe,
                title = strings.cloudSyncTitle(),
                subtitle = state.lastSyncedAt?.let { strings.syncUpdatedAgo(RelativeTripDate.string(it, strings)) }
                    ?: if (state.cloudSyncEnabled) strings.syncStateOn() else strings.syncStateOff(),
                showsDivider = true,
            ) {
                HouseSwitch(state.cloudSyncEnabled, onCloudSync, Modifier.testTag("sync_cloud_toggle").semantics { contentDescription = strings.cloudSyncTitle() })
            }
            AccountSettingsRow(
                icon = AccountGlyph.syncPath,
                title = strings.syncPerItemStatus(),
                onClick = onOpenStatus,
                modifier = Modifier.testTag("sync_status_row"),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.syncState.label(strings), style = inter(13f, monospacedDigits = true), color = colors.textSecondary, maxLines = 1)
                    Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 18.dp)
                }
            }
        }
        if (state.auth.isSignedIn) {
            SectionLabel(strings.sectionPrivacyLabel())
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                // Only after a successful /auth/me: an old server silently drops unknown fields, and
                // an ungated switch would fake-succeed.
                if (state.auth.isPublicProfile != null) {
                    AccountSettingsRow(
                        icon = AccountGlyph.lockFill,
                        title = strings.publicProfileTitle(),
                        subtitle = strings.publicProfileSubtitle(),
                        showsDivider = true,
                    ) {
                        HouseSwitch(state.publicProfile, onPublicProfile, Modifier.testTag("privacy_public_toggle").semantics { contentDescription = strings.publicProfileTitle() })
                    }
                }
                AccountSettingsRow(
                    icon = AccountGlyph.noSign,
                    title = strings.blockedUsersShort(),
                    onClick = onOpenBlocked,
                    modifier = Modifier.testTag("privacy_blocked_row"),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        state.blockedCount?.let { Text("$it", style = inter(13f, monospacedDigits = true), color = colors.textSecondary) }
                        Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 18.dp)
                    }
                }
            }
            SectionLabel(strings.sectionAccountLabel())
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                AccountSettingsRow(
                    icon = AccountGlyph.arrowDownToLine,
                    title = strings.signOut(),
                    subtitle = strings.signOutSubtitle(),
                    showsDivider = true,
                    onClick = onSignOut,
                    modifier = Modifier.testTag("account_signout_row"),
                )
                AccountSettingsRow(
                    icon = AccountGlyph.warning,
                    iconColor = AppTheme.red,
                    title = if (state.isWipingServer) strings.clearServerInProgress() else strings.clearServerTitle(),
                    titleColor = AppTheme.red,
                    subtitle = strings.clearServerSubtitle(),
                    onClick = onWipeServer,
                    enabled = !state.isWipingServer,
                    modifier = Modifier.testTag("account_clear_server_row"),
                ) {
                    if (state.isWipingServer) Spinner(AppTheme.red, 14.dp)
                }
                // Canon's 8 pt danger break: the last row is the end of the account, not one more setting.
                Box(Modifier.fillMaxWidth().height(8.dp).background(colors.bg))
                AccountSettingsRow(
                    icon = AccountGlyph.noSign,
                    iconColor = AppTheme.red,
                    title = strings.deleteAccountTitle(),
                    titleColor = AppTheme.red,
                    subtitle = strings.deleteAccountSubtitle(),
                    onClick = onDeleteAccount,
                    enabled = !state.isDeletingAccount,
                    modifier = Modifier.testTag("account_delete_row"),
                ) {
                    if (state.isDeletingAccount) Spinner(AppTheme.red, 14.dp)
                }
            }
            // A failure of one object no longer stays silent: the button promised an empty server.
            if (state.wipeServerFailures != null) ErrorLine(strings.wipeServerPartial(), "wipe_server_partial")
            if (state.deleteAccountFailed) ErrorLine(strings.deleteAccountFailed(), "delete_account_failed")
        }
    }
}

/** iOS: CustomNavBar in a sheet: the chevron closes, the title stays optically centred. */
@Composable
private fun NavRow(title: String, onClose: () -> Unit) {
    val strings = appStrings
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        NavCircleButton(AppGlyph.chevronLeft, strings.back(), onClose, Modifier.align(Alignment.CenterStart).testTag("account_sync_back"))
        Text(title, style = AppType.navTitle, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 52.dp))
    }
}

/** The 4 pt leading pad of iOS pushed labels past the card edge; the rhythm is 14 then 18 above, 8 below. */
@Composable
private fun SectionLabel(text: String, top: Int = 18) {
    AccountSectionLabel(text, Modifier.padding(top = top.dp, bottom = 8.dp))
}

@Composable
private fun ErrorLine(text: String, tag: String) {
    Text(text, style = inter(12f), color = AppTheme.red, modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp).testTag(tag))
}

/** iOS: accountCard. The masked email backs the subtitle; without one, the provider alone. */
@Composable
private fun AccountCard(state: AccountScreenState) {
    val colors = AppTheme.colors
    val strings = appStrings
    val email = state.auth.userEmail?.takeIf { it.isNotBlank() }
    val subtitle = if (email != null) stringResource(R.string.android_google_account_line, AccountFormat.maskedEmail(email))
        else stringResource(R.string.android_google_account_only)
    Row(
        Modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("account_identity_card"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            if (state.avatarEmoji.startsWith("pixel_car_")) {
                Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pixel_car), contentDescription = null, contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None, modifier = Modifier.size(width = 32.dp, height = 20.dp))
            } else Text(state.avatarEmoji, style = inter(24f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.auth.userName ?: strings.profile(), style = inter(15f, InterWeight.bold), color = colors.text, maxLines = 1)
            Text(subtitle, style = inter(12f), color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

/**
 * iOS: SyncStatusMarker. Dot and one word in the trailing slot of the settings row that opens
 * «Account & Sync»; a spinner while a pass runs.
 */
@Composable
fun SyncStatusMarker(state: SyncState, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val strings = appStrings
    Row(modifier.testTag("settings_sync_marker"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state is SyncState.Syncing) Spinner(colors.textSecondary, 9.dp)
        else Box(Modifier.size(7.dp).background(state.tint(colors), CircleShape))
        Text(state.shortLabel(strings), style = inter(13f, InterWeight.medium, monospacedDigits = true), color = colors.textSecondary, maxLines = 1)
    }
}
