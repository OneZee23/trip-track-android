// iOS: TripTrack.xcdatamodeld (UUID and Date attribute storage)
package com.onezee.triptrack.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.util.UUID

/**
 * Column encodings for the two Core Data attribute types SQLite has no word for.
 *
 * A UUID is stored as its canonical lowercase text. Ordering by that text is the same order as
 * comparing the sixteen bytes, which is what `TrackPointOrderKey` compares on iOS: every byte is two
 * hex digits, digits sort before letters, and the dashes sit in the same places.
 *
 * An Instant is stored as nanoseconds since 1970. Core Data keeps a `Double` of seconds, which
 * rounds a microsecond `Instant` on the way back; the repository compares stored stamps for
 * equality (a manual track is adopted only if `lastModifiedAt` is still the one it saw), so the
 * column must give back exactly what was written. A `Long` of nanoseconds covers 1677–2262.
 */
class Converters {
    @TypeConverter
    fun uuidToText(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun textToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    /**
     * Saturating: an instant beyond 1677–2262 becomes the end of the range instead of an overflow.
     * Only the iOS `distantPast`/`distantFuture` bounds of a query reach that far, and for a
     * comparison «the end of the range» is exactly what they mean.
     */
    @TypeConverter
    fun instantToNanos(value: Instant?): Long? = value?.let {
        when {
            it.epochSecond >= MAX_SECONDS -> Long.MAX_VALUE
            it.epochSecond < MIN_SECONDS -> Long.MIN_VALUE
            else -> it.epochSecond * NANOS + it.nano
        }
    }

    @TypeConverter
    fun nanosToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochSecond(Math.floorDiv(it, NANOS), Math.floorMod(it, NANOS)) }

    private companion object {
        const val NANOS = 1_000_000_000L
        const val MAX_SECONDS = Long.MAX_VALUE / NANOS
        const val MIN_SECONDS = Long.MIN_VALUE / NANOS
    }
}
