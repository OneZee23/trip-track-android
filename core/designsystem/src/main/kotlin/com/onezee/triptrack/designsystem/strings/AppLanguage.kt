package com.onezee.triptrack.designsystem.strings

import java.util.Locale

// iOS: LanguageManager.swift, Language. Codes also identify the saved in-app choice.
enum class AppLanguage(val code: String, region: String) {
    en("en", "US"), ru("ru", "RU"), de("de", "DE"), es("es", "ES"), fr("fr", "FR"),
    it("it", "IT"), pl("pl", "PL"), id("id", "ID"), tr("tr", "TR"), fil("fil", "PH"),
    uk("uk", "UA"), kk("kk", "KZ"), pt("pt", "BR");

    val locale: Locale = Locale.forLanguageTag("$code-$region")

    /**
     * iOS: LanguageManager.swift, endonym. Every language names itself, in itself: someone who
     * reads only Polish cannot find «Polish». Not copy, so it is not in the string tables.
     */
    val endonym: String
        get() = when (this) {
            en -> "English"
            ru -> "Русский"
            de -> "Deutsch"
            es -> "Español"
            fr -> "Français"
            it -> "Italiano"
            pl -> "Polski"
            id -> "Bahasa Indonesia"
            tr -> "Türkçe"
            fil -> "Filipino"
            uk -> "Українська"
            kk -> "Қазақша"
            pt -> "Português (Brasil)"
        }

    /** iOS: LanguageManager.swift, badge. The two letters on the picker row, in the language's own script. */
    val badge: String
        get() = when (this) {
            en -> "En"
            ru -> "Ру"
            de -> "De"
            es -> "Es"
            fr -> "Fr"
            it -> "It"
            pl -> "Pl"
            id -> "Id"
            tr -> "Tr"
            fil -> "Fi"
            uk -> "Ук"
            kk -> "Қа"
            pt -> "Pt"
        }

    companion object {
        val displayOrder = listOf(ru, en, de, es, fr, it, pl, uk, kk, tr, id, fil, pt)
        fun fromCode(code: String): AppLanguage = entries.find { it.code == code } ?: en
        fun detect(preferred: List<String>): AppLanguage = preferred.firstNotNullOfOrNull { tag ->
            entries.find { it.code == tag.substringBefore('-').lowercase(Locale.ROOT) }
        } ?: en
    }
}

// iOS: AppStringsCore.swift, String case conversion uses the chosen app language.
fun String.uppercased(lang: AppLanguage): String = uppercase(lang.locale)
fun String.lowercased(lang: AppLanguage): String = lowercase(lang.locale)
