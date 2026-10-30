package com.onezee.triptrack.designsystem.strings

import java.util.Locale

// iOS: LanguageManager.swift, Language. Codes also identify the saved in-app choice.
enum class AppLanguage(val code: String, region: String) {
    en("en", "US"), ru("ru", "RU"), de("de", "DE"), es("es", "ES"), fr("fr", "FR"),
    it("it", "IT"), pl("pl", "PL"), id("id", "ID"), tr("tr", "TR"), fil("fil", "PH"),
    uk("uk", "UA"), kk("kk", "KZ"), pt("pt", "BR");

    val locale: Locale = Locale.forLanguageTag("$code-$region")

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
