// iOS: TripDetailView.swift (social bar), ShareLinkPresenter.swift, PublicJourneyView.swift.
package com.onezee.triptrack.feature.social.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.*
import com.onezee.triptrack.designsystem.theme.*
import com.onezee.triptrack.feature.account.*
import com.onezee.triptrack.feature.feed.ui.ReactionPicker
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.feature.trip.*
import com.onezee.triptrack.ui.cards.ReactionTallyPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SocialShareAction(social: SocialFeature, id: java.util.UUID, journey: Boolean, title: String, beforeShare: (() -> Unit)? = null, autoStart: Boolean = false, onShared: () -> Unit = {}) {
    val strings = appStrings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastItem?>(null) }
    fun share() {
        if (busy) return
        if (!social.signedIn) { signIn = true; return }
        if (beforeShare != null) { beforeShare(); return }
        busy = true
        scope.launch {
            try {
                val url = if (journey) social.api.shareJourney(id).shareUrl else social.api.shareTrip(id).shareUrl
                ShareLinks.share(context, url, title)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { toast = ToastItem(ToastType.error, strings.proFailed()) }
            finally { busy = false; onShared() }
        }
    }
    LaunchedEffect(autoStart) { if (autoStart) share() }
    PressableCard(onClick = ::share, modifier = Modifier.testTag(if (journey) "journey_share" else "trip_share")) {
        Row(Modifier.surfaceCard(12.dp).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (busy) com.onezee.triptrack.feature.account.Spinner(AppTheme.accent)
            else Glyph(AppGlyph.squareAndArrowUp, null, tint = AppTheme.accent, size = 18.dp)
            Text(strings.share(), style = AppType.chipActive, color = AppTheme.accent)
        }
    }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.generic, it, onDismissRequest = { signIn = false }) }
    ToastHost(toast, onDismiss = { toast = null })
}

@Composable
fun TripSocialActions(state: TripDetailState, vm: TripDetailViewModel, social: SocialFeature, onPublishForShare: (() -> Unit)? = null, autoShare: Boolean = false, onShared: () -> Unit = {}) {
    val trip = state.trip ?: return
    val strings = appStrings
    var picker by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.reactionBreakdown.forEach { tally -> ReactionTallyPill(tally.emoji, tally.count, tally.emoji == state.myReaction, !state.isOwn) {
                if (social.signedIn) vm.toggleReaction(tally.emoji) else signIn = true
            } }
            if (!state.isOwn) PressableCard(onClick = { if (social.signedIn) picker = true else signIn = true }, modifier = Modifier.testTag("trip_react")) {
                Text("+", Modifier.padding(12.dp), style = AppType.chipActive, color = AppTheme.accent)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialShareAction(social, trip.id, false, trip.title ?: strings.tripTitle(), beforeShare = onPublishForShare.takeIf { trip.isPrivate }, autoStart = autoShare, onShared = onShared)
            if (!state.isOwn) PressableCard(onClick = { if (social.signedIn) report = true else signIn = true }, modifier = Modifier.testTag("trip_report")) {
                Glyph(AppGlyph.flag, strings.reportProfileAction(), Modifier.padding(12.dp), tint = AppTheme.colors.textTertiary)
            }
        }
    }
    if (picker) ReactionPicker(state.myReaction, onPick = { vm.toggleReaction(it) }, onDismiss = { picker = false })
    if (report) ReportSheet(ReportTarget.Trip(trip.id), social) { report = false }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.react, it, onDismissRequest = { signIn = false }) }
}
