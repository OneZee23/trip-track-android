// iOS: DiscoveryIdentityTests.swift, RiddleCatalogTests.swift, SecretCatalogTests.swift
package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.ModelJson
import java.time.Instant
import java.util.UUID
import kotlin.test.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*

class DiscoveryContractTests {
    private val json = ModelJson.storage
    private val tripId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private fun found(kind: DiscoveryKind, key: String) = Discovery(kind = kind, key = key,
        tripId = tripId, coordinate = GeoPoint(44.894, 37.316), foundAt = Instant.ofEpochSecond(1758000000), symbol = SealSymbol.lighthouse)

    @Test fun idIsDeterministicUUIDv5() {
        assertEquals(UUID.fromString("8A37654B-9D15-5AAB-92F6-ED2C3B8B4406"), found(DiscoveryKind.secret, "komsomolsky").id)
        assertEquals(UUID.fromString("08304055-7D26-538F-86AD-7659E445FE74"), found(DiscoveryKind.riddle, "lighthouse-anapa").id)
        assertEquals(UUID.fromString("B9C27CE7-4F53-50E8-AA0A-4A8F87CA0A17"), found(DiscoveryKind.milestone, "above2000:2026-09-16").id)
    }

    @Test fun kindSeparatesKeys() {
        assertNotEquals(found(DiscoveryKind.riddle, "x").id, found(DiscoveryKind.secret, "x").id)
        assertNotEquals(found(DiscoveryKind.milestone, "x").id, found(DiscoveryKind.secret, "x").id)
    }

    @Test fun versionVariantAndNamespaceStayFrozen() {
        assertEquals(5, found(DiscoveryKind.secret, "komsomolsky").id.version())
        assertEquals(2, found(DiscoveryKind.secret, "komsomolsky").id.variant())
        assertEquals(UUID.fromString("5A3C8B2E-7F10-4C6E-9D21-0E6B3A9F4C11"), Discovery.namespace)
        assertEquals(UUID.fromString("3C7A9C2E-5D3B-4F4E-8A1B-6E2F0D9C7B5A"), Place.namespace)
        assertEquals(7, Place.cellPrecision)
    }

    @Test fun decodingRebuildsIdFromKey() {
        val value = json.decodeFromString<Discovery>(fixture("DiscoveryIdentityTests-testDecodingRebuildsIdFromKey"))
        assertEquals(found(DiscoveryKind.riddle, "lighthouse-anapa").id, value.id)
        assertEquals(44.894, value.coordinate.latitude, 0.0001)
        assertEquals(SealSymbol.lighthouse, value.symbol)
        assertEquals(Instant.parse("2001-01-01T00:00:00Z"), value.foundAt)
        assertFalse(value.verified)
    }

    @Test fun discoveryRoundTripUsesFlatCoordinates() {
        val value = Discovery(kind = DiscoveryKind.milestone, key = "easternmost:2026-09-16", tripId = tripId,
            coordinate = GeoPoint(43.0, 131.9), foundAt = Instant.ofEpochSecond(1758000000), symbol = SealSymbol.extreme)
        val encoded = json.encodeToString(value)
        assertEquals(value, json.decodeFromString<Discovery>(encoded))
        val fields = jsonObject(encoded)
        assertFalse(fields.containsKey("coordinate"))
        assertEquals(43.0, fields["latitude"]?.jsonPrimitive?.double)
        assertEquals(131.9, fields["longitude"]?.jsonPrimitive?.double)
    }

    @Test fun discoveryRawValuesAreStorageContract() {
        assertEquals(listOf("secret", "riddle", "milestone"), DiscoveryKind.entries.map { it.rawValue })
        assertEquals("seal", SealSymbol.generic.rawValue)
        assertEquals(18, SealSymbol.entries.size)
        assertEquals("seal.komsomolsky", SealSymbol.komsomolsky.rawValue)
        assertEquals(10, Milestone.entries.size)
        assertEquals("above2000", Milestone.above2000.rawValue)
        assertEquals(SealSymbol.entries.size, SealSymbol.entries.map { it.rawValue }.toSet().size)
    }

    @Test fun riddleCompactKeysAndCoordinatePairRoundTrip() {
        val raw = """{"id":"pass:ubcr4xk","t":"pass","c":[43.6234,41.4571],"n":"Gumbashi Pass","r":"RU-KC"}"""
        val value = json.decodeFromString<Riddle>(raw)
        assertEquals(GeoPoint(43.6234, 41.4571), value.coordinate)
        assertEquals("Gumbashi Pass", value.name)
        assertEquals(RiddleType.pass, value.type)
        assertEquals(jsonObject(raw), jsonObject(json.encodeToString(value)))
        val noName = json.decodeFromString<Riddle>("""{"id":"x","t":"bridge","c":[1,2]}""")
        assertEquals("", noName.name)
        assertNull(noName.regionId)
        assertFailsWith<SerializationException> { json.decodeFromString<Riddle>("""{"id":"x","t":"pass","c":[1]}""") }
        assertEquals(12, RiddleType.entries.size)
    }

    @Test fun secretRecordToleratesUnknownSymbolAndKeepsUnsignedHashes() {
        val value = json.decodeFromString<SecretRecord>("""{"id":"future","hashes":[0,4294967295],"symbol":"future.symbol"}""")
        assertEquals(SealSymbol.generic, value.symbol)
        assertEquals(listOf(0u, UInt.MAX_VALUE), value.hashes)
        assertEquals(0.0, value.reach)
        assertFalse(value.polygon)
        assertEquals(value, json.decodeFromString<SecretRecord>(json.encodeToString(value)))
        assertFailsWith<SerializationException> { json.decodeFromString<SecretRecord>("""{"id":"x","hashes":[-1],"symbol":"seal"}""") }
    }
}
