// iOS: AccountFormatTests.swift, RandomDisplayNameTests.swift, ProfileSyncLatchTests.swift, the SyncState
// branches of SyncStatusMarker.swift, ContentFilter.clampedDisplayName
package com.onezee.triptrack.feature.account

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.feature.settings.PresentationPreferences
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountHelpersTests {
    // MARK: AccountFormat.maskedEmail

    @Test fun testNormalEmail() = assertEquals("a•••@icloud.com", AccountFormat.maskedEmail("alex@icloud.com"))
    @Test fun testSingleCharLocalPart() = assertEquals("a•••@icloud.com", AccountFormat.maskedEmail("a@icloud.com"))
    @Test fun testUnicodeLocalPart() = assertEquals("ю•••@почта.рф", AccountFormat.maskedEmail("юзер@почта.рф"))
    @Test fun testNoAtSignInput() = assertEquals("n•••", AccountFormat.maskedEmail("not-an-email"))
    @Test fun testEmptyInput() = assertEquals("", AccountFormat.maskedEmail(""))
    @Test fun testWhitespaceOnlyInput() = assertEquals("", AccountFormat.maskedEmail("   "))
    @Test fun testAtFirstCharacter() = assertEquals("•••@icloud.com", AccountFormat.maskedEmail("@icloud.com"))
    @Test fun testTrimsSurroundingWhitespace() = assertEquals("a•••@icloud.com", AccountFormat.maskedEmail("  alex@icloud.com  "))

    @Test fun testLongLocalPartKeepsOnlyFirstCharacter() {
        val masked = AccountFormat.maskedEmail("very.long.local.part@example.org")
        assertEquals("v•••@example.org", masked)
        assertFalse(masked.contains("long"))
    }

    @Test fun anEmojiFirstCharacterStaysWhole() = assertEquals("👩‍💻•••@gmail.com", AccountFormat.maskedEmail("👩‍💻dev@gmail.com"))

    // MARK: RandomDisplayName

    @Test fun testRecognizesGeneratedRussianName() = assertTrue(RandomDisplayName.isPlaceholder("Дерзкий Гонщик 472"))
    @Test fun testRecognizesGeneratedEnglishName() = assertTrue(RandomDisplayName.isPlaceholder("Swift Driver 42"))

    @Test fun testRecognizesAllGeneratedShape() {
        repeat(20) {
            val ru = RandomDisplayName.generate(AppLanguage.ru)
            assertTrue(RandomDisplayName.isPlaceholder(ru), "Failed to recognize generated name: $ru")
            val en = RandomDisplayName.generate(AppLanguage.en)
            assertTrue(RandomDisplayName.isPlaceholder(en), "Failed to recognize generated name: $en")
        }
    }

    @Test fun testRealNameIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder("Иван Петров"))
    @Test fun testNilIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder(null))
    @Test fun testEmptyStringIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder(""))
    @Test fun testNonNumericThirdTokenIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder("Дерзкий Гонщик abc"))
    @Test fun testTwoTokenNameIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder("Дерзкий Гонщик"))
    @Test fun testFourTokenNameIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder("Дерзкий Гонщик 472 extra"))
    @Test fun testUnknownAdjectiveIsNotPlaceholder() = assertFalse(RandomDisplayName.isPlaceholder("Случайный Гонщик 472"))

    @Test fun otherLanguagesGetTheEnglishPool() {
        val name = RandomDisplayName.generate(AppLanguage.de)
        assertTrue(name.split(' ').first() in setOf("Swift", "Bold", "Nimble", "Daring", "Sly", "Sharp", "Wise", "Quick", "Quiet", "Wild",
            "Steady", "Bright", "Lucky", "Cool", "Brave"))
        val number = name.split(' ').last().toInt()
        assertTrue(number in 10..9999)
    }

    // MARK: ContentFilter.sanitize / clampedDisplayName

    @Test fun invisibleCharactersAndWhitespaceRunsGo() {
        assertEquals("Anna Maria", DisplayNames.sanitize("​Anna   Maria‮ "))
    }

    @Test fun clampCountsGraphemesAfterSanitizing() {
        val long = "A".repeat(40)
        assertEquals(30, DisplayNames.clamped(long).length)
        // Padding does not spend the budget, and a flag is never split.
        assertEquals("Ann", DisplayNames.clamped("​​  Ann  "))
        val flags = "🇷🇺".repeat(31)
        assertEquals("🇷🇺".repeat(30), DisplayNames.clamped(flags))
    }

    // MARK: ProfileSyncLatch

    private class Prefs : PresentationPreferences {
        val values = mutableMapOf<String, String>()
        override fun string(key: String) = values[key]
        override fun set(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }

    @Test fun testPushesWhenTheNameIsLocalButTheServerNeverConfirmedIt() {
        val latch = ProfileSyncLatch(Prefs())
        latch.markUnconfirmed()
        assertTrue(latch.needsPush(isSignedIn = true, localName = "Дерзкий Гонщик 472"))
    }

    @Test fun testTreatsAnAbsentRecordAsUnconfirmed() = assertTrue(ProfileSyncLatch(Prefs()).needsPush(true, "Клаус"))

    @Test fun testDoesNotPushOnceTheServerConfirmed() {
        val latch = ProfileSyncLatch(Prefs())
        latch.markConfirmed()
        assertFalse(latch.needsPush(true, "Клаус"))
    }

    @Test fun testDoesNotPushWhenSignedOut() = assertFalse(ProfileSyncLatch(Prefs()).needsPush(false, null))

    @Test fun testPushesWhenThereIsNoNameAtAll() {
        val latch = ProfileSyncLatch(Prefs())
        latch.markConfirmed()
        assertTrue(latch.needsPush(true, null))
        assertTrue(latch.needsPush(true, "   "))
    }

    @Test fun testAFailedPushAfterAGoodOneReopensTheLatch() {
        val latch = ProfileSyncLatch(Prefs())
        latch.markConfirmed(); latch.markUnconfirmed()
        assertTrue(latch.needsPush(true, "Клаус"))
    }

    @Test fun testResetClearsTheRecord() {
        val prefs = Prefs()
        val latch = ProfileSyncLatch(prefs)
        latch.markConfirmed(); latch.reset()
        assertTrue(latch.needsPush(true, "Клаус"))
        assertFalse(ProfileSyncLatch.KEY in prefs.values)
    }

    // MARK: SyncState

    @Test fun offWithAQueueIsStillPublishing() {
        assertEquals(SyncState.Off, SyncState.current(false, false, 0, 0, 0))
        assertEquals(SyncState.OffPublishing(2), SyncState.current(false, true, 2, 1, 2))
    }

    @Test fun onShowsTheBatchThenTheQueueThenDone() {
        assertEquals(SyncState.Syncing(3, 10), SyncState.current(true, true, 7, 3, 10))
        assertEquals(SyncState.Queued(4), SyncState.current(true, false, 4, 0, 0))
        assertEquals(SyncState.Done, SyncState.current(true, false, 0, 0, 0))
    }
}
