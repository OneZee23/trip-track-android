// iOS: ContentFilterTests.swift, NameEditorSheet.swift, AboutEditorSheet.swift, PublicLevelsFlowTests.swift.
package com.onezee.triptrack.feature.me

import android.app.Application
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.feature.account.DisplayNames
import com.onezee.triptrack.model.DriverRank
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileAuxiliaryTests {
    private val strings get() = AppStrings(RuntimeEnvironment.getApplication(), AppLanguage.ru)
    @Test fun contentFilterKeepsTheIphoneSanitizerFixtures() {
        mapOf("Иван\u200B\u200B" to "Иван", "Анна\u202Eхак" to "Аннахак", "\uFEFFAnya" to "Anya",
            "  Anya\u00A0\u00A0Smith   " to "Anya Smith", "Anya    Smith" to "Anya Smith", "Иван 🚗" to "Иван 🚗")
            .forEach { (raw, clean) -> assertEquals(clean, DisplayNames.sanitize(raw)) }
    }
    @Test fun contentFilterAcceptsTheSameNames() {
        listOf("Иван", "Anya Smith", "Иван-Smith", "").forEach { assertNull(ProfileTextRules.error(it, ProfileTextField.displayName, strings), it) }
    }
    @Test fun contentFilterRejectsTheSameNames() {
        listOf("Тwitter", "aaaaa", "a....!", "123", "ab".repeat(16), "retard", "педик").forEach {
            assertNotNull(ProfileTextRules.error(it, ProfileTextField.displayName, strings), it)
        }
    }
    @Test fun aBioAllowsOrdinaryMixedScriptAndCanBeCleared() {
        assertNull(ProfileTextRules.error("Люблю BMW", ProfileTextField.bio, strings))
        assertTrue(ProfileTextRules.canSave("", "Дорога", ProfileTextField.bio, null))
        assertFalse(ProfileTextRules.canSave("", "Дорога", ProfileTextField.displayName, null))
        assertFalse(ProfileTextRules.canSave("Дорога ", "Дорога", ProfileTextField.bio, null))
    }
    @Test fun editorCapsCountExtendedGraphemes() {
        val flag = "🇷🇺"
        assertEquals(flag.repeat(30), ProfileTextRules.cap(flag.repeat(31), ProfileTextField.displayName))
        assertEquals(140, ProfileTextRules.count(ProfileTextRules.cap("👨‍👩‍👧‍👦".repeat(141), ProfileTextField.bio)))
    }
    @Test fun levelsUseTheOwnersXpAndClampStoredLevels() {
        assertEquals(1, ProfileLevels.level(0))
        assertEquals(110, ProfileLevels.level(111))
        assertEquals(26400f / 28500, ProfileLevels.progress(21, 26400))
        assertEquals(1f, ProfileLevels.progress(110, 0))
        assertEquals(1..4, ProfileLevels.range(DriverRank.novice))
        assertEquals(100..110, ProfileLevels.range(DriverRank.eternal))
    }
}
