package com.onezee.triptrack.map.label

import com.onezee.triptrack.map.MapLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLabelsTests {
    @Test
    fun cyrillicReadersGetTheirNameThenTheLocalOne() {
        assertEquals(listOf("name:ru", "name"), MapLabels.fields("ru"))
        assertEquals(listOf("name:uk", "name"), MapLabels.fields("uk"))
        assertEquals(listOf("name:kk", "name"), MapLabels.fields("kk"))
    }

    @Test
    fun latinReadersGetTheLatinSpellingBeforeTheLocalScript() {
        assertEquals(listOf("name:en", "name:latin", "name"), MapLabels.fields("en"))
        assertEquals(listOf("name:de", "name:latin", "name"), MapLabels.fields("de"))
    }

    @Test
    fun legacyLanguageCodesMapToTheTileKeys() {
        assertEquals("name:id", MapLabels.fields("in").first())
        assertEquals("name:fil", MapLabels.fields("tl").first())
    }

    @Test
    fun onlyNameLabelsAreRewritten() {
        // OpenFreeMap's street label, the doubled name of the bug.
        val street = """["case",["has","name:nonlatin"],["concat",["get","name:latin"]," ",["get","name:nonlatin"]],["coalesce",["get","name_en"],["get","name"]]]"""
        assertTrue(MapLabels.printsName(street))
        assertFalse(MapLabels.printsName("""["to-string",["get","ref"]]"""))
    }

    @Test
    fun theStylesNameLabelsAreKnownToTheSnapshotter() {
        for (id in listOf("highway-name-minor", "label_city", "label_other", "water_name_point_label")) {
            assertTrue(id, id in MapLabels.styleLabelLayers)
        }
    }
}
