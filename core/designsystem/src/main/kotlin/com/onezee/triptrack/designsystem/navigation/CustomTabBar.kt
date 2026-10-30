package com.onezee.triptrack.designsystem.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.onezee.triptrack.designsystem.components.Text
import com.onezee.triptrack.designsystem.components.plainPressFeedback
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.AtlasTheme
import com.onezee.triptrack.designsystem.theme.CapsuleShape
import com.onezee.triptrack.designsystem.theme.InterWeight
import com.onezee.triptrack.designsystem.theme.appStrings
import com.onezee.triptrack.designsystem.theme.continuousRoundedRect
import com.onezee.triptrack.designsystem.theme.inter
import com.onezee.triptrack.designsystem.theme.iosShadow
import com.onezee.triptrack.designsystem.theme.points
import com.onezee.triptrack.designsystem.theme.rememberHaptics

// iOS: CustomTabBar.swift, the geometry. One bar for all five tabs since 0.8.1, when the owner saw
// the Atlas capsule next to the older glass pill on the other four.
object TabBarMetrics {
    /** The capsule, without the raised record disk. */
    val pillHeight = 68.dp

    /** Side margin of the capsule from the screen edge. */
    val sideMargin = 16.dp

    /**
     * Gap under the capsule. iOS measures it from the physical bottom on every phone; Android
     * measures it from the top of a TAPPABLE system bar, so the capsule clears the three-button
     * navigation bar, and sits where iOS puts it on gesture navigation, whose handle takes no taps.
     */
    val bottomGap = 22.dp

    /** How far the record disk rises above the capsule. */
    val recordRise = 12.dp

    /**
     * Room a screen leaves at the bottom under the bar: gap + capsule + raised disk + 8 of air, read
     * from here by every tab (it used to be a literal 96 in each screen). Add the tappable inset.
     */
    val clearance: Dp = bottomGap + pillHeight + recordRise + 8.dp

    /** The tab cell inside the capsule. */
    internal val cellHeight = 56.dp

    /**
     * The same room for content that already stops at the navigation bar inset (scrolling content
     * inside insets): the capsule stands on the physical bottom, so the inset is taken back off.
     */
    fun clearanceAboveSafeArea(bottomInset: Dp, tappableInset: Dp = 0.dp): Dp =
        max(0.dp, tappableInset + clearance - bottomInset)
}

/** How far the capsule's gap starts from the bottom edge on this phone: the tappable bar's height. */
@Composable
fun tabBarTappableInset(): Dp = WindowInsets.tappableElement.asPaddingValues().calculateBottomPadding()

/** Bottom room for a screen drawn edge to edge under the bar. */
@Composable
fun tabBarContentClearance(): Dp = tabBarTappableInset() + TabBarMetrics.clearance

/** Bottom room for content that already stops above the navigation bar. */
@Composable
fun tabBarClearanceAboveSafeArea(): Dp = TabBarMetrics.clearanceAboveSafeArea(
    bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    tappableInset = tabBarTappableInset(),
)

// SwiftUI .snappy(duration: 0.22): a spring with response 0.22 s and bounce 0.15.
private val Snappy: AnimationSpec<Color> = spring(dampingRatio = 0.85f, stiffness = 815.7f)

/**
 * iOS: CustomTabBar.swift. The floating bar: Лента / Атлас / Запись / Места / Я. A paper capsule of
 * 68 with the record disk raised by 12, terracotta for the active tab. Labels are 10, not the
 * mock's 11 (the owner: «шрифт такой здоровый не нужно»), and the record disk has no label at all:
 * recording is an action, not a tab.
 *
 * Tapping the open tab again reports [onReselect]; the shell scrolls Лента to the top on it, the
 * one behaviour of the old bar that had to survive. [hasUnseenDrafts] draws the dot on Я and says
 * it to TalkBack too.
 */
@Composable
fun CustomTabBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    hasUnseenDrafts: Boolean = false,
    onReselect: (AppTab) -> Unit = {},
) {
    val atlas = AtlasTheme.colors
    val dark = AppTheme.isDark
    val strings = appStrings
    val haptics = rememberHaptics()
    fun select(tab: AppTab) {
        if (tab == selectedTab) onReselect(tab)
        onSelect(tab)
        haptics.tap()
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TabBarMetrics.sideMargin)
            .padding(bottom = TabBarMetrics.bottomGap + tabBarTappableInset())
            .testTag("tab_bar"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(TabBarMetrics.pillHeight)
                .iosShadow(CapsuleShape, Color.Black.copy(alpha = if (dark) 0.28f else 0.16f), radius = 14.dp, y = 8.dp)
                .background(atlas.navSurface.copy(alpha = atlas.navSurface.alpha * 0.94f), CapsuleShape)
                .border(1.dp, if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f), CapsuleShape)
                .padding(horizontal = 6.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabCell(AppTab.home, AppTab.home.label(strings), selectedTab == AppTab.home, null) { select(AppTab.home) }
            TabCell(AppTab.maps, AppTab.maps.label(strings), selectedTab == AppTab.maps, null) { select(AppTab.maps) }
            RecordDisk(AppTab.record.label(strings)) { select(AppTab.record) }
            TabCell(AppTab.places, AppTab.places.label(strings), selectedTab == AppTab.places, null) { select(AppTab.places) }
            TabCell(
                AppTab.profile, AppTab.profile.label(strings), selectedTab == AppTab.profile,
                unseenHint = if (hasUnseenDrafts) strings.draftsTabHint() else null,
            ) { select(AppTab.profile) }
        }
    }
}

@Composable
private fun RowScope.TabCell(tab: AppTab, label: String, isActive: Boolean, unseenHint: String?, onClick: () -> Unit) {
    val atlas = AtlasTheme.colors
    val ink by animateColorAsState(if (isActive) atlas.accent else atlas.navInactive, Snappy, label = "tabInk")
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .height(TabBarMetrics.cellHeight)
            .selectable(selected = isActive, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .plainPressFeedback(interaction)
            .testTag("tab_${tab.rawValue}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Box {
            TabGlyph(tab, ink, 24.dp)
            if (unseenHint != null) UnseenDot(unseenHint, Modifier.align(Alignment.TopEnd))
        }
        Text(
            label,
            style = inter(10f, InterWeight.semibold),
            color = ink,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = points(8f), maxFontSize = points(10f), stepSize = points(0.25f)),
        )
    }
}

/**
 * Eight with a ring of the capsule's colour two wide, so the dot reads even where it lands on the
 * glyph's own line; pushed half out of the glyph's corner, since an outline glyph's corner is
 * empty and a dot inside the frame would read as part of the drawing. What the eye sees is said
 * to TalkBack too.
 */
@Composable
private fun UnseenDot(hint: String, modifier: Modifier) {
    val atlas = AtlasTheme.colors
    Box(
        modifier
            .offset(x = 4.dp, y = (-2).dp)
            .size(12.dp)
            .semantics { contentDescription = hint }
            .testTag("tab_profile_unseen"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(atlas.accent, CircleShape)
                .border(2.dp, atlas.navSurface, CircleShape),
        )
    }
}

/** A 60 disk in a 68 ring of the capsule's ground, raised by [TabBarMetrics.recordRise]. */
@Composable
private fun RowScope.RecordDisk(label: String, onClick: () -> Unit) {
    val atlas = AtlasTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = -TabBarMetrics.recordRise)
                .size(68.dp)
                .plainPressFeedback(interaction)
                .iosShadow(CircleShape, atlas.accent.copy(alpha = 0.35f), radius = 8.dp, y = 6.dp)
                .background(atlas.navSurface, CircleShape)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag("tab_record"),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(60.dp).background(atlas.accent, CircleShape), contentAlignment = Alignment.Center) {
                TabGlyph(AppTab.record, Color.White, 28.dp)
            }
        }
    }
}

/** The mock's 24 × 24 outline glyphs, path for path: SF Symbols have another optical weight. */
@Composable
internal fun TabGlyph(tab: AppTab, tint: Color, size: Dp) {
    val path = remember(tab) { tabGlyphPath(tab) }
    val width = if (tab == AppTab.record) 2f else 1.8f
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, tint, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

internal fun tabGlyphPath(tab: AppTab): Path = Path().apply {
    when (tab) {
        AppTab.home -> {
            // SwiftUI Path.addRoundedRect defaults to continuous corners.
            addPath(continuousRoundedRect(Size(16f, 16f), 3.5f), Offset(4f, 4f))
            moveTo(4f, 12f)
            lineTo(20f, 12f)
        }
        AppTab.maps -> {
            moveTo(3f, 6.5f)
            lineTo(9f, 3.5f)
            lineTo(15f, 6.5f)
            lineTo(21f, 3.5f)
            lineTo(21f, 17.5f)
            lineTo(15f, 20.5f)
            lineTo(9f, 17.5f)
            lineTo(3f, 20.5f)
            close()
            moveTo(9f, 3.5f)
            lineTo(9f, 17.5f)
            moveTo(15f, 6.5f)
            lineTo(15f, 20.5f)
        }
        AppTab.record -> {
            addOval(Rect(3.5f, 3.5f, 20.5f, 20.5f))
            addOval(Rect(9.8f, 9.8f, 14.2f, 14.2f))
            moveTo(3.8f, 10.5f)
            cubicTo(6.3f, 9.5f, 9f, 9f, 12f, 9f)
            cubicTo(15f, 9f, 17.7f, 9.5f, 20.2f, 10.5f)
            moveTo(12f, 14.2f)
            lineTo(12f, 20.5f)
        }
        AppTab.places -> {
            addOval(Rect(8.5f, 4f, 15.5f, 11f))
            moveTo(12f, 11f)
            lineTo(12f, 20f)
            moveTo(8f, 20.5f)
            lineTo(16f, 20.5f)
        }
        AppTab.profile -> {
            addOval(Rect(8.2f, 4.2f, 15.8f, 11.8f))
            moveTo(4.5f, 20.5f)
            cubicTo(5.9f, 16.8f, 8.7f, 14.9f, 12f, 14.9f)
            cubicTo(15.3f, 14.9f, 18.1f, 16.8f, 19.5f, 20.5f)
        }
    }
}
