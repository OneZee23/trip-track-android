package com.onezee.triptrack.designsystem.strings

import android.icu.text.DateTimePatternGenerator
import android.icu.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

// iOS: AppStringsCore.swift, LocalizedDateFormatter. Android ICU speaks the same pattern language.
object LocalizedDateFormatter {
    private val patternTables = ConcurrentHashMap<Pair<String, ZoneId>, Map<AppLanguage, Formatter>>()
    private val templateTables = ConcurrentHashMap<Pair<String, ZoneId>, Map<AppLanguage, Formatter>>()
    // iOS: LocalizedDateFormatter's per-language DateFormatter table.
    class Formatter internal constructor(
        val language: AppLanguage,
        val pattern: String,
        zone: ZoneId,
    ) {
        private val formatter = SimpleDateFormat(pattern, language.locale).apply {
            timeZone = android.icu.util.TimeZone.getTimeZone(zone.id)
        }

        // DateFormat is mutable and not thread safe; feed and notification callers can overlap.
        @Synchronized
        fun format(date: Instant): String = formatter.format(Date.from(date))
    }

    fun patterns(pattern: String, zone: ZoneId = ZoneId.systemDefault()): Map<AppLanguage, Formatter> =
        patternTables.computeIfAbsent(pattern to zone) { table(zone) { pattern } }

    fun templates(template: String, zone: ZoneId = ZoneId.systemDefault()): Map<AppLanguage, Formatter> =
        templateTables.computeIfAbsent(template to zone) {
            table(zone) { DateTimePatternGenerator.getInstance(it.locale).getBestPattern(template) }
        }

    private fun table(zone: ZoneId, pattern: (AppLanguage) -> String): Map<AppLanguage, Formatter> =
        AppLanguage.entries.associateWith { Formatter(it, pattern(it), zone) }
}
