package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// iOS: AppTheme.swift, Haptics. The same six moments, each on the Android constant that means the
// same thing; the system's own «touch feedback» switch still decides whether anything is felt.
class Haptics(private val feedback: HapticFeedback) {
    /** A light tap: a tab, a row, a close button. */
    fun tap() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A deliberate action, a destructive one among them. */
    fun action() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    fun error() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
    fun selection() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A reaction lands: a crisp click. */
    fun reactionOn() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOn)

    /** A reaction taken back: duller, so undoing never feels like casting another vote. */
    fun reactionOff() = feedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
