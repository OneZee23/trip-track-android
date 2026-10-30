package com.onezee.triptrack.logic

import com.onezee.triptrack.model.GeoPoint
import kotlin.math.*
import kotlin.test.*
import org.junit.Test

// iOS: MyMapAtlasTests.swift (atlas cases), MapRegionsBundleTests.swift, WorldRegionsTests.swift.
class RegionAtlasTests {
    private val atlas
        get() = RegionAtlas.shared

    @Test
    fun testAtlasLoadsRussianSubjects() {
        assertTrue(atlas.isLoaded)
        assertTrue(atlas.regions.count { it.countryCode == "RU" } > 80)
        assertNotNull(atlas.region("RU-KDA"))
    }

    @Test
    fun testBlackSeaCoastResolvesToKrasnodarKrai() {
        for (point in
            listOf(
                GeoPoint(43.585, 39.723),
                GeoPoint(43.430, 39.920),
                GeoPoint(43.680, 40.200),
                GeoPoint(45.035, 38.975),
                GeoPoint(44.561, 38.077),
            )) assertEquals("RU-KDA", atlas.region(point)?.id, point.toString())
    }

    @Test
    fun testNeighbouringRegionsAreDistinct() {
        for ((point, id) in
            listOf(
                GeoPoint(47.222, 39.719) to "RU-ROS",
                GeoPoint(45.045, 41.969) to "RU-STA",
                GeoPoint(59.939, 30.315) to "RU-SPE",
            )) assertEquals(id, atlas.region(point)?.id)
    }

    @Test
    fun testAbroadResolvesToItsOwnCountry() {
        assertEquals("GE", atlas.region(GeoPoint(41.640, 41.640))?.countryCode)
    }

    @Test
    fun testOpenSeaHasNoRegion() {
        assertNull(atlas.region(GeoPoint(43.2, 34.5)))
    }

    @Test
    fun testCitiesAreAttachedToRegions() {
        val names = atlas.cities("RU-KDA").map { it.name }.toSet()
        assertTrue("Краснодар" in names)
        assertTrue("Сочи" in names)
        assertTrue(names.size > 15)
    }

    @Test
    fun testFlagFromCountryCode() {
        assertEquals("🇷🇺", RegionAtlas.flag("RU"))
        assertEquals("🇬🇪", RegionAtlas.flag("GE"))
    }

    private fun bundle() =
        assertNotNull(javaClass.getResourceAsStream("/MapRegions.json")).use { it.readBytes() }

    @Test
    fun testBundleSizeStaysUnderEightAndAHalfMegabytes() {
        assertTrue(bundle().size <= (8.5 * 1024 * 1024).toInt())
    }

    @Test
    fun testEveryRegionHasEnoughVertices() {
        assertTrue(atlas.regions.isNotEmpty())
        val sparse = setOf("MR-NKC", "SO-BN", "QA-US", "BW-JW")
        for (region in atlas.regions) {
            val vertices = region.rings.sumOf { it.size / 2 }
            assertTrue(vertices >= 4, region.id)
            val bounds = region.bounds
            val lat = bounds.maxLat - bounds.minLat
            val lon = bounds.maxLon - bounds.minLon
            if (sqrt(lat * lat + lon * lon) >= 0.25 && region.id !in sparse)
                assertTrue(vertices >= 12, region.id)
        }
    }

    @Test
    fun testFrozenHitVectors() {
        for ((id, point) in
            listOf(
                "RU-KDA" to GeoPoint(45.0355, 38.9753),
                "GE-AJ" to GeoPoint(41.6168, 41.6367),
                "GE-AJ" to GeoPoint(41.82, 41.78),
                "GE-TB" to GeoPoint(41.7151, 44.8271),
            )) assertEquals(id, atlas.region(point)?.id)
    }

    @Test
    fun testRegionIdsAreUnique() {
        assertEquals(atlas.regions.size, atlas.regions.map { it.id }.toSet().size)
    }

    @Test
    fun testParseKeepsFirstRegionOnDuplicateId() {
        val json =
            """{"v":1,"regions":[
          {"id":"XX-DUP","cc":"XX","ru":"Первый","en":"First","c":[10.0,20.0],"b":[9.0,19.0,11.0,21.0],"r":[[9.0,19.0,9.0,21.0,11.0,21.0,11.0,19.0]]},
          {"id":"XX-DUP","cc":"XX","ru":"Второй","en":"Second","c":[30.0,40.0],"b":[29.0,39.0,31.0,41.0],"r":[[29.0,39.0,29.0,41.0,31.0,41.0,31.0,39.0]]}
        ],"countries":[],"cities":[]}"""
        val parsed = assertNotNull(RegionAtlas.parse(json.toByteArray()))
        assertEquals(1, parsed.regions.size)
        assertEquals("Первый", parsed.regions.first().nameRu)
        assertEquals(mapOf("XX-DUP" to 0), parsed.regionIndexById)
    }

    private fun referenceContains(lat: Double, lon: Double, rings: List<List<Double>>): Boolean {
        var inside = false
        for (ring in rings) {
            val vertices = ring.chunked(2).filter { it.size == 2 }
            if (vertices.size <= 2) continue
            for (i in vertices.indices) {
                val a = vertices[i]
                val b = vertices[(i + vertices.size - 1) % vertices.size]
                if (
                    (a[0] > lat) != (b[0] > lat) &&
                        lon < (b[1] - a[1]) * (lat - a[0]) / (b[0] - a[0]) + a[1]
                )
                    inside = !inside
            }
        }
        return inside
    }

    @Test
    fun testContainsMatchesReferenceRayCasting() {
        var state = 42uL
        fun random(lo: Double, hi: Double): Double {
            state = state * 6_364_136_223_846_793_005uL + 1_442_695_040_888_963_407uL
            return lo + (hi - lo) * (state shr 11).toDouble() / 9_007_199_254_740_991.0
        }
        var checked = 0
        for (id in listOf("RU-KDA", "GE-AJ", "GE-TB", "RU-ROS", "RU-STA")) {
            val region = assertNotNull(atlas.region(id))
            val bounds = region.bounds
            fun check(lat: Double, lon: Double) {
                assertEquals(
                    referenceContains(lat, lon, region.rings),
                    RegionAtlas.contains(lat, lon, region.rings),
                    "$id ($lat, $lon)",
                )
                checked++
            }
            for (ring in region.rings) for (i in 0 until ring.size / 2) check(
                ring[i * 2],
                ring[i * 2 + 1],
            )
            val latPad = max(0.01, (bounds.maxLat - bounds.minLat) * 0.1)
            val lonPad = max(0.01, (bounds.maxLon - bounds.minLon) * 0.1)
            repeat(400) {
                check(
                    random(bounds.minLat - latPad, bounds.maxLat + latPad),
                    random(bounds.minLon - lonPad, bounds.maxLon + lonPad),
                )
            }
        }
        assertTrue(checked >= 2_000)
    }

    @Test
    fun testDriversOutsideTheOriginalTwentyGetARegion() {
        val cases =
            listOf(
                "DE" to GeoPoint(48.137, 11.575),
                "ES" to GeoPoint(40.417, -3.704),
                "US" to GeoPoint(34.052, -118.244),
                "FR" to GeoPoint(48.857, 2.352),
                "IT" to GeoPoint(41.903, 12.496),
                "GB" to GeoPoint(51.507, -0.128),
                "CA" to GeoPoint(43.653, -79.383),
                "AU" to GeoPoint(-33.869, 151.209),
                "IN" to GeoPoint(28.614, 77.209),
                "UG" to GeoPoint(0.347, 32.582),
            )
        for ((cc, point) in cases) assertEquals(cc, atlas.region(point)?.countryCode)
    }

    @Test
    fun testOriginalRegionsKeepTheirIds() {
        for ((id, point) in
            listOf(
                "RU-KDA" to GeoPoint(45.0355, 38.9753),
                "RU-AD" to GeoPoint(44.6098, 40.1006),
                "GE-AJ" to GeoPoint(41.82, 41.78),
                "RU-MOW" to GeoPoint(55.7558, 37.6173),
            )) assertEquals(id, atlas.region(point)?.id)
    }

    @Test
    fun testRegionNamesFollowTheAppLanguage() {
        val bavaria = assertNotNull(atlas.region(GeoPoint(48.137, 11.575)))
        assertEquals("Bavaria", bavaria.localizedName("en"))
        assertEquals("Bayern", bavaria.localizedName("de"))
        assertEquals("Бавария", bavaria.localizedName("ru"))
        assertEquals("Bavaria", bavaria.localizedName("kk"))
    }

    @Test
    fun testBundleWithoutLocalNamesStillParses() {
        val json =
            """{"v":2,"regions":[{"id":"XX-A","cc":"XX","ru":"Р","en":"E","c":[1,1],"b":[0,0,2,2],"r":[[0,0,0,2,2,2,2,0]]}],"countries":[],"cities":[]}"""
        assertEquals(
            "E",
            assertNotNull(RegionAtlas.parse(json.toByteArray())).regions.first().localizedName("de"),
        )
    }

    @Test
    fun testParsingTheWorldBundleStaysQuick() {
        val data = bundle()
        var best = Double.POSITIVE_INFINITY
        repeat(3) {
            val start = System.nanoTime()
            assertNotNull(RegionAtlas.parse(data))
            best = min(best, (System.nanoTime() - start) / 1e9)
        }
        assertTrue(best < 3.0, "parse took $best seconds")
    }

    @Test
    fun testLookupStaysFastWithTheWorldBundle() {
        val points =
            (0 until 20_000).flatMap { i ->
                val t = i / 20_000.0
                listOf(GeoPoint(44 + t * 12, 38 + t * 2), GeoPoint(47.5 + t * 6, 7 + t * 7))
            }
        val start = System.nanoTime()
        val hits = points.count { atlas.regionIndex(it) != null }
        val ms = (System.nanoTime() - start) / 1e6
        assertTrue(hits > points.size * 9 / 10)
        assertTrue(ms < 2_000, "lookup took $ms ms")
    }

    @Test
    fun previousPointFastPathHonoursEnclaves() {
        val maykop = GeoPoint(44.6098, 40.1006)
        val krai = assertNotNull(atlas.regionIndexById["RU-KDA"])
        val adygea = assertNotNull(atlas.regionIndexById["RU-AD"])
        assertFalse(atlas.regionAtIndex(krai, maykop))
        assertTrue(atlas.regionAtIndex(adygea, maykop))
        assertEquals(adygea, atlas.regionIndex(maykop))
        assertFalse(atlas.regionAtIndex(-1, maykop))
        assertFalse(atlas.regionAtIndex(atlas.regions.size, maykop))
    }

    @Test
    fun previousPointFastPathAgreesAcrossRegionBorders() {
        var previous: Int? = null
        for (i in 0..1_000) {
            val t = i / 1_000.0
            val point = GeoPoint(45.035 + (44.6098 - 45.035) * t, 38.975 + (40.1006 - 38.975) * t)
            val full = atlas.regionIndex(point)
            val fast =
                previous?.takeIf { atlas.regionAtIndex(it, point) } ?: atlas.regionIndex(point)
            assertEquals(full, fast)
            previous = fast
        }
    }
}
