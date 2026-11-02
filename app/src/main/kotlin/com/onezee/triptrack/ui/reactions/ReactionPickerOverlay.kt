// iOS: ReactionPickerOverlay.swift
package com.onezee.triptrack.ui.reactions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onezee.triptrack.designsystem.components.PlainModalWindow
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.rememberHaptics
import com.onezee.triptrack.model.ReactionEmoji
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS: ReactionPickerOverlay.swift. The iMessage/Telegram-style picker raised by a hold on a card or
 * by «+»: a dimmed backdrop and a floating glass capsule with the six drawn icons (Figma canon-6; the
 * old 8-emoji palette 263:2 is retired), hugging its content. It springs in (scale 0.85 → 1, the icons
 * popping up one after another); a tap outside or back fades it out in 0.18 s first. Picking closes
 * it at once: the iOS parent removes it in the same tap.
 *
 * My stored reaction can be a legacy key: it highlights its canonical replacement, and tapping it
 * again passes the RAW stored emoji so the server's same-emoji check takes it back instead of
 * replacing ❤️ with 👍. iOS blurs the page under the dim (`.ultraThinMaterial`); Compose cannot blur
 * another layer's pixels on minSdk 29, so a translucent wash stands in for the material, as in
 * `glassBackground` (docs/divergences.md).
 */
@Composable
fun ReactionPickerOverlay(currentReaction: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val dark = AppTheme.isDark
    val haptics = rememberHaptics()
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val presence = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    // spring(response: 0.32, dampingFraction: 0.7): stiffness (2π / 0.32)².
    LaunchedEffect(Unit) { presence.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 385f)) }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                if (!reduceMotion) presence.animateTo(0f, tween(180, easing = EaseOut))
                onDismiss()
            }
        }
    }
    val mine = currentReaction?.let(ReactionEmoji::canonical)
    // A window of its own, like the house dialog: the dim covers the tab bar as it does on iOS, and
    // TalkBack and back stay inside the picker (iOS `.isModal` and the escape action).
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PlainModalWindow()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (dark) Color(0x591C1C1E) else Color.White.copy(alpha = 0.3f))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(Unit) { detectTapGestures { dismiss() } }
                    .semantics { hideFromAccessibility() }
                    .testTag("reaction_picker_scrim"),
            )
            Row(
                Modifier
                    .graphicsLayer {
                        val p = presence.value
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.85f + 0.15f * p
                        alpha = p.coerceIn(0f, 1f)
                    }
                    .iosShadow(CapsuleShape, Color.Black.copy(alpha = 0.18f), radius = 24.dp, y = 8.dp)
                    .background(colors.glass, CapsuleShape)
                    .border(1.dp, if (dark) colors.glassBorder else Color.White.copy(alpha = 0.5f), CapsuleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("reaction_picker"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReactionEmoji.all.forEachIndexed { index, emoji ->
                    val isMine = emoji == mine
                    // Staggered entry: spring(response: 0.32, dampingFraction: 0.6), 35 ms apart.
                    val rise = remember { Animatable(if (reduceMotion) 0f else 12f) }
                    LaunchedEffect(Unit) {
                        if (reduceMotion) return@LaunchedEffect
                        delay(index * 35L)
                        rise.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 385f))
                    }
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationY = rise.value * density
                                val grow = if (isMine) 1.08f else 1f
                                scaleX = grow
                                scaleY = grow
                            }
                            .size(40.dp)
                            // Selected: the solid peach disc (Figma #FCE4DB, one of the two allowed
                            // literals; the adaptive accentDim in dark) under the filled accent icon.
                            .background(if (isMine) (if (dark) AppTheme.accentDim else Color(0xFFFCE4DB)) else Color.Transparent, CircleShape)
                            .clickable(interactionSource = null, indication = null, role = Role.Button, enabled = !closing) {
                                if (isMine) haptics.reactionOff() else haptics.reactionOn()
                                closing = true
                                onPick(if (isMine) currentReaction ?: emoji else emoji)
                                onDismiss()
                            }
                            .testTag("reaction_option"),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReactionIcon(emoji, size = 24.dp, filled = isMine)
                    }
                }
            }
        }
    }
}
