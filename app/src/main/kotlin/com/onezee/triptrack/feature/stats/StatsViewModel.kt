// iOS: StatsScreenView.swift (StatsRange, load, slice, previousSlice, chartModel, stepYear,
// clampedMonth, regionsInRange, newRegionsInRange, newRoads), StatsPeriodFormat
package com.onezee.triptrack.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.LocalizedDateFormatter
import com.onezee.triptrack.feature.social.RemoteTrips
import com.onezee.triptrack.feature.social.SocialApi
import com.onezee.triptrack.model.RoadCard
import com.onezee.triptrack.model.Trip
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StatsRange { month, year, all }

/**
 * Where the screen takes its trips (iOS TripSource): one's own library, or someone's public trips.
 * The screen is the same either way; only the array differs.
 */
sealed interface StatsSource {
    class Own(val trips: suspend () -> List<Trip>, val roads: suspend () -> List<RoadCard>) : StatsSource
    class Remote(val api: SocialApi, val accountId: UUID) : StatsSource
}

data class StatsState(
    val aggregates: MeAggregates? = null,
    val failed: Boolean = false,
    val range: StatsRange = StatsRange.month,
    /** The month or year the strip is parked on: the newest month that holds a trip, not «now». */
    val anchor: YearMonth = YearMonth.now(),
)

/** iOS StatsScreenView.ChartModel: twelve columns and the highlighted one. */
data class StatsChart(val km: List<Double>, val trips: List<Int>, val highlight: Int?)

/**
 * iOS: StatsScreenView's data half. The heavy pass runs once off the main thread; every range the
 * screen can ask for is then a lookup or a 12-term sum over the month buckets.
 */
class StatsViewModel(
    private val source: StatsSource,
    private val worker: CoroutineDispatcher,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val mutable = MutableStateFlow(StatsState(anchor = YearMonth.from(now().atZone(zone))))
    val state: StateFlow<StatsState> = mutable.asStateFlow()
    private var generation = 0
    val isRemote: Boolean get() = source is StatsSource.Remote

    init { load() }

    /** Both retry buttons start independent loads: only the latest may publish. */
    fun load(): Job {
        val current = ++generation
        return viewModelScope.launch {
            val (trips, roads, failed) = when (source) {
                is StatsSource.Own -> Triple(withContext(worker) { source.trips() }, withContext(worker) { source.roads() }, false)
                // The owner's cache never serves another account: its key would match by accident.
                is StatsSource.Remote -> withContext(worker) { RemoteTrips.load(source.api, source.accountId) }.let { Triple(it.trips, emptyList(), it.failed) }
            }
            if (current != generation) return@launch
            val aggregates = withContext(worker) { MeAggregates.compute(trips, roads, includeFarthestPoint = true, now = now(), zone = zone) }
            if (current != generation) return@launch
            mutable.update { it.copy(aggregates = aggregates, failed = failed,
                anchor = aggregates.lastTripDate?.let { date -> YearMonth.from(date.atZone(zone)) } ?: it.anchor) }
        }
    }

    /** A tapped column parks the month range on it (Variant B: the year strip IS the month picker). */
    fun selectMonth(index: Int) { mutable.update { it.copy(range = StatsRange.month, anchor = YearMonth.of(it.anchor.year, index + 1)) } }

    /** A second tap on the chosen chip goes back to the month: otherwise only a column would. */
    fun toggleRange(value: StatsRange) {
        mutable.update { s -> if (s.range == value) s.copy(range = StatsRange.month, anchor = clampedMonth(s.anchor, s.aggregates)) else s.copy(range = value) }
    }

    fun stepYear(delta: Int) { stepTarget(delta)?.let { target -> mutable.update { it.copy(anchor = target) } } }

    /** A year back or forward without leaving the recorded library; the month clamps to it. */
    fun stepTarget(delta: Int): YearMonth? {
        val s = state.value
        val first = s.aggregates?.firstTripDate ?: return null
        val candidate = s.anchor.plusYears(delta.toLong())
        val nowYear = now().atZone(zone).year
        if (candidate.year < first.atZone(zone).year || candidate.year > nowYear) return null
        return if (s.range == StatsRange.month) clampedMonth(candidate, s.aggregates) else candidate
    }

    private fun clampedMonth(month: YearMonth, aggregates: MeAggregates?): YearMonth {
        val nowMonth = YearMonth.from(now().atZone(zone))
        if (month > nowMonth) return nowMonth
        val first = aggregates?.firstTripDate?.let { YearMonth.from(it.atZone(zone)) }
        if (first != null && month < first) return first
        return month
    }

    fun monthKey(month: YearMonth) = month.year * 12 + month.monthValue - 1

    fun slice(s: StatsState = state.value): StatsSlice {
        val a = s.aggregates ?: return StatsSlice()
        return when (s.range) {
            StatsRange.month -> a.monthTotals[monthKey(s.anchor)] ?: StatsSlice()
            StatsRange.year -> yearSum(a, s.anchor.year)
            StatsRange.all -> a.monthTotals.values.fold(StatsSlice()) { sum, bucket -> sum + bucket }
        }
    }

    fun previousSlice(s: StatsState = state.value): StatsSlice? {
        val a = s.aggregates ?: return null
        return when (s.range) {
            StatsRange.month -> a.monthTotals[monthKey(s.anchor) - 1]
            StatsRange.year -> yearSum(a, s.anchor.year - 1)
            StatsRange.all -> null
        }
    }

    private fun yearSum(a: MeAggregates, year: Int) = (0 until 12).fold(StatsSlice()) { sum, m -> a.monthTotals[year * 12 + m]?.let { sum + it } ?: sum }

    fun chart(s: StatsState = state.value): StatsChart {
        val a = s.aggregates ?: return StatsChart(List(12) { 0.0 }, List(12) { 0 }, null)
        val km = DoubleArray(12); val trips = IntArray(12)
        if (s.range == StatsRange.all) {
            // A column is «every June there has ever been»; the strip's title spells out the span.
            for ((key, bucket) in a.monthTotals) { val m = ((key % 12) + 12) % 12; km[m] += bucket.km; trips[m] += bucket.trips }
        } else for (m in 0 until 12) { a.monthTotals[s.anchor.year * 12 + m]?.let { km[m] = it.km; trips[m] = it.trips } }
        return StatsChart(km.toList(), trips.toList(), if (s.range == StatsRange.month) s.anchor.monthValue - 1 else null)
    }

    fun regionsInRange(s: StatsState = state.value): Int {
        val a = s.aggregates ?: return 0
        return when (s.range) {
            StatsRange.all -> a.regionsAllTime
            StatsRange.month -> a.monthRegions[monthKey(s.anchor)]?.size ?: 0
            StatsRange.year -> (0 until 12).flatMapTo(HashSet()) { a.monthRegions[s.anchor.year * 12 + it].orEmpty() }.size
        }
    }

    private fun contains(date: Instant, s: StatsState): Boolean = when (s.range) {
        StatsRange.month -> YearMonth.from(date.atZone(zone)) == s.anchor
        StatsRange.year -> date.atZone(zone).year == s.anchor.year
        StatsRange.all -> true
    }

    /** Regions first seen inside the period, in order of entry (the name breaks a tie). */
    fun newRegions(s: StatsState = state.value): List<Pair<String, Instant>> =
        s.aggregates?.regionFirstVisit.orEmpty().filter { contains(it.value, s) }.map { it.key to it.value }
            .sortedWith(compareBy<Pair<String, Instant>> { it.second }.thenBy { it.first })

    fun newRoads(s: StatsState = state.value): Int = s.aggregates?.roadFirstDriven.orEmpty().count { contains(it, s) }

    /** Months that can be picked: from the first trip to now (a future import must not trap). */
    fun pickable(index: Int, s: StatsState = state.value): Boolean {
        if (s.range == StatsRange.all) return false
        val key = s.anchor.year * 12 + index
        val nowKey = monthKey(YearMonth.from(now().atZone(zone)))
        val firstKey = s.aggregates?.firstTripDate?.let { monthKey(YearMonth.from(it.atZone(zone))) } ?: nowKey
        return key in firstKey..nowKey
    }
}

/**
 * iOS: StatsPeriodFormat. Month words from ICU, the case transforms here; the prepositional table
 * is written out because ICU gives none (and «drop the ending, add -е» breaks on «мае», «августе»).
 */
object StatsPeriodFormat {
    private fun standalone(month: YearMonth, language: AppLanguage, zone: ZoneId): String =
        LocalizedDateFormatter.patterns("LLLL", zone).getValue(language).format(month.atDay(15).atStartOfDay(zone).toInstant())

    /** «Июнь 2026»: the strip's and the headline's period. */
    fun monthYear(month: YearMonth, language: AppLanguage, zone: ZoneId): String =
        standalone(month, language, zone).replaceFirstChar { it.titlecase(language.locale) } + " ${month.year}"

    /** «апреля 2026»: the genitive month and the year as a number, without «г.». */
    fun monthYearGenitive(date: Instant, language: AppLanguage, zone: ZoneId): String {
        val month = LocalizedDateFormatter.patterns("MMMM", zone).getValue(language).format(date)
        val year = date.atZone(zone).year
        if (month.isEmpty()) return LocalizedDateFormatter.templates("MMMMyyyy", zone).getValue(language).format(date)
        return (if (language == AppLanguage.ru) month.lowercase(language.locale) else month) + " $year"
    }

    /** «июнь»: after «За …», where the Russian accusative of a month is its nominative. */
    fun monthLower(month: YearMonth, language: AppLanguage, zone: ZoneId): String {
        val name = standalone(month, language, zone)
        return if (language == AppLanguage.ru) name.lowercase(language.locale) else name.replaceFirstChar { it.titlecase(language.locale) }
    }

    fun dayMonth(date: Instant, language: AppLanguage, zone: ZoneId): String = LocalizedDateFormatter.templates("dMMMM", zone).getValue(language).format(date)

    fun dayMonthShort(date: Instant, language: AppLanguage, zone: ZoneId): String = LocalizedDateFormatter.templates("dMMM", zone).getValue(language).format(date)

    /** «Янв…Дек»: the first three letters of the nominative name (the abbreviated format list is genitive, with dots). */
    fun shortMonths(language: AppLanguage, zone: ZoneId): List<String> = (1..12).map { m ->
        standalone(YearMonth.of(2026, m), language, zone).take(3).replaceFirstChar { it.titlecase(language.locale) }
    }

    fun monthName(index: Int, language: AppLanguage, zone: ZoneId): String =
        standalone(YearMonth.of(2026, index + 1), language, zone).replaceFirstChar { it.titlecase(language.locale) }
}
