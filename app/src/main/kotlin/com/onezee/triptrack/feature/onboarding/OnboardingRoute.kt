// iOS: OnboardingView.finishOnboarding, TripTrackApp (the onboarding branch of the window)
package com.onezee.triptrack.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.model.Trip
import kotlinx.coroutines.CancellationException

/** The first run, before the tab shell: the questions go into the recording's own ledger. */
@Composable
fun OnboardingRoute(graph: AppGraph, resumeTick: Int) {
    val settings by graph.settingsManager.state.collectAsState()
    val latestTrip by produceState<Trip?>(null, graph, resumeTick) {
        value = try { graph.trips.fetchAllTrips().filter { it.endDate != null }.maxByOrNull { it.endDate!! } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    OnboardingScreen(
        ledger = graph.location.permissionLedger,
        distanceUnit = settings.distanceUnit,
        exampleTrip = latestTrip,
        resumeTick = resumeTick,
        onFinished = {
            // Лента is home: it has other people's trips from the very first launch.
            graph.tabStore.select(AppTab.home)
            // The answers may have opened activity recognition: register the transitions now.
            graph.location.rearmAutoRecord()
            graph.onboarding.complete()
        },
    )
}
