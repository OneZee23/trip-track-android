package com.onezee.triptrack.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezee.triptrack.designsystem.R

// iOS: AppFont.swift, InterFace. The weight → file table, one for every text the app draws:
// heavy and black are ExtraBold, anything lighter than regular is Regular.
enum class InterWeight(val fontWeight: FontWeight) {
    regular(FontWeight.W400),
    medium(FontWeight.W500),
    semibold(FontWeight.W600),
    bold(FontWeight.W700),
    heavy(FontWeight.W800),
}

/**
 * Inter, the one typeface of the app (OFL, `assets/licenses/Inter-OFL.txt`). Until 26 September
 * 2026 the iPhone app had two faces split across screens by accident; the owner: «Inter должен
 * быть везде». Only icons are drawn in anything else.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_medium, FontWeight.W500),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_bold, FontWeight.W700),
    Font(R.font.inter_extrabold, FontWeight.W800),
)

/**
 * Press Start 2P, the pixel face of the brand mark (OFL, `assets/licenses/PressStart2P-OFL.txt`).
 * The one exception to «one typeface», and only where the mark appears: the PRO pill, posters.
 */
val PressStart2PFamily = FontFamily(Font(R.font.press_start_2p_regular, FontWeight.W400))

/**
 * Inter's natural line height in ems: (ascender 1984 + descender 494) / 2048, with no line gap.
 * The hhea and typo metrics agree, so iOS and Android lay a line out at the same height, and an
 * iOS `.lineSpacing(x)` is this height plus x.
 */
const val InterLineHeight = 2478f / 2048f

/**
 * iOS `Font.inter(_:weight:)`: a FIXED size that does not follow the system font scale, like
 * `.custom(_, fixedSize:)`. It keeps the geometry of rows nobody has checked at large sizes yet:
 * growing text inside a fixed row only hides more of it. Reviewed forms use [interScaled].
 */
@Composable
@ReadOnlyComposable
fun inter(size: Float, weight: InterWeight = InterWeight.regular, monospacedDigits: Boolean = false): TextStyle =
    LocalDensity.current.inter(size, weight, monospacedDigits)

/** [inter] outside a composition, for drawing code that holds a [Density]. */
fun Density.inter(size: Float, weight: InterWeight = InterWeight.regular, monospacedDigits: Boolean = false): TextStyle =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight.fontWeight,
        fontSize = size.dp.toSp(),
        // Inter's tabular figures: times and distances that change in place keep their width.
        fontFeatureSettings = if (monospacedDigits) "tnum" else null,
    )

/**
 * iOS `Font.interScaled(_:weight:relativeTo:)`: the same default size, following the system font
 * scale. Android has one scaling curve, so iOS `relativeTo` has no counterpart. Call sites must
 * let the text wrap and grow.
 */
fun interScaled(size: Float, weight: InterWeight = InterWeight.regular, monospacedDigits: Boolean = false): TextStyle =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight.fontWeight,
        fontSize = size.sp,
        fontFeatureSettings = if (monospacedDigits) "tnum" else null,
    )

/** iOS `.custom("PressStart2P-Regular", fixedSize:)`. */
@Composable
@ReadOnlyComposable
fun pressStart2P(size: Float): TextStyle =
    TextStyle(fontFamily = PressStart2PFamily, fontSize = with(LocalDensity.current) { size.dp.toSp() })

/** A length in iOS points that does not follow the font scale: tracking, fixed line spacing. */
@Composable
@ReadOnlyComposable
fun points(value: Float): TextUnit = with(LocalDensity.current) { value.dp.toSp() }
