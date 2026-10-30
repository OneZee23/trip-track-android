// iOS: CoreDataTripRepository.encodePhotoIds/decodePhotoIds, encodeSegments/decodeSegments,
// recordingBreaks(of:)/setRecordingBreaks, badgesJSON and companionsJSON coding;
// SettingsManager.vehicleFromEntity (stickersJSON); VehicleModTags.swift; VehicleMod.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.model.TripCompanion
import com.onezee.triptrack.model.TripSegment
import com.onezee.triptrack.model.VehicleMod
import com.onezee.triptrack.model.serialization.InstantSerializer
import com.onezee.triptrack.model.serialization.ModelJson
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The JSON columns, in the shape `JSONEncoder` writes on iOS: uuid strings in capitals, dates as
 * seconds since 2001, absent optionals omitted.
 *
 * Every decoder answers a broken column with an empty list, never a thrown error: the iOS readers
 * are all `try?` with a `[]` fallback, and one unreadable cell must not take the trip with it.
 */
internal object JsonColumns {
    private val json = ModelJson.storage
    private val strings = ListSerializer(String.serializer())
    private val segments = ListSerializer(TripSegment.serializer())
    private val instants = ListSerializer(InstantSerializer)
    private val companions = ListSerializer(TripCompanion.serializer())
    private val mods = ListSerializer(VehicleMod.serializer())
    private val uuidText = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

    /**
     * Attached photos, excluded trips: a JSON array of uuid strings, no relationship. An empty list
     * is stored as NULL, as on iOS.
     */
    fun encodeIds(ids: List<UUID>): String? =
        if (ids.isEmpty()) null else json.encodeToString(strings, ids.map { it.toString().uppercase() })

    /** Unparseable entries are skipped, not fatal (`compactMap(UUID.init(uuidString:))`). */
    fun decodeIds(text: String?): List<UUID> = decode(strings, text).mapNotNull(::parseUuid)

    /** Segments are a JSON column of the trip (0.6.8); an empty list is NULL. */
    fun encodeSegments(list: List<TripSegment>): String? =
        if (list.isEmpty()) null else json.encodeToString(segments, list)

    fun decodeSegments(text: String?): List<TripSegment> = decode(segments, text)

    /** Pause boundaries, normalized on the way in and on the way out. Empty is NULL. */
    fun encodeBreaks(breaks: List<Instant>): String? {
        val normalized = normalizedBreaks(breaks)
        return if (normalized.isEmpty()) null else json.encodeToString(instants, normalized)
    }

    fun decodeBreaks(text: String?): List<Instant> = normalizedBreaks(decode(instants, text))

    /** Earned badge ids, `[String]`. */
    fun encodeStrings(list: List<String>): String = json.encodeToString(strings, list)

    fun decodeStrings(text: String?): List<String> = decode(strings, text)

    /** The cached companion roster. */
    fun encodeCompanions(list: List<TripCompanion>): String = json.encodeToString(companions, list)

    fun decodeCompanions(text: String?): List<TripCompanion> = decode(companions, text)

    /** iOS: VehicleMod.encodeJSON. */
    fun encodeMods(list: List<VehicleMod>): String = json.encodeToString(mods, list)

    /** iOS: VehicleMod.decodeJSON. */
    fun decodeMods(text: String?): List<VehicleMod> = decode(mods, text)

    /**
     * iOS: RecordingBreaks.normalized: a set, sorted. `Instant` cannot hold the non-finite dates iOS
     * filters out. Inlined here while `core:logic` ports RecordingBreaks; there must be one copy
     * once both are merged.
     */
    fun normalizedBreaks(breaks: List<Instant>): List<Instant> = breaks.distinct().sorted()

    fun parseUuid(text: String): UUID? = if (uuidText.matches(text)) UUID.fromString(text) else null

    private fun <T> decode(serializer: KSerializer<List<T>>, text: String?): List<T> {
        if (text == null) return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
