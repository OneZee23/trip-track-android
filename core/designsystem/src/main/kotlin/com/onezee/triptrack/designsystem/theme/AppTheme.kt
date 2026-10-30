package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** sRGB from the 0…255 channels the iOS tokens are written in, so each value reads like its source. */
internal fun rgb(red: Int, green: Int, blue: Int, alpha: Float = 1f): Color =
    Color(red = red / 255f, green = green / 255f, blue = blue / 255f, alpha = alpha)

internal fun hex(value: Long, alpha: Float = 1f): Color =
    rgb(((value shr 16) and 0xFF).toInt(), ((value shr 8) and 0xFF).toInt(), (value and 0xFF).toInt(), alpha)

// iOS: AppTheme.swift, AppTheme. The fixed accents are one value in both themes; the adaptive
// surfaces and inks live in AppColors.
object AppTheme {
    /** #C2452B — terracotta, the road-trip accent. */
    val accent = rgb(194, 69, 43)
    val accentBg = accent.copy(alpha = 0.08f)

    val green = rgb(46, 174, 80)
    val greenBg = green.copy(alpha = 0.08f)
    val blue = rgb(56, 132, 224)
    val blueBg = blue.copy(alpha = 0.08f)
    val red = rgb(220, 60, 50)
    val yellow = rgb(245, 190, 30)
    val purple = rgb(160, 90, 210)
    val teal = rgb(80, 190, 210)
    val orange = accent

    /**
     * The ground the vehicle sprites stand on, one value in both themes. Brand navy: measured on
     * outline contrast alone a light plate wins, but saturated paint needs a dark ground to hold
     * its colour, and side by side there was nothing to argue about.
     */
    val spritePlateTop = hex(0x29364F)
    val spritePlateBottom = hex(0x161B2C)

    /** The plate gradient as a single fill, for callers that just want a colour. */
    val spritePlate = hex(0x1F273D)

    /** Gold of the LVL pill on Я. Its ground is adaptive: [AppColors.goldBg]. */
    val gold = rgb(206, 154, 46)

    val accentDim = accent.copy(alpha = 0.15f)
    val redDim = red.copy(alpha = 0.12f)
    val blueDim = blue.copy(alpha = 0.12f)
    val greenDim = green.copy(alpha = 0.12f)
    val orangeDim = accent.copy(alpha = 0.12f)

    /** iOS `AppTheme.colors(for:)`, for code that knows the scheme outside a composition. */
    fun colors(dark: Boolean): AppColors = if (dark) AppColors.dark else AppColors.light

    /** The palette of the surrounding [TripTrackTheme]. */
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    /** iOS `colorScheme == .dark` of the surrounding theme. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDarkTheme.current
}

// iOS: AppTheme.swift, AppTheme.Colors plus the adaptive statics (textPrimary, surface, goldBg).
@Immutable
data class AppColors(
    val bg: Color,
    val card: Color,
    val roadHeroCard: Color,
    val roadHeroAccent: Color,
    /**
     * Distance digits in the summary tiles. `green` in dark; 10 % darker in light, where #2EAE50 on
     * a white card is 2.88 : 1, under the 3 : 1 floor for large digits, and #299D48 is 3.49 : 1.
     */
    val distance: Color,
    val cardAlt: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderBright: Color,
    val glass: Color,
    val glassBorder: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val goldBg: Color,
) {
    companion object {
        val light = AppColors(
            bg = rgb(248, 246, 242),
            card = Color.White,
            roadHeroCard = rgb(255, 250, 247),
            roadHeroAccent = AppTheme.accent,
            distance = rgb(41, 157, 72),
            cardAlt = rgb(244, 242, 238),
            text = rgb(30, 30, 35),
            textSecondary = rgb(100, 100, 110),
            textTertiary = rgb(155, 155, 165),
            border = Color.Black.copy(alpha = 0.05f),
            borderBright = Color.Black.copy(alpha = 0.08f),
            glass = Color.White.copy(alpha = 0.82f),
            glassBorder = Color.Black.copy(alpha = 0.06f),
            surface = rgb(248, 246, 242),
            surfaceElevated = Color.White,
            goldBg = rgb(247, 239, 222),
        )

        val dark = AppColors(
            bg = rgb(18, 18, 20),
            card = rgb(30, 30, 32),
            roadHeroCard = rgb(36, 37, 42),
            roadHeroAccent = rgb(255, 156, 123),
            distance = AppTheme.green,
            cardAlt = rgb(42, 42, 44),
            text = Color.White.copy(alpha = 0.92f),
            textSecondary = Color.White.copy(alpha = 0.55f),
            textTertiary = Color.White.copy(alpha = 0.28f),
            border = Color.White.copy(alpha = 0.08f),
            borderBright = Color.White.copy(alpha = 0.14f),
            glass = rgb(40, 40, 42, alpha = 0.72f),
            glassBorder = Color.White.copy(alpha = 0.16f),
            surface = rgb(28, 28, 30),
            surfaceElevated = rgb(40, 40, 42),
            goldBg = rgb(206, 154, 46, alpha = 0.16f),
        )
    }
}
