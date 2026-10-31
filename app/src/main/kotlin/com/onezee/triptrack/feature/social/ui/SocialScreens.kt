// iOS: FollowListView.swift, ReportSheet.swift, SharedTripLinkSheet.swift, PublicGarageView.swift,
// PublicVehicleView.swift, PublicJourneysView.swift, PublicAchievementsView.swift, LevelsView(other:)
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.garage.GarageMileageCard
import com.onezee.triptrack.feature.garage.VehicleStage
import com.onezee.triptrack.feature.me.AchievementRow
import com.onezee.triptrack.feature.me.AchievementsScreen
import com.onezee.triptrack.feature.me.BadgeDetailScreen
import com.onezee.triptrack.feature.me.ProfileIdentity
import com.onezee.triptrack.feature.me.ProfileMapPreview
import com.onezee.triptrack.feature.me.LevelsScreen
import com.onezee.triptrack.feature.settings.MeRow
import com.onezee.triptrack.feature.settings.MeSection
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.logic.*
import com.onezee.triptrack.model.*
import com.onezee.triptrack.ui.PhotoImage
import com.onezee.triptrack.ui.PhotoSource
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS: FollowListView. A segment of the two lists, the people as rows into their profiles. No
 * follow button per row: the list carries no follow state, and a button would invent one.
 */
@Composable
fun FollowListScreen(dest: TripsDestination.FollowList, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val vm = entryViewModel("follow:${dest.id}") { social.followList(dest.id, dest.mode) }
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.start() }
    HideAppTabBar()
    val title = state.accountName?.takeIf { it.isNotEmpty() } ?: if (state.mode == FollowListMode.followers) strings.followListFollowers() else strings.feedSegmentFollowing()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("follow_list")) {
        SocialNavBar(title, onBack = { navigator.pop() })
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "segment") {
                Row(Modifier.fillMaxWidth().background(colors.cardAlt, RoundedCornerShape(12.dp)).padding(3.dp).testTag("follow_segment")) {
                    FollowListMode.entries.forEach { mode ->
                        val active = state.mode == mode
                        val counts = state.counts
                        val label = when (mode) {
                            FollowListMode.followers -> counts?.let { strings.followersCount(it.first) } ?: strings.followListFollowers()
                            FollowListMode.following -> counts?.let { strings.followingCountLabel(it.second) } ?: strings.feedSegmentFollowing()
                        }
                        Text(label, Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (active) colors.card else Color.Transparent)
                            .clickable(role = Role.Tab) { if (!active) { haptics.selection(); vm.setMode(mode) } }.semantics { selected = active }
                            .padding(vertical = 8.dp).testTag("follow_segment_${mode.name}"),
                            style = inter(13f, if (active) InterWeight.bold else InterWeight.semibold, monospacedDigits = true),
                            color = if (active) colors.text else colors.textSecondary, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
            when {
                state.isLoading && state.users.isEmpty() -> item(key = "skeleton") { SkeletonRows(6, Modifier.padding(top = 12.dp)) }
                state.loadFailed && state.users.isEmpty() -> item(key = "error") { SocialMessageState(strings.followListCouldnTLoad(), retry = { vm.load() }, tag = "follow_error") }
                state.users.isEmpty() -> item(key = "empty") {
                    SocialMessageState(if (state.mode == FollowListMode.followers) strings.followListNoFollowersYet() else strings.followListNotFollowingAnyone(),
                        glyph = SocialGlyph.personTwo, tag = "follow_empty")
                }
                else -> items(state.users, key = { it.id }) { user ->
                    PersonRow(user, social, onClick = { haptics.tap(); navigator.push(TripsDestination.PublicProfile(user.id, user)) })
                }
            }
        }
    }
}

/** iOS FollowListView.userRow: 36 pt avatar, the name with its badge, «LVL N», a chevron. */
@Composable
fun PersonRow(user: SocialAuthor, social: SocialFeature, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = AppTheme.colors
    PressableCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("follow_row")) {
        Row(Modifier.fillMaxWidth().surfaceCard(16.dp).padding(11.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(user.avatarEmoji, user.avatarFrame)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PersonName(user, inter(13.5f, InterWeight.bold), isOwn = user.id == social.accountId, showsOwnBadge = social.showsOwnBadge())
                Text("LVL ${user.profileLevel}", style = inter(11f, monospacedDigits = true), color = colors.textTertiary)
            }
            trailing()
            Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
        }
    }
}

/**
 * iOS: ReportSheet. A reason out of eight, optional notes (500 characters at most), the red
 * button. Sent once; the thanks replace the form. The server dedupes nothing, so no auto-retry.
 */
@Composable
fun ReportSheet(target: ReportTarget, social: SocialFeature, onDismiss: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<ReportReason?>(null) }
    var notes by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    ContentSizedSheet(onDismissRequest = onDismiss, background = colors.bg, detent = SheetDetent.large) {
        val sheet = this
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("report_sheet"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.reportProfileAction(), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                SheetCloseCircle(onClick = { sheet.dismiss() })
            }
            if (sent) {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Glyph(AppGlyph.checkmarkCircleFill, null, tint = AppTheme.green, size = 64.dp)
                    Text(strings.reportReportSent(), style = inter(18f, InterWeight.bold), color = colors.text)
                    Text(strings.reportThankYouWe(), style = inter(13f), color = colors.textSecondary, textAlign = TextAlign.Center)
                    SheetButton(strings.done(), AppTheme.accent, Modifier.padding(top = 12.dp).testTag("report_done")) { haptics.tap(); sheet.dismiss() }
                }
                return@Column
            }
            Text(strings.reportWeReviewReports(), style = inter(13f), color = colors.textSecondary)
            Column(Modifier.fillMaxWidth().surfaceCard(18.dp)) {
                ReportReason.entries.forEachIndexed { index, option ->
                    if (index > 0) Box(Modifier.padding(start = 14.dp).fillMaxWidth().height(1.dp).background(colors.border))
                    val chosen = reason == option
                    Row(Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { haptics.selection(); reason = option }.semantics { selected = chosen }
                        .padding(14.dp).testTag("report_reason_${option.rawValue}"), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.label(strings), Modifier.weight(1f), style = inter(14f, InterWeight.medium), color = colors.text)
                        Box(Modifier.size(18.dp).background(if (chosen) AppTheme.red else Color.Transparent, CircleShape)
                            .then(if (chosen) Modifier else Modifier.background(colors.cardAlt, CircleShape)), contentAlignment = Alignment.Center) {
                            if (chosen) Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(strings.reportAdditionalDetailsOptional().uppercased(strings.language), style = inter(11f, InterWeight.semibold), letterSpacing = points(.5f), color = colors.textTertiary)
                BasicTextField(notes, { notes = it.take(500) }, Modifier.fillMaxWidth().height(80.dp).surfaceCard(12.dp).padding(10.dp).testTag("report_notes"),
                    textStyle = inter(15f).copy(color = colors.text), cursorBrush = SolidColor(AppTheme.accent))
            }
            SheetButton(strings.reportSubmit(), if (reason == null) colors.textTertiary.copy(alpha = .3f) else AppTheme.red, Modifier.testTag("report_submit"),
                enabled = reason != null && !sending, busy = sending) {
                val chosen = reason ?: return@SheetButton
                haptics.action()
                sending = true; failed = false
                scope.launch {
                    try {
                        social.api.report(target, chosen, notes.trim().takeIf { it.isNotEmpty() }?.take(500))
                        sent = true; haptics.success()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { sending = false }
                }
            }
            Text(strings.reportAnonymousNote(), style = inter(12f), color = colors.textTertiary)
            if (failed) Text(strings.publicDataLoadFailed(), style = inter(12f), color = AppTheme.red, modifier = Modifier.testTag("report_error"))
        }
    }
}

fun ReportReason.label(strings: com.onezee.triptrack.designsystem.strings.AppStrings): String = when (this) {
    ReportReason.spam -> strings.reportReasonSpam()
    ReportReason.harassment -> strings.reportReasonHarassment()
    ReportReason.hate -> strings.reportReasonHate()
    ReportReason.nudity -> strings.reportReasonNudity()
    ReportReason.violence -> strings.reportReasonViolence()
    ReportReason.illegal -> strings.reportReasonIllegal()
    ReportReason.impersonation -> strings.reportReasonImpersonation()
    ReportReason.other -> strings.reportReasonOther()
}

@Composable
private fun SheetButton(title: String, fill: Color, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(modifier.fillMaxWidth().plainPressFeedback(interaction).background(fill, RoundedCornerShape(12.dp))
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        if (busy) com.onezee.triptrack.feature.account.Spinner(Color.White) else Text(title, style = inter(15f, InterWeight.semibold), color = Color.White)
    }
}

/**
 * iOS: SharedTripLinkSheet. The trip as a row, «Поделиться» into the system sheet and «Скопировать».
 * A refused `/social/share` still opens it: copying is off, sharing has nothing to send.
 */
@Composable
fun SharedTripLinkSheet(trip: SocialFeedTrip, url: String?, onDismiss: () -> Unit) {
    val strings = appStrings
    val colors = AppTheme.colors
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }
    val name = trip.author.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: strings.publicProfileDriver()
    val subtitle = "$name · ${RelativeTripDate.string(trip.startDate, java.time.Instant.now(), strings)}"
    val title = TripAutoTitle.localized(trip.title, trip.startDate, strings)
    ContentSizedSheet(onDismissRequest = onDismiss, background = colors.bg) {
        val sheet = this
        Column(Modifier.fillMaxWidth().testTag("trip_link_sheet")) {
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.share(), Modifier.weight(1f), style = inter(19f, InterWeight.bold), color = colors.text)
                SheetCloseCircle(onClick = { sheet.dismiss() })
            }
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().surfaceCard(16.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val route = remember(trip.previewPolyline) { trip.previewPolyline?.let(::decodedPolyline)?.let { Trip.decodePolyline(it) }.orEmpty() }
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) { com.onezee.triptrack.feature.me.RoutePreview(route, Modifier.matchParentSize()) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title ?: strings.tripTitle(), style = inter(15f, InterWeight.bold), color = colors.text, maxLines = 2)
                    Text(subtitle, style = inter(12f), color = colors.textTertiary, maxLines = 1)
                    if (url != null) Text(url.removePrefix("https://"), style = inter(12f), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                }
            }
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val interaction = remember { MutableInteractionSource() }
                Row(Modifier.weight(1f).plainPressFeedback(interaction).background(AppTheme.accent, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = interaction, indication = null, enabled = url != null, role = Role.Button) { haptics.tap(); url?.let { ShareLinks.share(context, it, title ?: subtitle) } }
                    .padding(vertical = 13.dp).testTag("trip_link_share"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(AppGlyph.squareAndArrowUp, null, tint = Color.White, size = 15.dp)
                    Text(strings.share(), style = inter(15f, InterWeight.bold), color = Color.White)
                }
                val copyInteraction = remember { MutableInteractionSource() }
                Row(Modifier.weight(1f).alpha(if (url == null) .5f else 1f).plainPressFeedback(copyInteraction).surfaceCard(14.dp)
                    .clickable(interactionSource = copyInteraction, indication = null, enabled = url != null, role = Role.Button) {
                        url?.let { ShareLinks.copy(context, it, title ?: subtitle); copied = true; haptics.success() }
                    }.padding(vertical = 13.dp).testTag("trip_link_copy"), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (copied) AppGlyph.checkmark else AppGlyph.characterBubble, null, tint = if (copied) AppTheme.green else colors.text, size = 15.dp)
                    Text(if (copied) strings.sharedTripLinkCopied() else strings.sharedTripLinkCopy(), style = inter(15f, InterWeight.bold), color = colors.text)
                }
            }
        }
    }
}

/** iOS: PublicGarageView. Unsold first; a failed load says so with a retry, an empty garage says so too. */
@Composable
fun PublicGarageScreen(dest: TripsDestination.PublicGarage, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val vm = entryViewModel("garage:${dest.id}") { social.garage(dest.id) }
    val state by vm.state.collectAsState()
    HideAppTabBar()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("public_garage")) {
        SocialNavBar(strings.garage(), onBack = { navigator.pop() }, subtitle = dest.name)
        when {
            state.state == LoadState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.atlas.ui.CarLoading() }
            state.state == LoadState.failed -> SocialMessageState(strings.garageLoadFailed(), retry = { vm.load() }, tag = "public_garage_error")
            state.vehicles.isEmpty() -> SocialMessageState(strings.publicGarageEmpty(), tag = "public_garage_empty")
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.vehicles, key = { it.id }) { car ->
                    PressableCard(onClick = { navigator.push(TripsDestination.PublicCar(dest.id, car.id, dest.name, car)) }, modifier = Modifier.fillMaxWidth().testTag("public_garage_vehicle")) {
                        Column(Modifier.fillMaxWidth().surfaceCard(16.dp).alpha(if (car.soldAt != null) .55f else 1f)) {
                            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                                val photo = car.mainPhoto?.takeIf { it.isNotEmpty() }
                                if (photo != null) PhotoImage(PhotoSource.Remote(photo), 1080, Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)))
                                else VehicleStage(car.asVehicle(), width = 220.dp)
                            }
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(car.name, Modifier.weight(1f, fill = false), style = inter(15f, InterWeight.semibold), color = colors.text, maxLines = 1)
                                    if (car.soldAt != null) Text(strings.vehicleSoldBadge(), Modifier.background(colors.cardAlt, CapsuleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = inter(10f, InterWeight.bold), color = colors.textTertiary)
                                    Spacer(Modifier.weight(1f))
                                    Glyph(AppGlyph.chevronRight, null, tint = colors.textTertiary, size = 12.dp)
                                }
                                val line = listOfNotNull(car.make, car.model, car.year?.takeIf { it > 0 }?.toString()).filter { it.isNotEmpty() }.joinToString(" · ")
                                if (line.isNotEmpty()) Text(line, style = inter(12f), color = colors.textTertiary, maxLines = 1)
                                Text("LVL ${car.level} · " + Measure.odometer(car.odometerKm, LocalDistanceUnit.current, strings.language.code, strings), style = inter(11f), color = colors.textTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * iOS: PublicVehicleView. The car's passport for someone else: the stage, its numbers, the road it
 * drove from their public trips (the map only where the owner lets it show; silence is not
 * permission), its trip photos, the notes, records, and a report action.
 */
@Composable
fun PublicCarScreen(dest: TripsDestination.PublicCar, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val colors = AppTheme.colors
    val unit = LocalDistanceUnit.current
    val vm = entryViewModel("car:${dest.id}:${dest.vehicleId}") { social.vehicle(dest.id, dest.vehicleId, dest.preloaded) }
    val state by vm.state.collectAsState()
    var actions by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ReportTarget?>(null) }
    HideAppTabBar()
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().testTag("public_vehicle")) {
        SocialNavBar(state.vehicle?.name ?: strings.garage(), onBack = { navigator.pop() }, subtitle = dest.name) {
            if (social.signedIn) Box {
                NavCircleButton(AppGlyph.ellipsis, strings.moreActions(), { actions = true }, Modifier.testTag("public_vehicle_more"))
                ActionPopover(actions, { actions = false }, listOf(ActionPopoverItem(strings.reportProfileAction(), AppGlyph.flag, isDestructive = true, testTag = "public_vehicle_report") {
                    report = ReportTarget.Vehicle(dest.vehicleId)
                }))
            }
        }
        val car = state.vehicle
        when {
            car != null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().surfaceCard(24.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val photo = car.mainPhoto?.takeIf { it.isNotEmpty() }
                    if (photo != null) PhotoImage(PhotoSource.Remote(photo), 1440, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
                    else VehicleStage(car.asVehicle())
                    Text(car.name, style = interScaled(19f, InterWeight.bold), color = colors.text)
                    val line = listOfNotNull(car.make, car.model, car.year?.takeIf { it > 0 }?.toString()).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, Modifier.padding(top = 7.dp), style = AppType.meta, color = colors.textSecondary)
                    if (car.soldAt != null) Text(strings.publicVehicleSold(), Modifier.padding(top = 6.dp), style = AppType.caption, color = AppTheme.accent)
                    if (!car.plate.isNullOrBlank()) Text(car.plate!!, Modifier.padding(top = 8.dp).background(colors.cardAlt, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 5.dp), style = AppType.chip, color = colors.text)
                    Text("LVL ${car.level}", Modifier.padding(top = 12.dp).background(colors.goldBg, CircleShape).padding(horizontal = 12.dp, vertical = 8.dp), style = interScaled(11f, InterWeight.semibold), color = AppTheme.gold)
                    state.biography?.let { bio ->
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth()) {
                            val days = bio.trips.map { it.startDate.atZone(strings.zone).toLocalDate() }.toSet().size
                            listOf("${bio.trips.size}" to strings.nounTrips(bio.trips.size), "${bio.regions}" to strings.nounRegions(bio.regions), "$days" to strings.nounDays(days)).forEach { (value, label) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(value, style = interScaled(23f, InterWeight.semibold), color = colors.text)
                                    Text(label, style = interScaled(11f), color = colors.textSecondary)
                                }
                            }
                        }
                    }
                    car.createdAt?.let { Text(strings.inGarageSince(strings.monthYear(it)), Modifier.fillMaxWidth().padding(top = 12.dp), style = AppType.caption, color = colors.textTertiary) }
                }
                GarageMileageCard(car.odometerKm, state.biography?.aggregates?.totalKm ?: 0.0,
                    vehicleYearDistances(state.biography?.trips.orEmpty(), car.id, strings.zone), unit, editable = false) {}
                val routes = state.biography?.routes.orEmpty()
                if (car.mapVisible == true && routes.isNotEmpty()) MeSection(strings.vehicleWhereWas()) {
                    MeRow(strings.vehicleWhereWas(), "${state.biography?.regions ?: 0} ${strings.nounRegions(state.biography?.regions ?: 0)} · ${state.biography?.cities ?: 0} ${strings.nounCities(state.biography?.cities ?: 0)}",
                        onClick = { navigator.push(TripsDestination.PublicMap(dest.id, dest.name, PublicMapStart.coveringRoutes(routes), dest.vehicleId)) })
                    ProfileMapPreview(routes, Modifier.fillMaxWidth().height(130.dp).padding(horizontal = 14.dp)
                        .clickable { navigator.push(TripsDestination.PublicMap(dest.id, dest.name, PublicMapStart.coveringRoutes(routes), dest.vehicleId)) })
                }
                val tripPhotos = car.tripPhotos.orEmpty().mapNotNull { (it.thumbUrl ?: it.url)?.let { url -> it.id to url } }
                if (tripPhotos.isNotEmpty()) MeSection(strings.garageTripPhotos()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tripPhotos.take(4).forEach { (_, url) -> PhotoImage(PhotoSource.Remote(url), 300, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) }
                    }
                }
                car.about?.takeIf { it.isNotBlank() }?.let { about -> MeSection(strings.publicAboutBy(dest.name ?: strings.publicProfileDriver())) { MeRow(about) } }
                val photos = car.photos.orEmpty().mapNotNull { (it.thumbUrl ?: it.url)?.let { url -> it.id to url } }
                if (car.photosVisible != false && photos.isNotEmpty()) MeSection(strings.vehiclePhotos()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.take(4).forEach { (_, url) -> PhotoImage(PhotoSource.Remote(url), 300, Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))) }
                    }
                }
                state.biography?.aggregates?.takeIf { it.tripCount > 0 }?.let { agg ->
                    MeSection(strings.statsRecords()) {
                        MeRow(strings.recordLongest(), Measure.distanceKm(agg.longestTripKm, unit, strings.language.code, strings))
                    }
                    Text(strings.publicRecordsNote(), style = inter(11f), color = colors.textTertiary)
                }
                if (state.failed) Text(strings.garageLoadFailed(), style = inter(12f), color = colors.textTertiary)
            }
            state.failed || state.gone -> SocialMessageState(strings.publicVehicleUnavailable(), tag = "public_vehicle_unavailable")
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.atlas.ui.CarLoading() }
        }
    }
    report?.let { ReportSheet(it, social, onDismiss = { report = null }) }
}

/** iOS: PublicJourneysView. Every journey of the account, 20 per page, as history cards. */
@Composable
fun PublicJourneysScreen(dest: TripsDestination.PublicJourneys, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val vm = entryViewModel("journeys:${dest.id}") { social.journeys(dest.id) }
    val state by vm.state.collectAsState()
    HideAppTabBar()
    Column(Modifier.fillMaxSize().background(AppTheme.colors.bg).statusBarsPadding().testTag("public_journeys")) {
        SocialNavBar(strings.journeysTitle(), onBack = { navigator.pop() }, subtitle = dest.name)
        when {
            state.state == LoadState.loading && state.journeys.isEmpty() -> com.onezee.triptrack.ui.cards.SkeletonCards(Modifier.padding(16.dp), count = 3)
            state.state == LoadState.failed && state.journeys.isEmpty() -> SocialMessageState(strings.publicDataLoadFailed(), retry = { vm.load() }, tag = "public_journeys_error")
            state.journeys.isEmpty() -> SocialMessageState(strings.journeyEmptyTitle(), tag = "public_journeys_empty")
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.journeys, key = { it.id }) { dto ->
                    LaunchedEffect(dto.id) { vm.loadMoreIfNeeded(dto) }
                    PublicJourneyCard(dto.asJourney(dest.id), dto.legs.map { it.asTrip() }, onTap = { navigator.push(TripsDestination.SocialJourney(dto.id)) })
                }
                if (state.isLoadingMore) item(key = "more") { Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { com.onezee.triptrack.feature.atlas.ui.CarLoading() } }
            }
        }
    }
}

/**
 * iOS: PublicAchievementsView. The same achievements screen as on «Я», over the list the profile
 * card counted «N из M» by; a badge opens its detail in the same stack.
 */
@Composable
fun PublicAchievementsScreen(dest: TripsDestination.PublicAchievements, navigator: DestinationNavigator<TripsDestination>) {
    val unlocked = dest.badgeIds.toSet()
    val rows = remember(unlocked) {
        BadgeDefinitions.all.filter { !it.isHidden || it.id in unlocked }.map { AchievementRow(it.id, it.category, it.rarity, it.id in unlocked, if (it.id in unlocked) 1 else 0) }
    }
    var opened by remember { mutableStateOf<String?>(null) }
    HideAppTabBar()
    val badge = opened
    if (badge == null) AchievementsScreen(rows, onBack = { navigator.pop() }, onBadge = { opened = it })
    else {
        androidx.activity.compose.BackHandler { opened = null }
        BadgeDetailScreen(rows.firstOrNull { it.id == badge }, onBack = { opened = null }, unit = LocalDistanceUnit.current)
    }
}

/** iOS: LevelsView(other:). Their level and rank; the experience bar only when the server sent it. */
@Composable
fun PublicLevelsScreen(dest: TripsDestination.PublicLevels, navigator: DestinationNavigator<TripsDestination>) {
    HideAppTabBar()
    LevelsScreen(ProfileIdentity(level = dest.level, xp = dest.xp ?: 0), onBack = { navigator.pop() }, ownerName = dest.name, showsProgress = dest.xp != null)
}
