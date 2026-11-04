// iOS: BlockedListView.swift
package com.onezee.triptrack.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.ContentSizedSheet
import com.onezee.triptrack.designsystem.components.NavCircleButton
import com.onezee.triptrack.designsystem.components.SheetDetent
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.model.BlockedUser
import com.onezee.triptrack.model.EmptyRequest
import com.onezee.triptrack.model.SocialBlockRequest
import com.onezee.triptrack.model.SocialBlockResponse
import com.onezee.triptrack.model.SocialBlockedListResponse
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.network.ApiClient
import com.onezee.triptrack.network.ApiEndpoint
import com.onezee.triptrack.network.NetworkJson
import com.onezee.triptrack.ui.AvatarGlyph
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** iOS: BlockedListView's networking. */
class BlockedUsers(private val client: ApiClient) {
    suspend fun load(): List<BlockedUser> = client.post<EmptyRequest, SocialBlockedListResponse>(ApiEndpoint.socialBlocked, EmptyRequest()).users
    suspend fun unblock(id: UUID) { client.post<SocialBlockRequest, SocialBlockResponse>(ApiEndpoint.socialUnblock, SocialBlockRequest(id)) }
}

/**
 * iOS: BlockedListView, pushed from the privacy card of «Account & Sync»; here a sheet over it with
 * the same back chevron. Blocking removed follows both ways and unblocking does NOT restore them;
 * the person reappears in the feed after its next refresh.
 */
@Composable
fun BlockedListSheet(users: BlockedUsers, onDismissRequest: () -> Unit) {
    var list by remember { mutableStateOf<List<BlockedUser>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<com.onezee.triptrack.designsystem.components.ToastItem?>(null) }
    val strings = appStrings
    var pending by remember { mutableStateOf<UUID?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    suspend fun load() {
        loading = true
        try { list = users.load(); failed = false }
        catch (cancelled: CancellationException) { throw cancelled }
        // Rows already loaded stay on a failed refresh; the error replaces only the empty list.
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load() }
    ContentSizedSheet(onDismissRequest = onDismissRequest, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        BlockedListContent(
            users = list, loading = loading, failed = failed, pending = pending,
            onRetry = { scope.launch { load() } },
            onUnblock = { user ->
                pending = user.id
                scope.launch {
                    try {
                        users.unblock(user.id)
                        list = list.filterNot { it.id == user.id }
                        haptics.success()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { toast = com.onezee.triptrack.designsystem.components.ToastItem(com.onezee.triptrack.designsystem.components.ToastType.error, strings.proFailed()) }
                    finally { pending = null }
                }
            },
        )
        com.onezee.triptrack.designsystem.components.ToastHost(toast, onDismiss = { toast = null })
    }
}

@Composable
fun SheetScope.BlockedListContent(
    users: List<BlockedUser>,
    loading: Boolean,
    failed: Boolean,
    pending: UUID?,
    onRetry: () -> Unit,
    onUnblock: (BlockedUser) -> Unit,
) {
    val colors = AppTheme.colors
    val strings = appStrings
    val sheet = this
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
        NavCircleButton(AppGlyph.chevronLeft, strings.back(), { sheet.dismiss() }, Modifier.align(Alignment.CenterStart))
        Text(strings.blockedUsersShort(), style = AppType.navTitle, color = colors.text, maxLines = 1)
    }
    Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 12.dp, bottom = 32.dp)
            .testTag("blocked_list_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            loading && users.isEmpty() -> com.onezee.triptrack.feature.social.ui.SkeletonRows(4, Modifier.padding(top = 12.dp))
            // Failed is not empty: an offline fetch must not claim «you haven't blocked anyone».
            failed && users.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 60.dp).testTag("blocked_error_state"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.onezee.triptrack.ui.EmptyStateIllustration(com.onezee.triptrack.R.drawable.empty_offline, size = 148.dp)
                Text(strings.blockedListLoadFailed(), style = inter(13f), color = colors.textTertiary, textAlign = TextAlign.Center)
                SmallButton(strings.retry(), Modifier.testTag("blocked_retry_button"), shape = CircleShape, onClick = onRetry)
            }
            users.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 60.dp).testTag("blocked_empty_state"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.onezee.triptrack.ui.EmptyStateIllustration(com.onezee.triptrack.R.drawable.empty_followers, size = 148.dp)
                Text(strings.blockedListYouHavenT(), style = inter(13f), color = colors.textTertiary, textAlign = TextAlign.Center)
            }
            else -> users.forEach { user -> BlockedRow(user, pending == user.id, strings) { onUnblock(user) } }
        }
    }
}

@Composable
private fun BlockedRow(user: BlockedUser, isPending: Boolean, strings: AppStrings, onUnblock: () -> Unit) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            AvatarGlyph(user.avatarEmoji ?: "🚗", 22f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.displayName ?: strings.blockedListUser(), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
            // «Blocked <date>» when the server sends `blockedAt`; an older server gets the level line.
            val since = blockedSince(user, strings)
            if (since != null) Text(since, style = inter(11.5f), color = colors.textTertiary)
            else Text(strings.tr(com.onezee.triptrack.designsystem.R.string.android_level_label, user.profileLevel), style = inter(11f, InterWeight.semibold, monospacedDigits = true), color = colors.textTertiary)
        }
        if (isPending) Box(Modifier.size(width = 90.dp, height = 30.dp), contentAlignment = Alignment.Center) { Spinner(colors.textSecondary, size = 15.dp) }
        else SmallButton(strings.blockProfileUnblock(), Modifier.testTag("blocked_unblock_button"), onClick = onUnblock)
    }
}

@Composable
private fun SmallButton(title: String, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp), onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Text(
        title,
        style = inter(12f, InterWeight.semibold),
        color = colors.text,
        modifier = modifier
            .plainPressFeedback(interaction)
            .background(colors.cardAlt, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun blockedSince(user: BlockedUser, strings: AppStrings): String? {
    val raw = user.blockedAt ?: return null
    val date = runCatching { NetworkJson.wire.decodeFromJsonElement(IsoInstantSerializer, JsonPrimitive(raw)) }.getOrNull() ?: return null
    return strings.blockedSince(LocalizedDateFormatter.patterns("d MMM yyyy").getValue(strings.language).format(date))
}
