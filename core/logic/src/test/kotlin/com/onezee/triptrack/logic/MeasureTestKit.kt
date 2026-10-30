package com.onezee.triptrack.logic

import kotlin.math.abs
import kotlin.test.assertTrue

// iOS: AppStrings.swift, inline unit words and language tables. Production uses generated resources.
object IosMeasureWords : MeasureWords {
    override fun word(key: UnitWord, lang: String): String = when (key) {
        UnitWord.kilometre -> if (lang in setOf("ru", "uk", "kk")) "км" else "km"
        UnitWord.kilometresPerHour -> when (lang) {
            "ru" -> "км/ч"; "uk" -> "км/год"; "kk" -> "км/сағ"; "tr" -> "km/s"; "id" -> "km/j"; else -> "km/h"
        }
        UnitWord.metre -> if (lang in setOf("ru", "uk", "kk")) "м" else "m"
        UnitWord.mileOne -> "миля"
        UnitWord.mileFew -> if (lang == "uk") "милі" else "мили"
        UnitWord.mileMany -> "миль"
        UnitWord.footOne -> "фут"
        UnitWord.footFew -> if (lang == "uk") "фути" else "фута"
        UnitWord.footMany -> if (lang == "uk") "футів" else "футов"
        UnitWord.footFraction -> "фута"
        UnitWord.milesPerHour -> when (lang) {
            "ru" -> "миль/ч"; "uk" -> "миль/год"; "kk" -> "миль/сағ"; "tr" -> "mil/sa"; else -> "mph"
        }
        UnitWord.litre -> when (lang) {
            "ru", "uk", "kk" -> "л"; "en", "fil" -> "L"; else -> "l"
        }
        UnitWord.kilometresFull, UnitWord.milesFull, UnitWord.dashboardApp, UnitWord.dashboardMetricFuel,
        UnitWord.dashboardImperialFuel -> IosWordFixtures.unit(key, lang)
        UnitWord.kWh -> when (lang) {
            "ru" -> "кВт·ч"; "uk" -> "кВт·год"; "kk" -> "кВт·сағ"; else -> "kWh"
        }
    }
}

internal fun assertNumber(expected: Number, actual: Number?, tolerance: Double = 0.0) {
    assertTrue(actual != null && abs(expected.toDouble() - actual.toDouble()) <= tolerance,
        "Expected $expected ± $tolerance, actual $actual")
}
