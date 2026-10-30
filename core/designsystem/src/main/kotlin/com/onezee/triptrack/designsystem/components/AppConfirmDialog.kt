package com.onezee.triptrack.designsystem.components

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.onezee.triptrack.designsystem.theme.AppColors
import com.onezee.triptrack.designsystem.theme.AppRadius
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.InterLineHeight
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// iOS: AppConfirmDialog.swift, AppDialogAction. One answer on a house dialog. `kind` decides the
// colour, not the caller: a screen says what the button MEANS, or the twentieth dialog invents its
// own red.
class AppDialogAction(
    val title: String,
    val kind: Kind = Kind.primary,
    /** Set when a row needs its own tag for the UI tests. */
    val testTag: String? = null,
    val handler: () -> Unit = {},
) {
    enum class Kind {
        /** The ordinary confirming answer: accent fill, white ink. */
        primary,

        /** Irreversible: red fill, white ink. */
        destructive,

        /** A secondary choice that is neither: quiet fill, normal ink. */
        plain,
    }

    internal fun fill(colors: AppColors): Color = when (kind) {
        Kind.primary -> AppTheme.accent
        Kind.destructive -> AppTheme.red
        Kind.plain -> colors.cardAlt
    }

    internal fun ink(colors: AppColors): Color = when (kind) {
        Kind.primary, Kind.destructive -> Color.White
        Kind.plain -> colors.text
    }
}

/**
 * iOS: AppConfirmDialog.swift. The app's own modal, instead of every `AlertDialog` (CLAUDE.md
 * «Dialogs — always ours, never the system's»): a scrim that swallows taps, the question in our
 * type on our card, the answers stacked with the safe one, Cancel, last and nearest the thumb.
 *
 * Two traps this exists to close, both shipped on iOS before it:
 * - A system dialog dismisses itself on any button; an overlay does not. Every answer here plays
 *   the exit and calls [onDismiss] BEFORE its handler, so no caller leaves a card hanging over a
 *   write that already happened, and a handler may open the next screen or dialog at once. A
 *   handler therefore must not clear the presenting state itself.
 * - An overlay is not a modal to TalkBack. The dialog is its own window, the scrim is hidden from
 *   accessibility, and the card announces itself as a pane.
 *
 * It is a window (Compose `Dialog`) and not an overlay for the iOS reason too: an overlay is laid
 * out inside the view it modifies, so on a section it got a section-sized scrim and on a tab root
 * the tab bar stayed tappable over it. A window outranks the whole screen from any call site.
 *
 * [cancelTitle] null hides Cancel: only for a pure acknowledgement whose single action is the way
 * out.
 */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String?,
    actions: List<AppDialogAction>,
    onDismiss: () -> Unit,
    cancelTitle: String? = appStrings.cancel(),
) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val closing = remember { ClosingLatch() }

    // The exit plays before control goes back, so the card leaves the way it came. Once an answer
    // is chosen it is carried out exactly once, even if the dialog is torn down mid-exit; a
    // second tap during the exit does nothing (on iOS it could run the handler twice).
    fun dismiss(then: (() -> Unit)? = null) {
        if (!closing.close()) return
        scope.launch {
            try {
                progress.animateTo(0f, tween(160, easing = EaseIn))
            } finally {
                withContext(NonCancellable) {
                    currentOnDismiss()
                    then?.invoke()
                }
            }
        }
    }

    LaunchedEffect(Unit) { progress.animateTo(1f, tween(220, easing = EaseOut)) }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PlainModalWindow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value }
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { dismiss() } }
                    .semantics { hideFromAccessibility() }
                    .testTag("app_dialog_scrim"),
            )
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = 0.94f + 0.06f * progress.value
                        scaleX = scale
                        scaleY = scale
                        alpha = progress.value
                    }
                    .iosShadow(ContinuousRoundedRectangle(AppRadius.dialog), Color.Black.copy(alpha = 0.18f), radius = 24.dp, y = 8.dp)
                    .background(colors.card, ContinuousRoundedRectangle(AppRadius.dialog))
                    .padding(20.dp)
                    .semantics { paneTitle = title }
                    .testTag("app_dialog"),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = inter(19f, InterWeight.bold), color = colors.text, textAlign = TextAlign.Center)
                    if (!message.isNullOrEmpty()) {
                        Text(
                            message,
                            style = inter(13.5f).copy(lineHeightStyle = TrimmedLineSpacing),
                            color = colors.textSecondary,
                            lineHeight = points(13.5f * InterLineHeight + 3f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        DialogButton(
                            text = action.title,
                            ink = action.ink(colors),
                            fill = action.fill(colors),
                            testTag = action.testTag ?: "app_dialog_action",
                        ) {
                            when (action.kind) {
                                AppDialogAction.Kind.destructive -> haptics.action()
                                AppDialogAction.Kind.primary -> haptics.success()
                                AppDialogAction.Kind.plain -> haptics.tap()
                            }
                            // Dismiss FIRST: the handler may push a screen or present another
                            // sheet, and doing that under a card still on screen is how two
                            // modals end up fighting over the window.
                            dismiss(then = action.handler)
                        }
                    }
                    if (cancelTitle != null) {
                        DialogButton(cancelTitle, ink = colors.textSecondary, fill = colors.cardAlt, testTag = "app_dialog_cancel") {
                            haptics.tap()
                            dismiss()
                        }
                    }
                }
            }
        }
    }
}

/** iOS `.appConfirm(isPresented:…)`: the dialog while [isPresented]; [onDismissRequest] clears it. */
@Composable
fun AppConfirm(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    actions: List<AppDialogAction>,
    message: String? = null,
    cancelTitle: String? = appStrings.cancel(),
) {
    if (isPresented) AppConfirmDialog(title, message, actions, onDismissRequest, cancelTitle)
}

/**
 * iOS `.appConfirm(item:…)`, for call sites that carry their subject in a nullable rather than a
 * flag («delete THIS photo»). The handlers receive the item: by the time they run the state that
 * held it has been cleared, so they must not read it back from there.
 */
@Composable
fun <T : Any> AppConfirm(
    item: T?,
    onDismissRequest: () -> Unit,
    title: (T) -> String,
    actions: (T) -> List<AppDialogAction>,
    message: (T) -> String? = { null },
    cancelTitle: String? = appStrings.cancel(),
) {
    if (item != null) {
        key(item) { AppConfirmDialog(title(item), message(item), actions(item), onDismissRequest, cancelTitle) }
    }
}

@Composable
private fun DialogButton(text: String, ink: Color, fill: Color, testTag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxWidth()
            .plainPressFeedback(interaction)
            .background(fill, ContinuousRoundedRectangle(AppRadius.dialogButton))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = inter(15f, InterWeight.bold), color = ink, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** iOS `.lineSpacing`: extra space between lines only, none above the first or below the last. */
internal val TrimmedLineSpacing = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

/**
 * The house modals draw their own scrim and their own entrance, so the window gets neither the
 * platform dim nor the platform animation, and it spans the whole screen: with
 * `decorFitsSystemWindows = false` Compose lays the dialog out edge to edge, bars included.
 */
@Composable
internal fun PlainModalWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null) {
            window.setDimAmount(0f)
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setWindowAnimations(0)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
    }
}

/** Lets a modal close once: a second tap during the exit animation must not run anything. */
internal class ClosingLatch {
    private var closed = false

    /** True the first time only. */
    fun close(): Boolean = if (closed) false else true.also { closed = true }
}
