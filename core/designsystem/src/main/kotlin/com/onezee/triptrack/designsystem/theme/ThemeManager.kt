package com.onezee.triptrack.designsystem.theme

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// iOS: ThemeManager.swift, Mode. The raw values are the stored values and also what the settings
// payload carries, so they keep their spelling.
enum class ThemeMode(val rawValue: String) {
    dark("dark"),
    light("light"),
    system("system");

    /** iOS `preferredColorScheme`: `system` hands the choice back to the phone. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        dark -> true
        light -> false
        system -> systemIsDark
    }

    companion object {
        fun fromRawValue(raw: String?): ThemeMode? = entries.find { it.rawValue == raw }
    }
}

/**
 * iOS: ThemeManager.swift. The chosen theme, saved on the device under the iOS key. Unlike iOS it
 * paints nothing itself: the activity follows [mode] (TripTrackTheme, system bars, window
 * background), and the palette cross-fades there.
 */
class ThemeManager(private val preferences: SharedPreferences) {
    var mode: ThemeMode by mutableStateOf(ThemeMode.fromRawValue(preferences.getString(STORAGE_KEY, null)) ?: ThemeMode.system)
        private set

    fun select(mode: ThemeMode) {
        if (mode == this.mode) return
        preferences.edit().putString(STORAGE_KEY, mode.rawValue).apply()
        this.mode = mode
    }

    companion object {
        const val STORAGE_KEY = "appThemeMode"
    }
}
