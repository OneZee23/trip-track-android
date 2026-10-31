package com.onezee.triptrack.feature

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.CardPressResponse
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.feature.drafts.ui.DraftsListScreen
import com.onezee.triptrack.feature.drafts.ui.ProfileDraftsRow
import com.onezee.triptrack.feature.feed.MineRowState
import com.onezee.triptrack.feature.feed.ui.FeedScreen
import com.onezee.triptrack.feature.journey.ui.JourneyComposerSheet
import com.onezee.triptrack.feature.journey.ui.JourneyDetailScreen
import com.onezee.triptrack.feature.journey.ui.JourneyFormat
import com.onezee.triptrack.feature.journey.ui.SocialJourneyScreen
import com.onezee.triptrack.feature.trip.TripSummary
import com.onezee.triptrack.feature.trip.ui.TripDetailScreen
import com.onezee.triptrack.logic.GeohashEncoder
import com.onezee.triptrack.logic.build
import com.onezee.triptrack.logic.distance
import com.onezee.triptrack.logic.localTripCount
import com.onezee.triptrack.model.JourneyAggregate
import com.onezee.triptrack.model.Measure
import com.onezee.triptrack.ui.cards.JourneyCard
import com.onezee.triptrack.ui.cards.ProfileTripCard
import com.onezee.triptrack.ui.navigation.TabNavHost
import com.onezee.triptrack.ui.navigation.TabNavigator
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import java.time.Instant
import kotlinx.coroutines.withContext

/** The screens a trips stack can push, one place for both tabs. */
@Composable
fun TripsDestinationScreen(dest: TripsDestination, feature: TripsFeature, navigator: TabNavigator<TripsDestination>) {
    when (dest) {
        is TripsDestination.TripDetail -> TripDetailScreen(dest, feature, navigator)
        is TripsDestination.JourneyDetail -> JourneyDetailScreen(dest.id, feature, navigator)
        is TripsDestination.SocialJourney -> SocialJourneyScreen(dest.id, feature, navigator)
        TripsDestination.Drafts -> DraftsListScreen(
            vm = feature.drafts,
            onOpenTrip = { navigator.push(TripsDestination.TripDetail(it.id, preview = it.asPreviewTrip())) },
            onBack = { navigator.pop() },
        )
    }
}

/** Лента with its own stack: the trip, the journey, someone else's journey, pushed over it. */
@Composable
fun FeedTab(feature: TripsFeature, scrollToTop: Int, offline: Boolean, onOpenRecord: () -> Unit, onOpenProfileTab: () -> Unit) {
    val navigator = feature.feedNavigator
    TabNavHost(navigator, Modifier.fillMaxSize(), root = {
        FeedScreen(feature, scrollToTop, offline, onOpenRecord, onOpenProfileTab)
    }) { dest -> TripsDestinationScreen(dest, feature, navigator) }
}

/**
 * Я while its own screen is still to be ported: the shell's settings card, then the drafts row
 * (only while there are drafts) and the history of one's own trips, where a journey folds its legs
 * into one card («Мои» is the only place that folds them). A hold on a trip combines a journey
 * around it, as on iOS.
 */
@Composable
fun MeTab(feature: TripsFeature, settings: @Composable (LazyListScope.() -> Unit) -> Unit) {
    val navigator = feature.meNavigator
    TabNavHost(navigator, Modifier.fillMaxSize(), root = {
        val feed by feature.feed.state.collectAsState()
        val drafts by feature.drafts.state.collectAsState()
        val strings = appStrings
        val haptics = rememberHaptics()
        var composeFor by remember { mutableStateOf<TripSummary?>(null) }
        var composerOpenings by remember { mutableIntStateOf(0) }
        var toast by remember { mutableStateOf<ToastItem?>(null) }
        val now = Instant.now()
        androidx.compose.runtime.LaunchedEffect(Unit) { feature.feed.loadTrips() }
        settings {
            if (drafts.drafts.isNotEmpty()) {
                item(key = "drafts") { ProfileDraftsRow(drafts.drafts, now, onTap = { navigator.push(TripsDestination.Drafts) }) }
            }
            if (feed.mineRows.isNotEmpty()) {
                item(key = "history") {
                    Text(strings.tripsHistory(), style = AppType.section, letterSpacing = AppType.sectionTracking, color = AppTheme.colors.text,
                        modifier = androidx.compose.ui.Modifier.semantics { heading() }.testTag("profile_history_header"))
                }
            }
            items(feed.mineRows.size, key = { index -> when (val row = feed.mineRows[index]) {
                is MineRowState.TripRow -> "trip:${row.trip.id}"
                is MineRowState.JourneyRow -> "journey:${row.journey.id}"
            } }) { index ->
                when (val row = feed.mineRows[index]) {
                    is MineRowState.TripRow -> ProfileTripCard(
                        trip = row.trip,
                        mapHeight = 118.dp,
                        pressResponse = CardPressResponse.hold,
                        onTap = { haptics.tap(); navigator.push(TripsDestination.TripDetail(row.trip.id, preview = row.trip.asPreviewTrip())) },
                        onLongPress = { haptics.action(); composerOpenings++; composeFor = row.trip },
                    )
                    is MineRowState.JourneyRow -> MineJourneyCard(row, feature) { navigator.push(TripsDestination.JourneyDetail(row.journey.id)) }
                }
            }
        }
        composeFor?.let { anchor ->
            val composer = rememberLocalViewModel("composer:${anchor.id}:$composerOpenings") { feature.journeyComposer(anchor.asPreviewTrip()) }
            JourneyComposerSheet(composer, onCreated = { feature.feed.loadTrips(); toast = ToastItem(ToastType.success, strings.journeyCreated()) },
                onDismissRequest = { composeFor = null })
        }
        ToastHost(toast, onDismiss = { toast = null })
    }) { dest -> TripsDestinationScreen(dest, feature, navigator) }
}

/**
 * iOS: JourneyCardView as the history uses it: the title from the journey's name or its ends'
 * cached localities, and «12–17 сент. · 1 640 км · 4 поездки · 2 по городу · 21 ч 5 мин в пути».
 */
@Composable
private fun MineJourneyCard(row: MineRowState.JourneyRow, feature: TripsFeature, onTap: () -> Unit) {
    val strings = appStrings
    val unit = LocalDistanceUnit.current
    val legs = row.legs
    val aggregate = remember(legs) { JourneyAggregate.build(legs.map { it.asPreviewTrip() }, strings.zone) }
    val names by produceState(emptyMap<String, String>(), aggregate) {
        val keys = listOfNotNull(aggregate.firstStart, aggregate.farthestEnd).map { GeohashEncoder.encode(it.latitude, it.longitude, 5) }.toSet()
        if (keys.isNotEmpty()) value = runCatching { withContext(feature.worker) { feature.localities(keys) } }.getOrDefault(emptyMap())
    }
    fun name(point: com.onezee.triptrack.model.GeoPoint?) = point?.let { names[GeohashEncoder.encode(it.latitude, it.longitude, 5)] }
    val journey = row.journey
    val windowEnd = journey.endDate ?: legs.maxOfOrNull { it.endDate ?: it.startDate } ?: journey.startDate
    val dateRange = JourneyFormat.dateRange(journey.startDate, windowEnd, strings)
    val title = JourneyFormat.title(journey.title, name(aggregate.firstStart), name(aggregate.farthestEnd), dateRange)
    val days = if (aggregate.calendarDays > 0) aggregate.calendarDays
        else maxOf(java.time.temporal.ChronoUnit.DAYS.between(journey.startDate.atZone(strings.zone).toLocalDate(), windowEnd.atZone(strings.zone).toLocalDate()).toInt() + 1, 1)
    val meta = if (legs.isEmpty()) strings.journeyEmptyTitle() else listOfNotNull(
        dateRange.takeIf { it != title },
        Measure.distance(aggregate.totalMetres, unit, strings.language.code, strings, Measure.Style.grouped),
        "${aggregate.legCount} ${strings.nounTrips(aggregate.legCount)}",
        aggregate.localTripCount.takeIf { it > 0 }?.let { strings.journeyAroundTownCount(it) },
        JourneyFormat.duration(aggregate.drivingSeconds, strings).takeIf { it.isNotEmpty() }?.let { "$it ${strings.journeyDrivingLabel().lowercase(strings.language.locale)}" },
    ).joinToString(" · ")
    JourneyCard(
        title = title,
        meta = meta,
        days = days,
        routes = legs.map { it.coordinates }.filter { it.size > 1 },
        thumbs = legs.sortedByDescending { it.startDate }.take(4).map { it.coordinates },
        onTap = onTap,
    )
}
