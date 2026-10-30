// iOS: DiscoveryStoreTests.swift
package com.onezee.triptrack.data

import com.onezee.triptrack.model.Discovery
import com.onezee.triptrack.model.DiscoveryKind
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.Milestone
import com.onezee.triptrack.model.SealSymbol
import com.onezee.triptrack.model.SecretRevealResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test

/** Writes only what is new, reads newest first; the same find twice is an empty answer, not a second seal. */
class DiscoveryStoreTests : RoomTestCase() {
    private lateinit var store: DiscoveryStore

    @Before
    fun makeStore() {
        store = DiscoveryStore(db)
    }

    private fun make(
        kind: DiscoveryKind = DiscoveryKind.riddle,
        key: String = "lighthouse-anapa",
        tripId: UUID = UUID.randomUUID(),
        foundAt: Instant = Instant.ofEpochSecond(1_758_000_000),
        symbol: SealSymbol = SealSymbol.lighthouse,
        title: String? = null,
    ) = Discovery(kind = kind, key = key, tripId = tripId, coordinate = GeoPoint(44.894, 37.316), foundAt = foundAt, symbol = symbol, title = title)

    private fun storedCount() = countRows("DiscoveryEntity")

    @Test
    fun testUpsertReturnsOnlyWhatWasNotThere() = test {
        val first = store.upsert(listOf(make(key = "a"), make(key = "b")))
        assertEquals(setOf("a", "b"), first.map { it.key }.toSet())

        val second = store.upsert(listOf(make(key = "a"), make(key = "c")))
        assertEquals(listOf("c"), second.map { it.key })
        assertEquals(3, storedCount())
    }

    @Test
    fun testRepeatFindsNothing() = test {
        store.upsert(listOf(make()))
        assertTrue(store.upsert(listOf(make())).isEmpty())
        assertEquals(1, storedCount())
    }

    /** The first find wins: a second pass neither redates the seal nor moves it to a new trip. */
    @Test
    fun testFirstFindKeepsItsTripAndDate() = test {
        val firstTrip = UUID.randomUUID()
        val firstDate = Instant.ofEpochSecond(1_700_000_000)
        store.upsert(listOf(make(tripId = firstTrip, foundAt = firstDate)))
        store.upsert(listOf(make(tripId = UUID.randomUUID(), foundAt = Instant.now())))

        val stored = assertNotNull(store.all().firstOrNull())
        assertEquals(firstTrip, stored.tripId)
        assertEquals(firstDate, stored.foundAt)
    }

    @Test
    fun testDuplicateInsideOneBatchLandsOnce() = test {
        val added = store.upsert(listOf(make(), make(), make(key = "other")))
        assertEquals(2, added.size)
        assertEquals(2, storedCount())
    }

    @Test
    fun testEmptyUpsertIsNoop() = test {
        assertTrue(store.upsert(emptyList()).isEmpty())
        assertEquals(0, storedCount())
    }

    @Test
    fun testAllIsSortedByFoundAtDescending() = test {
        val old = make(key = "old", foundAt = Instant.ofEpochSecond(1_000))
        val mid = make(key = "mid", foundAt = Instant.ofEpochSecond(2_000))
        val new = make(key = "new", foundAt = Instant.ofEpochSecond(3_000))
        store.upsert(listOf(old, new, mid))

        assertEquals(listOf("new", "mid", "old"), store.all().map { it.key })
    }

    @Test
    fun testReadingKeepsEveryField() = test {
        val found = make(kind = DiscoveryKind.secret, key = "komsomolsky", symbol = SealSymbol.generic, title = "Комсомольский")
        store.upsert(listOf(found))

        val stored = assertNotNull(store.all().firstOrNull())
        assertEquals(found, stored)
        assertEquals(Discovery.id(DiscoveryKind.secret, "komsomolsky"), stored.id)
        assertEquals(44.894, stored.coordinate.latitude, 0.000_001)
        assertEquals(37.316, stored.coordinate.longitude, 0.000_001)
        assertFalse(stored.verified)
    }

    @Test
    fun testDiscoveriesFilterByKind() = test {
        store.upsert(
            listOf(
                make(kind = DiscoveryKind.secret, key = "s"),
                make(kind = DiscoveryKind.riddle, key = "r"),
                make(kind = DiscoveryKind.milestone, key = "${Milestone.firstRegion.rawValue}:RU-KDA"),
            ),
        )
        assertEquals(listOf("s"), store.discoveries(DiscoveryKind.secret).map { it.key })
        assertEquals(listOf("r"), store.discoveries(DiscoveryKind.riddle).map { it.key })
        assertEquals(1, store.discoveries(DiscoveryKind.milestone).size)
    }

    @Test
    fun testContainsAnswersById() = test {
        val found = make(key = "a")
        store.upsert(listOf(found))
        assertTrue(store.contains(found.id))
        assertFalse(store.contains(Discovery.id(DiscoveryKind.riddle, "never")))
    }

    @Test
    fun testWipeEmptiesEverything() = test {
        store.upsert(listOf(make(key = "a"), make(key = "b")))
        store.wipe()
        assertTrue(store.all().isEmpty())
        assertEquals(0, storedCount())
    }

    /** After a wipe the same find is found AGAIN: the id is derived from the key. */
    @Test
    fun testFindingAgainAfterWipeIsNew() = test {
        store.upsert(listOf(make()))
        store.wipe()
        val again = store.upsert(listOf(make()))
        assertEquals(1, again.size)
        assertEquals(Discovery.id(DiscoveryKind.riddle, "lighthouse-anapa"), again.first().id)
    }

    // MARK: The server's answer

    private fun reveal(
        id: String,
        kind: String = "riddle",
        verified: Boolean = true,
        title: String? = null,
        story: String? = "История",
        finders: Int? = 42,
        first: Boolean = true,
        rarity: String? = "tens",
    ) = SecretRevealResponse(
        id = id, kind = kind, title = title, story = story, symbol = "compass", verified = verified,
        foundAt = Instant.parse("2026-09-16T12:00:00Z"), finders = finders,
        first = if (first) SecretRevealResponse.First(displayName = "Илья", foundAt = Instant.parse("2025-01-02T10:00:00Z")) else null,
        rarity = rarity,
    )

    @Test
    fun testApplyRevealAppendsWithoutMovingTheFind() = test {
        val mine = make(foundAt = Instant.ofEpochSecond(1_700_000_000))
        store.upsert(listOf(mine))

        assertTrue(store.apply(reveal(mine.key)))

        val stored = assertNotNull(store.all().firstOrNull())
        assertEquals("История", stored.story)
        assertEquals(42, stored.finders)
        assertEquals("Илья", stored.firstFinderName)
        assertEquals("tens", stored.rarity)
        assertTrue(stored.verified)
        assertEquals(mine.foundAt, stored.foundAt)
        assertEquals(mine.tripId, stored.tripId)
    }

    /** An answer for a find that is not here creates no row: a seal without a place is a point in the ocean. */
    @Test
    fun testApplyRevealOfAnUnknownFindCreatesNothing() = test {
        assertFalse(store.apply(reveal("never-found")))
        assertEquals(0, storedCount())
    }

    @Test
    fun testApplyRevealNeverUnverifies() = test {
        val mine = make()
        store.upsert(listOf(mine))
        store.apply(reveal(mine.key, verified = true))

        store.apply(reveal(mine.key, verified = false))

        assertTrue(assertNotNull(store.all().firstOrNull()).verified)
    }

    /** «No text yet» and «no text any more» differ, and the server only ever says the first. */
    @Test
    fun testApplyRevealDoesNotWipeWhatItDoesNotCarry() = test {
        val mine = make(title = "Анапский маяк")
        store.upsert(listOf(mine))
        store.apply(reveal(mine.key))

        store.apply(reveal(mine.key, verified = false, title = null, story = null, finders = null, first = false, rarity = null))

        val stored = assertNotNull(store.all().firstOrNull())
        assertEquals("Анапский маяк", stored.title)
        assertEquals("История", stored.story)
        assertEquals(42, stored.finders)
        assertEquals("tens", stored.rarity)
    }

    /** Without a known kind the find's id cannot be derived, and guessing it is not allowed. */
    @Test
    fun testApplyRevealOfAnUnknownKindIsIgnored() = test {
        val mine = make()
        store.upsert(listOf(mine))

        assertFalse(store.apply(reveal(mine.key, kind = "constellation")))
        assertNull(assertNotNull(store.all().firstOrNull()).story)
    }

    @Test
    fun testDiscoveryById() = test {
        val mine = make()
        store.upsert(listOf(mine))
        assertEquals(mine.key, store.discovery(mine.id)?.key)
        assertNull(store.discovery(UUID.randomUUID()))
    }

    // MARK: Pull (DiscoveryStore.applyRemote)

    private fun remote(key: String, tripId: UUID?, coordinate: GeoPoint?, title: String? = null, verified: Boolean = false) =
        DiscoveryStore.Remote(
            id = Discovery.id(DiscoveryKind.riddle, key), kind = DiscoveryKind.riddle, key = key, tripId = tripId,
            coordinate = coordinate, foundAt = Instant.ofEpochSecond(1_759_000_000), symbol = SealSymbol.lighthouse,
            title = title, story = null, verified = verified,
        )

    /** A new row needs place AND trip; without them it only appends to an existing find. */
    @Test
    fun testRemoteCreatesOnlyWithPlaceAndTripAndAppendsOtherwise() = test {
        assertFalse(store.applyRemote(listOf(remote("a", tripId = null, coordinate = GeoPoint(44.0, 37.0)))))
        assertFalse(store.applyRemote(listOf(remote("b", tripId = UUID.randomUUID(), coordinate = null))))
        assertEquals(0, storedCount())

        assertTrue(store.applyRemote(listOf(remote("c", tripId = UUID.randomUUID(), coordinate = GeoPoint(44.0, 37.0)))))
        assertEquals(1, storedCount())

        val mine = make(key = "d", foundAt = Instant.ofEpochSecond(1_700_000_000))
        store.upsert(listOf(mine))
        assertTrue(store.applyRemote(listOf(remote("d", tripId = null, coordinate = null, title = "Маяк", verified = true))))
        val stored = assertNotNull(store.discovery(mine.id))
        assertEquals("Маяк", stored.title)
        assertTrue(stored.verified)
        assertEquals(mine.foundAt, stored.foundAt, "the first find's date stays")
        assertFalse(store.applyRemote(listOf(remote("d", tripId = null, coordinate = null, title = "Маяк", verified = true))), "nothing new")
    }
}
