// iOS: MeDest / ProfileView.push. Destinations carry their subject, and a second tap is idempotent.
package com.onezee.triptrack.feature.me

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import java.util.UUID

// iOS: MeDest in ProfileView.swift.
internal sealed interface MeRoute {
    data object Garage : MeRoute
    data object Profile : MeRoute
    data class Passport(val id: UUID) : MeRoute
    data object Achievements : MeRoute
    data class Badge(val id: String) : MeRoute
    data object Clubs : MeRoute
    data object Drafts : MeRoute
    data class Trip(val id: UUID) : MeRoute
    data class Journey(val id: UUID) : MeRoute
}

internal class MeNavigator(initial: List<MeRoute> = emptyList()) {
    var path by mutableStateOf(initial)
        private set
    val current get() = path.lastOrNull()
    fun push(destination: MeRoute) { if (current != destination) path = path + destination }
    fun pop() { path = path.dropLast(1) }
    companion object {
        val Saver = listSaver<MeNavigator, String>(
            save = { it.path.map { route -> when (route) {
                MeRoute.Garage -> "garage"; MeRoute.Profile -> "profile"; MeRoute.Achievements -> "achievements"; MeRoute.Clubs -> "clubs"; MeRoute.Drafts -> "drafts"
                is MeRoute.Passport -> "passport:${route.id}"; is MeRoute.Badge -> "badge:${route.id}"
                is MeRoute.Trip -> "trip:${route.id}"; is MeRoute.Journey -> "journey:${route.id}"
            } } },
            restore = { raw -> MeNavigator(raw.map { value -> when (value.substringBefore(':')) {
                "garage" -> MeRoute.Garage; "profile" -> MeRoute.Profile; "achievements" -> MeRoute.Achievements; "clubs" -> MeRoute.Clubs; "drafts" -> MeRoute.Drafts
                "passport" -> MeRoute.Passport(UUID.fromString(value.substringAfter(':')))
                "trip" -> MeRoute.Trip(UUID.fromString(value.substringAfter(':')))
                "journey" -> MeRoute.Journey(UUID.fromString(value.substringAfter(':')))
                else -> MeRoute.Badge(value.substringAfter(':'))
            } }) },
        )
    }
}
