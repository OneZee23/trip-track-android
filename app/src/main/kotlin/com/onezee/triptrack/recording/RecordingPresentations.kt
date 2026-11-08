// iOS: ContentView.swift (recovery and summary are presented above every tab).
package com.onezee.triptrack.recording

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.R as Words
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.HideAppTabBar
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.location.drafts.DraftDecision
import com.onezee.triptrack.location.recording.StopOutcome
import com.onezee.triptrack.logic.RevealedLayer
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripDiscoveries
import com.onezee.triptrack.model.TripPhoto
import com.onezee.triptrack.ui.PhotoImport
import kotlinx.coroutines.*

@Composable
fun RecordingPresentations(graph: AppGraph) {
    val recorder by graph.location.recorder.state.collectAsState()
    val outcome by graph.location.recorder.completion.collectAsState()
    val scope = rememberCoroutineScope()
    val strings = appStrings
    var recovering by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    recorder.recovery?.let { recovery ->
        HideAppTabBar()
        fun resolve(finish: Boolean) {
            if (recovering) return
            recovering = true
            failed = false
            scope.launch {
                try {
                    withContext(graph.location.scope.coroutineContext.minusKey(Job)) {
                        if (finish) graph.location.finishRecovered() else graph.location.continueRecovered()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { recovering = false }
            }
        }
        RecoveryPromptSheet(recovery, recovering, { resolve(false) }, { resolve(true) }, failed)
    }
    LaunchedEffect(outcome) {
        if (outcome is StopOutcome.Discarded) { delay(5000); outcome?.let(graph.location.recorder::dismissCompletion) }
    }
    val trip = when (val result = outcome) {
        is StopOutcome.Saved -> result.trip
        is StopOutcome.AwaitingConfirmation -> result.trip
        else -> null
    }
    if (trip != null) {
        HideAppTabBar()
        key(trip.id) { SummaryPresentation(graph, trip, checkNotNull(outcome)) }
    }
    if (failed) ToastHost(remember { ToastItem(ToastType.error, strings.manualFailedDbTitle()) }, { failed = false })
}

@Composable
private fun SummaryPresentation(graph: AppGraph, initialTrip: Trip, outcome: StopOutcome) {
    val context = LocalContext.current
    val strings = appStrings
    val scope = rememberCoroutineScope()
    val settings by graph.settingsManager.state.collectAsState()
    val delta by graph.revealedLayer.lastIngest.collectAsState()
    val photos = remember { mutableStateMapOf<Uri, TripPhoto>() }
    var trip by remember(initialTrip) { mutableStateOf(initialTrip) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    var fog by remember { mutableStateOf<RevealedLayer?>(null) }
    LaunchedEffect(delta) { fog = graph.revealedLayer.layer() }
    val discoveries = delta?.takeIf { it.first == trip.id }?.second?.let {
        TripDiscoveries(trip.id, it.openedKm, it.newRegionIds, emptyList(), emptyList(), emptyList())
    }
    val preservedJunkReason = (outcome as? StopOutcome.AwaitingConfirmation)?.preservedJunkReason
    fun mutate(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        mutate {
            for (uri in uris.distinct()) if (uri !in photos && photos.size < 10) {
                val photo = PhotoImport.importSelected(context, uri, trip.id, graph.trips)
                if (photo == null) failed = true else photos[uri] = photo
            }
        }
    }
    ContentSizedSheet({}, background = AppColors.light.bg, detent = SheetDetent.large, dismissible = false) {
        Column(Modifier.fillMaxSize()) {
            preservedJunkReason?.let { reason ->
                val message = when (reason) {
                    com.onezee.triptrack.model.TripJunkClassifier.Reason.tooShort -> Words.string.android_recording_preserved_too_short
                    com.onezee.triptrack.model.TripJunkClassifier.Reason.noDrivingSpeed -> Words.string.android_recording_preserved_slow
                }
                Text(strings.tr(message), Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = AppType.body, color = AppTheme.colors.textSecondary)
            }
            TripCompleteSummary(trip, (outcome as? StopOutcome.Saved)?.rewards, discoveries,
            onDone = { notes, publish -> mutate {
                if (notes != trip.tripDescription.orEmpty()) graph.trips.updateNotes(trip.id, notes)
                if (publish) graph.trips.updatePrivacy(trip.id, false)
                graph.location.recorder.dismissCompletion(outcome)
                graph.tripsFeature.feedNavigator.push(TripsDestination.TripDetail(trip.id))
                graph.tabStore.select(AppTab.home)
            } },
            onSaveNotes = { notes -> mutate { graph.trips.updateNotes(trip.id, notes); trip = trip.copy(tripDescription = notes) } },
            onPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onConfirmDraft = { mutate {
                graph.location.queueDraftDecision(trip.id, DraftDecision.confirm)
                withContext(graph.location.scope.coroutineContext.minusKey(Job)) { graph.location.drafts.apply() }
                trip = graph.trips.fetchTripDetail(trip.id) ?: trip
            } },
            onDiscardDraft = { discarding = true }, busy = busy, photoCount = photos.size, averageSpeed = settings.averageSpeed, fog = fog, failed = failed,
            photos = {
                if (photos.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.toMap().forEach { (uri, photo) ->
                        // Android's picker has no persisted checked selection; removal lives beside the attached photo.
                        Column {
                            com.onezee.triptrack.ui.PhotoImage(com.onezee.triptrack.ui.PhotoSource.Local(photo.filename), maxPixels = 240, modifier = Modifier.size(72.dp))
                            Box(Modifier.size(44.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { mutate { graph.trips.deletePhoto(photo.id, trip.id); photos.remove(uri) } }
                                .semantics { contentDescription = strings.delete() }.testTag("summary_photo_remove_${photo.id}"), contentAlignment = Alignment.Center) {
                                Glyph(AppGlyph.trash, null, tint = AppTheme.red, size = 20.dp)
                            }
                        }
                    }
                }
            }, modifier = Modifier.weight(1f))
        }
    }
    if (discarding) AppConfirmDialog(strings.deleteTrip(), message = null, actions = listOf(AppDialogAction(strings.delete(), AppDialogAction.Kind.destructive) {
        mutate {
            graph.location.queueDraftDecision(trip.id, DraftDecision.discard)
            withContext(graph.location.scope.coroutineContext.minusKey(Job)) { graph.location.drafts.apply() }
            graph.location.recorder.dismissCompletion(outcome)
        }
    }), cancelTitle = strings.cancel(), onDismiss = { discarding = false })
    if (failed) ToastHost(remember { ToastItem(ToastType.error, strings.manualFailedDbTitle()) }, { failed = false })
}
