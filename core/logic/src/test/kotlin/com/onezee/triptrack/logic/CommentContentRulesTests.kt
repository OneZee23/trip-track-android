package com.onezee.triptrack.logic

import kotlin.test.*
import org.junit.Test

// iOS: ContentFilterTests.swift (sanitize/profanity cases; .comment limit from ContentFilter.Field)
class CommentContentRulesTests {
    @Test fun testSanitizeStripsZeroWidthSpace() { assertEquals("Иван", CommentContentRules.sanitize("Иван\u200B\u200B")) }
    @Test fun testSanitizeStripsBidiOverride() { assertEquals("Аннахак", CommentContentRules.sanitize("Анна\u202Eхак")) }
    @Test fun testSanitizeStripsBOM() { assertEquals("Anya", CommentContentRules.sanitize("\uFEFFAnya")) }
    @Test fun testSanitizeCollapsesNBSPAndTrims() { assertEquals("Anya Smith", CommentContentRules.sanitize("  Anya\u00A0\u00A0Smith   ")) }
    @Test fun testSanitizeCollapsesInternalWhitespace() { assertEquals("Anya Smith", CommentContentRules.sanitize("Anya    Smith")) }
    @Test fun testSanitizeKeepsEmoji() { assertEquals("Иван 🚗", CommentContentRules.sanitize("Иван 🚗")) }
    @Test fun testValidatePassesEmpty() { assertNull(CommentContentRules.validate("")) }
    @Test fun testValidateRejectsEnglishSlur() { assertEquals(CommentContentRules.Failure.inappropriateLanguage, CommentContentRules.validate("retard")) }
    @Test fun testValidateRejectsRussianSlur() { assertEquals(CommentContentRules.Failure.inappropriateLanguage, CommentContentRules.validate("педик")) }
    @Test fun testContainsObjectionableCaseInsensitive() { assertTrue(CommentContentRules.containsObjectionable("RETARD")); assertTrue(CommentContentRules.containsObjectionable("Педик")) }
    @Test fun testContainsObjectionableNoFalsePositiveOnInnocentText() { assertFalse(CommentContentRules.containsObjectionable("Hello world")); assertFalse(CommentContentRules.containsObjectionable("Поездка на дачу")) }
    @Test fun commentsUseFiveHundredCharactersAndValidateASanitizedCopy() {
        assertNull(CommentContentRules.validate("a".repeat(500)))
        assertEquals(CommentContentRules.Failure.tooLong, CommentContentRules.validate("a".repeat(501)))
        assertNull(CommentContentRules.validate("👨‍👩‍👧‍👦".repeat(100)))
    }
}
