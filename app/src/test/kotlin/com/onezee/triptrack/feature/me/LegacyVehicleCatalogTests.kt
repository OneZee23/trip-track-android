// iOS: VehicleCatalogTests.swift. The same bundle invariants, transport types, queries and expected names.
package com.onezee.triptrack.feature.me

import com.onezee.triptrack.feature.garage.*
import com.onezee.triptrack.model.VehicleCatalog
import com.onezee.triptrack.model.VehicleType
import org.junit.Test
import kotlin.test.*

class LegacyVehicleCatalogTests {
    private val catalog = LegacyVehicleCatalog()
    @Test fun catalogLoadsFromTheBundle() { assertTrue(catalog.makes.size > 50) }
    @Test fun everyBodyIsAKnownSilhouette() {
        catalog.makes.flatMap { it.models }.forEach { model -> assertTrue(model.bodies.isNotEmpty()); model.bodies.forEach { assertTrue(it in VehicleAvatar.styles) } }
    }
    @Test fun noDuplicateMakesOrModels() {
        assertEquals(catalog.makes.size, catalog.makes.map { it.name }.distinct().size)
        catalog.makes.forEach { assertEquals(it.models.size, it.models.map { model -> model.name }.distinct().size) }
    }
    @Test fun namesAreTrimmedAndNonEmpty() {
        (catalog.makes.map { it.name } + catalog.makes.flatMap { it.models }.map { it.name }).forEach { assertTrue(it.isNotEmpty()); assertEquals(it.trim(), it) }
    }
    @Test fun bicycleTypeNeverOffersCars() {
        val makes = catalog.makes(VehicleType.bicycle)
        assertTrue(makes.isNotEmpty()); assertFalse(makes.any { it.name == "Toyota" })
        makes.flatMap { it.models }.forEach { assertTrue("bicycle" in it.bodies) }
    }
    @Test fun aMakeSpanningTwoWorldsKeepsOnlyTheRightHalf() {
        val honda = catalog.makes(VehicleType.moto).first { it.name == "Honda" }
        assertTrue(honda.models.any { it.name == "CB400" }); assertFalse(honda.models.any { it.name == "Civic" })
    }
    @Test fun scooterAndMotorcycleDoNotMix() {
        val makes = catalog.makes(VehicleType.moped)
        assertTrue(makes.any { it.name == "Vespa" }); makes.flatMap { it.models }.forEach { assertTrue("scooter" in it.bodies) }
    }
    @Test fun searchFindsAModelWithoutKnowingItsMake() {
        val hits = catalog.search("solaris", VehicleType.car)
        assertEquals(1, hits.size); assertEquals("Hyundai", hits.single().name); assertEquals(listOf("Solaris"), hits.single().models.map { it.name })
    }
    @Test fun searchByMakeKeepsAllItsModels() {
        val hits = catalog.search("lada", VehicleType.car)
        assertEquals(1, hits.size); assertEquals("Lada", hits.single().name); assertTrue(hits.single().models.size > 5)
    }
    @Test fun searchIsFoldedWithoutALocaleOnPurpose() {
        assertEquals("Infiniti", catalog.search("INFINITI", VehicleType.car).first().name)
        assertEquals("Infiniti", catalog.search("infiniti", VehicleType.car).first().name)
    }
    @Test fun searchIgnoresDiacriticsBecausePeopleTypeBothWays() {
        assertEquals("Skoda", catalog.search("Škoda", VehicleType.car).first().name)
        assertEquals("Citroen", catalog.search("Citroën", VehicleType.car).first().name)
    }
    @Test fun emptyQueryReturnsTheWholeListForThatType() { assertEquals(catalog.makes(VehicleType.car).size, catalog.search("  ", VehicleType.car).size) }
    @Test fun searchMissReturnsNothingRatherThanEverything() { assertTrue(catalog.search("щщщ", VehicleType.car).isEmpty()) }
    private fun names(query: String) = (catalog.search(query, VehicleType.car) + catalog.search(query, VehicleType.moto)).map { it.name }
    @Test fun theQueriesThatUsedToFindNothing() {
        listOf("уаз" to "UAZ", "лада" to "Lada", "ваз" to "Lada", "GAZ" to "GAZ", "Moskvich" to "Moskvich", "Иж" to "IZh", "Урал" to "Ural").forEach { (query, expected) -> assertTrue(expected in names(query), query) }
    }
    @Test fun cyrillicTypingFindsLatinMakes() {
        listOf("тойота" to "Toyota", "шкода" to "Skoda", "мерседес" to "Mercedes-Benz", "пежо" to "Peugeot", "хендай" to "Hyundai", "ямаха" to "Yamaha").forEach { (query, expected) -> assertTrue(expected in names(query), query) }
    }
    @Test fun folkNamesFindTheModel() {
        val buhanka = catalog.search("буханка", VehicleType.car)
        assertEquals("UAZ", buhanka.first().name); assertEquals(listOf("3909"), buhanka.first().models.map { it.name })
        val niva = catalog.search("нива", VehicleType.car)
        assertEquals("Lada", niva.first().name); assertTrue(niva.first().models.size >= 2)
    }
    @Test fun canonicalNamesAreLatinOnly() {
        (catalog.makes.map { it.name } + catalog.makes.flatMap { it.models }.map { it.name }).forEach { name -> assertFalse(name.any { it.code in 0x0400..0x04FF }, name) }
    }
    @Test fun everyAliasFindsItsOwnMake() {
        catalog.makes.forEach { make -> (make.aliases.orEmpty() + make.models.flatMap { it.aliases.orEmpty() }).forEach { alias ->
            assertTrue(VehicleType.entries.flatMap { catalog.search(alias, it) }.any { it.name == make.name }, alias)
        } }
    }
    @Test fun aliasesDoNotLeakIntoTheVisibleName() { assertTrue("Тойота" in catalog.makes.first { it.name == "Toyota" }.aliases.orEmpty()) }
    @Test fun choosingAModelPreselectsItsSilhouette() {
        val polo = catalog.makes.first { it.name == "Volkswagen" }.models.first { it.name == "Polo" }
        assertEquals("car", catalog.defaultBody(polo, VehicleType.car))
    }
    @Test fun aSilhouetteForeignToTheTypeIsNotHandedOut() { assertEquals("motorcycle", catalog.defaultBody(VehicleCatalog.Model("Hilux", listOf("pickup")), VehicleType.moto)) }
}

class CatalogYearRangeTests {
    @Test fun openEndedGenerationsContinueThroughTheCurrentYear() {
        assertEquals("2018–2026", catalogYearRange(2018, null, 2026))
        assertEquals("2018–2022", catalogYearRange(2018, 2022, 2026))
        assertEquals("", catalogYearRange(null, null, 2026))
    }
}
