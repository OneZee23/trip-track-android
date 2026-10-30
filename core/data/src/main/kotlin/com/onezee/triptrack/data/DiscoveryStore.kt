// iOS: DiscoveryStore.swift
package com.onezee.triptrack.data

import androidx.room.withTransaction
import com.onezee.triptrack.data.db.TripTrackDatabase
import com.onezee.triptrack.data.db.entity.DiscoveryEntity
import com.onezee.triptrack.model.Discovery
import com.onezee.triptrack.model.DiscoveryKind
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.SealSymbol
import com.onezee.triptrack.model.SecretRevealResponse
import java.time.Instant
import java.util.UUID

/**
 * Finds in the database: one row per find, keyed by the derived id.
 *
 * **The first find wins.** A repeated upsert leaves an existing row alone: the seal carries the
 * date the person was there FIRST, and a second pass must not redate it or move it to a new trip.
 * Hence [upsert] returns ONLY what was not there: «found the same thing twice» is an empty list.
 *
 * The history cache of milestones (`discoveries.extremes`) is the caller's to wipe alongside
 * [wipe]: it lives in preferences, not here.
 */
class DiscoveryStore(private val db: TripTrackDatabase) {
    private val dao = db.discoveryDao()

    /**
     * A find that arrived by pull. Its own type for one field: the coordinate is OPTIONAL. Zeros in
     * a `Discovery` are a point in the ocean, not «unknown».
     */
    data class Remote(
        val id: UUID,
        val kind: DiscoveryKind,
        val key: String,
        /** The trip the OTHER phone found it on; null can append to a find, never create one. */
        val tripId: UUID?,
        val coordinate: GeoPoint?,
        val foundAt: Instant,
        val symbol: SealSymbol,
        val title: String?,
        val story: String?,
        val verified: Boolean,
    )

    /** Records finds and returns only those the database did not have. */
    suspend fun upsert(items: List<Discovery>): List<Discovery> {
        if (items.isEmpty()) return emptyList()
        return db.withTransaction {
            // One read for the whole batch: a parsed track brings dozens of finds.
            val known = items.map { it.id }.distinct().chunked(CHUNK)
                .flatMap { dao.discoveries(it) }.mapTo(HashSet()) { it.id }
            val seen = HashSet<UUID>()
            val fresh = items.filter { it.id !in known && seen.add(it.id) }
            if (fresh.isNotEmpty()) dao.insertIfAbsent(fresh.map(::entity))
            fresh
        }
    }

    /** iOS: wipe(). The account deletion; the extremes cache is cleared by the caller. */
    suspend fun wipe() {
        dao.deleteAll()
    }

    /**
     * Appends what the phone could not know: the story, the number of finders, the first finder,
     * the rarity. **Append, not rewrite.** `foundAt` is never touched; `verified` goes only false to
     * true (an answer without a track must not take back what was earned); an empty title or story
     * from the server is «no text yet», not «no text any more». An answer for an unknown or wiped
     * id is ignored (false).
     */
    suspend fun apply(reveal: SecretRevealResponse): Boolean {
        val kind = DiscoveryKind.fromRawValue(reveal.kind) ?: return false
        val id = Discovery.id(kind, reveal.id)
        return db.withTransaction {
            val row = dao.discovery(id) ?: return@withTransaction false
            var updated = row
            reveal.title?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(title = it) }
            reveal.story?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(story = it) }
            reveal.finders?.let { updated = updated.copy(finders = it) }
            reveal.first?.let { updated = updated.copy(firstFinderName = it.displayName, firstFinderAt = it.foundAt) }
            reveal.rarity?.takeIf { it.isNotEmpty() }?.let { updated = updated.copy(rarity = it) }
            if (reveal.verified) updated = updated.copy(verified = true)
            if (updated == row) return@withTransaction false
            dao.update(updated)
            true
        }
    }

    /**
     * Finds from the second phone. An existing row gets the text the server knows (never its
     * `foundAt`); a NEW row is created only where place AND trip are known, or it would be a seal in
     * the Gulf of Guinea with nothing to open. Returns whether anything changed.
     */
    suspend fun applyRemote(items: List<Remote>): Boolean {
        if (items.isEmpty()) return false
        return db.withTransaction {
            val known = items.map { it.id }.distinct().chunked(CHUNK)
                .flatMap { dao.discoveries(it) }.associateBy { it.id }
            val updates = mutableListOf<DiscoveryEntity>()
            val inserts = mutableListOf<DiscoveryEntity>()
            for (item in items) {
                val row: DiscoveryEntity? = known[item.id]
                if (row != null) {
                    var updated: DiscoveryEntity = row
                    item.title?.takeIf { it.isNotEmpty() && it != row.title }?.let { updated = updated.copy(title = it) }
                    item.story?.takeIf { it.isNotEmpty() && it != row.story }?.let { updated = updated.copy(story = it) }
                    if (item.verified && !row.verified) updated = updated.copy(verified = true)
                    if (updated != row) updates.add(updated)
                } else {
                    val coordinate = item.coordinate ?: continue
                    val tripId = item.tripId ?: continue
                    inserts.add(
                        DiscoveryEntity(
                            id = item.id, kind = item.kind.rawValue, key = item.key, tripId = tripId,
                            latitude = coordinate.latitude, longitude = coordinate.longitude, foundAt = item.foundAt,
                            symbol = item.symbol.rawValue, title = item.title, story = item.story, verified = item.verified,
                        ),
                    )
                }
            }
            if (updates.isNotEmpty()) dao.update(updates)
            val inserted = if (inserts.isNotEmpty()) dao.insertIfAbsent(inserts).count { it != -1L } else 0
            updates.isNotEmpty() || inserted > 0
        }
    }

    /** One find by its derived id, for a retried reveal: the queue holds the id only. */
    suspend fun discovery(id: UUID): Discovery? = dao.discovery(id)?.let(::discovery)

    /** Everything found, newest first. */
    suspend fun all(): List<Discovery> = dao.all().mapNotNull(::discovery)

    suspend fun discoveries(kind: DiscoveryKind): List<Discovery> = dao.ofKind(kind.rawValue).mapNotNull(::discovery)

    suspend fun contains(id: UUID): Boolean = dao.discovery(id) != null

    private fun entity(item: Discovery) = DiscoveryEntity(
        id = item.id,
        kind = item.kind.rawValue,
        key = item.key,
        tripId = item.tripId,
        latitude = item.coordinate.latitude,
        longitude = item.coordinate.longitude,
        foundAt = item.foundAt,
        symbol = item.symbol.rawValue,
        title = item.title,
        story = item.story,
        verified = item.verified,
        finders = item.finders,
        firstFinderName = item.firstFinderName,
        firstFinderAt = item.firstFinderAt,
        rarity = item.rarity,
    )

    /** A row with an unknown kind or symbol is migration garbage, skipped like a dangling segment. */
    private fun discovery(e: DiscoveryEntity): Discovery? {
        val kind = DiscoveryKind.fromRawValue(e.kind) ?: return null
        val symbol = SealSymbol.fromRawValue(e.symbol) ?: return null
        return Discovery(
            kind = kind,
            key = e.key,
            tripId = e.tripId,
            coordinate = GeoPoint(e.latitude, e.longitude),
            foundAt = e.foundAt,
            symbol = symbol,
            title = e.title,
            story = e.story,
            verified = e.verified,
            finders = e.finders,
            firstFinderName = e.firstFinderName,
            firstFinderAt = e.firstFinderAt,
            rarity = e.rarity,
        )
    }

    private companion object {
        const val CHUNK = 500
    }
}
