// iOS: ProfileView.guestSignInCard, ProfileSettingsSheet (the «Аккаунт и синхронизация» row with its
// SyncStatusMarker), CloudSyncView presentation
package com.onezee.triptrack.feature.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.appStrings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

/**
 * The account's place on «Я» until the designed profile arrives: a guest (or a soft-expired
 * session) sees the sign-in card, a signed-in person the «Account & Sync» row with its live marker,
 * which opens the screen as a sheet.
 */
@Composable
fun AccountEntry(graph: AppGraph, modifier: Modifier = Modifier) {
    val auth by graph.auth.state.collectAsState()
    val syncState by remember { syncStateFlow(graph.settingsManager, graph.syncHost) }.collectAsState(SyncState.Off)
    var showsAccount by rememberSaveable { mutableStateOf(false) }
    var showsBlocked by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (auth.isSignedIn) {
            Column(Modifier.fillMaxWidth().surfaceCard(16.dp)) {
                AccountSettingsRow(
                    icon = AccountGlyph.cloudFill,
                    iconColor = AppTheme.accent,
                    title = appStrings.accountSyncTitle(),
                    onClick = { showsAccount = true },
                    modifier = Modifier.testTag("settings_account_sync"),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        SyncStatusMarker(syncState)
                        Glyph(AppGlyph.chevronRight, contentDescription = null, tint = AppTheme.colors.textTertiary, size = 18.dp)
                    }
                }
            }
        } else {
            GuestSignInCard(
                expired = auth.needsReauth,
                isAuthenticating = auth.isAuthenticating,
                onSignIn = { context, flow, done -> graph.auth.launchSignIn(context, flow, graph.auth.serviceScope, done) },
            )
        }
    }
    if (showsAccount) {
        val viewModel = remember { graph.accountViewModel() }
        DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }
        CloudSyncRoute(viewModel, onDismissRequest = { showsAccount = false }, onOpenBlocked = { showsBlocked = true })
        if (showsBlocked) {
            val blocked = remember { BlockedUsers(graph.apiClient) }
            BlockedListSheet(blocked, onDismissRequest = {
                showsBlocked = false
                // iOS re-reads on the way back: an unblock changed the count.
                viewModel.refresh()
            })
        }
    }
}
