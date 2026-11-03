// iOS: FeedView / ProfileView `navigationDestination(for: ProfilePreviewDest.self)`: one place that
// draws every social screen, for every stack that can push one.
package com.onezee.triptrack.feature.social.ui

import androidx.compose.runtime.*
import com.onezee.triptrack.designsystem.components.ToastHost
import com.onezee.triptrack.designsystem.components.ToastItem
import com.onezee.triptrack.designsystem.components.ToastType
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.feature.social.SocialFeature
import com.onezee.triptrack.feature.stats.StatsScreen
import com.onezee.triptrack.feature.stats.StatsSource
import com.onezee.triptrack.feature.stats.StatsViewModel
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.entryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The social destination [dest] on the stack it was pushed to. Trips and journeys are drawn elsewhere. */
@Composable
fun SocialDestinationScreen(dest: TripsDestination, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    when (dest) {
        is TripsDestination.PublicProfile -> PublicProfileScreen(dest, social, navigator)
        is TripsDestination.FollowList -> FollowListScreen(dest, social, navigator)
        is TripsDestination.PublicStats -> PublicStatsScreen(dest, social, navigator)
        is TripsDestination.PublicMap -> PublicMapScreen(dest, social, navigator)
        is TripsDestination.PublicGarage -> PublicGarageScreen(dest, social, navigator)
        is TripsDestination.PublicCar -> PublicCarScreen(dest, social, navigator)
        is TripsDestination.PublicJourneys -> PublicJourneysScreen(dest, social, navigator)
        is TripsDestination.PublicAchievements -> PublicAchievementsScreen(dest, navigator)
        is TripsDestination.PublicLevels -> PublicLevelsScreen(dest, navigator)
        TripsDestination.Inbox -> InboxScreen(social, navigator)
        TripsDestination.Discover -> DiscoverScreen(social, navigator)
        else -> Unit
    }
}

/**
 * iOS StatsScreenView(source: RemoteTripSource): the same screen over someone's public trips. A
 * record's trip is asked from `/social/trip` (the server decides whether the viewer may see it)
 * and opened in the same stack, where iOS raised a cover because most of its hosts had no stack.
 */
@Composable
private fun PublicStatsScreen(dest: TripsDestination.PublicStats, social: SocialFeature, navigator: DestinationNavigator<TripsDestination>) {
    val strings = appStrings
    val scope = rememberCoroutineScope()
    val vm = entryViewModel("stats:${dest.id}") { StatsViewModel(StatsSource.Remote(social.api, dest.id), social.worker, social.clock, strings.zone) }
    var loading by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    StatsScreen(vm, dest.name, onBack = { navigator.pop() }, loadingRecordId = loading, onOpenTrip = { id, record ->
        if (loading != null) return@StatsScreen
        loading = record
        scope.launch {
            try {
                val item = withContext(social.worker) { social.api.trip(id, includeTrack = false).item }
                navigator.push(TripsDestination.TripDetail(item.id, preview = item.asTrip(), social = item))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { toast = ToastItem(ToastType.error, strings.tripLoadFailed()) }
            finally { loading = null }
        }
    })
    ToastHost(toast, onDismiss = { toast = null })
}
