package com.onezee.triptrack.shell

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.onezee.triptrack.designsystem.navigation.AppTab

/**
 * iOS: ContentView.swift, `@AppStorage(AppTab.storageKey) selectedTab`. The open tab survives a
 * restart, so onboarding can hand a new person straight to the record tab. An unknown stored value
 * opens Лента.
 */
class TabStore(private val preferences: SharedPreferences) {
    var selected: AppTab by mutableStateOf(AppTab.stored(preferences.getString(AppTab.storageKey, null)))
        private set

    fun select(tab: AppTab) {
        if (tab == selected) return
        selected = tab
        preferences.edit { putString(AppTab.storageKey, tab.rawValue) }
    }
}
