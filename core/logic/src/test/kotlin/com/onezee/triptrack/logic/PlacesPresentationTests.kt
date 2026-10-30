package com.onezee.triptrack.logic

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.PlacesPresentation.Group.Kind
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.test.*

// iOS: PlacesPresentationTests.swift. Freeze the clock so tests cannot cross midnight.
class PlacesPresentationTests {
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val zone = ZoneId.of("Europe/Moscow")
    private val ru = Locale.forLanguageTag("ru")
    private fun item(name: String?, passes: Int, lastDaysAgo: Int, frequent: Boolean = false): PlaceListItem {
        val last = now.atZone(zone).minusDays(lastDaysAgo.toLong()).toInstant()
        return PlaceListItem(Place(UUID.randomUUID(), "ucfv0j0", 45.0, 39.0, name, last),
            PlaceStats(passes, last, last, 600.0, frequent, emptyList()), 600.0)
    }
    private fun groups(items: List<PlaceListItem>, by: PlacesSort = PlacesSort.frequent) =
        PlacesPresentation.group(items, true, by, now, zone)
    private fun build(items: List<PlaceListItem>, query: String, sort: PlacesSort) =
        PlacesPresentation.build(items, query, sort, ru, now, zone)

    @Test fun testRecentSplitsByHowLongAgo() {
        val groups = groups(listOf(item("Дом", 9, 0), item("Работа", 9, 3), item("Дача", 9, 40)), PlacesSort.recent)
        assertEquals(listOf(Kind.today, Kind.thisWeek, Kind.earlier), groups.map { it.kind })
        assertEquals(listOf("Дом"), groups[0].items.map { it.place.name })
        assertEquals(listOf("Работа"), groups[1].items.map { it.place.name })
        assertEquals(listOf("Дача"), groups[2].items.map { it.place.name })
    }
    @Test fun testASingleSurvivingGroupCollapsesBackToAll() {
        val groups = groups(listOf(item("Дом", 9, 0), item("Работа", 9, 0)), PlacesSort.recent)
        assertEquals(listOf(Kind.all), groups.map { it.kind })
        assertEquals(2, groups[0].items.size)
    }
    @Test fun testAPlaceWithoutPassesLandsInEarlier() {
        val never = PlaceListItem(Place(UUID.randomUUID(), "ucfv0j1", 45.0, 39.0, "Новое", now),
            PlaceStats(0, isFrequentGuest = false, directions = emptyList()))
        val groups = groups(listOf(item("Дом", 9, 0), never), PlacesSort.recent)
        assertEquals(listOf(Kind.today, Kind.earlier), groups.map { it.kind })
        assertEquals(listOf("Новое"), groups[1].items.map { it.place.name })
    }
    @Test fun testByNameHasNoGroups() {
        assertEquals(listOf(Kind.all), groups(listOf(item("Дом", 9, 0, true), item("Дача", 1, 40)), PlacesSort.byName).map { it.kind })
    }
    @Test fun testFrequentKeepsItsTwoGroups() {
        assertEquals(listOf(Kind.frequent, Kind.others), groups(listOf(item("Дом", 9, 0, true), item("Дача", 1, 40))).map { it.kind })
    }
    @Test fun testSearchIgnoresCaseAndDiacritics() {
        val items = listOf(item("Дача", 3, 1), item("Работа", 9, 2))
        assertEquals(1, PlacesPresentation.filter(items, "дач", ru).size)
        assertEquals(2, PlacesPresentation.filter(items, "  ", ru).size)
        assertEquals(1, PlacesPresentation.filter(listOf(item("Repère", 3, 1)), "REPERE", Locale.FRENCH).size)
        assertEquals(1, PlacesPresentation.filter(listOf(item("Straße", 3, 1)), "STRASSE", Locale.ENGLISH).size)
        assertTrue(PlacesPresentation.filter(listOf(item("ı", 3, 1)), "i", Locale.ENGLISH).isEmpty())
    }
    @Test fun testUnnamedPlaceIsNotFoundByText() {
        val items = listOf(item(null, 2, 1))
        assertEquals(1, PlacesPresentation.filter(items, "", ru).size)
        assertTrue(PlacesPresentation.filter(items, "мест", ru).isEmpty())
    }
    @Test fun searchTrimsTheFoundationWhitespaceSet() {
        val items = listOf(item("Дача", 3, 1), item(null, 2, 1))
        assertEquals(2, PlacesPresentation.filter(items, "\u200b", ru).size)
        assertEquals(1, PlacesPresentation.filter(items, "\u200bдач\u0085", ru).size)
        assertTrue(PlacesPresentation.filter(items, "\u001c", ru).isEmpty())
    }
    @Test fun testRecentIsTheOldOrderUntouched() {
        val items = listOf(item("A", 1, 9), item("B", 99, 1))
        assertEquals(PlaceListItem.sorted(items).map { it.place.name }, PlacesPresentation.sort(items, PlacesSort.recent).map { it.place.name })
    }
    @Test fun testFrequentOrdersByPassCount() {
        assertEquals(listOf("B", "A"), PlacesPresentation.sort(listOf(item("A", 3, 1), item("B", 40, 9)), PlacesSort.frequent).map { it.place.name })
    }
    @Test fun testNameOrderPutsUnnamedLast() {
        assertEquals(listOf("Дача", "—"), PlacesPresentation.sort(listOf(item(null, 5, 1), item("Дача", 2, 5)), PlacesSort.byName, ru)
            .map { it.place.name ?: "—" })
    }
    @Test fun testTiesKeepTheRecentOrder() {
        assertEquals(listOf("A", "B"), PlacesPresentation.sort(listOf(item("A", 5, 1), item("B", 5, 4)), PlacesSort.frequent).map { it.place.name })
    }
    @Test fun testGroupsSplitFrequentGuestsFromTheRest() {
        assertEquals(listOf(Kind.frequent, Kind.others), groups(listOf(item("A", 40, 1, true), item("B", 2, 2))).map { it.kind })
    }
    @Test fun testGroupingCollapsesWhenEverythingIsFrequent() {
        assertEquals(listOf(Kind.all), groups(listOf(item("A", 40, 1, true), item("B", 30, 2, true))).map { it.kind })
    }
    @Test fun testSearchDoesNotChangeTheShapeOfTheList() {
        val items = (0 until PlacesPresentation.manyPlaces).map { item("Место $it", 40 - it, it, it < 2) } + item("Дача", 1, 30)
        assertEquals(listOf(Kind.today, Kind.thisWeek, Kind.earlier), build(items, "", PlacesSort.recent).map { it.kind })
        assertEquals(listOf(Kind.frequent, Kind.others), build(items, "", PlacesSort.frequent).map { it.kind })
        val searched = build(items, "Дача", PlacesSort.recent)
        assertEquals(listOf(Kind.all), searched.map { it.kind })
        assertEquals(listOf(Kind.all), build(items, "Дача", PlacesSort.frequent).map { it.kind })
        assertEquals(1, searched.first().items.size)
    }
    @Test fun testEmptySearchResultProducesNoGroups() {
        assertTrue(build(listOf(item("Дача", 2, 1)), "zzz", PlacesSort.recent).isEmpty())
    }
    @Test fun testSortSurvivesRelaunchAndRejectsGarbage() {
        // Persistence belongs to settings; this is the unchanged key/raw-value boundary.
        val defaults = mutableMapOf<String, String>()
        assertEquals("places.sort", PlacesSort.defaultsKey)
        assertEquals(PlacesSort.recent, PlacesSort.load(defaults[PlacesSort.defaultsKey]))
        defaults[PlacesSort.defaultsKey] = PlacesSort.frequent.rawValue
        assertEquals(PlacesSort.frequent, PlacesSort.load(defaults[PlacesSort.defaultsKey]))
        defaults[PlacesSort.defaultsKey] = "nonsense"
        assertEquals(PlacesSort.recent, PlacesSort.load(defaults[PlacesSort.defaultsKey]))
        assertEquals("name", PlacesSort.byName.rawValue)
    }
    // iOS: PlacesScreenStateTests.swift (testSearchPathNeverGroups, pure filter/sort part).
    @Test fun testSearchPathNeverGroups() {
        val day = Instant.ofEpochSecond(1_700_000_000)
        val items = (0 until 10).map { index ->
            PlaceListItem(Place(UUID.randomUUID(), "ucfv0j$index", 45.0, 39.0, "Дача $index", day),
                PlaceStats(index, day, day, 600.0, false, emptyList()), 600.0)
        }
        assertEquals(1, PlacesPresentation.filter(items, "Дача 3", ru).size)
        assertTrue(PlacesPresentation.filter(items, "Море", ru).isEmpty())
    }
}

// iOS: PlacesPresentationTests.swift (PlaceScreenTests)
class PlaceScreenTests {
    private val zone = ZoneId.of("Europe/Moscow")
    private fun pass(iso: String) = PlacePass(placeId = UUID.randomUUID(), tripId = UUID.randomUUID(),
        timestamp = java.time.OffsetDateTime.parse(iso).toInstant(), elapsedFromStart = 600.0, distanceFromStart = 1000.0, course = 90.0)
    @Test fun testTwoPassesInOneDayCollapseIntoOneTile() {
        val tiles = PlaceScreen.tiles(listOf(pass("2026-09-13T10:00:00+03:00"), pass("2026-09-13T19:00:00+03:00"),
            pass("2026-09-20T10:00:00+03:00")), zone)
        assertEquals(2, tiles.size)
        assertEquals(1, tiles.first().count)
        assertEquals(2, tiles.last().count)
    }
    @Test fun testTilesAreCappedAndOrderedNewestFirst() {
        val tiles = PlaceScreen.tiles((1..9).map { pass("2026-09-${it.toString().padStart(2, '0')}T10:00:00+03:00") }, zone)
        assertEquals(PlaceScreen.dateTiles, tiles.size)
        assertEquals(tiles.sortedByDescending { it.day }, tiles)
    }
    @Test fun testEmptyPassesGiveNoTiles() { assertTrue(PlaceScreen.tiles(emptyList(), zone).isEmpty()) }
    @Test fun testRouteJoinsTwoDifferentNames() { assertEquals("Краснодар → Горячий Ключ", PlaceScreen.route("Краснодар", "Горячий Ключ")) }
    @Test fun testRouteCollapsesWhenBothEndsAreTheSameCity() { assertEquals("Краснодар", PlaceScreen.route("Краснодар", "Краснодар")) }
    @Test fun testRouteFallsBackToTheKnownEnd() {
        assertEquals("Сочи", PlaceScreen.route(null, "Сочи"))
        assertEquals("Сочи", PlaceScreen.route("Сочи", "  "))
    }
    @Test fun testRouteIsNilWithoutAnyName() {
        assertNull(PlaceScreen.route(null, null))
        assertNull(PlaceScreen.route("", "   "))
    }
    @Test fun routeTrimsSpacesAndPreservesNewlinesLikeFoundation() {
        assertEquals("Сочи", PlaceScreen.route("\u200bСочи\u200b", null))
        assertEquals("\nСочи\n", PlaceScreen.route("\nСочи\n", null))
        assertEquals("\u000cСочи\u000c", PlaceScreen.route("\u000cСочи\u000c", null))
    }
    @Test fun testEndpointsNeedTwoPoints() {
        assertNull(PlaceScreen.endpoints(emptyList()))
        assertNull(PlaceScreen.endpoints(listOf(GeoPoint(45.0, 39.0))))
        val ends = assertNotNull(PlaceScreen.endpoints(listOf(GeoPoint(45.0, 39.0), GeoPoint(44.0, 38.0))))
        assertEquals(45.0, ends.first.latitude)
        assertEquals(44.0, ends.second.latitude)
    }
}
