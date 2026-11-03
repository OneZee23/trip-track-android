// iOS: Views/Trips/TripCompanionsSection.swift, CompanionsPickerSheet.swift, CompanionsRosterSheet.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.SignInAction
import com.onezee.triptrack.feature.account.SignInPromptSheet
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.model.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import java.util.UUID

@Composable
fun TripCompanionsSection(tripId: UUID, isOwn: Boolean, isOnServer: Boolean, cachedRoster: List<TripCompanion>, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var roster by remember(tripId) { mutableStateOf(cachedRoster.map { CompanionItem(it.accountId, it.displayName, it.avatarEmoji, it.status) }) }
    var loaded by remember(tripId) { mutableStateOf(false) }
    var failed by remember(tripId) { mutableStateOf(false) }
    var rosterOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CompanionItem?>(null) }
    suspend fun load() {
        if ((!isOwn || social.signedIn) && isOnServer) {
            try {
                val result = withContext(social.worker) { social.api.companions(tripId).items }
                    .filter { it.status != CompanionStatus.declined }
                roster = result
                withContext(social.worker) {
                    social.trips.repository.updateCompanions(tripId, result.map { TripCompanion(it.accountId, it.displayName, it.avatarEmoji, it.status) })
                }
                failed = false
                loaded = true
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; loaded = true }
        }
    }
    LaunchedEffect(tripId, social.signedIn, isOnServer) { load() }
    val show = isOwn || roster.isNotEmpty() || failed
    if (show) Column(Modifier.fillMaxWidth().testTag("trip_companions_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SocialSectionLabel(strings.companionsSection())
        Column(Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(14.dp)).testTag("companions_card")) {
            when {
                isOwn && !social.signedIn -> CompanionHint(strings.companionsSignInHint(), strings.signInShort(), "companions_sign_in") { signIn = true }
                isOwn && !isOnServer -> CompanionHint(strings.companionsPublishFirstHint(), null, "companions_not_published") {}
                !loaded -> Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.account.Spinner(AppTheme.colors.textSecondary, Modifier.size(18.dp)) }
                failed && roster.isEmpty() -> CompanionHint(strings.companionsLoadFailed(), strings.retry(), "companions_failed") { scope.launch { load() } }
                failed && roster.isNotEmpty() -> CompanionHint(strings.companionsCachedNotice(), strings.retry(), "companions_cached") { scope.launch { load() } }
                roster.isEmpty() && isOwn -> CompanionHint(strings.companionsEmptyHint(), strings.companionsAddPrompt(), "companions_empty") { pickerOpen = true }
                roster.isNotEmpty() -> Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { rosterOpen = true }.padding(horizontal = 16.dp, vertical = 14.dp).testTag("companions_summary"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(roster.firstOrNull()?.avatarEmoji, null, size = 34.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(roster.take(3).joinToString(", ") { it.displayName ?: strings.companionsNoName() }, style = inter(15f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 1)
                        Text(if (roster.size == 1) strings.companionsRodeWithYou() else strings.companionsRodeTogether(), style = inter(13f), color = AppTheme.colors.textTertiary)
                    }
                    Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 12.dp)
                }
            }
        }
    }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.companions, it, onDismissRequest = { signIn = false }) }
    if (rosterOpen) CompanionRosterSheet(tripId, isOwn, roster, social, onClose = { rosterOpen = false }, onInvite = { rosterOpen = false; pickerOpen = true }, onRemove = { removing = it }, onProfile = { item ->
        val author = SocialAuthor(item.accountId, item.displayName, item.avatarEmoji, 1)
        navigator.push(TripsDestination.PublicProfile(author.id, author))
    })
    if (pickerOpen) CompanionPickerSheet(tripId, roster.map { it.accountId }.toSet(), social, onClose = { pickerOpen = false }, onInvited = { item -> roster = roster.filterNot { it.accountId == item.accountId } + item; loaded = true })
    AppConfirm(isPresented = removing != null, onDismissRequest = { removing = null }, title = strings.companionsRemoveConfirmTitle(),
        actions = listOf(AppDialogAction(strings.companionsRemove(), AppDialogAction.Kind.destructive, testTag = "companion_remove_confirm") {
            val target = removing ?: return@AppDialogAction
            removing = null
            scope.launch { try { withContext(social.worker) { social.api.removeCompanion(tripId, target.accountId) }; roster = roster.filterNot { it.accountId == target.accountId } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } }
        }))
}

@Composable
private fun CompanionHint(message: String, action: String?, tag: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(message, style = inter(13f), color = AppTheme.colors.textSecondary)
        if (action != null) Text(action, Modifier.clickable(role = Role.Button, onClick = onClick), style = inter(13f, InterWeight.semibold), color = AppTheme.accent)
    }
}

@Composable
private fun CompanionRosterSheet(tripId: UUID, isOwn: Boolean, roster: List<CompanionItem>, social: SocialFeature, onClose: () -> Unit, onInvite: () -> Unit, onRemove: (CompanionItem) -> Unit, onProfile: (CompanionItem) -> Unit) {
    val strings = appStrings
    ContentSizedSheet(onDismissRequest = onClose, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        val sheet = this
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp, bottom = 16.dp).testTag("companions_roster")) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.companionsSection(), Modifier.weight(1f), style = inter(16f, InterWeight.bold), color = AppTheme.colors.text); SheetCloseCircle(onClick = { sheet.dismiss() }) }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roster, key = { it.accountId }) { item ->
                    Row(Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(14.dp)).clickable(role = Role.Button) { onProfile(item) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PersonAvatar(item.avatarEmoji, null)
                        Text(item.displayName ?: strings.companionsNoName(), Modifier.weight(1f), style = inter(14f, InterWeight.semibold), color = AppTheme.colors.text)
                        when (item.status) {
                            CompanionStatus.pending -> Text(strings.companionsWaiting(), style = inter(12f), color = AppTheme.colors.textTertiary)
                            CompanionStatus.declined -> Text(strings.companionsDeclinedNote(), style = inter(12f), color = AppTheme.colors.textTertiary)
                            CompanionStatus.accepted -> Unit
                        }
                        if (isOwn) Text(strings.companionsRemove(), Modifier.clickable(role = Role.Button) { onRemove(item) }, style = inter(12f, InterWeight.semibold), color = AppTheme.red)
                    }
                }
                if (isOwn) item { Text(strings.companionsInvite(), Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onInvite).background(AppTheme.colors.card, RoundedCornerShape(14.dp)).padding(15.dp).testTag("companions_invite"), style = inter(14f, InterWeight.bold), color = AppTheme.accent, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun CompanionPickerSheet(tripId: UUID, invitedIds: Set<UUID>, social: SocialFeature, onClose: () -> Unit, onInvited: (CompanionItem) -> Unit) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<CompanionCandidate>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    suspend fun search(term: String, cursor: String? = null, append: Boolean = false) {
        if (append) loadingMore = true else { loading = true; candidates = emptyList(); nextCursor = null }
        failed = false
        try {
            val page = withContext(social.worker) { social.api.companionCandidates(tripId, term.ifBlank { null }, cursor) }
            candidates = if (append) (candidates + page.items).distinctBy { it.accountId } else page.items
            nextCursor = page.nextCursor
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false; loadingMore = false }
    }
    val listState = remember { LazyListState() }
    LaunchedEffect(tripId, query) { if (query.isNotEmpty()) delay(300); search(query) }
    LaunchedEffect(listState, query, nextCursor, candidates.size, loadingMore) {
        if (nextCursor == null || loadingMore) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .filterNotNull().distinctUntilChanged().collect { lastVisible ->
                if (lastVisible >= candidates.size - 2) search(query, nextCursor, append = true)
            }
    }
    ContentSizedSheet(onDismissRequest = onClose, background = AppTheme.colors.bg, detent = SheetDetent.large) {
        val sheet = this
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 22.dp, bottom = 16.dp).testTag("companions_picker")) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(strings.companionsPickerTitle(), Modifier.weight(1f), style = inter(16f, InterWeight.bold), color = AppTheme.colors.text); SheetCloseCircle(onClick = { sheet.dismiss() }) }
            Spacer(Modifier.height(12.dp))
            BasicTextField(query, { query = it }, Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(12.dp)).padding(14.dp).testTag("companions_picker_search_field"),
                textStyle = inter(15f).copy(color = AppTheme.colors.text), cursorBrush = SolidColor(AppTheme.accent), singleLine = true,
                decorationBox = { inner -> Box { if (query.isEmpty()) Text(strings.companionsSearchPlaceholder(), style = inter(15f), color = AppTheme.colors.textTertiary); inner() } })
            when {
                loading && candidates.isEmpty() -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.account.Spinner(AppTheme.colors.textSecondary, Modifier.size(20.dp)) }
                failed && candidates.isEmpty() -> CompanionHint(strings.companionsCandidatesLoadFailed(), strings.retry(), "companions_picker_error") { scope.launch { search(query) } }
                candidates.isEmpty() -> CompanionHint(strings.companionsCandidatesEmptyTitle(), strings.companionsCandidatesEmptyHint(), "companions_picker_empty") {}
                else -> LazyColumn(Modifier.heightIn(max = 440.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates.filterNot { it.accountId in invitedIds }, key = { it.accountId }) { candidate ->
                        Row(Modifier.fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PersonAvatar(candidate.avatarEmoji, null)
                            Column(Modifier.weight(1f)) { Text(candidate.displayName ?: strings.companionsNoName(), style = inter(13f, InterWeight.bold), color = AppTheme.colors.text); Text("LVL ${candidate.profileLevel}", style = inter(11f), color = AppTheme.colors.textTertiary) }
                            Text(if (candidate.accountId in busy) strings.companionsInvited() else strings.companionsInvite(), Modifier.clickable(enabled = candidate.accountId !in busy, role = Role.Button) {
                                busy = busy + candidate.accountId
                                scope.launch { try {
                                    val result = withContext(social.worker) { social.api.inviteCompanion(tripId, candidate.accountId) }
                                    onInvited(CompanionItem(result.accountId, candidate.displayName, candidate.avatarEmoji, result.status))
                                } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failed = true } finally { busy = busy - candidate.accountId } }
                            }.testTag("invite_${candidate.accountId}"), style = inter(12f, InterWeight.bold), color = AppTheme.accent)
                        }
                    }
                    if (loadingMore) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.account.Spinner(AppTheme.colors.textSecondary, Modifier.size(18.dp)) } }
                }
            }
        }
    }
}
