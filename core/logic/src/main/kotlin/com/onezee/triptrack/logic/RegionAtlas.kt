package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoBounds
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.RegionAtlas.City
import com.onezee.triptrack.model.RegionAtlas.MapCountry
import com.onezee.triptrack.model.RegionAtlas.Region
import java.io.IOException
import kotlin.math.floor
import kotlinx.serialization.json.*

// iOS: RegionAtlas.swift. Immutable after parsing; callers may load it off their UI thread.
class RegionAtlas
private constructor(
    val regions: List<Region>,
    val citiesByRegion: Map<String, List<City>>,
    val countryNames: Map<String, Pair<String, String>>,
    val countries: List<MapCountry>,
    private val grid: Map<Int, List<Int>>,
    internal val regionIndexById: Map<String, Int>,
    private val enclaves: List<List<Int>>,
    val isLoaded: Boolean,
) {
    fun region(id: String): Region? = regionIndexById[id]?.let { regions[it] }

    fun region(containing: GeoPoint): Region? = regionIndex(containing)?.let { regions[it] }

    fun regionIndex(containing: GeoPoint): Int? =
        grid[cellKey(containing.latitude, containing.longitude)]?.firstOrNull { index ->
            val region = regions[index]
            region.bounds.contains(containing) &&
                contains(containing.latitude, containing.longitude, region.rings)
        }

    fun regionAtIndex(index: Int, coordinate: GeoPoint): Boolean {
        val region = regions.getOrNull(index) ?: return false
        if (
            !region.bounds.contains(coordinate) ||
                !contains(coordinate.latitude, coordinate.longitude, region.rings)
        )
            return false
        // A previous point's wrapper must never swallow a smaller overlapping region.
        return enclaves[index].none { other ->
            val smaller = regions[other]
            smaller.bounds.contains(coordinate) &&
                contains(coordinate.latitude, coordinate.longitude, smaller.rings)
        }
    }

    fun cities(regionId: String): List<City> = citiesByRegion[regionId].orEmpty()

    fun countryName(code: String, language: String): String? =
        countryNames[code]?.let { if (language == "ru") it.first else it.second }

    companion object {
        val shared: RegionAtlas by lazy { loadBundle() ?: empty }
        val empty =
            RegionAtlas(
                emptyList(),
                emptyMap(),
                emptyMap(),
                emptyList(),
                emptyMap(),
                emptyMap(),
                emptyList(),
                false,
            )

        fun loadBundle(): RegionAtlas? =
            try {
                RegionAtlas::class.java.getResourceAsStream("/MapRegions.json")?.use {
                    parse(it.readBytes())
                }
            } catch (_: IOException) {
                null
            }

        fun flag(countryCode: String): String = buildString {
            countryCode.uppercase(java.util.Locale.ROOT).codePoints().forEach { scalar ->
                val composed = 127_397 + scalar
                if (Character.isValidCodePoint(composed) && composed !in 0xD800..0xDFFF)
                    appendCodePoint(composed)
            }
        }

        fun contains(lat: Double, lon: Double, rings: List<List<Double>>): Boolean {
            var inside = false
            for (ring in rings) {
                val count = ring.size / 2
                if (count <= 2) continue
                var j = count - 1
                for (i in 0 until count) {
                    val yi = ring[2 * i]
                    val xi = ring[2 * i + 1]
                    val yj = ring[2 * j]
                    val xj = ring[2 * j + 1]
                    if ((yi > lat) != (yj > lat)) {
                        val crossing = (xj - xi) * (lat - yi) / (yj - yi) + xi
                        if (lon < crossing) inside = !inside
                    }
                    j = i
                }
            }
            return inside
        }

        private fun cellKey(lat: Double, lon: Double): Int =
            (floor(lat / 2).toInt() + 90) * 1000 + floor(lon / 2).toInt() + 180

        fun parse(data: ByteArray): RegionAtlas? =
            try {
                val payload = Json.parseToJsonElement(data.decodeToString()).jsonObject
                val regions = mutableListOf<Region>()
                val grid = mutableMapOf<Int, MutableList<Int>>()
                val byId = mutableMapOf<String, Int>()
                for (value in payload.getValue("regions").jsonArray) {
                    val raw = value.jsonObject
                    val id = raw.string("id")
                    val cc = raw.string("cc")
                    val ru = raw.optionalString("ru")
                    val en = raw.optionalString("en")
                    val c = raw.doubles("c")
                    val b = raw.doubles("b")
                    val rings =
                        raw.getValue("r").jsonArray.map { ring ->
                            ring.jsonArray.map { it.jsonPrimitive.double }
                        }
                    val names =
                        raw["l"]
                            ?.takeUnless { it is JsonNull }
                            ?.jsonObject
                            ?.mapValues { it.value.jsonPrimitive.content }
                            .orEmpty()
                    if (
                        b.size != 4 ||
                            c.size != 2 ||
                            ru == null ||
                            en == null ||
                            ru.isEmpty() ||
                            id in byId
                    )
                        continue
                    val index = regions.size
                    regions +=
                        Region(
                            id,
                            cc,
                            ru,
                            en,
                            GeoPoint(c[0], c[1]),
                            GeoBounds(b[0], b[2], b[1], b[3]),
                            rings,
                            names,
                        )
                    byId[id] = index
                    var lat = floor(b[0] / 2) * 2
                    while (lat <= b[2]) {
                        var lon = floor(b[1] / 2) * 2
                        while (lon <= b[3]) {
                            grid.getOrPut(cellKey(lat, lon)) { mutableListOf() }.add(index)
                            lon += 2
                        }
                        lat += 2
                    }
                }
                for (bucket in grid.values) bucket.sortBy { regions[it].bounds.area }
                // Only regions sharing a grid bucket can compete for the same point.
                val nested = List(regions.size) { mutableSetOf<Int>() }
                for (bucket in grid.values) for (outer in bucket) for (inner in bucket) {
                    val big = regions[outer].bounds
                    val small = regions[inner].bounds
                    if (inner != outer && small.area < big.area && small.intersects(big))
                        nested[outer].add(inner)
                }
                val cities = mutableMapOf<String, MutableList<City>>()
                for (value in payload.getValue("cities").jsonArray) {
                    val raw = value.jsonObject
                    val name = raw.string("n")
                    val en = raw.optionalString("e") ?: name
                    val regionId = raw.string("r")
                    val c = raw.doubles("c")
                    val population = raw.getValue("p").jsonPrimitive.int
                    if (c.size != 2) continue
                    cities
                        .getOrPut(regionId) { mutableListOf() }
                        .add(City(name, en, regionId, GeoPoint(c[0], c[1]), population))
                }
                cities.values.forEach { it.sortByDescending { city -> city.population } }
                val countries = mutableListOf<MapCountry>()
                val countryNames = mutableMapOf<String, Pair<String, String>>()
                for (value in payload.getValue("countries").jsonArray) {
                    val raw = value.jsonObject
                    val id = raw.string("id")
                    val ru = raw.string("ru")
                    val en = raw.string("en")
                    val c = raw.doubles("c")
                    val b = raw.doubles("b")
                    countryNames[id] = ru to en
                    if (c.size == 2 && b.size == 4)
                        countries +=
                            MapCountry(
                                id,
                                ru,
                                en,
                                GeoPoint(c[0], c[1]),
                                GeoBounds(b[0], b[2], b[1], b[3]),
                            )
                }
                RegionAtlas(
                    regions,
                    cities,
                    countryNames,
                    countries,
                    grid,
                    byId,
                    nested.map { it.toList() },
                    true,
                )
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: NoSuchElementException) {
                null
            }
    }
}

private fun JsonObject.string(key: String): String =
    getValue(key).jsonPrimitive.also { require(it.isString) }.content

private fun JsonObject.optionalString(key: String): String? =
    get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.also { require(it.isString) }?.content

private fun JsonObject.doubles(key: String): List<Double> =
    getValue(key).jsonArray.map { it.jsonPrimitive.double }

// iOS: RegionAtlas.swift
fun Region.localizedName(language: String): String =
    when (language) {
        "ru" -> nameRu
        "en" -> nameEn
        else -> localNames[language] ?: nameEn
    }

fun MapCountry.localizedName(language: String): String = if (language == "ru") nameRu else nameEn

fun City.localizedName(language: String): String = if (language == "ru") name else nameEn

val City.radiusMeters: Double
    get() =
        when {
            population >= 1_000_000 -> 20_000.0
            population >= 250_000 -> 12_000.0
            population >= 50_000 -> 8_000.0
            else -> 5_000.0
        }
