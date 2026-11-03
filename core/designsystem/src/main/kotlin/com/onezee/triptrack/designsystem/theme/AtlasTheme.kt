package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// iOS: AtlasTheme.swift. Surfaces of Atlas and Places, and of the tab bar that every tab shares.
// The neutral ones are the SAME numbers as AppColors: until 26 September these two tabs had their
// own dark palette from an HTML mock, and on the phone they read as a different app. Only the
// colours the rest of the app has no use for stay here: map paper, fog, pins and the terracotta.
object AtlasTheme {
    val sheetRadius = 28.dp

    /** 16, the `surfaceCard()` default of Лента and Я: 18 from the mock read rounder than them. */
    val cardRadius = 16.dp
    val statRadius = 16.dp
    val sideInset = 16.dp
    val controlSize = 44.dp

    fun colors(dark: Boolean): AtlasColors = if (dark) AtlasColors.dark else AtlasColors.light

    val colors: AtlasColors
        @Composable @ReadOnlyComposable get() = LocalAtlasColors.current
}

@Immutable
data class AtlasColors(
    /** = AppColors.bg. */
    val background: Color,
    /** = AppColors.text. */
    val ink: Color,
    /** = AppColors.textSecondary. This was the one four times louder. */
    val secondary: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    /** = AppColors.card. */
    val card: Color,
    val navSurface: Color,
    val navInactive: Color,
    val progressTrack: Color,
    val separator: Color,
    val searchBackground: Color,
    /** Neutral label chip («частый гость»): quieter than a card. In accent it read as an award. */
    val chip: Color,
    /** A place outside the chosen period: muted, not gone. */
    val mutedPin: Color,
    val handle: Color,
    val control: Color,
    /** Atlas fog; also the paper of the Places map when there are no tiles. */
    val fog: Color,
    val fogGrain: Color,
    /** Glass of the buttons floating over a map — and only of those. */
    val glass: Color,
    val glassBorder: Color,
    /** Scrim of the Places search over its map: the page goes under it, the sheet stays. */
    val scrim: Color,
    /**
     * The dim a SYSTEM sheet puts over the page it covers, as the iPhone draws it: black at 0.12
     * in light and 0.29 in dark, measured on the DRIVE2 sheet (248 to 218) and on the Atlas «Вид
     * карты» sheet. A third of [scrim] in light: every house sheet used the stronger one until
     * the 3 November audit (SH-04) measured the page going from 248 to 158.
     */
    val sheetScrim: Color,
) {
    companion object {
        val light = AtlasColors(
            background = hex(0xF8F6F2),
            ink = hex(0x1E1E23),
            secondary = hex(0x64646E),
            accent = hex(0xC8472D),
            accentSoft = hex(0xF6E1D9),
            accentInk = hex(0x8A2E1B),
            card = hex(0xFFFFFF),
            navSurface = hex(0xFAF8F4),
            navInactive = hex(0x64646E),
            progressTrack = hex(0xE6DFD4),
            separator = hex(0xEAE3D8),
            searchBackground = hex(0xECE6DC),
            chip = hex(0xF0EBE3),
            mutedPin = hex(0xA0988B),
            handle = hex(0xD3CCC1),
            control = hex(0xFFFFFF),
            fog = hex(0xEEEEEC),
            fogGrain = hex(0x7D8792),
            glass = hex(0xFAF8F4, alpha = 0.94f),
            glassBorder = hex(0x000000, alpha = 0.06f),
            scrim = hex(0x0C0E10, alpha = 0.38f),
            sheetScrim = hex(0x000000, alpha = 0.12f),
        )

        val dark = AtlasColors(
            background = hex(0x121214),
            ink = hex(0xEBEBEB),
            secondary = hex(0x8C8C8C),
            accent = hex(0xEF8063),
            accentSoft = hex(0x432A22),
            accentInk = hex(0xF6B09A),
            card = hex(0x1E1E20),
            navSurface = hex(0x1E1E20),
            navInactive = hex(0x8C8C8C),
            progressTrack = hex(0x2A2A2C),
            separator = hex(0x2A2A2C),
            searchBackground = hex(0x2A2A2C),
            chip = hex(0x2A2A2C),
            mutedPin = hex(0x6E6961),
            handle = hex(0x61594F),
            control = hex(0x1E1E20),
            fog = hex(0x262B36),
            fogGrain = hex(0x7D8792),
            glass = hex(0x1E2125, alpha = 0.92f),
            glassBorder = hex(0xFFFFFF, alpha = 0.08f),
            scrim = hex(0x000000, alpha = 0.55f),
            sheetScrim = hex(0x000000, alpha = 0.29f),
        )
    }
}
