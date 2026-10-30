// iOS: Journey.swift (contains), TripOrigin.swift (earnsRewards), TripRepository.swift (origin/confirmation/
// roadFillState of entity), GamificationModels.swift (VehicleLevelSystem), VehicleFinish.swift (parse),
// VehicleModTags.swift, Drive2Link.swift, VehicleType.swift (init(storage:), hasPlate)
package com.onezee.triptrack.data

import android.icu.text.BreakIterator
import com.onezee.triptrack.model.Journey
import com.onezee.triptrack.model.RoadFillState
import com.onezee.triptrack.model.Trip
import com.onezee.triptrack.model.TripConfirmation
import com.onezee.triptrack.model.TripOrigin
import com.onezee.triptrack.model.VehicleFinish
import com.onezee.triptrack.model.VehicleType
import com.onezee.triptrack.model.serialization.ModelJson
import java.net.URI
import java.net.URISyntaxException
import java.time.Instant
import java.util.UUID
import kotlin.math.floor
import kotlin.math.sqrt
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

// The few model rules the store itself has to apply. On iOS they are methods of the model types;
// `core:model` carries data shapes only (wave 0), so they are internal here until their owners
// port them, and then this file shrinks to nothing.

/** iOS: Journey.contains(tripId:startDate:). Start inside the window, inclusive; open window takes all after. */
internal fun Journey.contains(tripId: UUID, tripStartDate: Instant): Boolean {
    if (excludedTripIds.contains(tripId)) return false
    if (tripStartDate < startDate) return false
    val end = endDate ?: return true
    return tripStartDate <= end
}

internal fun Journey.contains(trip: Trip): Boolean = contains(trip.id, trip.startDate)

/** iOS: TripOrigin.earnsRewards. A manual trip counts for the odometer, never for rewards. */
internal val TripOrigin.earnsRewards: Boolean get() = this == TripOrigin.recorded

/** iOS: CoreDataTripRepository.origin(of:). Unknown text is «recorded», not a refusal. */
internal fun tripOrigin(source: String?): TripOrigin = TripOrigin.fromRawValue(source ?: "") ?: TripOrigin.recorded

/** iOS: confirmation(of:). Only what this build marked a draft is a draft. */
internal fun tripConfirmation(raw: String?): TripConfirmation =
    TripConfirmation.fromRawValue(raw ?: "") ?: TripConfirmation.confirmed

/** iOS: roadFillState(of:). */
internal fun roadFillState(raw: String?): RoadFillState = RoadFillState.fromRawValue(raw ?: "") ?: RoadFillState.unchecked

/** iOS: VehicleType.init(storage:). */
internal fun vehicleType(storage: String?): VehicleType = storage?.let(VehicleType::fromRawValue) ?: VehicleType.car

/** iOS: VehicleType.hasPlate. A bicycle keeps no plate, not even a hidden one. */
internal val VehicleType.hasPlate: Boolean get() = this == VehicleType.car || this == VehicleType.moto

/** iOS: VehicleFinish.parse. */
internal fun vehicleFinish(raw: String?): VehicleFinish? = raw?.let(VehicleFinish::fromRawValue)

/** iOS: VehicleLevelSystem. A car's level is its reward mileage, counted: step N costs N × 100 km. */
internal object VehicleLevelSystem {
    const val STEP_KM = 100.0

    fun kmForLevel(level: Int): Double {
        if (level <= 1) return 0.0
        val l = level.toDouble()
        return STEP_KM / 2 * l * (l - 1)
    }

    /** The closed form can land a hair under an exact threshold, so it is nudged against both sides. */
    fun level(km: Double): Int {
        if (km <= 0) return 1
        val estimate = (1 + sqrt(1 + 8 * km / STEP_KM)) / 2
        var level = maxOf(1, floor(estimate).toInt())
        while (kmForLevel(level + 1) <= km) level += 1
        while (level > 1 && kmForLevel(level) > km) level -= 1
        return level
    }
}

/** iOS: VehicleModTags. Three tags of at most 24 characters, trimmed and without repeats. */
internal object VehicleModTags {
    private val strings = ListSerializer(String.serializer())

    fun sanitized(input: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (value in input) {
            val tag = prefixCharacters(value.trim(), 24)
            if (tag.isNotEmpty() && !result.contains(tag)) result.add(tag)
            if (result.size == 3) break
        }
        return result
    }

    fun decodeJSON(json: String?): List<String> {
        if (json == null) return emptyList()
        return try {
            ModelJson.storage.decodeFromString(strings, json)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** An empty list is written as `[]`, not NULL: on the wire that is an explicit clear. */
    fun encodeJSON(tags: List<String>): String = ModelJson.storage.encodeToString(strings, sanitized(tags))

    // Swift's `prefix` counts characters as the reader sees them (grapheme clusters), not UTF-16 units.
    // ICU's iterator, not java.text's: the JVM one splits an emoji from its skin-tone modifier.
    private fun prefixCharacters(text: String, count: Int): String {
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var end = 0
        repeat(count) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) return text
            end = next
        }
        return text.substring(0, end)
    }
}

/**
 * iOS: Drive2Link.init?(_:). Only http(s) DRIVE2 logbook links: the five known hosts, no user, no
 * port, `/r/<make>/<model>/<digits>/` and anything after it. Query and fragment are dropped and the
 * link is rebuilt as `https://www.drive2.ru|com/r/<make>/<model>/<id>/`, at most 200 characters.
 */
internal object Drive2Link {
    private val prefix = Regex("^https?://[^/?#:]+(?:[/?#]|$)", RegexOption.IGNORE_CASE)
    private val path = Regex("^/r/[a-z0-9_-]+/[a-z0-9_-]+/[0-9]+(?:/.*)?$")
    private val hosts = setOf("drive2.ru", "www.drive2.ru", "m.drive2.ru", "drive2.com", "www.drive2.com")

    fun normalized(input: String): String? {
        val text = input.trim()
        if (!prefix.containsMatchIn(text)) return null
        val uri = try {
            URI(text)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in hosts) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        val rawPath = uri.rawPath ?: return null
        if (!path.matches(rawPath)) return null
        val parts = rawPath.split("/").filter { it.isNotEmpty() }
        val normalizedHost = if (host.endsWith(".com")) "www.drive2.com" else "www.drive2.ru"
        val result = "https://$normalizedHost/r/${parts[1]}/${parts[2]}/${parts[3]}/"
        return result.takeIf { it.length <= 200 }
    }
}
