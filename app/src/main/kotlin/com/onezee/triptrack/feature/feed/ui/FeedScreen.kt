package com.onezee.triptrack.feature.feed.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.R
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.AppConfirm
import com.onezee.triptrack.designsystem.components.AppDialogAction
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.components.glassBackground
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.navigation.TabBarMetrics
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.navigation.tabBarTappableInset
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.feature.asPreviewTrip
import com.onezee.triptrack.feature.feed.FeedMode
import com.onezee.triptrack.feature.feed.FeedViewModel
import com.onezee.triptrack.feature.feed.MineRowState
import com.onezee.triptrack.feature.feed.SocialCardState
import com.onezee.triptrack.feature.feed.SocialFeedState
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.LoadingMoreRow
import com.onezee.triptrack.ui.PullToRefresh
import com.onezee.triptrack.feature.social.LocalSocial
import com.onezee.triptrack.feature.social.ui.ReactionsListSheet
import com.onezee.triptrack.ui.reactions.ReactionPickerOverlay
import com.onezee.triptrack.ui.EmptyStateIllustration
import com.onezee.triptrack.ui.IdleRing
import com.onezee.triptrack.ui.cards.SkeletonCards
import com.onezee.triptrack.ui.cards.SocialFeedCard
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS: FeedView.swift. Лента: the public social feed in two segments, «Все» and «Подписки», paged
 * sideways under a fixed switcher. One's own trips live in Я (iOS retired the «Мои» segment); they
 * reach this feed when public, with their own card menu.
 *
 * [offline] decides the strip under the title; [onSignIn] is the sign-in door of the account wave,
 * and every control that needs it stays hidden while it is null (a press that does nothing is worse
 * than no button). [onOpenRecord] switches to the record tab from the recording banner.
 */
@Composable
fun FeedScreen(
    feature: TripsFeature,
    scrollToTop: Int,
    offline: Boolean,
    onOpenRecord: () -> Unit,
    onOpenProfileTab: () -> Unit,
    modifier: Modifier = Modifier,
    onSignIn: (() -> Unit)? = null,
    now: () -> Instant = Instant::now,
) {
    val vm = feature.feed
    val state by vm.state.collectAsState()
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val signedIn = feature.signedIn()
    val navigator = feature.feedNavigator

    val pager = rememberPagerState(initialPage = if (feature.feedFollowing) 1 else 0) { 2 }
    val mode = if (pager.currentPage == 1) FeedMode.following else FeedMode.all
    val allList = rememberLazyListState()
    val followingList = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.loadTrips()
        vm.show(mode)
    }
    LaunchedEffect(mode) {
        feature.feedFollowing = mode == FeedMode.following
        vm.show(mode)
    }
    // A tap on the title, a pill or the open Лента tab: back to the top of the visible page.
    suspend fun toTop() = (if (mode == FeedMode.following) followingList else allList).animateScrollToItem(0)
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) toTop() }

    var pendingPrivate by remember { mutableStateOf<SocialCardState?>(null) }
    var pendingDelete by remember { mutableStateOf<SocialCardState?>(null) }
    var pickerFor by remember { mutableStateOf<SocialCardState?>(null) }
    // iOS reactorsPeek: the card whose pill was held, and that pill's emoji.
    var peek by remember { mutableStateOf<Pair<SocialCardState, String>?>(null) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    val ownIds = remember(state.mineRows) {
        state.mineRows.flatMap { row -> when (row) {
            is MineRowState.TripRow -> listOf(row.trip.id)
            is MineRowState.JourneyRow -> row.legs.map { it.id }
        } }.toSet()
    }
    fun isOwn(card: SocialCardState) = card.author.id == feature.accountId() || card.trip.id in ownIds

    fun open(card: SocialCardState) {
        navigator.push(TripsDestination.TripDetail(card.trip.id, preview = card.trip.asPreviewTrip(), social = vm.socialTrip(card.trip.id)))
    }

    HideAppTabBar(pendingPrivate != null || pendingDelete != null)

    Box(modifier.fillMaxSize().background(colors.bg).testTag("screen_feed")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            FeedHeader(
                onTitle = { scope.launch { toTop() } },
                onSignIn = if (!signedIn) onSignIn else null,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
            )
            AnimatedVisibility(offline, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                OfflineStrip(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp))
            }
            ModeSwitcher(
                mode = mode,
                onSelect = { target ->
                    haptics.selection()
                    val again = target == mode
                    scope.launch {
                        pager.animateScrollToPage(if (target == FeedMode.following) 1 else 0, animationSpec = tween(200))
                        toTop()
                    }
                    if (again && (target == FeedMode.all || signedIn)) vm.refresh(target)
                },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 1) { page ->
                val pageMode = if (page == 1) FeedMode.following else FeedMode.all
                val social = if (pageMode == FeedMode.following) state.following else state.all
                val list = if (pageMode == FeedMode.following) followingList else allList
                if (pageMode == FeedMode.following && !signedIn) {
                    CenteredState(
                        title = strings.followingGuestTitle(),
                        body = strings.followingGuestBody(),
                        tag = "feed_following_guest",
                    ) {
                        if (onSignIn != null) AccentButton(strings.signInShort(), null, Modifier.testTag("feed_following_sign_in")) { haptics.tap(); onSignIn() }
                    }
                } else {
                    FeedPage(
                        social = social,
                        mode = pageMode,
                        list = list,
                        vm = vm,
                        now = now(),
                        isOwn = ::isOwn,
                        hasPrivateTrip = signedIn && state.hasAnyPrivateTrip,
                        onOpen = ::open,
                        onComments = { card -> navigator.push(TripsDestination.TripDetail(card.trip.id, preview = card.trip.asPreviewTrip(), social = vm.socialTrip(card.trip.id), focus = com.onezee.triptrack.model.TripFocus.Comments)) },
                        onOpenJourney = { navigator.push(TripsDestination.SocialJourney(it)) },
                        onReact = { card, emoji ->
                            if (signedIn) vm.toggleReaction(card.trip.id, emoji, pageMode)
                            else toast = ToastItem(ToastType.info, strings.signInPromptReact())
                        },
                        onOpenPicker = { card ->
                            if (signedIn) pickerFor = card else toast = ToastItem(ToastType.info, strings.signInPromptReact())
                        },
                        onPeekReactors = { card, emoji -> peek = card to emoji },
                        menu = { card ->
                            if (!isOwn(card)) emptyList() else listOf(
                                ActionPopoverItem(strings.edit(), AppGlyph.pencil, testTag = "feed_card_action_edit") { open(card) },
                                ActionPopoverItem(strings.makePrivateAction(), AppGlyph.lock, testTag = "feed_card_action_private") { pendingPrivate = card },
                                ActionPopoverItem(strings.delete(), AppGlyph.trash, isDestructive = true, testTag = "feed_card_action_delete") { pendingDelete = card },
                            )
                        },
                        onPublishOwn = onOpenProfileTab,
                    )
                }
            }
        }
        RecordingBanner(feature, visible = state.currentRecording != null, onTap = onOpenRecord, modifier = Modifier.align(Alignment.BottomCenter))
        pickerFor?.let { card ->
            ReactionPickerOverlay(
                currentReaction = card.myReaction,
                onPick = { emoji -> vm.toggleReaction(card.trip.id, emoji, mode) },
                onDismiss = { pickerFor = null },
            )
        }
        peek?.let { (card, emoji) ->
            val social = LocalSocial.current
            ReactionsListSheet(
                tripId = card.trip.id,
                initialEmoji = emoji,
                load = { id -> withContext(feature.worker) { feature.social.reactions(id) } },
                ownId = feature.accountId(),
                showsOwnBadge = social?.showsOwnBadge?.invoke() ?: true,
                onSelectUser = { author -> navigator.push(TripsDestination.PublicProfile(author.id, author)) },
                onDismissRequest = { peek = null },
            )
        }
        ToastHost(toast, onDismiss = { toast = null })
    }

    AppConfirm(
        item = pendingPrivate,
        onDismissRequest = { pendingPrivate = null },
        title = { strings.hideFromFeedAlertTitle() },
        message = { strings.hideFromFeedAlertBody() },
        cancelTitle = strings.no(),
        actions = { card ->
            listOf(AppDialogAction(strings.hideFromFeedAlertConfirm(), AppDialogAction.Kind.destructive, testTag = "feed_hide_confirm") {
                haptics.action()
                vm.removeOptimistically(card.trip.id)
                scope.launch {
                    runCatching { withContext(feature.worker) { feature.makePrivate(card.trip.id) } }
                        .onSuccess { vm.privacyChanged(card.trip.id, true); toast = ToastItem(ToastType.success, strings.tripHiddenToast()) }
                        .onFailure { toast = ToastItem(ToastType.error, strings.tripLoadFailed()) }
                }
            })
        },
    )
    AppConfirm(
        item = pendingDelete,
        onDismissRequest = { pendingDelete = null },
        title = { strings.deleteTripAlertTitle() },
        message = { strings.deleteTripAlertBody() },
        cancelTitle = strings.no(),
        actions = { card ->
            listOf(AppDialogAction(strings.deleteTripAlertConfirm(), AppDialogAction.Kind.destructive, testTag = "feed_delete_confirm") {
                haptics.action()
                vm.removeOptimistically(card.trip.id)
                scope.launch {
                    runCatching { withContext(feature.worker) { feature.deleteTrip(card.trip.id) } }
                        .onSuccess { vm.loadTrips(); toast = ToastItem(ToastType.success, strings.tripDeletedToast()) }
                }
            })
        },
    )
}

/** iOS feedHeader: «Лента» in the screen-title role; a tap scrolls back to the top. */
@Composable
private fun FeedHeader(onTitle: () -> Unit, onSignIn: (() -> Unit)?, modifier: Modifier = Modifier) {
    val strings = appStrings
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            strings.feed(),
            style = AppType.title,
            letterSpacing = AppType.titleTracking,
            color = AppTheme.colors.text,
            modifier = Modifier
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTitle)
                .semantics { heading() }
                .testTag("feed_title"),
        )
        Spacer(Modifier.weight(1f))
        if (onSignIn != null) {
            val interaction = remember { MutableInteractionSource() }
            Text(
                strings.signInShort(),
                style = inter(14f, InterWeight.bold),
                color = Color.White,
                modifier = Modifier
                    .plainPressFeedback(interaction)
                    .background(AppTheme.accent, CapsuleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onSignIn() }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("feed_sign_in"),
            )
        }
    }
}

/** iOS offlineStrip: a statement about the app, above both pages, not about one feed. */
@Composable
private fun OfflineStrip(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(AppTheme.accentBg, CapsuleShape)
            .border(1.dp, AppTheme.accent.copy(alpha = 0.35f), CapsuleShape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("feed_offline_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(AppGlyph.airplane, contentDescription = null, tint = AppTheme.accent, size = 16.dp)
        Text(appStrings.feedOfflineTripsAre(), style = inter(12f, InterWeight.semibold), color = AppTheme.colors.text, maxLines = 1,
            modifier = Modifier.weight(1f))
    }
}

/** iOS feedModeSwitcher: two halves on a card-alt track; the active one lifts onto the card. */
@Composable
private fun ModeSwitcher(mode: FeedMode, onSelect: (FeedMode) -> Unit, modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.cardAlt, RoundedCornerShape(11.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((target, label, tag) in listOf(
            Triple(FeedMode.all, strings.all(), "feed_segment_all"),
            Triple(FeedMode.following, strings.feedSegmentFollowing(), "feed_segment_following"),
        )) {
            val active = target == mode
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .iosShadow(shape, if (active) Color.Black.copy(alpha = 0.03f) else Color.Transparent, radius = 2.dp, y = 1.dp)
                    .background(if (active) colors.card else Color.Transparent, shape)
                    .clickable(interactionSource = null, indication = null, role = Role.Tab) { onSelect(target) }
                    .semantics { selected = active }
                    .padding(vertical = 8.dp)
                    .testTag(tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = inter(13f, InterWeight.bold), color = if (active) colors.text else colors.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FeedPage(
    social: SocialFeedState,
    mode: FeedMode,
    list: LazyListState,
    vm: FeedViewModel,
    now: Instant,
    isOwn: (SocialCardState) -> Boolean,
    hasPrivateTrip: Boolean,
    onOpen: (SocialCardState) -> Unit,
    onComments: (SocialCardState) -> Unit,
    onOpenJourney: (UUID) -> Unit,
    onReact: (SocialCardState, String) -> Unit,
    onOpenPicker: (SocialCardState) -> Unit,
    onPeekReactors: (SocialCardState, String) -> Unit,
    menu: (SocialCardState) -> List<ActionPopoverItem>,
    onPublishOwn: () -> Unit,
) {
    val strings = appStrings
    val haptics = rememberHaptics()
    val clearance = tabBarContentClearance()
    PullToRefresh(refreshing = social.isLoading && social.cards.isNotEmpty(), onRefresh = { vm.refresh(mode) }, modifier = Modifier.fillMaxSize()) {
        when {
            social.isLoading && social.cards.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), state = list,
                contentPadding = PaddingValues(start = 30.dp, end = 30.dp, top = 12.dp, bottom = clearance)) {
                item { SkeletonCards() }
            }
            social.error != null && social.cards.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), state = list,
                contentPadding = PaddingValues(horizontal = 14.dp)) {
                item { ErrorState { haptics.tap(); vm.refresh(mode) } }
            }
            social.cards.isEmpty() -> if (mode == FeedMode.following) {
                CenteredState(strings.followingEmptyTitle(), strings.followingEmptyBody(), tag = "feed_following_empty")
            } else {
                CenteredState(strings.feedEmptyTitle(), strings.feedEmptyBody(), tag = "feed_empty") {
                    if (hasPrivateTrip) {
                        val interaction = remember { MutableInteractionSource() }
                        Row(
                            Modifier
                                .plainPressFeedback(interaction)
                                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onPublishOwn() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("feed_publish_own"),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(AppGlyph.globe, contentDescription = null, tint = AppTheme.accent, size = 16.dp)
                            Text(strings.feedPublishOneOf(), style = inter(14f, InterWeight.semibold), color = AppTheme.accent)
                        }
                    }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().testTag(if (mode == FeedMode.following) "feed_list_following" else "feed_list_all"),
                state = list,
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = clearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(social.cards, key = { it.trip.id }) { card ->
                    // Only the last card asks for the next page (iOS: currentItem == trips.last).
                    LaunchedEffect(card.trip.id, social.cards.lastOrNull()?.trip?.id) {
                        if (social.cards.lastOrNull()?.trip?.id == card.trip.id) vm.loadMore(mode, card.trip.id)
                    }
                    val own = isOwn(card)
                    SocialFeedCard(
                        card = card,
                        isOwn = own,
                        now = now,
                        onTapCard = { onOpen(card) },
                        onTapComments = { onComments(card) },
                        onReact = { emoji -> if (!own) onReact(card, emoji) },
                        onOpenPicker = { if (!own) onOpenPicker(card) },
                        onOpenJourney = onOpenJourney,
                        menuItems = menu(card),
                        onPeekReactors = { emoji -> onPeekReactors(card, emoji) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (social.isLoadingMore) item { LoadingMoreRow(Modifier.padding(vertical = 16.dp)) }
            }
        }
    }
}

/** iOS socialEmptyState / followingEmptyState / followingGuestState: the idle ring, a title, a line, one way forward. */
@Composable
private fun CenteredState(title: String, body: String, tag: String, action: @Composable () -> Unit = {}) {
    val colors = AppTheme.colors
    Box(Modifier.fillMaxSize().padding(bottom = tabBarContentClearance()).testTag(tag), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IdleRing(Modifier.padding(top = 40.dp))
            Text(title, style = inter(19f, InterWeight.heavy), color = colors.text, textAlign = TextAlign.Center)
            Text(body, style = inter(14f), color = colors.textSecondary, textAlign = TextAlign.Center, lineHeight = points(14f * 1.21f + 6f),
                modifier = Modifier.width(280.dp))
            action()
        }
    }
}

/** iOS socialErrorState: the offline illustration, one message whatever the failure, «Попробовать снова». */
@Composable
private fun ErrorState(onRetry: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(bottom = 40.dp).testTag("feed_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EmptyStateIllustration(R.drawable.empty_offline, Modifier.padding(top = 40.dp), size = 148.dp)
        Text(strings.feedCouldnTLoad(), style = inter(19f, InterWeight.heavy), color = colors.text, textAlign = TextAlign.Center)
        Text(strings.feedCheckYourInternet(), style = inter(14f), color = colors.textSecondary, textAlign = TextAlign.Center,
            lineHeight = points(14f * 1.21f + 6f), modifier = Modifier.width(280.dp))
        AccentButton(strings.tryAgain(), AppGlyph.arrowClockwise, Modifier.padding(top = 4.dp).testTag("feed_error_retry"), onRetry)
    }
}

@Composable
private fun AccentButton(text: String, glyph: AppGlyph?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .plainPressFeedback(interaction)
            .iosShadow(shape, AppTheme.accent.copy(alpha = 0.3f), radius = 1.5.dp, y = 1.dp)
            .background(AppTheme.accent, shape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (glyph == null) 22.dp else 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) Glyph(glyph, contentDescription = null, tint = Color.White, size = 19.dp)
        Text(text, style = inter(14f, InterWeight.bold), color = Color.White)
    }
}

/**
 * iOS: RecordingBanner.swift. While a trip records, a glass pill over the tab bar: REC, the
 * distance and the clock; a tap returns to the record tab.
 */
@Composable
private fun RecordingBanner(feature: TripsFeature, visible: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val recorder by feature.recorder.collectAsState()
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val reduceMotion = LocalReduceMotion.current
    val elapsed by produceState(recorder.elapsedSeconds(Instant.now()), recorder, visible) {
        while (visible) {
            value = recorder.elapsedSeconds(Instant.now())
            delay(1000)
        }
    }
    AnimatedVisibility(
        visible,
        modifier = modifier.padding(bottom = tabBarTappableInset() + TabBarMetrics.clearance + 4.dp),
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300)),
    ) {
        val pulse = if (reduceMotion) 1f else rememberInfiniteTransition(label = "rec")
            .animateFloat(1f, 1.4f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "recPulse").value
        val interaction = remember { MutableInteractionSource() }
        Row(
            Modifier
                .plainPressFeedback(interaction)
                .glassBackground(20.dp)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("feed_recording_banner"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).scale(pulse).alpha(1.4f - pulse * 0.6f + 0.2f).background(AppTheme.red, CircleShape))
            Text("REC", style = inter(12f, InterWeight.bold), color = AppTheme.red)
            Text(Measure.distance(recorder.distance, unit, strings.language.code, strings, Measure.Style.tenths),
                style = inter(13f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.text)
            Text("·", style = inter(13f), color = AppTheme.colors.textTertiary)
            Text(clock(elapsed), style = inter(13f, InterWeight.semibold, monospacedDigits = true), color = AppTheme.colors.text)
        }
    }
}

/** iOS MapViewModel.duration: «MM:SS», «H:MM:SS» from the first hour. */
private fun clock(seconds: Double): String {
    val total = maxOf(0L, seconds.toLong())
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

