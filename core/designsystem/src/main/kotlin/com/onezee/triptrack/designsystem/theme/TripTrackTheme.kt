package com.onezee.triptrack.designsystem.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.onezee.triptrack.designsystem.components.HouseIndication
import com.onezee.triptrack.designsystem.components.LocalContentColor
import com.onezee.triptrack.designsystem.components.LocalTextStyle
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings

// Dynamic, not static: the palette animates on a theme change, and only its readers should
// recompose on every frame of that fade.
val LocalAppColors = compositionLocalOf { AppColors.light }
val LocalAtlasColors = compositionLocalOf { AtlasColors.light }
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * iOS `\.windowColorScheme`: what the WINDOW looks like, that is, the colour of the status bar
 * glyphs. Usually the theme itself; the record screen paints a night map under a forced dark
 * scheme while the glyphs still follow the window. `null` means «the same as the theme».
 */
val LocalWindowIsDark = staticCompositionLocalOf<Boolean?> { null }

/**
 * iOS `accessibilityReduceMotion`. Android has no setting with that name; «Remove animations»
 * (animator duration scale 0) is the one that asks the same thing.
 */
val LocalReduceMotion = compositionLocalOf { false }

val LocalAppStrings = staticCompositionLocalOf<AppStrings> {
    error("AppStrings are provided by TripTrackTheme")
}

/** The strings facade of the surrounding [TripTrackTheme], in the app's chosen language. */
val appStrings: AppStrings
    @Composable @ReadOnlyComposable get() = LocalAppStrings.current

/**
 * The palette, the strings and the default text of every TripTrack screen.
 *
 * A theme change cross-fades the palette in 0.28 s, the iOS window dissolve of
 * `ThemeManager.paint`: a colour scheme swap is otherwise a hard cut between two frames. The first
 * composition does not animate, like the cold-start paint.
 */
@Composable
fun TripTrackTheme(
    isDark: Boolean,
    language: AppLanguage,
    windowIsDark: Boolean? = null,
    reduceMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val strings = remember(context, language) { AppStrings(context, language) }
    val colors = animatedColors(AppTheme.colors(isDark))
    val atlas = animatedAtlasColors(AtlasTheme.colors(isDark))
    val systemReduceMotion = rememberRemoveAnimations(context.contentResolver)
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAtlasColors provides atlas,
        LocalIsDarkTheme provides isDark,
        LocalWindowIsDark provides windowIsDark,
        LocalReduceMotion provides (reduceMotion ?: systemReduceMotion),
        LocalAppStrings provides strings,
        LocalContentColor provides colors.text,
        // No Material theme here, so nothing else would answer a press: without this every
        // clickable that names no indication draws the foundation's grey rectangle.
        LocalIndication provides HouseIndication,
    ) {
        val body: TextStyle = AppType.body
        CompositionLocalProvider(LocalTextStyle provides body, content = content)
    }
}

private val paletteFade: AnimationSpec<Color> = tween(durationMillis = 280)

@Composable
private fun animatedColor(target: Color): Color = animateColorAsState(target, paletteFade, label = "palette").value

@Composable
private fun animatedColors(target: AppColors) = AppColors(
    bg = animatedColor(target.bg),
    card = animatedColor(target.card),
    roadHeroCard = animatedColor(target.roadHeroCard),
    roadHeroAccent = animatedColor(target.roadHeroAccent),
    distance = animatedColor(target.distance),
    cardAlt = animatedColor(target.cardAlt),
    text = animatedColor(target.text),
    textSecondary = animatedColor(target.textSecondary),
    textTertiary = animatedColor(target.textTertiary),
    border = animatedColor(target.border),
    borderBright = animatedColor(target.borderBright),
    glass = animatedColor(target.glass),
    glassBorder = animatedColor(target.glassBorder),
    surface = animatedColor(target.surface),
    surfaceElevated = animatedColor(target.surfaceElevated),
    goldBg = animatedColor(target.goldBg),
)

@Composable
private fun animatedAtlasColors(target: AtlasColors) = AtlasColors(
    background = animatedColor(target.background),
    ink = animatedColor(target.ink),
    secondary = animatedColor(target.secondary),
    accent = animatedColor(target.accent),
    accentSoft = animatedColor(target.accentSoft),
    accentInk = animatedColor(target.accentInk),
    card = animatedColor(target.card),
    navSurface = animatedColor(target.navSurface),
    navInactive = animatedColor(target.navInactive),
    progressTrack = animatedColor(target.progressTrack),
    separator = animatedColor(target.separator),
    searchBackground = animatedColor(target.searchBackground),
    chip = animatedColor(target.chip),
    mutedPin = animatedColor(target.mutedPin),
    handle = animatedColor(target.handle),
    control = animatedColor(target.control),
    fog = animatedColor(target.fog),
    fogGrain = animatedColor(target.fogGrain),
    glass = animatedColor(target.glass),
    glassBorder = animatedColor(target.glassBorder),
    scrim = animatedColor(target.scrim),
    sheetScrim = animatedColor(target.sheetScrim),
)

private fun removeAnimations(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Follows the setting while the screen is up: it can change from Quick Settings mid-session. */
@Composable
private fun rememberRemoveAnimations(resolver: ContentResolver): Boolean {
    var value by remember(resolver) { mutableStateOf(removeAnimations(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = removeAnimations(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return value
}
