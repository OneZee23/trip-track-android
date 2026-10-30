package com.onezee.triptrack.location.world

import android.content.Context
import android.content.SharedPreferences
import com.onezee.triptrack.data.RecordingStore
import com.onezee.triptrack.data.SettingsStore
import com.onezee.triptrack.data.TripRepository
import com.onezee.triptrack.data.VehicleStore
import com.onezee.triptrack.logic.BadgeLedger
import com.onezee.triptrack.logic.BadgeManager
import com.onezee.triptrack.logic.BadgeRule
import com.onezee.triptrack.logic.GamificationManager
import com.onezee.triptrack.logic.TripStreak
import com.onezee.triptrack.logic.from
import com.onezee.triptrack.logic.total
import com.onezee.triptrack.logic.updateStreak
import com.onezee.triptrack.model.DriverRank
import com.onezee.triptrack.model.RoadCompletionInfo
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.XPBreakdown
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * iOS: TripCompletionData. Badges stay rules here: their words and art are resolved by id on the
 * screen that shows them, not stored with the result.
 */
data class TripRewardsResult(
    val xpEarned: Int,
    val xpBreakdown: XPBreakdown,
    val previousLevel: Int,
    val newLevel: Int,
    val previousXP: Int,
    val newXP: Int,
    val previousRank: DriverRank,
    val newRank: DriverRank,
    val vehicleOdometerBefore: Double,
    val vehicleOdometerAfter: Double,
    val vehicleLevelBefore: Int,
    val vehicleLevelAfter: Int,
    val newBadges: List<BadgeRule>,
    val repeatedBadgeCounts: Map<String, Int>,
    val currentStreak: Int,
    val roadCard: RoadCompletionInfo? = null,
)

/** iOS: BadgeManager's `unlockedBadgeIds` and `badgeEarnCounts` in UserDefaults. */
interface BadgeLedgerStore {
    fun load(): BadgeLedger
    fun save(ledger: BadgeLedger)

    class InMemory(private var value: BadgeLedger = BadgeLedger()) : BadgeLedgerStore {
        override fun load(): BadgeLedger = value
        override fun save(ledger: BadgeLedger) {
            value = ledger
        }
    }
}

class PreferencesBadgeLedgerStore(private val preferences: SharedPreferences) : BadgeLedgerStore {
    constructor(context: Context) : this(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))

    override fun load(): BadgeLedger = BadgeLedger(
        unlockedIds = preferences.getStringSet(UNLOCKED_KEY, emptySet()).orEmpty().toSet(),
        earnCounts = preferences.getStringSet(EARN_COUNTS_KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val (id, count) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            count.toIntOrNull()?.let { id to it }
        }.toMap(),
    )

    override fun save(ledger: BadgeLedger) {
        preferences.edit()
            .putStringSet(UNLOCKED_KEY, ledger.unlockedIds)
            .putStringSet(EARN_COUNTS_KEY, ledger.earnCounts.map { (id, count) -> "$id=$count" }.toSet())
            .apply()
    }

    companion object {
        const val FILE_NAME = "triptrack.badges"
        // The iOS UserDefaults keys.
        const val UNLOCKED_KEY = "unlockedBadgeIds"
        const val EARN_COUNTS_KEY = "badgeEarnCounts"
    }
}

/**
 * iOS: GamificationManager.processCompletedTrip. Experience and level, the streak, the car's
 * odometer and level, the trip's XP and badges. The rules are core:logic's; this writes them.
 *
 * The odometer counts everything the car drove; its level counts only rewarded kilometres, by
 * subtraction (`unrewardedKm`), because the incrementally kept odometer may hold more than the
 * trips on this phone.
 */
class TripRewards(
    private val trips: TripRepository,
    private val recording: RecordingStore,
    private val settings: SettingsStore,
    private val vehicles: VehicleStore,
    private val badges: BadgeLedgerStore,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val weekFields: () -> WeekFields = { WeekFields.of(Locale.getDefault()) },
    private val clock: () -> Instant = Instant::now,
) {
    suspend fun processCompletedTrip(trip: Trip, allTrips: List<Trip>): TripRewardsResult {
        val breakdown = GamificationManager.calculateXP(trip, allTrips, zone())
        val before = settings.settings()
        val previousXP = before?.profileXP?.toInt() ?: 0
        val previousLevel = before?.profileLevel ?: 1
        val newXP = previousXP + breakdown.total
        // A restored level without its XP must not fall back to level one on the next trip.
        val newLevel = GamificationManager.newProfileLevel(previousLevel, newXP)
        var streak = TripStreak(before?.currentStreak ?: 0, before?.bestStreak ?: 0, before?.lastTripDate)
        if (before != null) {
            streak = GamificationManager.updateStreak(streak, trip.startDate, zone())
            settings.save {
                it.copy(
                    profileXP = newXP.toLong(), profileLevel = newLevel, currentStreak = streak.current,
                    bestStreak = streak.best, lastTripDate = streak.lastTripDate,
                )
            }
        }

        val vehicle = trip.vehicleId?.let { vehicles.entity(it) }
        val odometerBefore = vehicle?.odometerKm ?: 0.0
        val levelBefore = vehicle?.vehicleLevel ?: 1
        var odometerAfter = odometerBefore
        var levelAfter = levelBefore
        if (vehicle != null) {
            odometerAfter = GamificationManager.completedVehicleOdometer(odometerBefore, trip)
            levelAfter = GamificationManager.completedVehicleLevel(odometerAfter, trips.unrewardedKm(vehicle.id))
            recording.setVehicleProgress(vehicle.id, odometerAfter, levelAfter)
        }
        recording.saveXP(trip.id, breakdown.total)

        val ledger = badges.load()
        val evaluation = BadgeManager.evaluateBadgesForTrip(
            trip, allTrips, ledger, clock(), zone(), weekFields(), streak.current, streak.best,
        )
        badges.save(evaluation.ledger)
        trips.saveBadgesJSON(trip.id, evaluation.allEarned.map { it.id })

        return TripRewardsResult(
            xpEarned = breakdown.total, xpBreakdown = breakdown,
            previousLevel = previousLevel, newLevel = newLevel, previousXP = previousXP, newXP = newXP,
            previousRank = DriverRank.from(previousLevel), newRank = DriverRank.from(newLevel),
            vehicleOdometerBefore = odometerBefore, vehicleOdometerAfter = odometerAfter,
            vehicleLevelBefore = levelBefore, vehicleLevelAfter = levelAfter,
            newBadges = evaluation.allEarned, repeatedBadgeCounts = evaluation.repeatedBadgeCounts,
            currentStreak = streak.current,
        )
    }
}
