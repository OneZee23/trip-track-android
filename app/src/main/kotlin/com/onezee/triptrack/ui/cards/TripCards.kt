package com.onezee.triptrack.ui.cards

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.ActionPopover
import com.onezee.triptrack.designsystem.components.ActionPopoverItem
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.CardPressResponse
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.PlusBadgeVisibility
import com.onezee.triptrack.designsystem.components.PressableCard
import com.onezee.triptrack.designsystem.components.ProBadge
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.strings.uppercased
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.handjet
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.feed.SocialCardState
import com.onezee.triptrack.feature.social.ui.avatarFrame
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.logic.distanceParts
import com.onezee.triptrack.logic.from
import com.onezee.triptrack.logic.speedParts
import com.onezee.triptrack.model.AvatarFrame
import com.onezee.triptrack.model.DriverRank
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.model.ReactionEmoji
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.ui.MapSnapshotPreview
import com.onezee.triptrack.ui.MapSnapshotRoutesPreview
import com.onezee.triptrack.ui.RegionDisplay
import com.onezee.triptrack.ui.RelativeTripDate
import com.onezee.triptrack.ui.SystemColors
import com.onezee.triptrack.ui.TripAutoTitle
import com.onezee.triptrack.ui.reactions.ReactionTallyPill
import java.time.Instant

// ---------------------------------------------------------------------------------------------
// Shared pieces

/** iOS: Trip.hasDisplayableName. A non-empty title that is not the date the app stamped itself. */
fun TripSummary.hasDisplayableName(strings: AppStrings): Boolean =
    !title.isNullOrBlank() && !TripAutoTitle.isAuto(title, startDate, strings.zone)

/** iOS: ProfileDateFormat.dayMonth: «14 апр», «Apr 14»; the month's own abbreviation dot dropped. */
fun AppStrings.dayMonth(date: Instant): String =
    LocalizedDateFormatter.templates("dMMM", zone).getValue(language).format(date).replace(Regex("(?<=\\p{L})\\."), "")

/** iOS: Trip.duration in whole seconds, the end minus the start. */
val TripSummary.seconds: Long get() = maxOf(0L, duration.toLong())

/**
 * The metric block of the cards (iOS SocialFeedCardView / ProfileTripCardView metricsStrip): three
 * equal columns, the number heavy, its unit half its size, the caption in small capitals.
 */
@Composable
private fun MetricsStrip(blocks: List<Pair<List<Pair<String, String>>, String>>) {
    val colors = AppTheme.colors
    val strings = appStrings
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((runs, label) in blocks) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val value = buildAnnotatedString {
                    runs.forEachIndexed { index, (number, unit) ->
                        if (index > 0) append(" ")
                        withStyle(SpanStyle(letterSpacing = if (index == 0) points(-0.19f) else points(0f), fontFeatureSettings = "tnum")) { append(number) }
                        if (unit.isNotEmpty()) {
                            withStyle(SpanStyle(fontSize = AppType.statUnit.fontSize, fontWeight = FontWeight.W600, color = colors.textSecondary)) { append(" $unit") }
                        }
                    }
                }
                Text(value, style = AppType.statValue, color = colors.text, maxLines = 1)
                Text(label.uppercased(strings.language), style = AppType.statCaption, letterSpacing = AppType.statCaptionTracking,
                    color = colors.textTertiary, maxLines = 1)
            }
        }
    }
}

/**
 * The three blocks: distance in tenths, time, the overall average speed ([averageSpeed], metres per
 * second, replaces it: the Я journal honours the «moving only» setting). [padMinutes]: «4 ч 05 мин» on the profile card.
 */
@Composable
private fun tripMetrics(distance: Double, seconds: Long, padMinutes: Boolean, averageSpeed: Double? = null): List<Pair<List<Pair<String, String>>, String>> {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val lang = strings.language.code
    val d = Measure.distanceParts(distance, unit, lang, strings, Measure.Style.tenths)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val time = if (h == 0L) listOf("$m" to strings.minutesUnitShort())
    else listOf("$h" to strings.hoursUnitShort(), (if (padMinutes) "%02d".format(m) else "$m") to strings.minutesUnitShort())
    val speed = Measure.speedParts(averageSpeed ?: if (seconds > 0) distance / seconds else 0.0, unit, lang, strings)
    return listOf(
        listOf(d.value to d.unit) to strings.distance(),
        time to strings.duration(),
        listOf(speed.value to speed.unit) to strings.avgSpeedShort(),
    )
}

/** The map slot of a card: the route on the card-alt ground, or the struck-out map when there is none. */
@Composable
private fun CardMap(coordinates: List<GeoPoint>, tripId: java.util.UUID, height: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    if (coordinates.size > 1) {
        MapSnapshotPreview(coordinates, tripId, modifier.fillMaxWidth().height(height).clip(shape), height = height)
    } else {
        Box(modifier.fillMaxWidth().height(height).clip(shape).background(AppTheme.colors.cardAlt), contentAlignment = Alignment.Center) {
            Glyph(AppGlyph.mapSlash, contentDescription = null, tint = AppTheme.colors.textTertiary, size = 26.dp)
        }
    }
}

/** iOS: DriverRank.color, the LVL tag of the feed's authors. */
fun DriverRank.color(): Color = when (this) {
    DriverRank.novice -> Color(0.486f, 0.510f, 0.557f)
    DriverRank.driver -> Color(0.690f, 0.475f, 0.235f)
    DriverRank.traveler -> Color(0.808f, 0.604f, 0.180f)
    DriverRank.explorer -> Color(0.882f, 0.627f, 0.090f)
    DriverRank.navigator -> Color(0.933f, 0.506f, 0.129f)
    DriverRank.trucker -> Color(0.761f, 0.271f, 0.169f)
    DriverRank.legend -> Color(0.847f, 0.243f, 0.082f)
    DriverRank.pioneer -> Color(0.784f, 0.180f, 0.106f)
    DriverRank.nomad -> Color(0.706f, 0.129f, 0.129f)
    DriverRank.conqueror -> Color(0.612f, 0.106f, 0.157f)
    DriverRank.cartographer -> Color(0.518f, 0.102f, 0.196f)
    DriverRank.odysseus -> Color(0.435f, 0.086f, 0.184f)
    DriverRank.roadSpirit -> Color(0.345f, 0.075f, 0.157f)
    DriverRank.eternal -> Color(0.259f, 0.063f, 0.129f)
}

// ---------------------------------------------------------------------------------------------
// ProfileTripCardView

/**
 * iOS: ProfileTripCardView.swift. A trip of one's own history (Я) and of the pinned rows: the
 * level tag and date, the car it was driven in, the title, the route, three numbers. No author, no
 * reactions: status is the screen's business, never a badge on the card. The caller owns the tap's
 * haptic. [vehicle] is the car of the trip when it still stands in the garage with a drawn sprite;
 * [averageSpeed] replaces the overall average (metres per second); [map] draws the route slot of a
 * trip with a route, where the caller has a better map than the light preview.
 */
@Composable
fun ProfileTripCard(
    trip: TripSummary,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    level: Int? = null,
    isPinned: Boolean = false,
    mapHeight: Dp = 178.dp,
    pressResponse: CardPressResponse = CardPressResponse.tap,
    onLongPress: (() -> Unit)? = null,
    vehicle: ProfileCardVehicle? = null,
    averageSpeed: Double? = null,
    onLongPressLabel: String? = null,
    map: (@Composable (List<GeoPoint>, Dp) -> Unit)? = null,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val named = trip.hasDisplayableName(strings)
    val region = RegionDisplay.localized(trip.region, strings.language)
    val date = strings.dayMonth(trip.startDate) + if (named && !region.isNullOrEmpty()) " · $region" else ""
    val title = (if (named) TripAutoTitle.localized(trip.title, trip.startDate, strings)?.takeIf { it.isNotEmpty() } else null)
        ?: region?.takeIf { it.isNotEmpty() } ?: strings.dayMonth(trip.startDate)
    PressableCard(onClick = onTap, modifier = modifier.fillMaxWidth().testTag("profile_trip_card"), response = pressResponse, onLongClick = onLongPress, onLongClickLabel = onLongPressLabel) {
        Column(Modifier.fillMaxWidth().surfaceCard()) {
            Row(
                Modifier.padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The date gives way first: the car's name keeps its room, as iOS's layoutPriority does.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (level != null) Text("LVL $level", style = handjet(13f), color = AppTheme.gold, maxLines = 1)
                    Text(date, style = inter(11f), color = colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (trip.source == TripOrigin.manual) {
                        Glyph(AppGlyph.pencilLine, strings.manualTripBadge(), tint = colors.textTertiary, size = 14.dp, modifier = Modifier.testTag("profile_trip_manual"))
                    }
                    if (vehicle != null) ProfileCardVehicleTag(vehicle)
                }
                when {
                    isPinned -> Text("● " + strings.profilePinned(), style = inter(11f, InterWeight.semibold), color = AppTheme.accent, maxLines = 1,
                        modifier = Modifier.testTag("profile_trip_pinned"))
                    trip.isPrivate -> Glyph(AppGlyph.lockFill, contentDescription = strings.privacyOnlyMe(), tint = colors.textTertiary, size = 16.dp)
                    else -> Glyph(AppGlyph.globe, contentDescription = strings.privacyPublic(), tint = AppTheme.green, size = 17.dp)
                }
            }
            Text(title, style = inter(17f, InterWeight.heavy), letterSpacing = points(-0.085f), color = colors.text, maxLines = 2,
                modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 10.dp))
            if (map != null && trip.coordinates.size > 1) map(trip.coordinates, mapHeight) else CardMap(trip.coordinates, trip.id, mapHeight)
            Box(Modifier.padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp)) {
                MetricsStrip(tripMetrics(trip.distance, trip.seconds, padMinutes = true, averageSpeed = averageSpeed))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// SocialFeedCardView

/**
 * iOS: SocialFeedCardView.swift. A trip in the social feed: who drove, when and where, the title,
 * the route, three numbers, the journey it belongs to, and the reactions with the discussion.
 */
@Composable
fun SocialFeedCard(
    card: SocialCardState,
    isOwn: Boolean,
    now: Instant,
    onTapCard: () -> Unit,
    onTapComments: () -> Unit,
    onReact: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenJourney: (java.util.UUID) -> Unit,
    menuItems: List<ActionPopoverItem>,
    modifier: Modifier = Modifier,
    onTapAuthor: (() -> Unit)? = null,
    onPeekReactors: ((String) -> Unit)? = null,
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val trip = card.trip
    Column(modifier.fillMaxWidth().surfaceCard().testTag("social_trip_card")) {
        SocialAuthorRow(card, isOwn, now, menuItems, onTapAuthor)
        // The body opens the trip; a hold opens the reaction picker on someone else's trip.
        PressableCard(
            onClick = { haptics.tap(); onTapCard() },
            response = if (isOwn) CardPressResponse.tap else CardPressResponse.hold,
            onLongClick = if (isOwn) null else ({ haptics.action(); onOpenPicker() }),
        ) {
            Column {
                val title = TripAutoTitle.localized(trip.title, trip.startDate, strings)
                if (!title.isNullOrEmpty()) {
                    Text(title, style = inter(17f, InterWeight.heavy), letterSpacing = points(-0.085f), color = colors.text, maxLines = 2,
                        modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 10.dp))
                }
                CardMap(trip.coordinates, trip.id, 178.dp)
                Box(Modifier.padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp)) {
                    MetricsStrip(tripMetrics(trip.distance, trip.seconds, padMinutes = false))
                }
            }
        }
        card.journey?.let { journey ->
            com.onezee.triptrack.ui.JourneyLinkRow(journey.title, journey.startDate, journey.endDate, onTap = { onOpenJourney(journey.id) },
                modifier = Modifier.padding(start = 13.dp, end = 13.dp, bottom = 10.dp).testTag("feed_card_journey"), now = now)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border))
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReactionArea(card, isOwn, onReact, onOpenPicker, onPeekReactors)
            Spacer(Modifier.weight(1f))
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .widthIn(min = 34.dp)
                    .height(34.dp)
                    .plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); onTapComments() }
                    .semantics { contentDescription = strings.comments() }
                    .testTag("feed_card_comments"),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(AppGlyph.bubbleRight, contentDescription = null, tint = colors.textSecondary, size = 19.dp)
                Text("${card.commentCount}", style = inter(12f, InterWeight.bold, monospacedDigits = true), color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SocialAuthorRow(card: SocialCardState, isOwn: Boolean, now: Instant, menuItems: List<ActionPopoverItem>, onTapAuthor: (() -> Unit)?) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val author = card.author
    val name = author.displayName?.trim()?.takeIf { it.isNotEmpty() } ?: strings.companionsNoName()
    val region = RegionDisplay.localized(card.trip.region, strings.language)
    val dateLine = RelativeTripDate.string(card.trip.startDate, now, strings) + if (!region.isNullOrEmpty()) " · $region" else ""
    Row(
        Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val authorTap = if (onTapAuthor != null) Modifier.clickable(interactionSource = null, indication = null) { haptics.tap(); onTapAuthor() } else Modifier
        // iOS: .avatarFrame(AvatarFrame.from(trip.author.avatarFrame), lineWidth: 2). The server sends the frame only for a live
        // subscription, so nothing is re-checked here; a frame this build does not know reads as none.
        Box(Modifier.size(36.dp).background(colors.cardAlt, CircleShape)
            .avatarFrame(AvatarFrame.fromRawValue(author.avatarFrame.orEmpty()) ?: AvatarFrame.none, 2.dp).then(authorTap).testTag("feed_card_avatar"), contentAlignment = Alignment.Center) {
            Text(author.avatarEmoji ?: "🙂", style = inter(19f))
        }
        Column(Modifier.weight(1f).then(authorTap).testTag("feed_card_author"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = inter(14f, InterWeight.bold), color = colors.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text("LVL ${author.profileLevel}", style = handjet(13f), color = DriverRank.from(author.profileLevel).color(), maxLines = 1)
                if (PlusBadgeVisibility.shows(author.isPlus, isOwn, showsOwnBadge = true)) ProBadge(height = 16.dp)
                if (isOwn) {
                    Text(strings.meGuestName(), style = inter(10f, InterWeight.bold), color = AppTheme.accent, maxLines = 1,
                        modifier = Modifier.background(AppTheme.accentBg, CapsuleShape).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dateLine, style = inter(11f), color = colors.textTertiary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (card.trip.source == TripOrigin.manual) {
                    Glyph(AppGlyph.pencilLine, strings.manualTripBadge(), tint = colors.textTertiary, size = 14.dp, modifier = Modifier.testTag("feed_card_manual"))
                }
            }
        }
        if (card.photoCount > 0) {
            Row(Modifier.background(colors.cardAlt, CapsuleShape).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(AppGlyph.cameraFill, contentDescription = null, tint = colors.textTertiary, size = 14.dp)
                Text("+${card.photoCount}", style = inter(12f, InterWeight.bold), color = colors.textTertiary)
            }
        }
        if (menuItems.isNotEmpty()) {
            var showMenu by remember { mutableStateOf(false) }
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(34.dp)
                    .plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.tap(); showMenu = true }
                    .semantics { contentDescription = strings.moreActions() }
                    .testTag("feed_card_menu"),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(AppGlyph.ellipsis, contentDescription = null, tint = colors.textTertiary, size = 24.dp)
                ActionPopover(expanded = showMenu, onDismissRequest = { showMenu = false }, items = menuItems)
            }
        }
    }
}

@Composable
private fun ReactionArea(card: SocialCardState, isOwn: Boolean, onReact: (String) -> Unit, onOpenPicker: () -> Unit, onPeekReactors: ((String) -> Unit)?) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val tallies = ReactionEmoji.mergedTallies(card.breakdown).take(3)
    if (tallies.isEmpty()) {
        if (isOwn) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // iOS: SF face.dashed. Own trips cannot be self-reacted: a quiet line, not a button.
                Glyph(AppGlyph.faceDashed, contentDescription = null, tint = colors.textTertiary, size = 15.dp)
                Text(strings.socialFeedNoReactionsYet(), style = inter(12f, InterWeight.semibold), color = colors.textTertiary)
            }
        } else {
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .plainPressFeedback(interaction)
                    .background(colors.cardAlt.copy(alpha = colors.cardAlt.alpha * 0.6f), CapsuleShape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.selection(); onOpenPicker() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("feed_card_react"),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(AppGlyph.faceSmiling, contentDescription = null, tint = colors.textSecondary, size = 15.dp)
                Text(strings.socialFeedReact(), style = inter(12f, InterWeight.semibold), color = colors.textSecondary)
            }
        }
        return
    }
    // Tallies arrive canonical; my stored reaction may still be a legacy key, so ownership is
    // compared through the same lens. Taking one back sends the RAW stored emoji: the store's
    // same-emoji check is what turns the request into an unreact.
    val mine = card.myReaction?.let(ReactionEmoji::canonical)
    for (tally in tallies) {
        val isMine = mine == tally.emoji
        ReactionTallyPill(tally.emoji, tally.count, isMine, isEnabled = !isOwn, onLongPress = onPeekReactors) {
            onReact(if (isMine) card.myReaction ?: tally.emoji else tally.emoji)
        }
    }
    if (!isOwn) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(34.dp)
                .plainPressFeedback(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) { haptics.selection(); onOpenPicker() }
                .semantics { contentDescription = strings.addReaction() }
                .testTag("feed_card_add_reaction"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(28.dp).background(colors.cardAlt.copy(alpha = colors.cardAlt.alpha * 0.6f), CapsuleShape), contentAlignment = Alignment.Center) {
                Glyph(AppGlyph.plus, contentDescription = null, tint = colors.textSecondary, size = 14.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// JourneyCardView

/**
 * iOS: JourneyCardView.swift. One card for a whole journey in the history: the routes of every leg
 * in one frame under a «ПУТЕШЕСТВИЕ · 4 ДНЯ» pill, the legs' thumbnails, the title, one summary
 * line. [names] are the cached localities of the first start and the farthest end.
 */
@Composable
fun JourneyCard(
    title: String,
    meta: String,
    days: Int,
    routes: List<List<GeoPoint>>,
    thumbs: List<List<GeoPoint>>,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    snapshotIdentity: String = "journey:${routes.hashCode()}",
    thumbIds: List<java.util.UUID> = emptyList(),
) {
    val strings = appStrings
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    PressableCard(onClick = { haptics.tap(); onTap() }, modifier = modifier.fillMaxWidth().testTag("profile_journey_card")) {
        Column(
            Modifier
                .fillMaxWidth()
                .cardShadow(shape)
                .clip(shape)
                .background(colors.card),
        ) {
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                if (routes.isEmpty()) {
                    Box(Modifier.matchParentSize().background(colors.cardAlt), contentAlignment = Alignment.Center) {
                        Glyph(AppGlyph.mapSlash, contentDescription = null, tint = colors.textTertiary, size = 24.dp)
                    }
                } else {
                    MapSnapshotRoutesPreview(routes, snapshotIdentity, Modifier.matchParentSize(), height = 150.dp, cornerRadius = 16.dp)
                }
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.72f), CapsuleShape)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(AppGlyph.suitcaseFill, contentDescription = null, tint = AppTheme.accent, size = 11.dp)
                    Text("${strings.journeyWord()} · $days ${strings.nounDays(days)}".uppercased(strings.language),
                        style = inter(10f, InterWeight.heavy), letterSpacing = points(0.5f), color = Color.White, maxLines = 1)
                }
                if (thumbs.isNotEmpty()) {
                    Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        thumbs.forEachIndexed { index, leg ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(colors.cardAlt)
                                    .border(2.dp, Color.White, RoundedCornerShape(7.dp)),
                            ) {
                                if (leg.size > 1) MapSnapshotPreview(leg, thumbIds.getOrNull(index)?.toString() ?: "journey-thumb:${leg.hashCode()}",
                                    Modifier.matchParentSize(), height = 28.dp, width = 28.dp, cornerRadius = 7.dp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(start = 13.dp, end = 13.dp, top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = inter(19f, InterWeight.heavy), letterSpacing = points(-0.2f), color = colors.text, maxLines = 2, modifier = Modifier.weight(1f))
                Glyph(AppGlyph.chevronRight, contentDescription = null, tint = colors.textTertiary, size = 16.dp)
            }
            Text(meta, style = inter(11.5f, InterWeight.semibold), color = colors.textTertiary, maxLines = 2,
                modifier = Modifier.padding(start = 13.dp, end = 13.dp, top = 3.dp, bottom = 12.dp))
        }
    }
}

/** The card's light-only shadow, drawn BEFORE the clip so the map's corners clip with the card. */
@Composable
private fun Modifier.cardShadow(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val shadow = if (AppTheme.isDark) Color.Transparent else Color.Black.copy(alpha = 0.03f)
    return iosShadow(shape, shadow, radius = 2.dp, y = 1.dp)
}

// ---------------------------------------------------------------------------------------------
// SkeletonPlaceholder(.card)

/**
 * iOS: SkeletonPlaceholder (shape .card). Three card silhouettes that breathe between 0.55 and
 * 1.0 over 0.9 s; still under «Remove animations». One element for TalkBack: «Загружаем».
 */
@Composable
fun SkeletonCards(modifier: Modifier = Modifier, count: Int = 3) {
    val colors = AppTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val pulse = if (reduceMotion) 1f else {
        val transition = rememberInfiniteTransition(label = "skeleton")
        transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeletonPulse").value
    }
    val label = appStrings.loadingGeneric()
    Column(modifier.alpha(pulse).semantics(mergeDescendants = true) { contentDescription = label }.testTag("feed_skeleton"),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) {
            Column(Modifier.fillMaxWidth().surfaceCard().padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(colors.cardAlt, CircleShape))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(120.dp).height(10.dp).background(colors.cardAlt, CapsuleShape))
                        Box(Modifier.width(72.dp).height(8.dp).background(colors.cardAlt, CapsuleShape))
                    }
                }
                Box(Modifier.fillMaxWidth().height(140.dp).background(colors.cardAlt, RoundedCornerShape(12.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { Box(Modifier.width(54.dp).height(9.dp).background(colors.cardAlt, CapsuleShape)) }
                }
            }
        }
    }
}

/** System colours the feed borrows: the connectivity banner's orange. */
@Composable
fun systemOrange(): Color = SystemColors.orange(AppTheme.isDark)
