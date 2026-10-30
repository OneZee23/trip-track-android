package com.onezee.triptrack.designsystem.theme

import androidx.compose.ui.unit.dp

// The radii and gaps the iOS components repeat as literals (AppTheme.swift, AppConfirmDialog.swift,
// ToastView.swift, SettingsOptionPicker.swift, ProfileTripCardView.swift, AtlasTheme.swift).
// Named here so a ported call site can say which surface it is instead of retyping a number.
object AppRadius {
    /** `surfaceCard()` default: cards of Лента, Я, Atlas and Places. */
    val card = 16.dp

    /** `glassBackground()` default. */
    val glass = 20.dp

    /** House dialog card. Continuous corners. */
    val dialog = 22.dp

    /** Buttons inside the house dialog. Continuous corners. */
    val dialogButton = 14.dp

    /** The toast pill. Continuous corners. */
    val toast = 18.dp

    /** Selected row fill in a picker sheet. */
    val pickerRow = 12.dp

    /** Leading badge disc of a picker row: half of its 32 so it reads as a circle. */
    val pickerBadge = 16.dp

    /** Photo and map thumbnails inside a card. */
    val thumbnail = 12.dp

    /** Top corners of a bottom sheet, the house value of the Atlas sheet. */
    val sheet = AtlasTheme.sheetRadius
}

object AppSpacing {
    /** Side margin of screen content, sheet headers and the tab bar. */
    val screenSide = 16.dp

    /** Gap between cards in a list. */
    val listStep = 12.dp

    /** Horizontal field of a card row (`ProfileTripCardView`). */
    val cardRowHorizontal = 13.dp

    /** Top field of a card row (`ProfileTripCardView`). */
    val cardRowTop = 12.dp
}
