// iOS: PublicProfileView.swift (body, unavailableScreen, previewBanner, heroSection,
// profileSectionsRow, journeysEntryCard, recentTrips, tripCard, layoutToggle), ProfileHeroCard.swift
// (isForeign), ProfileGarageHeader.swift (foreign), SkeletonProfileView.swift
package com.onezee.triptrack.feature.social.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.account.SignInAction
import com.onezee.triptrack.feature.account.SignInPromptSheet
import com.onezee.triptrack.feature.feed.SocialCardState
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.ui.reactions.ReactionPickerOverlay
import com.onezee.triptrack.feature.garage.VehicleAvatar
import com.onezee.triptrack.feature.garage.VehicleStage
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.cards.ProfileTripCard
import com.onezee.triptrack.ui.cards.SocialFeedCard
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import java.util.UUID
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * iOS: PublicProfileView. Someone's profile in the stack it was opened from: the hero (their
 * background and frame only while they pay, `showsPaidCosmetics`), their pinned trips, one row of
 * sections, their journeys and the journal of public trips, paged. The own profile opens here too
 * («Как видят другие») and says so in a card on top.
 */
@Composable
fun PublicProfileScreen(dest: TripsDestination.PublicProfile, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val vm = entryViewModel("profile:${dest.id}") {
        PublicProfileViewModel(dest.id, dest.preloaded, social, locale = { strings.language.locale }, zone = { strings.zone })
    }
    val state by vm.state.collectAsState()
    val now = social.clock()
    var actionsOpen by remember { mutableStateOf(false) }
    var blockAsked by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }
    var signInFor by remember { mutableStateOf<SignInAction?>(null) }
    var resumeFollow by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<SocialFeedTrip?>(null) }
    var linkShare by remember { mutableStateOf<TripLinkShare?>(null) }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    var gallery by remember { mutableStateOf<PublicPhotoSelection?>(null) }
    val isOwn = vm.isOwnProfile
    val name = vm.displayName(strings.publicProfileDriver())

    LaunchedEffect(vm) { if (state.profile == null && state.unavailable == null && !state.isLoading) vm.refresh() }
    // `.task` does not re-run on a session change: a sign-in or sign-out reloads the profile here,
    // so nothing of the previous account's answer stays on screen.
    LaunchedEffect(social.auth) {
        social.auth?.state?.map { it.signedInAccountId }?.distinctUntilChanged()?.drop(1)?.collect { vm.sessionChanged() }
    }
    LaunchedEffect(vm) { social.commentCounts.collect { (trip, delta) -> vm.commentCountChanged(trip, delta) } }
    HideAppTabBar(blockAsked)

    fun requireAccount(action: SignInAction, block: () -> Unit) {
        if (social.signedIn) block() else if (social.auth != null) signInFor = action else toast = ToastItem(ToastType.info, action.headline(strings))
    }
    fun openTrip(trip: SocialFeedTrip, focus: TripFocus = TripFocus.Top) {
        haptics.tap()
        // One's own trip opens the local copy (the owner's controls), anyone else's from the payload.
        navigator.push(if (trip.author.id == social.accountId) TripsDestination.TripDetail(trip.id, preview = trip.asTrip(), focus = focus)
            else TripsDestination.TripDetail(trip.id, preview = trip.asTrip(), social = trip, focus = focus))
    }
    fun shareTrip(trip: SocialFeedTrip) { scope.launch { linkShare = TripLinkShare(trip, vm.shareLink(trip.id)) } }
    val profile = state.profile
    val hasCover = !profile?.photos.isNullOrEmpty()
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40 } }

    val actionItems = buildList {
        add(ActionPopoverItem(strings.settingsShareProfile(), AppGlyph.squareAndArrowUp, testTag = "profile_share") { ShareLinks.share(context, profileShareUrl(dest.id), name) })
        add(ActionPopoverItem(strings.copyProfileLink(), AppGlyph.characterBubble, testTag = "profile_copy_link") {
            if (ShareLinks.copy(context, profileShareUrl(dest.id), name)) toast = ToastItem(ToastType.success, strings.profileLinkCopied())
        })
        // Reporting or blocking oneself is not an action; for a guest both would only detour through sign-in.
        if (social.signedIn && !isOwn) {
            add(ActionPopoverItem(strings.reportProfileAction(), AppGlyph.flag, testTag = "profile_report") { reportTarget = ReportTarget.User(dest.id) })
            add(ActionPopoverItem(strings.blockProfileAction(state.isBlocked), AppGlyph.xmarkCircleFill, isDestructive = !state.isBlocked, testTag = "profile_block") { blockAsked = true })
        }
    }
    @Composable fun MoreButton(onCover: Boolean) {
        Box {
            if (onCover) {
                val interaction = remember { MutableInteractionSource() }
                Box(Modifier.size(40.dp).plainPressFeedback(interaction).background(Color.White, CircleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); actionsOpen = true }
                    .semantics { contentDescription = strings.moreActions() }.testTag("profile_more"), contentAlignment = Alignment.Center) {
                    Glyph(AppGlyph.ellipsis, null, tint = Color(0xFF1F1F1F), size = 22.dp)
                }
            } else NavCircleButton(AppGlyph.ellipsis, strings.moreActions(), { haptics.tap(); actionsOpen = true }, Modifier.testTag("profile_more"))
            ActionPopover(actionsOpen, { actionsOpen = false }, actionItems)
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg).testTag("public_profile")) {
        Column(Modifier.fillMaxSize()) {
            if (!hasCover) Box(Modifier.statusBarsPadding()) { CustomNavBar(name, onBack = { navigator.pop() }) { MoreButton(onCover = false) } }
            com.onezee.triptrack.ui.PullToRefresh(refreshing = state.isLoading && profile != null, onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
            // iOS: 16 between the hero, the pinned cards (12 apart), the sections and the journeys; inside «Поездки» the header
            // is 10 above its content and chapter headers and cards are 12 apart. One uniform spacing could say only one of those,
            // so every item carries the gap that precedes it.
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 120.dp)) {
                if (isOwn) item(key = "preview") { PreviewBanner(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 8.dp)) }
                when {
                    profile != null -> {
                        item(key = "hero") {
                            PublicHero(profile, state.garage.firstOrNull { it.id == profile.activeVehicle?.id }
                                ?: profile.activeVehicle?.let { PublicVehicle(it.id, it.name, it.avatarEmoji, avatarStyle = it.avatarStyle, level = it.level, odometerKm = it.odometerKm) }, name, isOwn,
                                // iOS countryGlyph: the country is this device's setting and the profile DTO carries none, so only one's own preview prints it.
                                country = if (isOwn) countryLine(social.ownCountry(), strings) else null,
                                followBusy = state.isTogglingFollow, now = now,
                                onFollow = {
                                    if (!isOwn) {
                                        haptics.action()
                                        if (!social.signedIn && social.auth != null) resumeFollow = true
                                        requireAccount(SignInAction.follow) { vm.toggleFollow() }
                                    }
                                },
                                onFollowers = { haptics.tap(); navigator.push(TripsDestination.FollowList(dest.id, FollowListMode.followers)) },
                                onFollowing = { haptics.tap(); navigator.push(TripsDestination.FollowList(dest.id, FollowListMode.following)) },
                                onCopyName = { if (ShareLinks.copy(context, name, name)) toast = ToastItem(ToastType.success, strings.profileNameCopied()) },
                                onVehicle = { id -> navigator.push(TripsDestination.PublicCar(dest.id, id, name, state.garage.firstOrNull { it.id == id })) },
                                onLevel = { navigator.push(TripsDestination.PublicLevels(dest.id, name, profile.profileLevel, profile.profileXp)) },
                                onStats = { navigator.push(TripsDestination.PublicStats(dest.id, name)) },
                                onPhoto = { pages, index -> gallery = PublicPhotoSelection(pages, index) },
                                coverOverlay = { if (hasCover && !scrolled) Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    NavCircleButton(AppGlyph.chevronLeft, strings.back(), { navigator.pop() }, Modifier.testTag("social_back")); Spacer(Modifier.weight(1f)); MoreButton(onCover = true)
                                } })
                        }
                        val journal = state.journal
                        if (journal != null && journal.history.pinned.isNotEmpty()) itemsIndexed(journal.history.pinned, key = { _, it -> "pin_${it.id}" }) { index, trip ->
                            ProfileTripCard(TripSummary.from(trip), onTap = { journal.originals[trip.id]?.let { openTrip(it) } }, level = profile.profileLevel,
                                isPinned = true, mapHeight = 118.dp, modifier = Modifier.padding(horizontal = 16.dp).padding(top = if (index == 0) 16.dp else 12.dp))
                        }
                        item(key = "sections") {
                            SectionsRow(profile, state, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                                onStats = { navigator.push(TripsDestination.PublicStats(dest.id, name)) },
                                onMap = { navigator.push(TripsDestination.PublicMap(dest.id, name, PublicMapStart.bounds(state.tripCards.take(PublicMapStart.TRIP_LIMIT).map { it.asTrip() }))) },
                                onAchievements = { state.allBadgeIds?.let { navigator.push(TripsDestination.PublicAchievements(dest.id, name, it)) } },
                                onGarage = { navigator.push(TripsDestination.PublicGarage(dest.id, name)) })
                        }
                        if (state.journeys.isNotEmpty()) item(key = "journeys") {
                            JourneysEntryCard(state.journeys, dest.id, Modifier.padding(horizontal = 16.dp).padding(top = 16.dp),
                                onAll = { navigator.push(TripsDestination.PublicJourneys(dest.id, name)) },
                                onJourney = { navigator.push(TripsDestination.SocialJourney(it)) })
                        }
                        recentTrips(state, profile, gridMode, now, onGrid = { gridMode = it },
                            onOpen = { openTrip(it) }, onComments = { openTrip(it, TripFocus.Comments) },
                            onReact = { trip, emoji -> requireAccount(SignInAction.react) { vm.toggleReaction(trip.id, emoji) } },
                            onPicker = { trip -> requireAccount(SignInAction.react) { pickerFor = trip } },
                            onShare = ::shareTrip,
                            onReport = { trip -> requireAccount(SignInAction.generic) { reportTarget = ReportTarget.Trip(trip.id) } },
                            onJourney = { navigator.push(TripsDestination.SocialJourney(it)) },
                            onAppear = { index, count -> vm.loadMoreIfNeeded(index, count) }, isOwnTrip = { it.author.id == social.accountId })
                    }
                    state.unavailable != null -> item(key = "unavailable") { UnavailableProfile(state.unavailable!!, onRetry = vm::retry) }
                    else -> item(key = "skeleton") { SkeletonProfile(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp)) }
                }
            }
        }
        }
        if (hasCover && scrolled) Column(Modifier.fillMaxWidth().background(colors.bg).statusBarsPadding()) {
            CustomNavBar(name, onBack = { navigator.pop() }) { MoreButton(onCover = false) }
        }
        LaunchedEffect(profile?.id, state.garage.isEmpty()) { if (profile != null) { vm.loadGarage(); vm.loadJourneys() } }
        pickerFor?.let { trip -> ReactionPickerOverlay(trip.myReaction, onPick = { vm.toggleReaction(trip.id, it) }, onDismiss = { pickerFor = null }) }
        ToastHost(toast, onDismiss = { toast = null })
    }

    gallery?.let { PublicPhotoViewer(it, onDismiss = { gallery = null }) }
    AppConfirm(
        isPresented = blockAsked,
        onDismissRequest = { blockAsked = false },
        title = strings.blockProfileConfirmTitle(state.isBlocked),
        message = strings.blockProfileConfirmBody(state.isBlocked),
        // Blocking is destructive; unblocking is the kindness of the same button, never red.
        actions = listOf(AppDialogAction(strings.blockProfileAction(state.isBlocked), if (state.isBlocked) AppDialogAction.Kind.primary else AppDialogAction.Kind.destructive,
            testTag = "profile_block_confirm") { requireAccount(SignInAction.generic) { vm.toggleBlock() } }),
    )
    reportTarget?.let { target -> ReportSheet(target, social, onDismiss = { reportTarget = null }) }
    linkShare?.let { share -> SharedTripLinkSheet(share.trip, share.url, onDismiss = { linkShare = null }) }
    signInFor?.let { action ->
        social.auth?.let { auth ->
            SignInPromptSheet(action, auth, onDismissRequest = {
                signInFor = null
                // Resumed after the sheet closed: the follow the guest tapped is not dropped.
                if (resumeFollow && social.signedIn) vm.resumeFollowAfterSignIn()
                resumeFollow = false
            })
        }
    }
}

/** A trip card's share: the trip and the link minted for it, null when `/social/share` was unreachable. */
data class TripLinkShare(val trip: SocialFeedTrip, val url: String?)

/** iOS previewBanner: the only thing telling one's own page from a stranger's. */
@Composable
private fun PreviewBanner(modifier: Modifier = Modifier) {
    val strings = appStrings
    val colors = AppTheme.colors
    Row(modifier.fillMaxWidth().surfaceCard(12.dp).padding(horizontal = 12.dp, vertical = 9.dp).testTag("profile_preview_banner"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        SocialGlyphIcon(SocialGlyph.eyeFill, null, tint = colors.textSecondary, size = 16.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(strings.myProfileRowPreview(), style = inter(13f, InterWeight.bold), color = colors.text)
            Text(strings.previewBannerSubtitle(), style = inter(11f), color = colors.textSecondary)
        }
    }
}

/**
 * iOS ProfileHeroCard(isForeign: true). Three shapes, as on «Я»: photos make a cover pager with the
 * panel under it; a paid background (while it is paid) fills the card under a dark veil with white
 * ink; otherwise the road scene behind their car.
 */
@Composable
private fun PublicHero(
    profile: SocialProfile, vehicle: PublicVehicle?, name: String, isOwn: Boolean, country: String?, followBusy: Boolean, now: java.time.Instant,
    onFollow: () -> Unit, onFollowers: () -> Unit, onFollowing: () -> Unit, onCopyName: () -> Unit, onVehicle: (UUID) -> Unit,
    onLevel: () -> Unit, onStats: () -> Unit, onPhoto: (List<PublicPhotoPage>, Int) -> Unit, coverOverlay: @Composable BoxScope.() -> Unit,
) {
    val strings = appStrings
    val paid = profile.showsPaidCosmetics
    val background = (ProfileBackground.fromRawValue(profile.profileBackground.orEmpty()) ?: ProfileBackground.none).effectiveFor(paid)
    val hasCover = background != ProfileBackground.none
    val photos = profile.photos.orEmpty().filter { !it.originalUrl.isNullOrEmpty() || !it.thumbnailUrl.isNullOrEmpty() }
    val ink = if (hasCover) Color.White else AppTheme.colors.text
    val secondary = if (hasCover) Color.White.copy(alpha = .85f) else AppTheme.colors.textSecondary
    val followTitle = if (isOwn) strings.previewThisIsYou() else if (profile.isFollowing == true) strings.notificationsInboxFollowing() + " ✓" else strings.discoverFollow()
    val privacy = profile.isPrivacyMode(isOwn)
    val roadTime = remember(now) { RoadSceneTime.slot(now, strings.zone) }
    val frame = (AvatarFrame.fromRawValue(profile.avatarFrame.orEmpty()) ?: AvatarFrame.none).let { if (paid) it else AvatarFrame.none }
    val showsBadge = PlusBadgeVisibility.saysPlus(profile.isPlus)
    val identity = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(if (photos.isEmpty()) 60.dp else 46.dp).background(if (hasCover) Color.White.copy(alpha = .16f) else AppTheme.colors.cardAlt, CircleShape)
                .avatarFrame(frame, 3.dp), contentAlignment = Alignment.Center) {
                Text(profile.avatarEmoji ?: "🚗", style = inter(if (photos.isEmpty()) 32f else 26f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // iOS identityText: the name is at least 26 tall under a cover photo and 32 on the card, the level row 18 and 26; the
                // 44-point targets iOS takes back out of the layout are Compose's own minimum touch size, so no padding grows the rows.
                Row(Modifier.heightIn(min = if (photos.isEmpty()) 32.dp else 26.dp).combinedClickable(role = Role.Button, onClick = onCopyName, onLongClick = onCopyName).testTag("profile_display_name"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = interScaled(21f, InterWeight.bold), letterSpacing = points(-.3f), color = ink, modifier = Modifier.weight(1f, fill = false))
                    if (showsBadge) ProBadge(height = 16.dp)
                }
                Row(Modifier.heightIn(min = if (photos.isEmpty()) 26.dp else 18.dp).clickable(role = Role.Button, onClick = onLevel).testTag("public_lvl_pill"),
                    horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("LVL ${profile.profileLevel}", style = handjet(17f), color = if (hasCover) Color.White else AppTheme.colors.roadHeroAccent)
                    Text(rankTitle(DriverRank.from(profile.profileLevel)), style = interScaled(12f, InterWeight.medium), color = secondary)
                    Glyph(AppGlyph.chevronRight, null, tint = secondary, size = 9.dp)
                }
                if (country != null) Text(country, style = inter(12f), color = secondary, modifier = Modifier.testTag("profile_country"))
            }
            FollowButton(followTitle, followBusy, enabled = !isOwn, onClick = onFollow)
        }
    }
    val followers = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.followersCount(profile.followerCount), style = interScaled(13f), color = secondary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onFollowers).padding(vertical = 6.dp).testTag("profile_followers"))
            Text("·", style = interScaled(13f), color = secondary)
            Text(strings.followingCountLabel(profile.followingCount), style = interScaled(13f), color = secondary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onFollowing).padding(vertical = 6.dp).testTag("profile_following"))
        }
    }
    val bio = profile.bio?.trim()?.takeIf { it.isNotEmpty() }
    val stats = @Composable {
        if (profile.openBlocks.counters) Row(Modifier.fillMaxWidth().clickable(enabled = !privacy && profile.openBlocks.stats, role = Role.Button, onClick = onStats).testTag("profile_stats_strip")) {
            StatColumn(if (privacy) "••••" else "${profile.stats.tripCount}", strings.trips(), ink, secondary, photos.isNotEmpty())
            Spacer(Modifier.weight(1f))
            StatColumn(if (privacy) "••••" else Measure.distanceValueKm(profile.stats.totalKm, LocalDistanceUnit.current, strings.language.code), strings.statsKmTotal(LocalDistanceUnit.current), ink, secondary, photos.isNotEmpty())
            Spacer(Modifier.weight(1f))
            StatColumn(if (privacy) "••••" else "${profile.stats.regionsCount}", strings.statsRegions(), ink, secondary, photos.isNotEmpty())
        }
    }
    val garage = @Composable { car: PublicVehicle?, road: Boolean, onCoverEdge: Boolean -> if (car != null) ForeignCar(car, paid, road, onCoverEdge, ink, secondary, onVehicle) else if (road) RoadScene(roadTime) }
    Column(Modifier.fillMaxWidth().testTag("profile_hero")) {
        if (photos.isNotEmpty()) {
            Box {
                val pager = rememberPagerState { photos.size }
                HorizontalPager(pager, Modifier.fillMaxWidth().height(232.dp)) { index ->
                    PressableCard(onClick = { onPhoto(photos.map { PublicPhotoPage(it.id, it.originalUrl ?: it.thumbnailUrl!!) }, index) }, modifier = Modifier.testTag("profile_cover_photo")) {
                        PhotoImage(PhotoSource.Remote(photos[index].originalUrl ?: photos[index].thumbnailUrl!!), 1440, Modifier.fillMaxSize())
                    }
                }
                coverOverlay()
            }
            Column(Modifier.fillMaxWidth().drawBehind { if (hasCover) { drawCover(background); drawRect(Color.Black.copy(alpha = .64f)) } }
                .padding(horizontal = 16.dp).padding(bottom = 12.dp, top = if (vehicle != null) 0.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                garage(vehicle, false, true)
                identity()
                followers()
                if (bio != null) Text(bio, style = interScaled(15f), color = ink, modifier = Modifier.testTag("profile_bio"))
                if (profile.openBlocks.counters) Box(Modifier.padding(top = 4.dp)) { stats() }
            }
        } else {
            val shape = RoundedCornerShape(24.dp)
            // iOS: the card's own 4 and heroSection's 6 below the bar (a cover hero starts at the very top).
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp).fillMaxWidth().clip(shape)
                .background(if (hasCover) Color.Transparent else AppTheme.colors.roadHeroCard)
                .drawBehind { if (hasCover) { drawCover(background); drawRect(Color.Black.copy(alpha = .64f)) } }
                .border(1.dp, if (hasCover) Color.Transparent else AppTheme.accent.copy(alpha = .22f), shape).testTag("profile_hero_card")) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    identity()
                    if (bio != null) Text(bio, style = interScaled(14f), color = ink, modifier = Modifier.testTag("profile_bio"))
                }
                garage(vehicle, !hasCover, false)
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    followers()
                    if (profile.openBlocks.counters) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(if (hasCover) Color.White.copy(alpha = .22f) else AppTheme.accent.copy(alpha = .18f)))
                        stats()
                    }
                }
            }
        }
        if (privacy) Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(AppGlyph.lockFill, null, tint = AppTheme.colors.textTertiary, size = 10.dp)
            Text(strings.publicProfileHiddenRoadsThis(), style = inter(12f), color = AppTheme.colors.textTertiary)
        }
    }
}

/** iOS column(value:label:): 22 bold, and 19 heavy under a cover photo, where the panel is tighter. */
@Composable
private fun RowScope.StatColumn(value: String, label: String, ink: Color, secondary: Color, withPhotos: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = if (withPhotos) interScaled(19f, InterWeight.heavy, monospacedDigits = true) else interScaled(22f, InterWeight.bold, monospacedDigits = true), color = ink, maxLines = 1)
        Text(label, style = interScaled(11f, InterWeight.medium), color = secondary)
    }
}

@Composable
private fun FollowButton(title: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.plainPressFeedback(interaction).background(AppTheme.accent, RoundedCornerShape(12.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp).testTag("profile_follow"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (busy) com.onezee.triptrack.feature.account.Spinner(Color.White, size = 12.dp)
        Text(title, style = interScaled(13f, InterWeight.semibold), color = Color.White, maxLines = 1)
    }
}

/**
 * iOS ProfileGarageHeader.foreign: their active car on the road (or its photo filling the band), its
 * name. On a cover's edge ([onCoverEdge]) the car stands pulled up 82 over the photo: the stage 250
 * wide in a 155 frame, bottom aligned, and the name with the make, model and year on ONE centred line
 * (the name 15 semibold, the rest 12 in the secondary ink), about 106 of panel instead of 270.
 */
@Composable
private fun ForeignCar(car: PublicVehicle, paid: Boolean, road: Boolean, onCoverEdge: Boolean, ink: Color, secondary: Color, onVehicle: (UUID) -> Unit) {
    val vehicle = car.asVehicle()
    val style = vehicle.avatarStyle.takeIf { it in VehicleAvatar.styles(vehicle.type) } ?: VehicleAvatar.styles(vehicle.type).first()
    val photo = car.mainPhoto?.takeIf { it.isNotEmpty() }
    val caption = listOfNotNull(car.make, car.model, car.year?.takeIf { it > 0 }?.toString()).filter { it.isNotEmpty() }.joinToString(" · ")
    BoxWithConstraints(if (onCoverEdge) Modifier.pulledUp(82.dp) else Modifier) {
        val width = if (onCoverEdge) 250.dp else if (road) minOf(maxWidth - 72.dp, 300.dp) else minOf(maxWidth * .67f, 300.dp)
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onVehicle(car.id) }.testTag("garage_header_vehicle"),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val stage = VehicleAvatar.stageHeight(style, width)
            if (onCoverEdge) Box(Modifier.fillMaxWidth().height(155.dp), contentAlignment = Alignment.BottomCenter) {
                if (photo != null) PhotoImage(PhotoSource.Remote(photo), 1080, Modifier.size(width, width * .6f).clip(RoundedCornerShape(12.dp)))
                // A stage taller than the frame stands on its bottom edge and rises over the photo, as it does in iOS.
                else VehicleStage(vehicle, Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), width, isPlus = paid)
            }
            else if (road && photo != null) PhotoImage(PhotoSource.Remote(photo), 1080, Modifier.fillMaxWidth().height(stage + 20.dp))
            else Box(Modifier.fillMaxWidth().height(stage + if (road) 20.dp else 0.dp), contentAlignment = Alignment.BottomCenter) {
                if (road) RoadScene(RoadSceneTime.slot(java.time.Instant.now(), java.time.ZoneId.systemDefault()), Modifier.matchParentSize())
                if (photo != null && !road) PhotoImage(PhotoSource.Remote(photo), 1080, Modifier.size(width, width * .6f).clip(RoundedCornerShape(12.dp)))
                else VehicleStage(vehicle, Modifier.padding(vertical = if (road) 10.dp else 0.dp), width, isPlus = paid)
            }
            if (onCoverEdge) Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(car.name, Modifier.weight(1f, fill = false), style = interScaled(15f, InterWeight.semibold), color = ink, maxLines = 1)
                Glyph(AppGlyph.chevronRight, null, tint = secondary, size = 11.dp)
                if (caption.isNotEmpty()) Text(caption, Modifier.weight(1f, fill = false), style = interScaled(12f), color = secondary, maxLines = 1)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(car.name, style = interScaled(17f, InterWeight.semibold), color = ink)
                    Glyph(AppGlyph.chevronRight, null, tint = secondary, size = 11.dp)
                }
                if (caption.isNotEmpty()) Text(caption, style = interScaled(12f), color = secondary, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
    }
}

/**
 * iOS `.padding(.top, -x)`: the content is placed [amount] higher and the layout is that much
 * shorter, so what follows moves up with it. Offsetting alone would leave the gap behind.
 */
private fun Modifier.pulledUp(amount: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx()
    layout(placeable.width, (placeable.height - pull).coerceAtLeast(0)) { placeable.place(0, -pull) }
}

/** iOS countryPillText: the flag, then the country's name; the neutral flag stands alone, «no country» prints nothing. */
internal fun countryLine(stored: String, strings: com.onezee.triptrack.designsystem.strings.AppStrings): String? {
    val flag = com.onezee.triptrack.feature.me.ProfileIdentityChoices.flag(stored) ?: return null
    return when (stored) {
        "NEUTRAL" -> flag
        "WORLD" -> "$flag ${strings.countryWorld()}"
        else -> flag + " " + runCatching { java.util.Locale.Builder().setRegion(stored.uppercase()).build().getDisplayCountry(strings.language.locale) }.getOrDefault(stored)
    }
}

@Composable
private fun RoadScene(time: RoadSceneTime, modifier: Modifier = Modifier.fillMaxWidth().height(211.dp)) {
    val id = com.onezee.triptrack.feature.me.MeArtwork.drawables["road_${time.name}"] ?: return
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(id), null, modifier, contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
}

/** The rank's name from the same table the Levels screen reads. */
@Composable
internal fun rankTitle(rank: DriverRank): String {
    val titles = listOf(com.onezee.triptrack.designsystem.R.string.driver_rank_novice, com.onezee.triptrack.designsystem.R.string.driver_rank_driver,
        com.onezee.triptrack.designsystem.R.string.driver_rank_traveler, com.onezee.triptrack.designsystem.R.string.driver_rank_explorer,
        com.onezee.triptrack.designsystem.R.string.driver_rank_navigator, com.onezee.triptrack.designsystem.R.string.driver_rank_trucker,
        com.onezee.triptrack.designsystem.R.string.driver_rank_legend, com.onezee.triptrack.designsystem.R.string.driver_rank_pioneer,
        com.onezee.triptrack.designsystem.R.string.driver_rank_nomad, com.onezee.triptrack.designsystem.R.string.driver_rank_conqueror,
        com.onezee.triptrack.designsystem.R.string.driver_rank_cartographer, com.onezee.triptrack.designsystem.R.string.driver_rank_odysseus,
        com.onezee.triptrack.designsystem.R.string.driver_rank_roadSpirit, com.onezee.triptrack.designsystem.R.string.driver_rank_eternal)
    return appStrings.tr(titles[rank.ordinal])
}

/**
 * iOS profileSectionsRow: «Статистика · Карта · Достижения · Гараж» in one card, each only when
 * the owner shows it and there is somewhere to go (the garage from two cars, the map with public
 * routes, achievements once the whole list came).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SectionsRow(profile: SocialProfile, state: PublicProfileState, modifier: Modifier, onStats: () -> Unit, onMap: () -> Unit, onAchievements: () -> Unit, onGarage: () -> Unit) {
    val strings = appStrings
    val blocks = profile.openBlocks
    val entries = buildList {
        if (blocks.stats) add(Triple(strings.stats(), "profile_stats_entry", onStats))
        if (blocks.map && profile.hasPublicRoutes) add(Triple(strings.tabMap(), "profile_map_entry", onMap))
        if (blocks.achievements && state.allBadgeIds != null) add(Triple(strings.achievementsSection(), "public_achievements_all", onAchievements))
        if (state.garage.size > 1) add(Triple(strings.garage(), "profile_garage_entry", onGarage))
    }
    if (entries.isEmpty()) return
    FlowRow(modifier.fillMaxWidth().surfaceCard(16.dp).padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.Center) {
        entries.forEachIndexed { index, (title, tag, action) ->
            if (index > 0) Text("·", style = interScaled(13f, InterWeight.medium), color = AppTheme.colors.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
            val interaction = remember { MutableInteractionSource() }
            Text(title, style = interScaled(13f, InterWeight.medium), color = AppTheme.colors.textSecondary,
                modifier = Modifier.heightIn(min = 44.dp).wrapContentHeight(Alignment.CenterVertically).plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = action).testTag(tag))
        }
    }
}

/** iOS journeysEntryCard: the heading as one button, three cards beside it (never a button in a button). */
@Composable
private fun JourneysEntryCard(journeys: List<PublicJourneyDto>, owner: UUID, modifier: Modifier, onAll: () -> Unit, onJourney: (UUID) -> Unit) {
    val strings = appStrings
    Column(modifier.fillMaxWidth().surfaceCard(16.dp).padding(14.dp).testTag("profile_journeys"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onAll), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.journeysTitle(), style = inter(15f, InterWeight.bold), color = AppTheme.colors.text)
                // Every loaded journey, not the three shown: «3 путешествия» over three cards would read as «that is all».
                Text("${journeys.size} ${strings.nounJourneys(journeys.size)}", style = inter(11.5f), color = AppTheme.colors.textTertiary)
            }
            Glyph(AppGlyph.chevronRight, null, tint = AppTheme.colors.textTertiary, size = 13.dp)
        }
        journeys.take(3).forEach { dto -> PublicJourneyCard(dto.asJourney(owner), dto.legs.map { it.asTrip() }, onTap = { onJourney(dto.id) }) }
    }
}

/** iOS recentTrips: «Поездки · 47 · всего 52», the list/grid toggle, the chapters or the tiles, a pager row. */
private fun LazyListScope.recentTrips(
    state: PublicProfileState, profile: SocialProfile, grid: Boolean, now: java.time.Instant, onGrid: (Boolean) -> Unit,
    onOpen: (SocialFeedTrip) -> Unit, onComments: (SocialFeedTrip) -> Unit, onReact: (SocialFeedTrip, String) -> Unit,
    onPicker: (SocialFeedTrip) -> Unit, onShare: (SocialFeedTrip) -> Unit, onReport: (SocialFeedTrip) -> Unit,
    onJourney: (UUID) -> Unit, onAppear: (Int, Int) -> Unit, isOwnTrip: (SocialFeedTrip) -> Boolean,
) {
    val journal = state.journal
    if (state.tripCards.isEmpty() && state.pinnedCards.isEmpty()) {
        item(key = "trips_empty") {
            if (state.loadFailed) SocialMessageState(appStrings.publicProfileCouldnTLoad(), glyph = null, tag = "profile_trips_error")
            else SocialMessageState(appStrings.publicProfileNoPublicTrips(), tag = "profile_trips_empty")
        }
        return
    }
    item(key = "trips_header") {
        val strings = appStrings
        val publicCount = profile.stats.publicTripCount
        val total = profile.stats.tripCount
        val head = "${strings.tripsTab()} · $publicCount" + if (total > publicCount) " · " + strings.publicProfileTripsTotal(total) else ""
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialSectionLabel(head, Modifier.weight(1f))
            LayoutToggle(grid, onGrid)
        }
    }
    if (journal == null) return
    if (grid) {
        journal.gridCards.chunked(2).forEachIndexed { index, pair ->
            item(key = "grid_$index") {
                LaunchedEffect(journal.gridCards.size) { onAppear(index * 2 + 1, journal.gridCards.size) }
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { trip -> TripGridCell(trip, now, onTap = { onOpen(trip) }, modifier = Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        journal.history.chapters.forEachIndexed { chapterIndex, chapter ->
            item(key = "chapter_${chapter.id}") { ChapterHeader(chapter, Modifier.padding(horizontal = 16.dp).padding(top = if (chapterIndex == 0) 10.dp else 12.dp)) }
            items(chapter.rows, key = { row -> when (row) { is HistoryRow.Trip -> "trip_${row.value.id}"; is HistoryRow.Journey -> "journey_${row.value1.id}"; is HistoryRow.Routine -> "routine_${row.value.trips.first().id}" } }) { row ->
                Box(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                    when (row) {
                        is HistoryRow.Trip -> journal.originals[row.value.id]?.let { trip ->
                            val own = isOwnTrip(trip)
                            val strings = appStrings
                            SocialFeedCard(SocialCardState.from(trip), isOwn = own, now = now, onTapCard = { onOpen(trip) }, onTapComments = { onComments(trip) },
                                onReact = { onReact(trip, it) }, onOpenPicker = { onPicker(trip) }, onOpenJourney = onJourney,
                                menuItems = buildList {
                                    add(ActionPopoverItem(strings.share(), AppGlyph.squareAndArrowUp, testTag = "profile_trip_share") { onShare(trip) })
                                    if (!own) add(ActionPopoverItem(strings.reportProfileAction(), AppGlyph.flag, isDestructive = true, testTag = "profile_trip_report") { onReport(trip) })
                                })
                        }
                        is HistoryRow.Routine -> Unit
                        is HistoryRow.Journey -> PublicJourneyCard(row.value1, row.legs, onTap = { onJourney(row.value1.id) })
                    }
                }
            }
        }
        // A footer that asks for the next page whenever the list grew, journeys folding cards included.
        item(key = "trips_footer") { LaunchedEffect(state.tripCards.size) { onAppear(journal.history.rows.size - 1, journal.history.rows.size) } }
    }
    if (state.pager.isLoading) item(key = "trips_loading") { Box(Modifier.fillMaxWidth().padding(top = 10.dp).padding(vertical = 12.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.atlas.ui.CarLoading() } }
}

/**
 * iOS layoutToggle: no plate under either half, the accent alone says which one is on. Two 26 × 22
 * frames 2 apart inside a 2 margin: the glyphs stand 28 apart and the list one 25 from the edge. The
 * 44-point targets iOS takes back out of the layout are Compose's own minimum touch size here.
 */
@Composable
private fun LayoutToggle(grid: Boolean, onGrid: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(true to SocialGlyph.squareGrid, false to SocialGlyph.listBullet).forEach { (value, glyph) ->
            Box(Modifier.size(26.dp, 22.dp).clickable(role = Role.RadioButton) { haptics.selection(); onGrid(value) }.testTag(if (value) "profile_layout_grid" else "profile_layout_list"),
                contentAlignment = Alignment.Center) {
                SocialGlyphIcon(glyph, null, tint = if (grid == value) AppTheme.accent else AppTheme.colors.textTertiary, size = 18.dp)
            }
        }
    }
}

/**
 * iOS unavailableScreen. A closed profile seen in the public feed keeps its name and avatar; a cold
 * link confirms nothing (the server answers alike for «none», «closed» and «blocked» on purpose);
 * anything else is a failed load that offers a retry, not a follow.
 */
@Composable
private fun UnavailableProfile(state: ProfileUnavailableState, onRetry: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 40.dp).testTag("profile_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is ProfileUnavailableState.Closed -> {
                Box(Modifier.size(84.dp).background(colors.cardAlt, CircleShape), contentAlignment = Alignment.Center) { Text(state.avatar ?: "🙂", style = inter(44f)) }
                Text(state.name, style = inter(20f, InterWeight.bold), color = colors.text)
                Glyph(AppGlyph.lock, null, tint = colors.textTertiary, size = 18.dp)
                Text(strings.closedProfileTitle(), style = inter(14f, InterWeight.bold), color = colors.text)
                Text(strings.closedProfileBody(), style = inter(12.5f), color = colors.textTertiary, textAlign = TextAlign.Center)
            }
            ProfileUnavailableState.Unavailable -> {
                Box(Modifier.size(84.dp).background(colors.cardAlt, CircleShape))
                Glyph(AppGlyph.lock, null, tint = colors.textTertiary, size = 18.dp)
                Text(strings.profileUnavailableTitle(), style = inter(14f, InterWeight.bold), color = colors.text)
                Text(strings.profileUnavailableBody(), style = inter(12.5f), color = colors.textTertiary, textAlign = TextAlign.Center)
            }
            ProfileUnavailableState.Transient -> {
                SocialGlyphIcon(SocialGlyph.wifiExclamation, null, tint = colors.textTertiary, size = 26.dp)
                Text(strings.publicDataLoadFailed(), style = inter(14f, InterWeight.bold), color = colors.text)
                Text(strings.retry(), style = inter(13f, InterWeight.bold), color = AppTheme.accent,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onRetry).padding(8.dp).testTag("profile_retry"))
            }
        }
    }
}

/** iOS SkeletonProfileView: the hero's and the cards' shapes pulsing until the first answer. */
@Composable
private fun SkeletonProfile(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.fillMaxWidth().surfaceCard(24.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).background(colors.cardAlt, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(140.dp).height(14.dp).background(colors.cardAlt, CapsuleShape))
                    Box(Modifier.width(80.dp).height(10.dp).background(colors.cardAlt, CapsuleShape))
                }
            }
            Box(Modifier.fillMaxWidth().height(150.dp).background(colors.cardAlt, RoundedCornerShape(16.dp)))
        }
        com.onezee.triptrack.ui.cards.SkeletonCards(count = 2)
    }
}

/**
 * The public car as the local model the stage draws. Its platform and finish are passed raw: the
 * stage shows the paid ones only with `isPlus`, which a foreign profile sets from `showsPaidCosmetics`.
 */
fun PublicVehicle.asVehicle(): Vehicle = Vehicle(id = id, name = name, avatarEmoji = avatarEmoji,
    avatarStyle = avatarStyle ?: "", type = vehicleType?.let(VehicleType::fromRawValue) ?: VehicleType.car,
    make = make.orEmpty(), model = model.orEmpty(), year = year ?: 0, level = level, odometerKm = odometerKm,
    cardStyle = cardStyle, paintFinish = paintFinish, about = about.orEmpty(), bodyType = bodyType.orEmpty(),
    plate = plate.orEmpty(), createdAt = createdAt ?: java.time.Instant.EPOCH, soldAt = soldAt,
    catalogModelId = catalogModelId, catalogGenerationId = catalogGenerationId, catalogVariantId = catalogVariantId,
    drive2URL = drive2Url, modTags = modTags.orEmpty())
