package com.onezee.triptrack.map.style

import com.onezee.triptrack.map.MapLabels
import com.onezee.triptrack.map.MapStyles
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two styles drawn after the iPhone's Apple Maps (tools/mapstyle/build_styles.py). The generator writes the assets;
 * these tests read what ships.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapStyleAssetsTests {
    private fun style(uri: String): JSONObject = JSONObject(File("src/main/assets/" + uri.removePrefix("asset://")).readText())

    private fun layers(style: JSONObject): List<JSONObject> = style.getJSONArray("layers").let { array -> (0 until array.length()).map(array::getJSONObject) }

    @Test
    fun bothStylesShipAndReadTheOpenFreeMapTiles() {
        for (uri in listOf(MapStyles.LIGHT, MapStyles.DARK)) {
            val style = style(uri)
            assertEquals(uri, "https://tiles.openfreemap.org/planet", style.getJSONObject("sources").getJSONObject("openmaptiles").getString("url"))
            assertTrue(uri, style.getString("glyphs").startsWith("https://tiles.openfreemap.org/fonts/"))
        }
        assertEquals(MapStyles.DARK, MapStyles.of(dark = true))
        assertEquals(MapStyles.LIGHT, MapStyles.of(dark = false))
    }

    /** iOS: `pointOfInterestFilter = .excludingAll` and a flat standard map: no POIs, no hill shading, no 3D buildings. */
    @Test
    fun noPointsOfInterestReliefOrExtrusions() {
        for (uri in listOf(MapStyles.LIGHT, MapStyles.DARK)) {
            val style = style(uri)
            assertFalse(uri, style.getJSONObject("sources").has("ne2_shaded"))
            for (layer in layers(style)) {
                val id = layer.getString("id")
                assertFalse(id, id.startsWith("poi") || id == "airport")
                assertFalse(id, layer.getString("type") in setOf("raster", "fill-extrusion"))
            }
        }
    }

    /** Apple's sea is deeper than its lakes; one water colour made the Black Sea a lake. */
    @Test
    fun theSeaHasItsOwnColour() {
        for (uri in listOf(MapStyles.LIGHT, MapStyles.DARK)) {
            val water = layers(style(uri)).single { it.getString("id") == "water" }
            val fill = water.getJSONObject("paint").get("fill-color") as JSONArray
            assertEquals(uri, "match", fill.getString(0))
            assertEquals(uri, "ocean", fill.getString(2))
        }
    }

    /** The snapshotter rewrites the labels it is told about; a name label it does not know stays in English. */
    @Test
    fun everyNameLabelIsKnownToMapLabels() {
        for (uri in listOf(MapStyles.LIGHT, MapStyles.DARK)) {
            for (layer in layers(style(uri)).filter { it.getString("type") == "symbol" }) {
                val text = layer.optJSONObject("layout")?.opt("text-field")?.toString() ?: continue
                val id = layer.getString("id")
                if (MapLabels.printsName(text)) assertTrue("$uri $id", id in MapLabels.styleLabelLayers)
            }
        }
    }

    /** The far land takes Apple's mean of forest and open land, the near land its open colour (dark, sampled 4 Nov 2026). */
    @Test
    fun theDarkLandIsAppleTeal() {
        val background = layers(style(MapStyles.DARK)).single { it.getString("id") == "background" }
        val colour = background.getJSONObject("paint").get("background-color").toString()
        assertTrue(colour, "#3f6d69" in colour && "#4d7266" in colour)
    }
}
