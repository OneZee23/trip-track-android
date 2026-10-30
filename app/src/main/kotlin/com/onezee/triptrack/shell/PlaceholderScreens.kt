package com.onezee.triptrack.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.SettingsOptionPicker
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.surfaceCard
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.theme.AppSpacing
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.ThemeManager
import com.onezee.triptrack.designsystem.theme.ThemeMode
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.interScaled
import com.onezee.triptrack.designsystem.theme.rememberHaptics

// Stand-ins for the five tab roots until their screens are ported (waves 2 and 3). Each carries its
// tab's title in the screen-title role and leaves the tab bar's clearance at the bottom; Я already
// holds the two settings that belong to the shell, theme and language.

@Composable
internal fun FeedPlaceholder(scrollToTop: Int) {
    val scroll = rememberScrollState()
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) scroll.animateScrollTo(0) }
    PlaceholderScreen(appStrings.feed(), scroll, "screen_feed")
}

@Composable
internal fun AtlasPlaceholder() = PlaceholderScreen(appStrings.tabMap(), rememberScrollState(), "screen_atlas")

@Composable
internal fun PlacesPlaceholder() = PlaceholderScreen(appStrings.tabPlaces(), rememberScrollState(), "screen_places")

/**
 * The record tab hides the tab bar, so it brings its own way back: iOS `TrackingView.backButton`,
 * a dark disc over the map that returns to Лента.
 */
@Composable
internal fun RecordPlaceholder(onClose: () -> Unit) {
    val strings = appStrings
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().testTag("screen_record")) {
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = AppSpacing.screenSide, top = 8.dp)
                .size(44.dp)
                .plainPressFeedback(interaction)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClose)
                .semantics { contentDescription = strings.back() }
                .testTag("tracking_back"),
            contentAlignment = Alignment.Center,
        ) {
            // iOS: an 18 pt semibold chevron.left in white.
            Glyph(AppGlyph.chevronLeft, contentDescription = null, tint = Color.White, size = 22.dp)
        }
        Text(
            strings.recordTripCta(),
            style = AppType.sheetTitle,
            color = AppTheme.colors.text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = AppSpacing.screenSide),
        )
    }
}

@Composable
internal fun MePlaceholder(themeManager: ThemeManager, languageManager: LanguageManager) {
    val strings = appStrings
    val language = languageManager.current(LocalConfiguration.current)
    var picking by rememberSaveable { mutableStateOf<Picker?>(null) }
    PlaceholderScreen(strings.tabMe(), rememberScrollState(), "screen_me") {
        // iOS: ProfileSettingsSheet.swift, appGroup: words, then look.
        Column(Modifier.fillMaxWidth().surfaceCard()) {
            SettingsRow(AppGlyph.characterBubble, strings.lang(), language.endonym, "settings_language") { picking = Picker.language }
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.colors.borderBright))
            // The icon carries the current mode, the glyph the picker badges it with: a fixed sun
            // next to «Тёмная» reads as a stale row.
            SettingsRow(themeGlyph(themeManager.mode), strings.theme(), themeLabel(themeManager.mode, strings), "settings_theme") { picking = Picker.theme }
        }
    }
    when (picking) {
        Picker.theme -> SettingsOptionPicker(
            title = strings.theme(),
            options = listOf(ThemeMode.system, ThemeMode.light, ThemeMode.dark),
            selection = themeManager.mode,
            footnote = strings.themePickerFootnote(),
            badgeGlyph = { themeGlyph(it) },
            label = { themeLabel(it, strings) },
            onSelect = themeManager::select,
            onDismissRequest = { picking = null },
            testTagPrefix = "settings_theme",
        )
        // Endonyms, as on iOS: every language names itself, in itself.
        Picker.language -> SettingsOptionPicker(
            title = strings.lang(),
            options = AppLanguage.displayOrder,
            selection = language,
            footnote = strings.languagePickerFootnote(),
            badge = { it.badge },
            label = { it.endonym },
            // Close first: the choice recreates the activity, and a picker still open in the saved
            // state would come back in the new language.
            onSelect = {
                picking = null
                languageManager.choose(it)
            },
            onDismissRequest = { picking = null },
            testTagPrefix = "settings_language",
        )
        null -> Unit
    }
}

private enum class Picker { theme, language }

// iOS: ProfileSettingsSheet.swift, themeLabel / themeBadge.
private fun themeLabel(mode: ThemeMode, strings: AppStrings): String = when (mode) {
    ThemeMode.system -> strings.themeSystem()
    ThemeMode.dark -> strings.dark()
    ThemeMode.light -> strings.light()
}

private fun themeGlyph(mode: ThemeMode): AppGlyph = when (mode) {
    ThemeMode.system -> AppGlyph.gearshapeFill
    ThemeMode.dark -> AppGlyph.moonFill
    ThemeMode.light -> AppGlyph.sunMaxFill
}

@Composable
private fun PlaceholderScreen(
    title: String,
    scroll: ScrollState,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = AppSpacing.screenSide)
            .padding(top = 12.dp, bottom = tabBarContentClearance())
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.listStep),
    ) {
        Text(
            title,
            style = AppType.title,
            letterSpacing = AppType.titleTracking,
            color = AppTheme.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** iOS: ProfileComponents.swift, SettingsIconRow with SettingsRowValue, as on the Я settings sheet. */
@Composable
private fun SettingsRow(glyph: AppGlyph, title: String, value: String, testTag: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap()
                onClick()
            }
            .plainPressFeedback(interaction)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).background(AppTheme.accentBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            // iOS: a 16 pt symbol, decoration: the title beside it says what the row is.
            Glyph(glyph, contentDescription = null, tint = AppTheme.accent, size = 19.dp)
        }
        Text(title, style = interScaled(14.5f, InterWeight.semibold), color = colors.text, modifier = Modifier.weight(1f))
        Text(value, style = interScaled(14f, InterWeight.medium), color = colors.textSecondary, maxLines = 1)
    }
}
