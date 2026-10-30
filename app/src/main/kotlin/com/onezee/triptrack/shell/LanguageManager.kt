package com.onezee.triptrack.shell

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.onezee.triptrack.designsystem.strings.AppLanguage

/**
 * iOS: LanguageManager.swift. The language of the interface.
 *
 * The choice is Android's own per-app language: from Android 13 the system keeps it and offers it
 * in Settings → App languages (`res/xml/locales_config.xml`), below 13 AppCompat stores it
 * (`autoStoreLocales` in the manifest). So there is no `appLanguage` key of ours; choosing
 * recreates the activity in the new language, as any configuration change does.
 *
 * Until something is chosen, the app follows the phone with the iOS rule: walk the phone's
 * language list and take the first language the app has, else English. iOS also SAVES that first
 * guess (its widget reads the saved key); here it stays unsaved, so «system default» in the
 * system settings keeps meaning what it says (docs/divergences.md).
 */
class LanguageManager {
    /** What the person chose in the app or in system settings; `null` follows the phone. */
    val chosen: AppLanguage?
        get() {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) null else AppLanguage.detect(locales.toLanguageTags().split(','))
        }

    /** The interface language for [configuration], the activity's own. */
    fun current(configuration: Configuration): AppLanguage =
        chosen ?: AppLanguage.detect(configuration.locales.toLanguageTags().split(','))

    fun choose(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.code))
    }
}
