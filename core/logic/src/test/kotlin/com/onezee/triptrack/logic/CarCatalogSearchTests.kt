// iOS: CarCatalogSearchTests.swift, GarageDataTests.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.CarCatalog.Document
import org.junit.Test
import kotlin.test.*

class CarCatalogSearchTests {
    private fun catalog() = CarCatalog(currentYear = { 2026 })
    @Test fun poloIsFirstAndAliasesCombineAcrossMakeAndModel() {
        val catalog = catalog()
        for (query in listOf("polo", "  PoLo  ", "фольксваген поло седан", "ФОЛЬКСВАГЕН   ПОЛО")) {
            val hit = catalog.search(query).first()
            assertEquals("volkswagen-polo", hit.model.id, query)
            assertEquals("Volkswagen", hit.make.name)
        }
        assertTrue(catalog.search("nonexistent-model-xyz").isEmpty())
    }
    @Test fun yearFiltersModelsAndPrioritizesGeneration() {
        val catalog = catalog()
        val hit = catalog.search("поло 2018").first()
        assertEquals("volkswagen-polo", hit.model.id)
        assertEquals(2018, hit.matchedYear)
        assertEquals(hit.model.generations.mapNotNull { it.yearStart }.minOrNull(), hit.yearStart)
        assertEquals(hit.model.generations.maxOf { it.yearEnd ?: 2026 }, hit.yearEnd)
        val generations = catalog.generations(hit.model.id, hit.matchedYear)
        assertEquals("volkswagen-polo-sedan-2015", generations.first().id)
        assertEquals(hit.model.generations.size, generations.size)
        assertTrue(catalog.search("polo 1990").isEmpty())
    }
    @Test fun yoWhitespaceAndOpenEndedSpan() {
        val document = CarCatalog.decode("""{"schema":2,"makes":[{"id":"test","name":"Test","nameRu":"Тёст","aliases":[],"models":[{"id":"test-model","name":"Model","aliases":["Модель"],"generations":[{"id":"test-generation","name":"First","yearStart":2000,"variants":[]}]}]}]}""")
        val catalog = CarCatalog(document) { 2026 }
        val hit = catalog.search("  ТЕСТ  модель  2026").first()
        assertEquals("test-model", hit.model.id)
        assertEquals(2026, hit.matchedYear)
        assertEquals(2026, hit.yearEnd)
        assertEquals(1, catalog.search("тёст").size)
    }
    @Test fun variantsOwnYearsAndUndatedVariants() {
        val catalog = catalog()
        val generation = "volkswagen-polo-sedan-2015"
        val in2018 = catalog.variants(generation, 2018).map { it.id }
        assertTrue("$generation-16-110-at6" in in2018)
        assertFalse("$generation-16-105-at6" in in2018)
        val in2015 = catalog.variants(generation, 2015).map { it.id }
        assertTrue("$generation-16-105-at6" in in2015)
        assertFalse("$generation-14t-125-dct7" in in2015)
        assertEquals(catalog.variants("volkswagen-polo-liftback-2020"), catalog.variants("volkswagen-polo-liftback-2020", 2021))
    }
    @Test fun selectionRoundTripsAndExplicitlyClearsIds() {
        val catalog = catalog()
        val hit = catalog.search("polo 2018").first()
        val generation = catalog.generations(hit.model.id, 2018).first()
        val variant = catalog.variants(generation.id, 2018).first { it.id.endsWith("-16-110-at6") }
        val selection = catalogSelection(hit, generation, 2018, variant, "car")
        assertEquals("Volkswagen", selection.make)
        assertEquals("Polo", selection.model)
        assertEquals(2018, selection.year)
        assertEquals("car", selection.body)
        assertEquals(variant, catalog.resolve(selection.catalogModelId, selection.catalogGenerationId, selection.catalogVariantId))
        val unknown = catalogSelection(hit, generation, 2018, null, "car")
        assertEquals(hit.model.id, unknown.catalogModelId)
        assertEquals(generation.id, unknown.catalogGenerationId)
        assertNull(unknown.catalogVariantId)
        with(selection.clearedCatalogIds()) {
            assertEquals("", catalogModelId); assertEquals("", catalogGenerationId); assertEquals("", catalogVariantId)
        }
        for ((body, style) in listOf("sedan" to "car", "liftback" to "car", "suv" to "crossover", "wagon" to "hatchback")) assertEquals(style, catalogBody(body, "car"))
        assertEquals("bicycle", catalogBody("sedan", "bicycle"))
        assertNull(catalog.resolve("missing", "missing", "missing"))
    }
    @Test fun bundleIsFullSchemaTwoWithFractionalHorsepower() {
        val document = CarCatalog.decode(javaClass.getResource("/CarCatalog.json")!!.readText())
        assertEquals(19, document.makes.size)
        val models = document.makes.flatMap { it.models }
        assertEquals(50, models.size)
        assertEquals(144, models.sumOf { it.generations.size })
        val variants = models.flatMap { it.generations }.flatMap { it.variants }
        assertEquals(429, variants.size)
        assertTrue(variants.any { it.powerHp == 79.6 })
        assertFailsWith<IllegalArgumentException> { CarCatalog.decode("""{"schema":1,"makes":[]}""") }
    }
}
