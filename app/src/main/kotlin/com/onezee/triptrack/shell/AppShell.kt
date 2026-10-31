package com.onezee.triptrack.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.onezee.triptrack.AppGraph
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.navigation.CustomTabBar
import com.onezee.triptrack.designsystem.navigation.LocalTabBarVisibility
import com.onezee.triptrack.designsystem.navigation.TabBarVisibility
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.feature.account.AccountEntry
import com.onezee.triptrack.designsystem.theme.LocalDistanceUnit
import com.onezee.triptrack.feature.FeedTab
import com.onezee.triptrack.feature.MeTab
import com.onezee.triptrack.ui.LocalTripPhotoImages

/**
 * iOS: ContentView.swift. The five tabs and the floating bar over them, on the theme ground that
 * runs under every system bar.
 *
 * A tab switch cross-fades the screens on the same spring the bar uses, the way SwiftUI swaps the
 * `switch` inside `withAnimation(.snappy)`. Unlike iOS, each tab keeps its saveable state (scroll
 * position, open sheet flags) while another tab is shown: that is what Android's bottom navigation
 * does, and nothing on iOS depends on the reset.
 *
 * The bar is hidden on the record tab, which has its own way back, and whenever a screen asks
 * with `HideAppTabBar()`. Back on any other tab returns to Лента before it leaves the app, as
 * Android's navigation principles ask of a start destination.
 */
@Composable
fun AppShell(graph: AppGraph, resumeTick: Int = 0) {
    val tabs = graph.tabStore
    val visibility = remember { TabBarVisibility() }
    val saveable = rememberSaveableStateHolder()
    var feedScrollToTop by remember { mutableIntStateOf(0) }
    val selected = tabs.selected

    BackHandler(enabled = selected != AppTab.home) { tabs.select(AppTab.home) }

    val trips = graph.tripsFeature
    val drafts by trips.drafts.state.collectAsState()
    // The unit and the network are read again whenever the app comes back to the screen.
    val unit = remember(resumeTick) { trips.distanceUnit() }
    val offline = remember(resumeTick) { trips.offline() }

    CompositionLocalProvider(
        LocalTabBarVisibility provides visibility,
        LocalTripPhotoImages provides trips.photos,
        LocalDistanceUnit provides unit,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppTheme.colors.bg)
                .testTag("app_shell"),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { fadeIn(TabSwitch) togetherWith fadeOut(TabSwitch) using null },
                label = "tab",
            ) { tab ->
                saveable.SaveableStateProvider(tab.rawValue) {
                    TabScreen(tab, graph, feedScrollToTop, resumeTick, offline, onSelect = tabs::select)
                }
            }
            AnimatedVisibility(
                visible = selected != AppTab.record && !visibility.isHiddenByScreen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(250, easing = EaseInOut)) { it } + fadeIn(tween(250, easing = EaseInOut)),
                exit = slideOutVertically(tween(250, easing = EaseInOut)) { it } + fadeOut(tween(250, easing = EaseInOut)),
            ) {
                CustomTabBar(
                    selectedTab = selected,
                    onSelect = tabs::select,
                    hasUnseenDrafts = drafts.hasUnseen,
                    // A second tap on the open Лента takes it back to the top.
                    onReselect = { if (it == AppTab.home) feedScrollToTop++ },
                )
            }
        }
    }
}

// SwiftUI .snappy(duration: 0.22), the animation of every tab switch.
private val TabSwitch = spring<Float>(dampingRatio = 0.85f, stiffness = 815.7f)

@Composable
private fun TabScreen(tab: AppTab, graph: AppGraph, feedScrollToTop: Int, resumeTick: Int, offline: Boolean, onSelect: (AppTab) -> Unit) {
    when (tab) {
        AppTab.home -> FeedTab(graph.tripsFeature, feedScrollToTop, offline, onOpenRecord = { onSelect(AppTab.record) }, onOpenProfileTab = { onSelect(AppTab.profile) })
        AppTab.maps -> AtlasPlaceholder()
        AppTab.record -> RecordTab(graph, resumeTick, onClose = { onSelect(AppTab.home) })
        AppTab.places -> PlacesPlaceholder()
        AppTab.profile -> MeTab(graph.tripsFeature) { below -> MePlaceholder(graph.themeManager, graph.languageManager, below, account = { AccountEntry(graph) }) }
    }
}
