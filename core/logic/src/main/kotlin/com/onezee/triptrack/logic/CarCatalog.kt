// iOS: CarCatalog.swift, VehicleCatalogSelection.swift
package com.onezee.triptrack.logic

import com.onezee.triptrack.model.CarCatalog.Document
import com.onezee.triptrack.model.CarCatalog.Generation
import com.onezee.triptrack.model.CarCatalog.ModelHit
import com.onezee.triptrack.model.CarCatalog.Variant
import com.onezee.triptrack.model.VehicleCatalogSelection
import java.text.Normalizer
import java.time.Year
import java.util.Locale
import kotlinx.serialization.json.Json

// iOS: CarCatalog.swift. The owner calls this on its worker dispatcher; loading is lazy.
class CarCatalog(document: Document? = null, private val currentYear: () -> Int = { Year.now().value }) {
    private val loaded: Document? by lazy {
        document ?: CarCatalog::class.java.getResourceAsStream("/CarCatalog.json")?.use {
            decode(it.readBytes().decodeToString())
        }
    }

    fun search(query: String): List<ModelHit> {
        val tokens = fold(query).split(Regex("(?U)\\s+")).filter(String::isNotEmpty)
        val current = currentYear()
        val year = tokens.firstOrNull { it.length == 4 && it.toIntOrNull() in 1990..current }?.toInt()
        val words = tokens.filter { it != year?.toString() }
        return loaded?.makes.orEmpty().flatMap { make ->
            make.models.mapNotNull { model ->
                val names = listOf(make.name, make.nameRu.orEmpty(), model.name) + make.aliases.orEmpty() + model.aliases.orEmpty()
                val haystack = fold(names.joinToString(" "))
                if (!words.all(haystack::contains)) return@mapNotNull null
                if (year != null && model.generations.none { covers(year, it.yearStart, it.yearEnd) }) return@mapNotNull null
                ModelHit(make, model, model.generations.mapNotNull { it.yearStart }.minOrNull(),
                    model.generations.maxOfOrNull { it.yearEnd ?: current }, year)
            }
        }.sortedWith(compareByDescending<ModelHit> { fold(it.model.name) in words }
            .thenBy { it.make.name + " " + it.model.name })
    }

    fun generations(modelId: String, year: Int? = null): List<Generation> {
        val all = loaded?.makes.orEmpty().flatMap { it.models }.firstOrNull { it.id == modelId }?.generations.orEmpty()
        return if (year == null) all else all.filter { covers(year, it.yearStart, it.yearEnd) } +
            all.filterNot { covers(year, it.yearStart, it.yearEnd) }
    }

    fun variants(generationId: String, year: Int? = null): List<Variant> {
        val all = loaded?.makes.orEmpty().flatMap { it.models }.flatMap { it.generations }
            .firstOrNull { it.id == generationId }?.variants.orEmpty()
        return if (year == null) all else all.filter { covers(year, it.yearStart, it.yearEnd) }
    }

    fun resolve(modelId: String?, generationId: String?, variantId: String?): Variant? {
        if (modelId == null || generationId == null || variantId == null) return null
        return generations(modelId).firstOrNull { it.id == generationId }?.variants?.firstOrNull { it.id == variantId }
    }

    companion object {
        fun decode(text: String): Document = Json.decodeFromString<Document>(text).also {
            require(it.schema == 2) { "unsupportedSchema" }
        }
        private fun fold(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('ё', 'е')
        private fun covers(year: Int, start: Int?, end: Int?) = year >= (start ?: Int.MIN_VALUE) && year <= (end ?: Int.MAX_VALUE)
    }
}

// iOS: VehicleCatalog.swift (defaultBody). IDs and the fallback must match the existing silhouettes.
fun catalogBody(body: String?, type: String): String {
    if (type != "car") return when (type) { "moto" -> "motorcycle"; "moped" -> "scooter"; "bicycle" -> "bicycle"; else -> "car" }
    return when (body) {
        "sedan", "liftback" -> "car"
        "wagon" -> "hatchback"
        "suv" -> "crossover"
        else -> body?.takeIf { it in setOf("car", "hatchback", "crossover", "pickup", "van", "convertible", "sports") } ?: "car"
    }
}

fun catalogSelection(hit: ModelHit, generation: Generation, year: Int, variant: Variant?, type: String) =
    VehicleCatalogSelection(hit.make.name, hit.model.name, catalogBody(generation.bodies?.firstOrNull(), type), year,
        hit.model.id, generation.id, variant?.id)

fun VehicleCatalogSelection.clearedCatalogIds() = copy(catalogModelId = "", catalogGenerationId = "", catalogVariantId = "")
