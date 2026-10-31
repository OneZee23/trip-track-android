// iOS: RegionDisplayTests.swift. Raw geocoder strings remain unchanged in storage.
package com.onezee.triptrack.feature.me

import org.junit.Test
import kotlin.test.*

class ProfileRegionDisplayTests {
    @Test fun englishStoredRendersRussianShort() {
        assertEquals("Краснодар. край", ProfileRegionDisplay.localized("Krasnodar Krai", "ru"))
        assertEquals("Моск. обл.", ProfileRegionDisplay.localized("Moscow Oblast", "ru"))
        assertEquals("Карелия", ProfileRegionDisplay.localized("Republic of Karelia", "ru"))
    }
    @Test fun englishStoredStaysEnglish() { assertEquals("Krasnodar Krai", ProfileRegionDisplay.localized("Krasnodar Krai", "en")) }
    @Test fun russianStoredRendersEnglish() {
        assertEquals("Krasnodar Krai", ProfileRegionDisplay.localized("Краснодарский край", "en"))
        assertEquals("Moscow Oblast", ProfileRegionDisplay.localized("Московская область", "en"))
    }
    @Test fun russianShortFormRecognized() { assertEquals("Моск. обл.", ProfileRegionDisplay.localized("Моск. обл.", "ru")) }
    @Test fun federalCities() {
        assertEquals("Санкт-Петербург", ProfileRegionDisplay.localized("Saint Petersburg", "ru"))
        assertEquals("Moscow", ProfileRegionDisplay.localized("Москва", "en"))
    }
    @Test fun caseAndWhitespaceInsensitive() { assertEquals("Краснодар. край", ProfileRegionDisplay.localized("  krasnodar krai ", "ru")) }
    @Test fun unknownRegionPassesThrough() {
        assertEquals("Île-de-France", ProfileRegionDisplay.localized("Île-de-France", "ru"))
        assertEquals("Bavaria", ProfileRegionDisplay.localized("Bavaria", "en"))
    }
    @Test fun canonicalJoinsBothLanguages() {
        listOf("Краснодарский край", "Krasnodar Krai", "  Краснодар. край ").forEach { assertEquals("Krasnodar Krai", ProfileRegionDisplay.canonical(it)) }
        assertEquals("Bavaria", ProfileRegionDisplay.canonical(" Bavaria "))
    }
    @Test fun nilAndEmptyPassThrough() {
        assertNull(ProfileRegionDisplay.localized(null, "ru")); assertEquals("", ProfileRegionDisplay.localized("", "ru"))
    }
}
