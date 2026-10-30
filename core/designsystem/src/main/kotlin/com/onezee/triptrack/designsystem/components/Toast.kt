package com.onezee.triptrack.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.theme.AppRadius
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.ContinuousRoundedRectangle
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import java.util.UUID
import kotlinx.coroutines.delay

// iOS: ToastView.swift, ToastItem. Two items are the same toast only if they are the same id.
@Immutable
class ToastItem(
    val type: ToastType,
    val message: String,
    /** Words of the undo button; the call site passes `appStrings.undo()`. */
    val undoLabel: String = "",
    val undoAction: (() -> Unit)? = null,
    val id: UUID = UUID.randomUUID(),
) {
    override fun equals(other: Any?): Boolean = other is ToastItem && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

// iOS: ToastView.swift, ToastItem.ToastType.
enum class ToastType(val glyph: AppGlyph, val autoDismissMillis: Long) {
    success(AppGlyph.checkmarkCircleFill, 2_500),
    error(AppGlyph.xmarkCircleFill, 2_500),
    info(AppGlyph.infoCircleFill, 2_500),
    undo(AppGlyph.arrowUturnBackwardCircleFill, 3_500);

    val tint: Color
        get() = when (this) {
            success -> AppTheme.green
            error -> AppTheme.red
            info -> AppTheme.blue
            undo -> AppTheme.accent
        }
}

/**
 * iOS: ToastView.swift. Sized to what it says and centred, not a full-width slab: a three-word
 * confirmation stretched across the screen reads as a banner announcing a problem, a pill reads as
 * a receipt. A solid card, not a material: over the warm background the material went milky grey.
 */
@Composable
fun ToastView(item: ToastItem, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val shape = ContinuousRoundedRectangle(AppRadius.toast)
    val shadow = Color.Black.copy(alpha = if (AppTheme.isDark) 0.35f else 0.10f)
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 340.dp)
            .iosShadow(shape, shadow, radius = 14.dp, y = 5.dp)
            .background(colors.card, shape)
            .border(0.5.dp, colors.border, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("toast"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // iOS: an 18 pt semibold symbol.
        Glyph(item.type.glyph, contentDescription = null, tint = item.type.tint, size = 20.dp)
        // Two lines rather than an ellipsis: a truncated failure explains nothing.
        Text(item.message, style = inter(14f, InterWeight.medium), color = colors.text, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        val undo = item.undoAction
        if (item.type == ToastType.undo && undo != null) {
            Spacer(Modifier.width(4.dp))
            val interaction = remember { MutableInteractionSource() }
            Text(
                item.undoLabel,
                style = inter(14f, InterWeight.bold),
                color = AppTheme.accent,
                maxLines = 1,
                modifier = Modifier
                    .plainPressFeedback(interaction)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        haptics.action()
                        undo()
                        onDismiss()
                    }
                    .testTag("toast_undo"),
            )
        }
    }
}

/**
 * iOS `.toast(item:)`: the toast at the top of what it is placed over, below the status bar,
 * gone after its delay or a flick up. Place it last in a full-size Box so it floats above the
 * screen. [onDismiss] clears the caller's item.
 */
@Composable
fun ToastHost(item: ToastItem?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(item) }
    if (item != null) shown = item
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // SwiftUI .spring(response: 0.4, dampingFraction: 0.82): stiffness = (2π / response)².
    val springSpec = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 246.7f)
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = item != null,
            enter = slideInVertically(springSpec) { -it } + fadeIn(spring(dampingRatio = 0.82f, stiffness = 246.7f)),
            exit = slideOutVertically(springSpec) { -it } + fadeOut(spring(dampingRatio = 0.82f, stiffness = 246.7f)),
        ) {
            val toast = shown ?: return@AnimatedVisibility
            LaunchedEffect(toast.id) {
                delay(toast.type.autoDismissMillis)
                currentOnDismiss()
            }
            var travel by remember(toast.id) { mutableStateOf(0f) }
            ToastView(
                toast,
                onDismiss = { currentOnDismiss() },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .pointerInput(toast.id) {
                        detectVerticalDragGestures(
                            onDragStart = { travel = 0f },
                            onDragEnd = { if (travel < -10.dp.toPx()) currentOnDismiss() },
                        ) { _, dragAmount -> travel += dragAmount }
                    },
            )
        }
    }
}
