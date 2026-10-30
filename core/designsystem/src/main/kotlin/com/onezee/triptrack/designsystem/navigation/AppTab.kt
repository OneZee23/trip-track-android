package com.onezee.triptrack.designsystem.navigation

import com.onezee.triptrack.designsystem.strings.AppStrings

// iOS: AppTab.swift. The five top-level tabs. The raw values are stored under [storageKey] and
// must keep their spelling. 0.6.8 replaced «groups» with «places»: an unknown stored value, that
// «groups» among them, opens Лента, which needs no migration.
enum class AppTab(val rawValue: String) {
    home("home"),
    maps("maps"),
    record("record"),
    places("places"),
    profile("profile");

    /** The label under the tab's glyph; the record disk has none and uses it only for TalkBack. */
    fun label(strings: AppStrings): String = when (this) {
        home -> strings.feed()
        maps -> strings.tabMap()
        record -> strings.recordTripCta()
        places -> strings.tabPlaces()
        profile -> strings.tabMe()
    }

    companion object {
        /** iOS `@AppStorage(AppTab.storageKey)`. iOS also migrates an older integer key; Android never had it. */
        const val storageKey = "selectedTabV2"

        fun fromRawValue(raw: String?): AppTab? = entries.find { it.rawValue == raw }

        /** What a stored value opens: the tab it names, otherwise Лента. */
        fun stored(raw: String?): AppTab = fromRawValue(raw) ?: home
    }
}
