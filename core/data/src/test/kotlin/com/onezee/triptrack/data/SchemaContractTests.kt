// iOS: TripTrack v24.xcdatamodel (the model itself), TrackPointOrderTests.swift, GarageDataTests.swift (Drive2Link,
// VehicleModTags), CoreDataV23MigrationTests.swift (the unordered points and their index)
package com.onezee.triptrack.data

import com.onezee.triptrack.logic.VehicleModTags
import com.onezee.triptrack.logic.Drive2Link

import com.onezee.triptrack.data.db.dao.WorldSql
import com.onezee.triptrack.data.db.entity.TrackPointEntity
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.model.SyncStatus
import com.onezee.triptrack.model.TripConfirmation
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The schema IS a contract: names, defaults, optionality, the cascade and the indexes of Core Data
 * v24. Read back from SQLite itself, so a changed annotation fails here and not on a phone.
 */
class SchemaContractTests : RoomTestCase() {
    private data class Column(val name: String, val type: String, val notNull: Boolean, val default: String?)

    private fun columns(table: String): Map<String, Column> =
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c ->
            val out = linkedMapOf<String, Column>()
            while (c.moveToNext()) {
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                out[name] = Column(
                    name, c.getString(c.getColumnIndexOrThrow("type")), c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                    c.getString(c.getColumnIndexOrThrow("dflt_value")),
                )
            }
            out
        }

    private fun indexes(table: String): Map<String, List<String>> =
        db.openHelper.readableDatabase.query("PRAGMA index_list(`$table`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(c.getColumnIndexOrThrow("name")))
            names.filter { !it.startsWith("sqlite_autoindex") }.associateWith { index ->
                db.openHelper.readableDatabase.query("PRAGMA index_info(`$index`)").use { i ->
                    val cols = mutableListOf<String>()
                    while (i.moveToNext()) cols.add(i.getString(i.getColumnIndexOrThrow("name")))
                    cols
                }
            }
        }

    private fun foreignKeys(table: String): List<Triple<String, String, String>> =
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_list(`$table`)").use { c ->
            val out = mutableListOf<Triple<String, String, String>>()
            while (c.moveToNext()) {
                out.add(Triple(c.getString(c.getColumnIndexOrThrow("table")), c.getString(c.getColumnIndexOrThrow("from")), c.getString(c.getColumnIndexOrThrow("on_delete"))))
            }
            out
        }

    @Test
    fun testEveryCoreDataEntityIsATable() {
        val tables = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            val out = mutableSetOf<String>()
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }
        val model = setOf(
            "TripEntity", "TripCheckpointEntity", "TripPhotoEntity", "TrackPointEntity", "VehiclePhotoEntity", "VehicleEntity",
            "UserSettingsEntity", "VisitedGeohashEntity", "GeocodeCacheEntity", "RoadEntity", "JourneyEntity", "PlaceEntity",
            "PlacePassEntity", "RevealedCellEntity", "DiscoveryEntity",
        )
        assertTrue(tables.containsAll(model), "missing: ${model - tables}")
    }

    @Test
    fun testTripColumnsKeepTheModelDefaultsAndOptionality() {
        val trip = columns("TripEntity")
        assertEquals(Column("syncStatus", "INTEGER", true, "0"), trip["syncStatus"])
        assertEquals(Column("isPrivate", "INTEGER", true, "1"), trip["isPrivate"])
        assertEquals(Column("source", "TEXT", false, "'recorded'"), trip["source"])
        assertEquals(Column("confirmation", "TEXT", false, "'confirmed'"), trip["confirmation"])
        assertEquals(Column("roadFillState", "TEXT", false, "'unchecked'"), trip["roadFillState"])
        assertEquals(Column("energyMode", "TEXT", false, null), trip["energyMode"])
        assertEquals(Column("startDate", "INTEGER", true, null), trip["startDate"])
        assertEquals(Column("endDate", "INTEGER", false, null), trip["endDate"])
        assertEquals(Column("previewPolyline", "BLOB", false, null), trip["previewPolyline"])
        assertEquals(33, trip.size, "the 33 attributes of v24, id included")
    }

    @Test
    fun testGarageColumnsAreNullableAndVehicleDefaultsHold() {
        val vehicle = columns("VehicleEntity")
        for (name in listOf("drive2URL", "modTagsJSON", "paintFinish", "modsJSON", "catalogModelId", "catalogGenerationId", "catalogVariantId")) {
            assertEquals(false, vehicle[name]?.notNull, "$name must stay nullable: NULL is «never said»")
        }
        assertEquals("'🏎️'", vehicle["avatarEmoji"]?.default)
        assertEquals("'app'", vehicle["dashboardUnits"]?.default)
        assertEquals("'fuel'", vehicle["powertrain"]?.default)
        assertEquals("1", vehicle["mapVisible"]?.default)
        assertEquals("1", vehicle["vehicleLevel"]?.default)
        assertEquals(false, vehicle["manualOdometerKm"]?.notNull, "«not entered» differs from zero")
        assertEquals("1", columns("JourneyEntity")["conflictVersion"]?.default, "a journey starts at version 1")
        assertEquals("-1.0", columns("TrackPointEntity")["course"]?.default)
        assertEquals("-1.0", columns("PlacePassEntity")["course"]?.default)
        val settings = columns("UserSettingsEntity")
        assertEquals("'km'", settings["distanceUnit"]?.default)
        assertEquals("'€'", settings["fuelCurrency"]?.default)
        assertEquals("7.8", settings["fuelConsumption"]?.default)
        assertEquals(false, columns("DiscoveryEntity")["finders"]?.notNull, "nil finders is «we did not ask»")
    }

    @Test
    fun testRelationshipsCascadeAndRelationlessEntitiesHaveNoForeignKeys() {
        for (child in listOf("TrackPointEntity", "TripPhotoEntity", "TripCheckpointEntity")) {
            assertEquals(listOf(Triple("TripEntity", "tripId", "CASCADE")), foreignKeys(child), child)
        }
        for (relationless in listOf("VehiclePhotoEntity", "JourneyEntity", "PlaceEntity", "PlacePassEntity", "RevealedCellEntity", "DiscoveryEntity")) {
            assertTrue(foreignKeys(relationless).isEmpty(), "$relationless must not cascade: it is deleted by name")
        }
    }

    @Test
    fun testTheModelIndexesExist() {
        assertEquals(listOf("tripId", "timestamp"), indexes("TrackPointEntity")["TrackPointEntity_byTripTimestamp"])
        assertEquals(listOf("startDate"), indexes("TripEntity")["TripEntity_byStartDate"])
        assertEquals(listOf("startDate"), indexes("JourneyEntity")["JourneyEntity_byStartDate"])
        assertEquals(listOf("cell"), indexes("PlaceEntity")["PlaceEntity_byCell"])
        assertEquals(listOf("placeId"), indexes("PlacePassEntity")["PlacePassEntity_byPlace"])
        assertEquals(listOf("tripId"), indexes("PlacePassEntity")["PlacePassEntity_byTrip"])
        assertEquals(listOf("key"), indexes("RevealedCellEntity")["RevealedCellEntity_byKey"])
        assertEquals(listOf("hash6"), indexes("VisitedGeohashEntity")["VisitedGeohashEntity_byHash6"])
        assertEquals(listOf("geohash5"), indexes("GeocodeCacheEntity")["GeocodeCacheEntity_byGeohash5"])
    }

    @Test
    fun testWorldSqlNumbersAreTheEnumRawValues() {
        assertEquals(SyncStatus.pendingUpload.rawValue.toInt(), WorldSql.PENDING_UPLOAD)
        assertEquals(SyncStatus.synced.rawValue.toInt(), WorldSql.SYNCED)
        assertEquals(SyncStatus.pendingDelete.rawValue.toInt(), WorldSql.PENDING_DELETE)
        assertTrue(WorldSql.NOT_DRAFT.contains("'${TripConfirmation.draft.rawValue}'"))
    }

    /** Instants come back exactly: equality of stamps is a rule (manual track adoption). */
    @Test
    fun testInstantsRoundTripToTheNanosecond() = test {
        val stamp = Instant.ofEpochSecond(1_760_000_000, 123_456_789)
        val before1970 = Instant.ofEpochSecond(-1, 999_999_999)
        val id = insert(TripEntity(id = UUID.randomUUID(), startDate = before1970, lastModifiedAt = stamp))
        assertEquals(stamp, trip(id)?.lastModifiedAt)
        assertEquals(before1970, trip(id)?.startDate)
    }

    // MARK: TrackPointOrderTests: time, then id; the id order is the order of its bytes

    @Test
    fun testPointsComeBackInTimeOrderWithIdBreakingTies() = test {
        val tripId = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.ofEpochSecond(1_700_000_000)))
        val base = Instant.ofEpochSecond(1_700_000_000)
        val points = (0 until 500).shuffled().map { i ->
            // Every ten points share a second: the second key has work to do.
            TrackPointEntity(id = UUID.randomUUID(), latitude = 52 + i * 1e-5, longitude = 76.0, timestamp = base.plusSeconds(i / 10L), tripId = tripId)
        }
        db.tripDao().insertTrackPoints(points)

        val ordered = db.tripDao().orderedTrackPoints(tripId)

        assertEquals(500, ordered.size)
        assertEquals(points.sortedWith(compareBy<TrackPointEntity> { it.timestamp }.thenBy { it.id.toString().uppercase() }).map { it.id }, ordered.map { it.id })
        assertEquals(ordered.map { it.id }, repo.fetchTripDetail(tripId)?.trackPoints?.map { it.id })
    }

    @Test
    fun testUuidTextOrderMatchesItsBytes() = test {
        val low = UUID.fromString("0A000000-0000-0000-0000-000000000000")
        val high = UUID.fromString("A0000000-0000-0000-0000-000000000000")
        val tripId = insert(TripEntity(id = UUID.randomUUID(), startDate = Instant.EPOCH))
        val t = Instant.ofEpochSecond(1_700_000_000)
        db.tripDao().insertTrackPoints(listOf(TrackPointEntity(id = high, timestamp = t, tripId = tripId), TrackPointEntity(id = low, timestamp = t, tripId = tripId)))
        assertEquals(listOf(low, high), db.tripDao().orderedTrackPoints(tripId).map { it.id })
    }

    // MARK: GarageDataTests: Drive2Link and VehicleModTags

    @Test
    fun testDrive2NormalizesEveryAcceptedHost() {
        for (host in listOf("drive2.ru", "www.drive2.ru", "m.drive2.ru", "drive2.com", "www.drive2.com")) {
            val link = Drive2Link.normalized("  http://$host/r/volkswagen/polo/123456/logbook/?utm=a#part \n")
            assertEquals("https://www.drive2.${if (host.endsWith("com")) "com" else "ru"}/r/volkswagen/polo/123456/", link)
        }
    }

    @Test
    fun testDrive2RejectsArbitraryAndSpoofedLinks() {
        for (input in listOf(
            "drive2.ru/r/vw/polo/123", "https://drive2.ru.evil.example/r/vw/polo/123/", "https://drive2.ru/users/test/",
            "https://drive2.ru/l/123/", "https://drive2.ru/r/vw/polo/abc/", "https://drive2.ru/r/vw/polo/123abc/",
            "https://user@drive2.ru/r/vw/polo/123/", "https://drive2.ru:443/r/vw/polo/123/",
            "https:drive2.ru/r/vw/polo/123/", "https://drive2.ru:/r/vw/polo/123/",
            "http://drive2.ru:80/r/vw/polo/123/",
            "ftp://drive2.ru/r/vw/polo/123/", "https://drive2.ru/r/vw/%70olo/123/",
        )) {
            assertNull(Drive2Link.normalized(input), input)
        }
    }

    @Test
    fun testTagsAreTrimmedLimitedAndDeduplicated() {
        val tags = VehicleModTags.sanitized(listOf("  CarPlay  ", "", "CarPlay", "я".repeat(40), "Шумка", "fourth"))
        assertEquals(listOf("CarPlay", "я".repeat(24), "Шумка"), tags)
        assertEquals(tags, VehicleModTags.decodeJSON(VehicleModTags.encodeJSON(tags)))
        assertEquals("[]", VehicleModTags.encodeJSON(emptyList()), "an empty list is an explicit clear, not NULL")
        // A tag is cut by characters as the reader sees them, not by UTF-16 units.
        assertEquals(listOf("👍🏽".repeat(24)), VehicleModTags.sanitized(listOf("👍🏽".repeat(30))))
    }

    @Test
    fun testPhotoIdColumnsAreCapitalUuidStringsAndTolerateGarbage() {
        val a = UUID.fromString("00000000-0000-4000-8000-00000000000a")
        assertEquals("[\"00000000-0000-4000-8000-00000000000A\"]", JsonColumns.encodeIds(listOf(a)))
        assertNull(JsonColumns.encodeIds(emptyList()), "an empty list is NULL, as on iOS")
        assertEquals(listOf(a), JsonColumns.decodeIds("[\"00000000-0000-4000-8000-00000000000a\",\"not-a-uuid\"]"))
        assertEquals(emptyList(), JsonColumns.decodeIds("{broken"))
    }
}
