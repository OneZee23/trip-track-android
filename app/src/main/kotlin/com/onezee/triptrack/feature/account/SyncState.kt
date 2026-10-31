// iOS: SyncStatusMarker.swift (SyncState), RelativeTripDate.swift
package com.onezee.triptrack.feature.account

import androidx.compose.ui.graphics.Color
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.designsystem.theme.AppColors
import com.onezee.triptrack.designsystem.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * iOS: SyncState. What synchronisation is doing right now, in one word and one colour. One state
 * machine for the two surfaces that show it (the status row on «Account & Sync» and the marker on
 * the row that opens it), including the off-but-publishing case: with Cloud Sync off the queue can
 * still hold operations for explicitly public trips.
 */
sealed interface SyncState {
    data object Off : SyncState
    data class OffPublishing(val count: Int) : SyncState
    data class Syncing(val done: Int, val total: Int) : SyncState
    data class Queued(val count: Int) : SyncState
    data object Done : SyncState

    fun label(strings: AppStrings): String = when (this) {
        Off -> strings.syncOffState()
        is OffPublishing -> strings.syncOffPublishing(count)
        is Syncing -> if (total > 0) strings.syncingProgress(done, total) else strings.syncingNow()
        is Queued -> strings.syncQueuedCount(count)
        Done -> strings.syncAllDone()
    }

    /** The row version: one word next to the dot; the full wording heads the screen it opens. */
    fun shortLabel(strings: AppStrings): String = when (this) {
        Off -> strings.syncStatusMarkerOff()
        is OffPublishing -> strings.syncQueuedCount(count)
        is Queued -> strings.syncQueuedCount(count)
        is Syncing -> if (total > 0) "$done/$total" else "…"
        Done -> strings.syncStatusMarkerSynced()
    }

    fun tint(colors: AppColors): Color = when (this) {
        Off -> colors.textTertiary
        // iOS paints the waiting states system orange; the house orange is the accent.
        is OffPublishing, is Queued -> SystemOrange
        is Syncing -> AppTheme.accent
        Done -> AppTheme.green
    }

    companion object {
        private val SystemOrange = Color(0xFFFF9500)

        fun current(enabled: Boolean, isSyncing: Boolean, pending: Int, batchProcessed: Int, batchTotal: Int): SyncState {
            if (!enabled) return if (pending > 0) OffPublishing(pending) else Off
            if (isSyncing) return Syncing(batchProcessed, batchTotal)
            return if (pending > 0) Queued(pending) else Done
        }
    }
}

/**
 * iOS: RelativeTripDate. «Just now», «5 min ago», «Today at 14:30», «Yesterday at 14:30», «3 days
 * ago», then an absolute date, with the year only when it differs. Calendar-day boundaries, not
 * 24-hour offsets, against the injected [now].
 */
object RelativeTripDate {
    fun string(date: Instant, strings: AppStrings, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val secondsAgo = maxOf(0L, now.epochSecond - date.epochSecond)
        if (secondsAgo < 60) return strings.relativeTripDateJustNow()
        val day = date.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val language = strings.language
        if (day == today) {
            if (secondsAgo < 3600) return strings.relTimeMinutesAgo((secondsAgo / 60).toInt())
            return strings.relTimeTodayAt(format("HH:mm", date, language, zone))
        }
        if (day == today.minusDays(1)) return strings.relTimeYesterdayAt(format("HH:mm", date, language, zone))
        val days = ChronoUnit.DAYS.between(day, today)
        if (days in 2..6) return strings.relTimeDaysAgo(days.toInt())
        val pattern = if (day.year == today.year) "d MMM" else "d MMM yyyy"
        val text = format(pattern, date, language, zone)
        // ru «апр.»: the meta canon is dotless («12 апр · Карелия»).
        return if (language == AppLanguage.ru) text.replace(".", "") else text
    }

    private fun format(pattern: String, date: Instant, language: AppLanguage, zone: ZoneId): String =
        LocalizedDateFormatter.patterns(pattern, zone).getValue(language).format(date)
}
