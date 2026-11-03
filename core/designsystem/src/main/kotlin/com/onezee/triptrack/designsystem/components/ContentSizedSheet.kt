package com.onezee.triptrack.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.theme.AppRadius
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.ContinuousTopRoundedRectangle
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The column of a house sheet plus iOS `@Environment(\.dismiss)`: [dismiss] plays the exit, then
 * reports the dismissal to the presenter.
 */
@Stable
interface SheetScope : ColumnScope {
    fun dismiss()
}

private class SheetColumnScope(column: ColumnScope, private val close: () -> Unit) : SheetScope, ColumnScope by column {
    override fun dismiss() = close()
}

/** iOS `presentationDetents`. */
enum class SheetDetent {
    /** As tall as the content (`ContentSizedSheet`). */
    content,

    /** Up to the status bar (`.large`), for content that scrolls. */
    large,
}

/**
 * iOS: ContentSizedSheet.swift, as a house bottom sheet. A sheet exactly as tall as what it holds:
 * a detent picked by hand leaves a field of dead ground under the buttons, worse the more the
 * content varies. Tall content lands at full height and scrolls if it was built to.
 *
 * [background] is the sheet's own ground, painted to the bottom edge, so the strip under the
 * content never shows another grey. The content gets the navigation-bar inset as bottom padding
 * unless [contentClearsNavigationBar]: content that ends in a caption with its own padding may
 * draw into that strip, so the sheet ends where the design says. Never pass it for content with
 * anything tappable at the bottom.
 *
 * A window, like the house dialog: it outranks the tab bar from any call site. The sheet slides in
 * on a spring, a drag down or the scrim closes it, and so does back.
 *
 * It looks like the iPhone's system sheet: the page under it dims by [AtlasColors.sheetScrim] (12 %
 * in light) and the top corners are [AppRadius.sheet], a continuous curve. The Atlas sheets and the
 * sync sheet ask for a larger [cornerRadius] there too. A [CustomNavBar] inside it clears the
 * grabber's strip, as iOS `navBarInSheet` does.
 */
@Composable
fun ContentSizedSheet(
    onDismissRequest: () -> Unit,
    background: Color = AppTheme.colors.card,
    detent: SheetDetent = SheetDetent.content,
    minimumHeight: Dp = 0.dp,
    contentClearsNavigationBar: Boolean = false,
    /** iOS interactiveDismissDisabled: recovery and saved-trip results require an explicit answer. */
    dismissible: Boolean = true,
    /** iOS `presentationCornerRadius`; the system's own, unless the screen sets one. */
    cornerRadius: Dp = AppRadius.sheet,
    content: @Composable SheetScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val canDismiss by rememberUpdatedState(dismissible)
    // 0 = closed (off screen), 1 = open. The sheet's own offset is derived from its height.
    val presence = remember { Animatable(0f) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    val closing = remember { ClosingLatch() }

    val dismiss: () -> Unit = remember {
        {
            if (canDismiss && closing.close()) {
                scope.launch {
                    try {
                        presence.animateTo(0f, if (reduceMotion) tween(0) else tween(220, easing = EaseIn))
                    } finally {
                        withContext(NonCancellable) { currentOnDismiss() }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        presence.animateTo(1f, if (reduceMotion) tween(0) else spring(dampingRatio = 0.9f, stiffness = 420f))
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PlainModalWindow()
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val maxSheet = maxHeight - statusTop - 10.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = presence.value }
                    .background(AtlasTheme.colors.sheetScrim)
                    .pointerInput(Unit) { detectTapGestures { dismiss() } }
                    .semantics { hideFromAccessibility() }
                    .testTag("sheet_scrim"),
            )
            val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val shape = remember(cornerRadius) { ContinuousTopRoundedRectangle(cornerRadius) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (detent == SheetDetent.large) Modifier.heightIn(min = maxSheet, max = maxSheet)
                        else Modifier.heightIn(min = minimumHeight, max = maxSheet),
                    )
                    .onSizeChanged { sheetHeight = it.height.toFloat() }
                    // The layer's own height: the slide needs nothing measured from outside it.
                    .graphicsLayer {
                        translationY = (1f - presence.value) * size.height + drag.coerceAtLeast(0f)
                    }
                    .draggable(
                        enabled = dismissible,
                        state = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            val threshold = with(density) { 120.dp.toPx() }
                            if (drag > sheetHeight * 0.25f || drag > threshold || velocity > 1800f) {
                                // Hand the dragged distance to the exit, so the sheet leaves from
                                // where the finger let go instead of jumping back first.
                                val from = 1f - drag / sheetHeight.coerceAtLeast(1f)
                                drag = 0f
                                presence.snapTo(from.coerceIn(0f, 1f))
                                dismiss()
                            } else {
                                Animatable(drag).animateTo(0f, spring(stiffness = 600f)) { drag = value }
                            }
                        },
                    )
                    .background(background, shape)
                    .padding(bottom = if (contentClearsNavigationBar) 0.dp else navigation)
                    .testTag("sheet"),
            ) {
                val sheet = remember(this) { SheetColumnScope(this, dismiss) }
                CompositionLocalProvider(LocalNavBarInSheet provides true) { sheet.content() }
            }
        }
    }
}
