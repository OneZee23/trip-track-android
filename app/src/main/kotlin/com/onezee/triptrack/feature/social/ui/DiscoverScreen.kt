// iOS: Views/Social/DiscoverView.swift
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.account.SignInAction
import com.onezee.triptrack.feature.account.SignInPromptSheet
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.SocialAuthor
import com.onezee.triptrack.model.SocialSuggestedUser
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.ui.EmptyStateIllustration
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiscoverScreen(social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SocialAuthor>>(emptyList()) }
    var suggested by remember { mutableStateOf<List<SocialSuggestedUser>>(emptyList()) }
    var loadingSuggested by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    var followed by remember { mutableStateOf<Set<java.util.UUID>>(emptySet()) }
    var signIn by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    HideAppTabBar()

    LaunchedEffect(social) {
        try { suggested = withContext(social.worker) { social.api.suggestedPeople().users } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { suggested = emptyList() }
        finally { loadingSuggested = false }
    }
    LaunchedEffect(query) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isEmpty()) { results = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(300)
        try { results = withContext(social.worker) { social.api.searchPeople(normalized).users } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { results = emptyList() }
        finally { searching = false }
    }

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("discover_screen")) {
        CustomNavBar(strings.findPeople(), onBack = { navigator.pop() })
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(colors.card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(AppGlyph.magnifyingglass, null, tint = colors.textTertiary, size = 18.dp)
                    BasicTextField(query, { query = it }, Modifier.weight(1f).testTag("discover_search_field"),
                        textStyle = inter(15f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent), singleLine = true,
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text(strings.discoverSearchByName(), style = inter(15f), color = colors.textTertiary); inner() } })
                    if (query.isNotEmpty()) Text("×", Modifier.clickable(role = Role.Button) { query = "" }.testTag("discover_clear"), style = inter(19f), color = colors.textTertiary)
                }
            }
            item { SocialSectionLabel(if (query.isBlank()) strings.suggestedByRegions() else strings.discoverResults(), Modifier.padding(horizontal = 16.dp)) }
            if (query.isBlank()) {
                when {
                    loadingSuggested && suggested.isEmpty() -> item { SkeletonRows(5, Modifier.padding(horizontal = 16.dp)) }
                    suggested.isEmpty() -> item { DiscoverEmpty(strings.discoverNoSuggestionsYet(), strings.discoverWhenNewDrivers(), false) }
                    else -> items(suggested, key = { it.id }) { user ->
                        DiscoverPersonRow(user.asAuthor(), user.matchReason, user.totalKm, followed.contains(user.id), social,
                            onOpen = { navigator.push(TripsDestination.PublicProfile(user.id, user.asAuthor())) },
                            onFollow = { if (!social.signedIn) signIn = true else toggleFollow(user.id, followed, { followed = it }, social, scope) })
                    }
                }
            } else {
                when {
                    searching && results.isEmpty() -> item { SkeletonRows(5, Modifier.padding(horizontal = 16.dp)) }
                    results.isEmpty() -> item { DiscoverEmpty(strings.discoverNoUsersFound(), strings.discoverTryADifferent(), true) }
                    else -> items(results, key = { it.id }) { user ->
                        DiscoverPersonRow(user, null, null, followed.contains(user.id), social,
                            onOpen = { navigator.push(TripsDestination.PublicProfile(user.id, user)) },
                            onFollow = { if (!social.signedIn) signIn = true else toggleFollow(user.id, followed, { followed = it }, social, scope) })
                    }
                }
            }
        }
    }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.follow, it, onDismissRequest = { signIn = false }) }
}

private fun toggleFollow(id: java.util.UUID, current: Set<java.util.UUID>, update: (Set<java.util.UUID>) -> Unit, social: SocialFeature, scope: kotlinx.coroutines.CoroutineScope) {
    val wasFollowing = id in current
    update(if (wasFollowing) current - id else current + id)
    scope.launch {
        try { withContext(social.worker) { social.api.follow(id, !wasFollowing) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { update(if (wasFollowing) current else current - id) }
    }
}

private fun SocialSuggestedUser.asAuthor() = SocialAuthor(id, displayName, avatarEmoji, profileLevel, isPlus, avatarFrame)

@Composable
private fun DiscoverPersonRow(user: SocialAuthor, reason: String?, totalKm: Double?, isFollowed: Boolean, social: SocialFeature, onOpen: () -> Unit, onFollow: () -> Unit) {
    val strings = appStrings
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(AppTheme.colors.card, RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PersonAvatar(user.avatarEmoji, user.avatarFrame)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PersonName(user, inter(13.5f, InterWeight.bold), user.id == social.accountId, social.showsOwnBadge())
            reason?.let { Text(when (it) { "sharedRegion" -> strings.suggestReasonSharedRegion(); "nearby" -> strings.suggestReasonNearby(); "popular" -> strings.suggestReasonPopular(); else -> "" }, style = inter(11.5f), color = AppTheme.colors.textSecondary, maxLines = 1) }
            Text("LVL ${user.profileLevel}" + (totalKm?.takeIf { it > 0 }?.let { " · ${Measure.distanceKm(it, LocalDistanceUnit.current, strings.language.code, strings, Measure.Style.grouped)}" } ?: ""), style = inter(11f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.textTertiary)
        }
        Text(if (isFollowed) strings.notificationsInboxFollowing() else strings.discoverFollow(), Modifier.width(122.dp).clickable(role = Role.Button, onClick = onFollow).background(if (isFollowed) AppTheme.colors.cardAlt else AppTheme.accent, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 9.dp),
            style = inter(12f, InterWeight.bold), color = if (isFollowed) AppTheme.colors.text else androidx.compose.ui.graphics.Color.White, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun DiscoverEmpty(title: String, body: String, search: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 20.dp).testTag(if (search) "discover_empty_results" else "discover_empty_suggestions"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (search) EmptyStateIllustration(R.drawable.empty_search, size = 132.dp)
        else SocialGlyphIcon(SocialGlyph.personTwo, null, tint = AppTheme.colors.textTertiary, size = 30.dp)
        Text(title, style = inter(14f, InterWeight.semibold), color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
        Text(body, style = inter(12f), color = AppTheme.colors.textTertiary, textAlign = TextAlign.Center)
    }
}
