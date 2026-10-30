package com.onezee.triptrack

import android.content.Context
import com.onezee.triptrack.designsystem.theme.ThemeManager
import com.onezee.triptrack.shell.LanguageManager
import com.onezee.triptrack.shell.TabStore

/** The app's singletons, built once in [TripTrackApplication]. */
class AppGraph(context: Context) {
    // The iOS UserDefaults.standard: the app's default preferences file.
    private val preferences = context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

    val themeManager = ThemeManager(preferences)
    val languageManager = LanguageManager()
    val tabStore = TabStore(preferences)
}
