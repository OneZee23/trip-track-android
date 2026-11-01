// iOS: NotificationsInboxView.swift (header, chips, rows, decision row, follow toggle, routing),
// NotificationInviteRowModel.decisionLine
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.me.MeArtwork
import com.onezee.triptrack.feature.settings.NotificationPreferencesContent
import com.onezee.triptrack.feature.settings.NotificationPreferencesState
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.cards.ReactionEmoji
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** iOS InboxChipFilter: an in-memory filter over the loaded rows; unknown kinds stay under «Все». */
enum class InboxFilter { all, reactions, follows, comments, achievements, companions;

    fun matches(item: NotificationItem): Boolean = when (this) {
        all -> true
        reactions -> item.typedKind == NotificationKind.reaction
        follows -> item.typedKind == NotificationKind.follow
        comments -> item.typedKind == NotificationKind.comment
        achievements -> item.typedKind == NotificationKind.achievement
        companions -> item.typedKind == NotificationKind.companionInvite || item.typedKind == NotificationKind.companionAccepted
    }

    fun title(strings: AppStrings): String = when (this) {
        all -> strings.all(); reactions -> strings.chipReactions(); follows -> strings.chipFollows()
        comments -> strings.chipComments(); achievements -> strings.chipAchievements(); companions -> strings.chipCompanions()
    }
}

/**
 * iOS: NotificationsInboxView. What others did to one's trips and profile, newest first, today then
 * earlier. A row seen counts as read (batched); tapping a reaction, a comment or an accepted invite
 * opens the trip, a follow opens the profile. A pending companion invite is a decision card, and
 * its trip never opens before it is answered.
 */
@Composable
fun InboxScreen(social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val inbox = social.inbox
    val state by inbox.state.collectAsState()
    val answers by social.invites.answers.collectAsState()
    var filter by rememberSaveableFilter()
    var followed by remember { mutableStateOf(emptySet<UUID>()) }
    val previews = remember { mutableStateMapOf<UUID, InvitePreviewState>() }
    var resolving by remember { mutableStateOf(emptySet<UUID>()) }
    var preferencesOpen by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    val now = social.clock()
    LaunchedEffect(Unit) { inbox.refresh() }
    // Merged, not replaced: a toggle made in this session survives a refresh in flight.
    LaunchedEffect(state.items) { followed = followed + state.items.filter { it.isFollowing == true }.mapNotNull { it.actor?.id } }
    // Peek-and-close: what was on screen is reported before the debounce had its turn.
    DisposableEffect(Unit) { onDispose { inbox.flushOnDismiss() } }
    HideAppTabBar()

    fun answered(item: NotificationItem): CompanionStatus? = item.tripId?.let { trip -> social.invites.respondedStatus(trip, item.id) }.also { answers.size }
    fun openTrip(id: UUID, card: SocialFeedTrip? = null, focus: TripFocus = TripFocus.Top) { navigator.push(TripsDestination.TripDetail(id, social = card, focus = focus)) }
    fun tap(item: NotificationItem) {
        scope.launch { inbox.markRead(item) }
        when (item.typedKind) {
            NotificationKind.reaction, NotificationKind.comment, NotificationKind.companionAccepted, NotificationKind.achievement -> {
                val trip = item.tripId
                // An achievement's actor IS the recipient: with no trip it opens nothing rather than one's own profile.
                if (trip != null) openTrip(trip, focus = if (item.typedKind == NotificationKind.comment) item.commentId?.let { TripFocus.Comment(it) } ?: TripFocus.Comments else TripFocus.Top)
                else if (item.typedKind != NotificationKind.achievement) item.actor?.let { navigator.push(TripsDestination.PublicProfile(it.id, it)) }
            }
            NotificationKind.companionInvite -> {
                val trip = item.tripId ?: return
                if (answered(item) != CompanionStatus.accepted || trip in resolving) return
                resolving = resolving + trip
                scope.launch {
                    val found = try { social.invites.acceptedTrip(trip) } finally { resolving = resolving - trip }
                    if (found != null) openTrip(trip, found) else toast = ToastItem(ToastType.error, strings.companionTripUnavailable())
                }
            }
            NotificationKind.follow, null -> item.actor?.let { navigator.push(TripsDestination.PublicProfile(it.id, it)) }
        }
    }
    fun setFollow(user: UUID, follow: Boolean) {
        followed = if (follow) followed + user else followed - user
        scope.launch {
            try { social.api.follow(user, follow) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                // The server and the button already agree: a no-op, not a failure.
                if (error.serverCode != "ALREADY_FOLLOWING" && error.serverCode != "NOT_FOLLOWING") {
                    followed = if (follow) followed - user else followed + user
                    toast = ToastItem(ToastType.error, strings.proFailed())
                }
            }
        }
    }
    fun ensurePreview(item: NotificationItem) {
        val trip = item.tripId ?: return
        if (item.kind != NotificationKind.companionInvite.rawValue || answered(item) != null || previews[item.id] != null) return
        previews[item.id] = InvitePreviewState.Loading
        scope.launch {
            try { previews[item.id] = InvitePreviewState.Loaded(social.invites.preview(trip)) }
            catch (cancelled: CancellationException) { previews.remove(item.id); throw cancelled }
            catch (error: Exception) {
                NotificationInviteRows.stateAfterFailure(false, error.serverCode == "COMPANION_INVITE_NOT_FOUND")?.let { previews[item.id] = it }
            }
        }
    }
    fun respond(item: NotificationItem, accept: Boolean) {
        val trip = item.tripId ?: return
        if (trip in resolving) return
        resolving = resolving + trip
        haptics.action()
        scope.launch {
            try { social.invites.respond(trip, item.id, accept) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { toast = ToastItem(ToastType.error, strings.companionRespondFailed()) }
            finally { resolving = resolving - trip }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg).testTag("inbox")) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NavCircleButton(AppGlyph.chevronLeft, strings.back(), { navigator.pop() }, Modifier.testTag("social_back"))
                Text(strings.activityTitle(), Modifier.weight(1f), style = inter(22f, InterWeight.bold), letterSpacing = points(-.22f), color = colors.text, maxLines = 1)
                if (social.notificationPreferences != null) NavCircleButton(AppGlyph.gearshapeFill, strings.settingsNotifications(), { haptics.tap(); preferencesOpen = true }, Modifier.testTag("inbox_settings"))
            }
            if (state.items.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InboxFilter.entries.forEach { option ->
                    val on = filter == option
                    Text(option.title(strings), Modifier.background(colors.card, CapsuleShape).border(1.5.dp, if (on) AppTheme.accent else Color.Transparent, CapsuleShape)
                        .clickable(role = Role.Tab) { haptics.selection(); filter = option }.semantics { selected = on }
                        .padding(horizontal = 12.dp, vertical = 7.dp).testTag("notif_chip_${option.name}"),
                        style = inter(12.5f, if (on) InterWeight.bold else InterWeight.semibold), color = if (on) AppTheme.accent else colors.textSecondary)
                }
            }
            when {
                state.isLoading && state.items.isEmpty() -> SkeletonRows(6, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                state.loadFailed && state.items.isEmpty() -> SocialMessageState(strings.publicDataLoadFailed(), retry = { scope.launch { inbox.refresh() } }, tag = "inbox_error")
                state.items.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp).testTag("inbox_empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    SocialGlyphIcon(SocialGlyph.bell, null, tint = colors.textTertiary, size = 40.dp)
                    Text(strings.notificationsInboxNothingYet(), style = inter(16f, InterWeight.semibold), color = colors.textSecondary)
                    Text(strings.notificationsInboxWhenSomeoneReacts(), style = inter(13f), color = colors.textTertiary, textAlign = TextAlign.Center)
                }
                else -> {
                    val items = state.items.filter(filter::matches)
                    val zone = strings.zone
                    val today = now.atZone(zone).toLocalDate()
                    val (fresh, earlier) = items.partition { it.createdAt.atZone(zone).toLocalDate() == today }
                    com.onezee.triptrack.ui.PullToRefresh(refreshing = state.isLoading, onRefresh = { scope.launch { inbox.refresh() } }, modifier = Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (items.isEmpty()) item(key = "filtered_empty") {
                                Text(strings.noFilteredNotifications(), Modifier.fillMaxWidth().padding(top = 60.dp), style = inter(14f), color = colors.textSecondary, textAlign = TextAlign.Center)
                            }
                            listOf(strings.today() to fresh, strings.earlier() to earlier).forEach { (title, rows) ->
                                if (rows.isNotEmpty()) item(key = "header_$title") {
                                    Text(title.uppercased(strings.language), Modifier.padding(top = 6.dp, start = 2.dp), style = inter(11f, InterWeight.semibold), letterSpacing = points(.44f), color = colors.textTertiary)
                                }
                                items(rows, key = { it.id }) { item ->
                                    LaunchedEffect(item.id) {
                                        inbox.noteSeen(item)
                                        // The store pages off the FULL list's last row: a chip hiding the tail must hand that row over.
                                        inbox.loadMoreIfNeeded(if (item.id == items.lastOrNull()?.id) state.items.lastOrNull() ?: item else item)
                                    }
                                    val preview = previews[item.id] ?: InvitePreviewState.Loading
                                    if (NotificationInviteRows.isDecision(item, answered(item), preview)) {
                                        LaunchedEffect(item.id) { ensurePreview(item) }
                                        DecisionRow(item, preview, strings, now, onRetry = { previews.remove(item.id); ensurePreview(item) }, onRespond = { respond(item, it) })
                                    } else InfoRow(item, strings, now, actionLine(item, strings, answered(item), previews[item.id]),
                                        followedBack = item.actor?.id?.let { it in followed }, resolving = item.tripId in resolving,
                                        canTap = when (item.typedKind) {
                                            NotificationKind.companionInvite -> answered(item) == CompanionStatus.accepted && item.tripId != null
                                            NotificationKind.achievement -> item.tripId != null
                                            NotificationKind.follow, null -> item.actor != null
                                            else -> item.tripId != null || item.actor != null
                                        },
                                        onFollow = { follow -> item.actor?.let { setFollow(it.id, follow) } }, onTap = { haptics.tap(); tap(item) })
                                }
                            }
                        }
                    }
                }
            }
        }
        ToastHost(toast, onDismiss = { toast = null })
    }
    if (preferencesOpen) social.notificationPreferences?.let { factory ->
        val model = rememberLocalViewModel("inbox-preferences") { factory() }
        val prefs by model.state.collectAsState()
        ContentSizedSheet(onDismissRequest = { preferencesOpen = false }, background = colors.bg) {
            val sheet = this
            Column(Modifier.fillMaxWidth().padding(16.dp).testTag("inbox_preferences"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.settingsNotifications(), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                    SheetCloseCircle(onClick = { sheet.dismiss() })
                }
                Column(Modifier.surfaceCard(16.dp)) { NotificationPreferencesContent(prefs, model) }
            }
        }
    }
}

@Composable
private fun rememberSaveableFilter(): MutableState<InboxFilter> = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(InboxFilter.all) }

/** iOS infoRow: avatar with the kind on its corner, WHO, WHAT, WHEN; unread rows on a whisper of accent. */
@Composable
private fun InfoRow(item: NotificationItem, strings: AppStrings, now: Instant, action: String, followedBack: Boolean?, resolving: Boolean, canTap: Boolean,
                    onFollow: (Boolean) -> Unit, onTap: () -> Unit) {
    val colors = AppTheme.colors
    val accent = AppTheme.accent
    val content = @Composable {
        Row(Modifier.fillMaxWidth().surfaceCard(16.dp).background(if (item.isRead) Color.Transparent else accent.copy(alpha = .05f), RoundedCornerShape(16.dp))
            .drawBehind { if (!item.isRead) drawRoundRect(accent, Offset(0f, 14.dp.toPx()), Size(3.5.dp.toPx(), size.height - 28.dp.toPx()), CornerRadius(2.dp.toPx())) }
            .padding(11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            InboxAvatar(item)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(actorName(item, strings), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
                Text(action, style = inter(13f), color = colors.textSecondary, maxLines = 2)
                Text(RelativeTripDate.string(item.createdAt, now, strings), Modifier.padding(top = 1.dp), style = inter(11f), color = colors.textTertiary)
            }
            if (item.typedKind == NotificationKind.follow && item.actor != null && followedBack != null) {
                val interaction = remember { MutableInteractionSource() }
                Text(if (followedBack) strings.notificationsInboxFollowing() else strings.followBack(),
                    Modifier.width(110.dp).plainPressFeedback(interaction).background(if (followedBack) colors.cardAlt else accent, CapsuleShape)
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button) { onFollow(!followedBack) }
                        .padding(vertical = 8.dp).testTag("inbox_follow_back"),
                    style = inter(13f, InterWeight.semibold), color = if (followedBack) colors.text else Color.White, textAlign = TextAlign.Center, maxLines = 1)
            }
            if (resolving) com.onezee.triptrack.feature.account.Spinner(colors.textSecondary, Modifier.testTag("companion_invite_trip_loading"))
        }
    }
    if (canTap) PressableCard(onClick = onTap, modifier = Modifier.fillMaxWidth().testTag("inbox_row")) { content() }
    else Box(Modifier.fillMaxWidth().testTag("inbox_row")) { content() }
}

/** iOS decisionRow: the actor, the trip's shape from the invite preview, Decline and Accept. Nothing to open yet. */
@Composable
private fun DecisionRow(item: NotificationItem, preview: InvitePreviewState, strings: AppStrings, now: Instant, onRetry: () -> Unit, onRespond: (Boolean) -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(11.dp).testTag("companion_invite_row"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            InboxAvatar(item)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(actorName(item, strings), style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1)
                Text(strings.companionInviteAction(), style = inter(13f), color = colors.textSecondary)
            }
        }
        when (preview) {
            InvitePreviewState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.account.Spinner(colors.textSecondary) }
            InvitePreviewState.Failed, InvitePreviewState.Unavailable -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(AppGlyph.exclamationmarkTriangleFill, null, tint = AppTheme.red, size = 13.dp)
                Text(strings.companionInvitePreviewFailed(), Modifier.weight(1f), style = inter(12.5f), color = colors.textSecondary)
                Text(strings.retry(), Modifier.clickable(role = Role.Button, onClick = onRetry), style = inter(12.5f, InterWeight.semibold), color = AppTheme.accent)
            }
            is InvitePreviewState.Loaded -> Text(decisionLine(preview.preview, strings, now), style = inter(12.5f), color = colors.textSecondary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.companionDecline(), Modifier.weight(1f).background(colors.cardAlt, CapsuleShape).clickable(role = Role.Button) { onRespond(false) }
                .padding(vertical = 9.dp).testTag("companion_invite_decline"), style = inter(13f, InterWeight.semibold), color = colors.text, textAlign = TextAlign.Center)
            Text(strings.companionAccept(), Modifier.weight(1f).background(AppTheme.accent, CapsuleShape).clickable(role = Role.Button) { onRespond(true) }
                .padding(vertical = 9.dp).testTag("companion_invite_accept"), style = inter(13f, InterWeight.bold), color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

/** iOS NotificationInviteRowModel.decisionLine: «вчера · Адыгея · 142,3 км · 2 ч 10 мин», missing parts left out. */
@Composable
private fun decisionLine(preview: CompanionInvitePreview, strings: AppStrings, now: Instant): String = buildList {
    add(RelativeTripDate.string(preview.startDate, now, strings))
    RegionDisplay.localized(preview.region, strings.language)?.takeIf { it.isNotEmpty() }?.let(::add)
    add(Measure.distance(preview.distance, LocalDistanceUnit.current, strings.language.code, strings, Measure.Style.tenths))
    preview.duration?.takeIf { it > 0 }?.let { add(com.onezee.triptrack.feature.journey.ui.JourneyFormat.duration(it, strings)) }
}.joinToString(" · ")

/** iOS avatar(_:): an actor's emoji on a disc, a system row's on a tint; the kind as a badge on the corner. */
@Composable
private fun InboxAvatar(item: NotificationItem) {
    val colors = AppTheme.colors
    // An achievement arrives with an actor (the column is NOT NULL: it is the recipient) and is still a system row.
    val system = item.actor == null || item.typedKind == NotificationKind.achievement
    val fallback = if (item.typedKind == NotificationKind.achievement) "🏅" else "🚗"
    Box {
        Box(Modifier.size(40.dp).background(if (system) AppTheme.accent.copy(alpha = .15f) else colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) {
            Text(if (system) item.emoji ?: fallback else item.actor?.avatarEmoji ?: item.emoji ?: fallback, style = inter(20f))
        }
        val badge: @Composable () -> Unit = when (item.typedKind) {
            NotificationKind.reaction -> { { Text(ReactionEmoji.canonical(item.emoji ?: "🔥") ?: "🔥", style = inter(10f)) } }
            NotificationKind.follow -> { { SocialGlyphIcon(SocialGlyph.personPlus, null, tint = AppTheme.accent, size = 10.dp) } }
            NotificationKind.comment -> { { Glyph(AppGlyph.bubbleRight, null, tint = AppTheme.accent, size = 10.dp) } }
            NotificationKind.companionInvite, NotificationKind.companionAccepted -> { { SocialGlyphIcon(SocialGlyph.personTwo, null, tint = AppTheme.accent, size = 10.dp) } }
            NotificationKind.achievement -> { { SocialGlyphIcon(SocialGlyph.rosette, null, tint = AppTheme.accent, size = 10.dp) } }
            null -> { {} }
        }
        if (item.typedKind != null) Box(Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(18.dp).background(colors.card, CircleShape), contentAlignment = Alignment.Center) { badge() }
    }
}

/** A badge unlock is signed by the app: «Кто-то» would name a person who does not exist. */
private fun actorName(item: NotificationItem, strings: AppStrings): String =
    if (item.typedKind == NotificationKind.achievement) strings.tr(com.onezee.triptrack.designsystem.R.string.app_name)
    else item.actor?.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: strings.notificationsInboxSomeone()

/** iOS actionLine: the action as a plain phrase, the object in quotes. */
private fun actionLine(item: NotificationItem, strings: AppStrings, answered: CompanionStatus?, preview: InvitePreviewState?): String {
    val trip = item.tripTitle?.trim()?.takeIf { it.isNotEmpty() } ?: strings.activityYourTrip()
    return when (item.typedKind) {
        NotificationKind.reaction -> strings.activityReactedTo(trip)
        NotificationKind.follow -> strings.activityFollowedYou()
        NotificationKind.comment -> strings.activityCommented(item.commentText, trip)
        NotificationKind.companionAccepted -> strings.companionAcceptedAction()
        NotificationKind.achievement -> {
            // The badge by its id in the app's language; the server's title only for an id this build lacks.
            val name = item.badgeId?.trim()?.takeIf { it.isNotEmpty() }?.let { MeArtwork.badgeTitles[it] }?.let { strings.tr(it) }
                ?: item.tripTitle?.trim()?.takeIf { it.isNotEmpty() }
            if (name == null) strings.activityAchievementUnlockedGeneric() else strings.activityAchievementUnlocked(name)
        }
        NotificationKind.companionInvite -> when {
            // Answered on another device: this phone does not know which way, so the line stays neutral.
            preview == InvitePreviewState.Unavailable -> strings.companionInviteUnavailableNote()
            answered == CompanionStatus.declined -> strings.companionInviteDeclinedNote()
            else -> strings.companionInviteAcceptedNote()
        }
        null -> trip
    }
}
