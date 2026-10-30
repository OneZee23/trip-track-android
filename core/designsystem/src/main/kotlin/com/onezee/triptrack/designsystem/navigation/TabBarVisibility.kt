package com.onezee.triptrack.designsystem.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * iOS: ContentView.swift, HideTabBarPreferenceKey. Which screens currently ask the shell to hide
 * the tab bar (trip detail wants the bigger canvas, a dialog on a tab root must not leave the bar
 * tappable over its scrim). Any one request hides it, as the iOS preference ORs them.
 */
@Stable
class TabBarVisibility {
    private val requests = mutableStateListOf<Any>()

    val isHiddenByScreen: Boolean get() = requests.isNotEmpty()

    internal fun add(token: Any) {
        requests += token
    }

    internal fun remove(token: Any) {
        requests -= token
    }
}

val LocalTabBarVisibility = staticCompositionLocalOf { TabBarVisibility() }

/** iOS `.hideAppTabBar(_:)`: while this is composed with [hide] true, the shell hides the tab bar. */
@Composable
fun HideAppTabBar(hide: Boolean = true) {
    val visibility = LocalTabBarVisibility.current
    DisposableEffect(visibility, hide) {
        val token = Any()
        if (hide) visibility.add(token)
        onDispose { if (hide) visibility.remove(token) }
    }
}
