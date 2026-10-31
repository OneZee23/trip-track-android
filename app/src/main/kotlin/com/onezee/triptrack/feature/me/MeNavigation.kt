// iOS: MeDest / ProfileView.push. A second tap on the same subject is idempotent.
package com.onezee.triptrack.feature.me

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.feature.TripsDestinationScreen
import com.onezee.triptrack.feature.TripsFeature
import com.onezee.triptrack.model.SocialFeedTrip
import com.onezee.triptrack.ui.navigation.DestinationNavigator
import com.onezee.triptrack.ui.navigation.NavDestination
import com.onezee.triptrack.ui.navigation.TabNavHost
import com.onezee.triptrack.ui.navigation.TabNavigator
import java.util.UUID

// iOS: MeDest in ProfileView.swift. Keys identify the subject, independently of opening previews.
internal sealed interface MeRoute : NavDestination {
    data object Garage : MeRoute
    data object Profile : MeRoute
    data class Passport(val id: UUID) : MeRoute
    data object Achievements : MeRoute
    data class Badge(val id: String) : MeRoute
    data object Clubs : MeRoute
    data object Levels : MeRoute
    data object Country : MeRoute
    data object Drafts : MeRoute
    data class Trip(val id: UUID, val preview: com.onezee.triptrack.model.Trip? = null, val social: SocialFeedTrip? = null) : MeRoute
    data class Journey(val id: UUID) : MeRoute
    data class SocialJourney(val id: UUID) : MeRoute
    /** iOS MeDest.stats: the full statistics of one's own library, from the hero's numbers. */
    data object Stats : MeRoute
    /** iOS MeDest's social cases (profile, follow list, someone's stats, map, garage …): the shared screens. */
    data class Social(val destination: TripsDestination) : MeRoute

    override val key: String get() = when (this) {
        Garage -> "garage"; Profile -> "profile"; Achievements -> "achievements"; Clubs -> "clubs"
        Levels -> "levels"; Country -> "country"; Drafts -> "drafts"; Stats -> "stats"
        is Passport -> "passport:$id"; is Badge -> "badge:$id"
        is Trip -> "trip:$id"; is Journey -> "journey:$id"; is SocialJourney -> "social-journey:$id"
        is Social -> "social|${destination.key}"
    }

    val tripsDestination: TripsDestination? get() = when (this) {
        Drafts -> TripsDestination.Drafts
        is Trip -> TripsDestination.TripDetail(id, preview, social)
        is Journey -> TripsDestination.JourneyDetail(id)
        is SocialJourney -> TripsDestination.SocialJourney(id)
        is Social -> destination
        else -> null
    }
}

// iOS: ProfileView.mePath. The shared trip screens push into this same stack, never a nested one.
internal class MeNavigator(initial: List<MeRoute> = emptyList()) : DestinationNavigator<TripsDestination> {
    val stack = TabNavigator<MeRoute>().apply { initial.forEach(::push) }
    val path get() = stack.stack
    val current get() = stack.top
    fun push(destination: MeRoute) = stack.push(destination)
    override fun push(destination: TripsDestination) = push(when (destination) {
        is TripsDestination.TripDetail -> MeRoute.Trip(destination.id, destination.preview, destination.social)
        is TripsDestination.JourneyDetail -> MeRoute.Journey(destination.id)
        is TripsDestination.SocialJourney -> MeRoute.SocialJourney(destination.id)
        TripsDestination.Drafts -> MeRoute.Drafts
        else -> MeRoute.Social(destination)
    })
    override fun pop() = stack.pop()

    companion object {
        fun saver(owner: MeNavigator) = listSaver<MeNavigator, String>(
            save = { it.path.map { route -> route.key } },
            // Configuration changes reuse the live entries; a fresh process restores only subjects.
            restore = { raw -> owner.apply { if (path.isEmpty()) raw.mapNotNull { value -> when {
                // A social screen comes back only when its key names it whole; the others close.
                value.startsWith("social|") -> socialRoute(value.substringAfter('|'))
                else -> when (value.substringBefore(':')) {
                "garage" -> MeRoute.Garage; "profile" -> MeRoute.Profile; "achievements" -> MeRoute.Achievements
                "clubs" -> MeRoute.Clubs; "levels" -> MeRoute.Levels; "country" -> MeRoute.Country; "drafts" -> MeRoute.Drafts
                "stats" -> MeRoute.Stats
                "passport" -> MeRoute.Passport(UUID.fromString(value.substringAfter(':')))
                "trip" -> MeRoute.Trip(UUID.fromString(value.substringAfter(':')))
                "journey" -> MeRoute.Journey(UUID.fromString(value.substringAfter(':')))
                "social-journey" -> MeRoute.SocialJourney(UUID.fromString(value.substringAfter(':')))
                else -> MeRoute.Badge(value.substringAfter(':'))
            } } }.forEach(::push) } },
        )
    }
}

/** The social screens that restore from their key alone: a profile and its follow lists. */
private fun socialRoute(key: String): MeRoute? {
    val parts = key.split(':')
    val id = parts.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
    return when (parts[0]) {
        "profile" -> MeRoute.Social(TripsDestination.PublicProfile(id))
        "follow" -> parts.getOrNull(2)?.let { mode -> runCatching { com.onezee.triptrack.feature.social.FollowListMode.valueOf(mode) }.getOrNull() }
            ?.let { MeRoute.Social(TripsDestination.FollowList(id, it)) }
        else -> null
    }
}

/** iOS: ProfileView.navigationDestination(for: MeDest.self). One owner for scroll and view models. */
@Composable
internal fun MeNavHost(feature: TripsFeature, navigator: MeNavigator, root: @Composable () -> Unit,
                       screen: @Composable (MeRoute) -> Unit = {}) {
    if (navigator.current != null) HideAppTabBar()
    TabNavHost(navigator.stack, root = root) { destination ->
        val trips = destination.tripsDestination
        if (trips == null) screen(destination) else TripsDestinationScreen(trips, feature, navigator)
    }
}
