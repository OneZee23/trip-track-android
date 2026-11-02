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
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.feature.trip.*
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

/**
 * iOS: TripDetailView.swift, the social block under the details: the «Реакции» section (see
 * [TripReactionsSection]), then sharing and, on someone else's trip, the report flag. The reaction
 * picker and the reactors list belong to the screen's root ([onOpenPicker], [onPeekReactors]), never
 * to the scroll. Reacting needs an account; a guest is asked to sign in.
 */
@Composable
fun TripSocialActions(state: TripDetailState, vm: TripDetailViewModel, social: SocialFeature, onPublishForShare: (() -> Unit)? = null, autoShare: Boolean = false, onShared: () -> Unit = {},
                      onOpenPicker: () -> Unit = {}, onPeekReactors: (String) -> Unit = {}, onOpenProfile: (com.onezee.triptrack.model.SocialAuthor) -> Unit = {}) {
    val trip = state.trip ?: return
    val strings = appStrings
    val haptics = rememberHaptics()
    var report by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TripReactionsSection(
            state = state,
            onReact = { emoji -> haptics.tap(); if (social.signedIn) vm.toggleReaction(emoji) else signIn = true },
            onOpenPicker = { if (social.signedIn) onOpenPicker() else signIn = true },
            onPeek = onPeekReactors,
            onOpenProfile = { onOpenProfile(it.socialAuthor()) },
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialShareAction(social, trip.id, false, trip.title ?: strings.tripTitle(), beforeShare = onPublishForShare.takeIf { trip.isPrivate }, autoStart = autoShare, onShared = onShared)
            if (!state.isOwn) PressableCard(onClick = { if (social.signedIn) report = true else signIn = true }, modifier = Modifier.testTag("trip_report")) {
                Glyph(AppGlyph.flag, strings.reportProfileAction(), Modifier.padding(12.dp), tint = AppTheme.colors.textTertiary)
            }
        }
    }
    if (report) ReportSheet(ReportTarget.Trip(trip.id), social) { report = false }
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.react, it, onDismissRequest = { signIn = false }) }
}
