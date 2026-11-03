package com.onezee.triptrack.feature

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onezee.triptrack.feature.drafts.ui.DraftsListScreen
import com.onezee.triptrack.feature.feed.ui.FeedScreen
import com.onezee.triptrack.feature.journey.ui.JourneyDetailScreen
import com.onezee.triptrack.feature.journey.ui.SocialJourneyScreen
import com.onezee.triptrack.feature.trip.ui.TripDetailScreen
import com.onezee.triptrack.ui.navigation.TabNavHost
import com.onezee.triptrack.ui.navigation.DestinationNavigator

/** The screens a trips stack can push, one place for both tabs. */
@Composable
fun TripsDestinationScreen(dest: TripsDestination, feature: TripsFeature, navigator: DestinationNavigator<TripsDestination>) {
    when (dest) {
        is TripsDestination.TripDetail -> TripDetailScreen(dest, feature, navigator)
        is TripsDestination.JourneyDetail -> JourneyDetailScreen(dest.id, feature, navigator)
        is TripsDestination.SocialJourney -> SocialJourneyScreen(dest.id, feature, navigator)
        TripsDestination.Drafts -> DraftsListScreen(
            vm = feature.drafts,
            onOpenTrip = { navigator.push(TripsDestination.TripDetail(it.id, preview = it.asPreviewTrip())) },
            onBack = { navigator.pop() },
            now = feature.clock,
        )
        // The social screens: profiles, their lists, maps, stats, garages, the inbox.
        else -> com.onezee.triptrack.feature.social.LocalSocial.current?.let { social ->
            com.onezee.triptrack.feature.social.ui.SocialDestinationScreen(dest, social, navigator)
        }
    }
}

/** Лента with its own stack: the trip, the journey, someone else's journey, pushed over it. */
@Composable
fun FeedTab(feature: TripsFeature, scrollToTop: Int, offline: Boolean, onOpenRecord: () -> Unit, onOpenProfileTab: () -> Unit, onSignIn: (() -> Unit)? = null) {
    val navigator = feature.feedNavigator
    TabNavHost(navigator, Modifier.fillMaxSize(), root = {
        FeedScreen(feature, scrollToTop, offline, onOpenRecord, onOpenProfileTab, onSignIn = onSignIn, now = feature.clock)
    }) { dest -> TripsDestinationScreen(dest, feature, navigator) }
}
