package com.onezee.triptrack.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.onezee.triptrack.BuildConfig

/**
 * The version the settings rows print. Screenshot tests fix it, so a release bump in
 * version.properties does not repaint their baselines.
 */
val LocalAppVersion = staticCompositionLocalOf { BuildConfig.VERSION_NAME }
