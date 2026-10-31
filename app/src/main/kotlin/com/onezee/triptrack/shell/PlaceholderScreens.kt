package com.onezee.triptrack.shell

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.onezee.triptrack.designsystem.components.AppGlyph
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.components.Glyph
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.navigation.tabBarContentClearance
import com.onezee.triptrack.designsystem.theme.AppSpacing
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AppType
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.rememberHaptics

// Remaining tab placeholders share the shell typography and tab-bar clearance.

/**
 * The record tab hides the tab bar, so it brings its own way back: iOS `TrackingView.backButton`,
 * a dark disc over the map that returns to Лента.
 */
@Composable
internal fun RecordPlaceholder(onClose: () -> Unit) {
    val strings = appStrings
    Box(Modifier.fillMaxSize().testTag("screen_record")) {
        TrackingBackButton(onClose)
        Text(
            strings.recordTripCta(),
            style = AppType.sheetTitle,
            color = AppTheme.colors.text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = AppSpacing.screenSide),
        )
    }
}

/**
 * The record tab with the recording screen from core:location. The full designed screen replaces
 * it later; until then it keeps the same way back as the placeholder.
 */
@Composable
internal fun RecordTab(graph: com.onezee.triptrack.AppGraph, resumeTick: Int, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("screen_record")) {
        com.onezee.triptrack.recording.RecordingRoute(graph.location, graph.strings, resumeTick)
        TrackingBackButton(onClose)
    }
}

/** iOS `TrackingView.backButton`: a dark disc over the map that returns to Лента. */
@Composable
private fun TrackingBackButton(onClose: () -> Unit) {
    val strings = appStrings
    val interaction = remember { MutableInteractionSource() }
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
