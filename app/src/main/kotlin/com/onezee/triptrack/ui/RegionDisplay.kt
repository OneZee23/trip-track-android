package com.onezee.triptrack.ui

import com.onezee.triptrack.designsystem.strings.AppLanguage

/**
 * iOS: RegionDisplay.swift. Region names are geocoded once, in whatever language the geocoder
 * answered, and stored verbatim; the DISPLAY follows the app language (RU «Краснодар. край», every
 * other language the English name) while storage stays raw (decision of 6 August 2026). Covers the
 * Russian federal subjects; anything unrecognised passes through untouched.
 */
object RegionDisplay {
    fun localized(raw: String?, language: AppLanguage): String? {
        if (raw.isNullOrEmpty()) return raw
        val entry = index[normalize(raw)] ?: return raw
        return if (language == AppLanguage.ru) entry.ruShort else entry.en
    }

    /** One key per region whatever language stored it; an unknown region stays itself, trimmed. */
    fun canonical(raw: String): String = index[normalize(raw)]?.en ?: raw.trim()

    private class Entry(val en: String, val ruShort: String)

    private class Subject(val en: String, val aliases: List<String>, val ruFull: String, val ruShort: String)

    // (English geocoder name, extra recognition aliases, RU full form, RU short display form.)
    private val subjects = listOf(
        Subject("Moscow", listOf(), "Москва", "Москва"),
        Subject("Saint Petersburg", listOf("St Petersburg", "St. Petersburg"), "Санкт-Петербург", "Санкт-Петербург"),
        Subject("Sevastopol", listOf(), "Севастополь", "Севастополь"),
        Subject("Amur Oblast", listOf(), "Амурская область", "Амурская обл."),
        Subject("Arkhangelsk Oblast", listOf(), "Архангельская область", "Архангельская обл."),
        Subject("Astrakhan Oblast", listOf(), "Астраханская область", "Астраханская обл."),
        Subject("Belgorod Oblast", listOf(), "Белгородская область", "Белгородская обл."),
        Subject("Bryansk Oblast", listOf(), "Брянская область", "Брянская обл."),
        Subject("Chelyabinsk Oblast", listOf(), "Челябинская область", "Челяб. обл."),
        Subject("Irkutsk Oblast", listOf(), "Иркутская область", "Иркутская обл."),
        Subject("Ivanovo Oblast", listOf(), "Ивановская область", "Ивановская обл."),
        Subject("Kaliningrad Oblast", listOf(), "Калининградская область", "Калинингр. обл."),
        Subject("Kaluga Oblast", listOf(), "Калужская область", "Калужская обл."),
        Subject("Kemerovo Oblast", listOf("Kuzbass"), "Кемеровская область", "Кемеровская обл."),
        Subject("Kirov Oblast", listOf(), "Кировская область", "Кировская обл."),
        Subject("Kostroma Oblast", listOf(), "Костромская область", "Костромская обл."),
        Subject("Kurgan Oblast", listOf(), "Курганская область", "Курганская обл."),
        Subject("Kursk Oblast", listOf(), "Курская область", "Курская обл."),
        Subject("Leningrad Oblast", listOf(), "Ленинградская область", "Ленингр. обл."),
        Subject("Lipetsk Oblast", listOf(), "Липецкая область", "Липецкая обл."),
        Subject("Magadan Oblast", listOf(), "Магаданская область", "Магаданская обл."),
        Subject("Moscow Oblast", listOf("Moskovskaya Oblast"), "Московская область", "Моск. обл."),
        Subject("Murmansk Oblast", listOf(), "Мурманская область", "Мурманская обл."),
        Subject("Nizhny Novgorod Oblast", listOf(), "Нижегородская область", "Нижегор. обл."),
        Subject("Novgorod Oblast", listOf(), "Новгородская область", "Новгородская обл."),
        Subject("Novosibirsk Oblast", listOf(), "Новосибирская область", "Новосиб. обл."),
        Subject("Omsk Oblast", listOf(), "Омская область", "Омская обл."),
        Subject("Orenburg Oblast", listOf(), "Оренбургская область", "Оренбургская обл."),
        Subject("Oryol Oblast", listOf("Orel Oblast"), "Орловская область", "Орловская обл."),
        Subject("Penza Oblast", listOf(), "Пензенская область", "Пензенская обл."),
        Subject("Pskov Oblast", listOf(), "Псковская область", "Псковская обл."),
        Subject("Rostov Oblast", listOf(), "Ростовская область", "Ростовская обл."),
        Subject("Ryazan Oblast", listOf(), "Рязанская область", "Рязанская обл."),
        Subject("Sakhalin Oblast", listOf(), "Сахалинская область", "Сахалинская обл."),
        Subject("Samara Oblast", listOf(), "Самарская область", "Самарская обл."),
        Subject("Saratov Oblast", listOf(), "Саратовская область", "Саратовская обл."),
        Subject("Smolensk Oblast", listOf(), "Смоленская область", "Смоленская обл."),
        Subject("Sverdlovsk Oblast", listOf(), "Свердловская область", "Свердл. обл."),
        Subject("Tambov Oblast", listOf(), "Тамбовская область", "Тамбовская обл."),
        Subject("Tomsk Oblast", listOf(), "Томская область", "Томская обл."),
        Subject("Tula Oblast", listOf(), "Тульская область", "Тульская обл."),
        Subject("Tver Oblast", listOf(), "Тверская область", "Тверская обл."),
        Subject("Tyumen Oblast", listOf(), "Тюменская область", "Тюменская обл."),
        Subject("Ulyanovsk Oblast", listOf(), "Ульяновская область", "Ульяновская обл."),
        Subject("Vladimir Oblast", listOf(), "Владимирская область", "Владимирская обл."),
        Subject("Volgograd Oblast", listOf(), "Волгоградская область", "Волгоградская обл."),
        Subject("Vologda Oblast", listOf(), "Вологодская область", "Вологодская обл."),
        Subject("Voronezh Oblast", listOf(), "Воронежская область", "Воронежская обл."),
        Subject("Yaroslavl Oblast", listOf(), "Ярославская область", "Ярославская обл."),
        Subject("Altai Krai", listOf(), "Алтайский край", "Алтайский край"),
        Subject("Kamchatka Krai", listOf(), "Камчатский край", "Камчатский край"),
        Subject("Khabarovsk Krai", listOf(), "Хабаровский край", "Хабаровский край"),
        Subject("Krasnodar Krai", listOf("Krasnodarskiy Kray"), "Краснодарский край", "Краснодар. край"),
        Subject("Krasnoyarsk Krai", listOf(), "Красноярский край", "Краснояр. край"),
        Subject("Perm Krai", listOf(), "Пермский край", "Пермский край"),
        Subject("Primorsky Krai", listOf("Primorye"), "Приморский край", "Приморский край"),
        Subject("Stavropol Krai", listOf(), "Ставропольский край", "Ставроп. край"),
        Subject("Zabaykalsky Krai", listOf("Transbaikal Krai"), "Забайкальский край", "Забайкальский край"),
        Subject("Adygea", listOf("Republic of Adygea"), "Республика Адыгея", "Адыгея"),
        Subject("Altai Republic", listOf(), "Республика Алтай", "Респ. Алтай"),
        Subject("Bashkortostan", listOf("Republic of Bashkortostan"), "Республика Башкортостан", "Башкортостан"),
        Subject("Buryatia", listOf("Republic of Buryatia"), "Республика Бурятия", "Бурятия"),
        Subject("Chechnya", listOf("Chechen Republic"), "Чеченская Республика", "Чечня"),
        Subject("Chuvashia", listOf("Chuvash Republic"), "Чувашская Республика", "Чувашия"),
        Subject("Crimea", listOf("Republic of Crimea"), "Республика Крым", "Крым"),
        Subject("Dagestan", listOf("Republic of Dagestan"), "Республика Дагестан", "Дагестан"),
        Subject("Ingushetia", listOf("Republic of Ingushetia"), "Республика Ингушетия", "Ингушетия"),
        Subject("Kabardino-Balkaria", listOf("Kabardino-Balkarian Republic"), "Кабардино-Балкарская Республика", "Кабардино-Балкария"),
        Subject("Kalmykia", listOf("Republic of Kalmykia"), "Республика Калмыкия", "Калмыкия"),
        Subject("Karachay-Cherkessia", listOf("Karachay-Cherkess Republic"), "Карачаево-Черкесская Республика", "Карачаево-Черкесия"),
        Subject("Karelia", listOf("Republic of Karelia"), "Республика Карелия", "Карелия"),
        Subject("Khakassia", listOf("Republic of Khakassia"), "Республика Хакасия", "Хакасия"),
        Subject("Komi", listOf("Komi Republic"), "Республика Коми", "Коми"),
        Subject("Mari El", listOf("Mari El Republic"), "Республика Марий Эл", "Марий Эл"),
        Subject("Mordovia", listOf("Republic of Mordovia"), "Республика Мордовия", "Мордовия"),
        Subject("North Ossetia", listOf("North Ossetia-Alania", "Republic of North Ossetia-Alania"), "Республика Северная Осетия — Алания", "Сев. Осетия"),
        Subject("Sakha", listOf("Sakha Republic", "Yakutia", "Sakha (Yakutia)"), "Республика Саха (Якутия)", "Якутия"),
        Subject("Tatarstan", listOf("Republic of Tatarstan"), "Республика Татарстан", "Татарстан"),
        Subject("Tuva", listOf("Tyva Republic", "Republic of Tuva"), "Республика Тыва", "Тыва"),
        Subject("Udmurtia", listOf("Udmurt Republic"), "Удмуртская Республика", "Удмуртия"),
        Subject("Chukotka", listOf("Chukotka Autonomous Okrug"), "Чукотский автономный округ", "Чукотка"),
        Subject("Khanty-Mansi Autonomous Okrug", listOf("Khanty-Mansiysk Autonomous Okrug", "Yugra"), "Ханты-Мансийский автономный округ — Югра", "ХМАО"),
        Subject("Nenets Autonomous Okrug", listOf(), "Ненецкий автономный округ", "НАО"),
        Subject("Yamalo-Nenets Autonomous Okrug", listOf("Yamal-Nenets Autonomous Okrug"), "Ямало-Ненецкий автономный округ", "ЯНАО"),
        Subject("Jewish Autonomous Oblast", listOf(), "Еврейская автономная область", "Еврейская АО"),
    )

    private val index: Map<String, Entry> = buildMap {
        for (s in subjects) {
            val entry = Entry(s.en, s.ruShort)
            for (key in listOf(s.en, s.ruFull, s.ruShort) + s.aliases) put(normalize(key), entry)
        }
    }

    private fun normalize(s: String): String = s.trim().lowercase()
        .replace("ё", "е").replace("'", "").replace("\u2019", "")
}
