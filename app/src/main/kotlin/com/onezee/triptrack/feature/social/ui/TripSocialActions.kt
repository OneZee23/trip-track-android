// iOS: TripDetailView.swift (openStoryShare, the social block under the details), ShareLinkPresenter.swift,
// PublicJourneyView.swift.
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

/** A journey's «Поделиться» card: the link of the journey into the system sheet. */
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

/** iOS: TripDetailView `isGeneratingShare` and `openStoryShare`. What the top bar needs of a share in progress: whether the button is spent, and the tap. */
@Stable
class TripShare(val busy: Boolean, val start: () -> Unit)

/**
 * iOS: TripDetailView.openStoryShare, wired to the top bar's share. A guest is asked to sign in; a
 * private trip of one's own is published first (the screen's publish sheet, after which [autoShare]
 * flips and the share goes on by itself); anything else mints the link and hands it to the system
 * sheet. iOS opens the poster studio here, which is not ported: the link is what goes out.
 * [onFailed] is the screen's toast; the sign-in sheet is drawn from here, in a window of its own.
 */
@Composable
fun rememberTripShare(
    social: SocialFeature?,
    tripId: java.util.UUID,
    title: String,
    needsPublish: Boolean,
    onPublish: () -> Unit,
    autoShare: Boolean,
    onShared: () -> Unit,
    onFailed: () -> Unit,
): TripShare {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var signIn by remember { mutableStateOf(false) }
    fun start() {
        val services = social ?: return
        if (busy) return
        if (!services.signedIn) { signIn = true; return }
        if (needsPublish) { onPublish(); return }
        busy = true
        scope.launch {
            try { ShareLinks.share(context, services.api.shareTrip(tripId).shareUrl, title) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onFailed() }
            finally { busy = false; onShared() }
        }
    }
    LaunchedEffect(autoShare) { if (autoShare) start() }
    if (signIn) social?.auth?.let { SignInPromptSheet(SignInAction.share, it, onDismissRequest = { signIn = false }) }
    return TripShare(busy, ::start)
}

/**
 * iOS: TripDetailView.swift, the «Реакции» block of the details column (see [TripReactionsSection]).
 * Share moved to the top bar and the screen has no report (iOS offers it on the cards and the
 * profile). The reaction picker and the reactors list belong to the screen's root ([onOpenPicker],
 * [onPeekReactors]), never to the scroll. Reacting needs an account; a guest is asked to sign in.
 */
@Composable
fun TripSocialActions(state: TripDetailState, vm: TripDetailViewModel, social: SocialFeature,
                      onOpenPicker: () -> Unit = {}, onPeekReactors: (String) -> Unit = {}, onOpenProfile: (com.onezee.triptrack.model.SocialAuthor) -> Unit = {}) {
    if (state.trip == null) return
    val haptics = rememberHaptics()
    var signIn by remember { mutableStateOf(false) }
    TripReactionsSection(
        state = state,
        onReact = { emoji -> haptics.tap(); if (social.signedIn) vm.toggleReaction(emoji) else signIn = true },
        onOpenPicker = { if (social.signedIn) onOpenPicker() else signIn = true },
        onPeek = onPeekReactors,
        onOpenProfile = { onOpenProfile(it.socialAuthor()) },
    )
    if (signIn) social.auth?.let { SignInPromptSheet(SignInAction.react, it, onDismissRequest = { signIn = false }) }
}
