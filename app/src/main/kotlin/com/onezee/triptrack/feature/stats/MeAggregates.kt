// iOS: StatsScreenView.swift (MeAggregates, StatsSlice)
package com.onezee.triptrack.feature.stats

import com.onezee.triptrack.logic.duration
import com.onezee.triptrack.logic.previewCoordinates
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RoadCard
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.ui.RegionDisplay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One period's three numbers: a monthly bucket and any sum of them alike. */
data class StatsSlice(val km: Double = 0.0, val trips: Int = 0, val hours: Double = 0.0) {
    fun add(km: Double, hours: Double) = StatsSlice(this.km + km, trips + 1, this.hours + hours)
    operator fun plus(other: StatsSlice) = StatsSlice(km + other.km, trips + other.trips, hours + other.hours)
}

/**
 * iOS: MeAggregates. A pure aggregate over trips shared by the statistics screen of one's own and
 * someone else's library (and the public car page's records). Every number is real: computed on
 * the client from the trips at hand, safe to run off the main thread.
 */
data class MeAggregates(
    val tripCount: Int = 0,
    /** Trips that carry a photo or a note: what «фото и заметки в N поездках» counts. */
    val tripsWithMemories: Int = 0,
    val totalKm: Double = 0.0,
    val totalHours: Double = 0.0,
    val regionsAllTime: Int = 0,
    val longestTripKm: Double = 0.0,
    val longestTripTitle: String? = null,
    val longestTripRegion: String? = null,
    val longestTripDate: Instant? = null,
    val longestTripId: UUID? = null,
    val firstTripDate: Instant? = null,
    val lastTripDate: Instant? = null,
    /** Max Σ duration over any single calendar day, seconds. */
    val maxDayDuration: Double = 0.0,
    val year: Int = 0,
    val yearTripCount: Int = 0,
    val yearKm: Double = 0.0,
    val yearHours: Double = 0.0,
    val yearRegions: Int = 0,
    val yearPhotoCount: Int = 0,
    /** 12 buckets (Jan…Dec) of Σ km for the current year. */
    val monthlyKm: List<Double> = List(12) { 0.0 },
    /** Longest run of consecutive trip days inside the current year. */
    val yearStreak: Int = 0,
    /** Best single day of the current year, Σ km. */
    val maxDayKm: Double = 0.0,
    val topRegion: String? = null,
    val yearAgoTrip: Trip? = null,
    val longestYearTrip: Trip? = null,
    /** A region first visited this year, only once two regions exist: a lone region is not «new». */
    val newRegion: String? = null,
    val recentTrips: List<Trip> = emptyList(),
    /** Σ per calendar month, keyed by [monthKey]: every range is a lookup or a sum over this. */
    val monthTotals: Map<Int, StatsSlice> = emptyMap(),
    /** When each region was first driven («Новые места»). */
    val regionFirstVisit: Map<String, Instant> = emptyMap(),
    val monthRegions: Map<Int, Set<String>> = emptyMap(),
    /** First-drive dates of collected roads («+2 новые дороги»). */
    val roadFirstDriven: List<Instant> = emptyList(),
    val bestDayKm: Double = 0.0,
    val bestDayDate: Instant? = null,
    val bestDayTripId: UUID? = null,
    val photoPlace: String? = null,
    val photoPlaceCount: Int = 0,
    val championRoadName: String? = null,
    val championRoadDrives: Int = 0,
    val farthestPlace: String? = null,
    val farthestKm: Double = 0.0,
    val farthestTripId: UUID? = null,
) {
    companion object {
        /** `year * 12 + (month - 1)`: orderable, so the month before is −1 and a year ago −12. */
        fun monthKey(date: Instant, zone: ZoneId): Int = date.atZone(zone).let { it.year * 12 + it.monthValue - 1 }

        fun compute(trips: List<Trip>, roads: List<RoadCard> = emptyList(), includeFarthestPoint: Boolean = false,
                    now: Instant, zone: ZoneId): MeAggregates {
            val year = now.atZone(zone).year
            if (trips.isEmpty()) return MeAggregates(year = year)
            val regionsAll = HashSet<String>()
            val durationByDay = HashMap<LocalDate, Double>()
            val kmByDay = HashMap<LocalDate, Double>()
            val bestTripOfDay = HashMap<LocalDate, Pair<Double, UUID>>()
            val photosByPlace = HashMap<String, Int>()
            val monthTotals = HashMap<Int, StatsSlice>()
            val monthRegions = HashMap<Int, MutableSet<String>>()
            val firstVisit = HashMap<String, Instant>()
            var totalKm = 0.0; var totalHours = 0.0; var memories = 0
            var longest: Trip? = null
            var first: Instant? = null; var last: Instant? = null
            for (t in trips) {
                val km = t.distance / 1000
                totalKm += km
                if (t.photos.isNotEmpty() || !t.tripDescription.isNullOrEmpty()) memories++
                totalHours += t.duration / 3600
                // One region, one key, whatever language the geocoder wrote it in.
                val region = t.region?.takeIf { it.isNotEmpty() }?.let(RegionDisplay::canonical)
                region?.let(regionsAll::add)
                val day = t.startDate.atZone(zone).toLocalDate()
                durationByDay[day] = (durationByDay[day] ?: 0.0) + t.duration
                kmByDay[day] = (kmByDay[day] ?: 0.0) + km
                if (km > (bestTripOfDay[day]?.first ?: -1.0)) bestTripOfDay[day] = km to t.id
                if (t.distance > (longest?.distance ?: -1.0)) longest = t
                val month = monthKey(t.startDate, zone)
                monthTotals[month] = (monthTotals[month] ?: StatsSlice()).add(km, t.duration / 3600)
                if (first == null || t.startDate < first) first = t.startDate
                if (last == null || t.startDate > last) last = t.startDate
                if (region != null) {
                    monthRegions.getOrPut(month) { HashSet() }.add(region)
                    firstVisit[region] = firstVisit[region]?.let { minOf(it, t.startDate) } ?: t.startDate
                }
                // Photos are grouped by place; a trip with no region counts under its own name.
                if (t.photos.isNotEmpty()) {
                    val place = region ?: t.title.orEmpty()
                    if (place.isNotEmpty()) photosByPlace[place] = (photosByPlace[place] ?: 0) + t.photos.size
                }
            }
            // Deterministic maxima: the value first, then the earlier day / the smaller key.
            val bestDay = kmByDay.entries.maxWithOrNull(compareBy<Map.Entry<LocalDate, Double>> { it.value }.thenByDescending { it.key })
            val photoBest = photosByPlace.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
            val champion = roads.maxWithOrNull(compareBy<RoadCard> { it.timesDriven }.thenByDescending { it.name })
            val target = now.atZone(zone).minusYears(1).toInstant()
            val yearTrips = trips.filter { it.startDate.atZone(zone).year == year }
            val monthly = DoubleArray(12)
            val yearKmByDay = HashMap<LocalDate, Double>()
            val regionCounts = HashMap<String, Int>()
            for (t in yearTrips) {
                monthly[t.startDate.atZone(zone).monthValue - 1] += t.distance / 1000
                val day = t.startDate.atZone(zone).toLocalDate()
                yearKmByDay[day] = (yearKmByDay[day] ?: 0.0) + t.distance / 1000
                t.region?.takeIf { it.isNotEmpty() }?.let(RegionDisplay::canonical)?.let { regionCounts[it] = (regionCounts[it] ?: 0) + 1 }
            }
            val yearStart = java.time.ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, zone).toInstant()
            var result = MeAggregates(
                tripCount = trips.size, tripsWithMemories = memories, totalKm = totalKm, totalHours = totalHours,
                regionsAllTime = regionsAll.size, longestTripKm = (longest?.distance ?: 0.0) / 1000, longestTripTitle = longest?.title,
                longestTripRegion = longest?.region, longestTripDate = longest?.startDate, longestTripId = longest?.id,
                firstTripDate = first, lastTripDate = last, maxDayDuration = durationByDay.values.maxOrNull() ?: 0.0, year = year,
                yearTripCount = yearTrips.size, yearKm = yearTrips.sumOf { it.distance / 1000 },
                yearHours = yearTrips.sumOf { it.duration / 3600 }, yearRegions = regionCounts.size,
                yearPhotoCount = yearTrips.sumOf { it.photos.size }, monthlyKm = monthly.toList(),
                yearStreak = longestStreak(yearKmByDay.keys), maxDayKm = yearKmByDay.values.maxOrNull() ?: 0.0,
                // Highest count wins, the name breaks ties.
                topRegion = regionCounts.entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key,
                // «Год назад в этот день»: the best trip within ±3 days; an exact day almost never has one.
                yearAgoTrip = trips.filter { abs(it.startDate.epochSecond - target.epochSecond) < 3 * 86_400 }.maxByOrNull { it.distance },
                longestYearTrip = yearTrips.maxByOrNull { it.distance },
                newRegion = if (regionsAll.size >= 2) firstVisit.filterValues { it >= yearStart }.maxByOrNull { it.value }?.key else null,
                recentTrips = trips.sortedByDescending { it.startDate }.take(10),
                monthTotals = monthTotals, regionFirstVisit = firstVisit, monthRegions = monthRegions,
                roadFirstDriven = roads.map { it.firstDriven },
                bestDayKm = bestDay?.value ?: 0.0, bestDayDate = bestDay?.key?.atStartOfDay(zone)?.toInstant(),
                bestDayTripId = bestDay?.let { bestTripOfDay[it.key]?.second },
                photoPlace = photoBest?.key, photoPlaceCount = photoBest?.value ?: 0,
                championRoadName = champion?.name, championRoadDrives = champion?.timesDriven ?: 0,
            )
            if (includeFarthestPoint) result = result.withFarthestPoint(trips)
            return result
        }

        /** Longest run of consecutive calendar days. */
        fun longestStreak(days: Set<LocalDate>): Int {
            if (days.isEmpty()) return 0
            val sorted = days.sorted()
            var best = 1; var run = 1
            for (i in 1 until sorted.size) {
                if (sorted[i - 1].plusDays(1) == sorted[i]) { run++; best = maxOf(best, run) } else run = 1
            }
            return best
        }

        private fun cellKey(c: GeoPoint) = "${floor(c.latitude * 20).toInt()}_${floor(c.longitude * 20).toInt()}"

        private fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(dLat / 2) * sin(dLat / 2) + sin(dLon / 2) * sin(dLon / 2) * cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude))
            return 2 * 6371.0 * asin(min(1.0, sqrt(h)))
        }
    }

    /**
     * «Самая дальняя точка»: the furthest any route got from home. Home is not stored, so it is
     * inferred: the ~5 km cell that starts the most trips. The label is the trip itself: its
     * region is where it STARTED, the one place the farthest point is guaranteed not to be.
     */
    private fun withFarthestPoint(trips: List<Trip>): MeAggregates {
        val cells = LinkedHashMap<String, Triple<Int, Double, Double>>()
        for (t in trips) {
            val start = t.previewCoordinates.firstOrNull() ?: continue
            val key = cellKey(start)
            val (count, lat, lon) = cells[key] ?: Triple(0, 0.0, 0.0)
            cells[key] = Triple(count + 1, lat + start.latitude, lon + start.longitude)
        }
        val home = cells.entries.maxWithOrNull(compareBy<Map.Entry<String, Triple<Int, Double, Double>>> { it.value.first }.thenByDescending { it.key })?.value
            ?: return this
        val origin = GeoPoint(home.second / home.first, home.third / home.first)
        var km = 0.0; var tripId: UUID? = null; var place: String? = null
        for (t in trips) for (coord in t.previewCoordinates) {
            val d = haversineKm(origin, coord)
            if (d <= km) continue
            km = d; tripId = t.id; place = t.title?.takeIf { it.isNotEmpty() } ?: t.region
        }
        return if (place.isNullOrEmpty()) copy(farthestKm = 0.0, farthestTripId = null, farthestPlace = null)
        else copy(farthestKm = km, farthestTripId = tripId, farthestPlace = place)
    }
}
