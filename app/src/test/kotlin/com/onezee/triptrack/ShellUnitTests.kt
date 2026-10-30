package com.onezee.triptrack

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.onezee.triptrack.designsystem.navigation.AppTab
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.shell.LanguageManager
import com.onezee.triptrack.shell.TabStore
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

// iOS: AppTab.swift (AppTabTests.swift), LanguageManager.swift.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellUnitTests {
    private val preferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("shell-tests", Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    @After fun forgetTheLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test fun aFreshInstallOpensTheFeedAndAPickIsStored() {
        assertEquals(AppTab.home, TabStore(preferences).selected)
        TabStore(preferences).select(AppTab.record)
        assertEquals("record", preferences.getString("selectedTabV2", null))
        assertEquals(AppTab.record, TabStore(preferences).selected)
    }

    /** iOS AppTabTests.testStoredGroupsFallsBackToHome: «groups» from before 0.6.8 opens Лента. */
    @Test fun storedGroupsOpensTheFeed() {
        preferences.edit().putString("selectedTabV2", "groups").commit()
        assertEquals(AppTab.home, TabStore(preferences).selected)
    }

    private fun configuration(vararg tags: String) = Configuration().apply {
        setLocales(LocaleList(*tags.map { Locale.forLanguageTag(it) }.toTypedArray()))
    }

    /** iOS `Language.detect`: walk the phone's list, take the first language the app has, else English. */
    @Test fun withoutAChoiceThePhonesListDecides() {
        val languages = LanguageManager()
        assertNull(languages.chosen)
        assertEquals(AppLanguage.de, languages.current(configuration("zh-Hans-CN", "de-DE", "ru-RU")))
        assertEquals(AppLanguage.pt, languages.current(configuration("pt-PT")))
        assertEquals(AppLanguage.id, languages.current(configuration("in-ID")))
        assertEquals(AppLanguage.fil, languages.current(configuration("fil-PH")))
        assertEquals(AppLanguage.en, languages.current(configuration("zh-CN", "ja-JP")))
    }

    /** AppCompat answers only while one of its activities is alive, as in the app. */
    @Test fun aChoiceBeatsThePhone() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
        val languages = LanguageManager()
        languages.choose(AppLanguage.kk)
        assertEquals(AppLanguage.kk, languages.chosen)
        assertEquals(AppLanguage.kk, languages.current(configuration("de-DE")))
        assertEquals("kk", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        activity.close()
    }
}
