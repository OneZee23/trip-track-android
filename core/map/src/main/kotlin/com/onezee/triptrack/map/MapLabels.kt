package com.onezee.triptrack.map

import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * iOS: Apple Maps labels the map in one language. OpenFreeMap's styles print a name twice wherever
 * the local script is not Latin, «KUZNECHNAYA STREET КУЗНЕЧНАЯ УЛИЦА» on every street of
 * Krasnodar (`concat(name:latin, name:nonlatin)`). Each label that prints a name is rewritten to
 * the app's language, falling back to the local name; road numbers (`ref`) stay as they are.
 */
object MapLabels {
    /** The labels of the two styles the app draws (liberty, dark), for the snapshotter, which cannot list its layers. */
    val styleLabelLayers = listOf(
        "waterway_line_label", "water_name_point_label", "water_name_line_label", "poi_r20", "poi_r7", "poi_r1",
        "poi_transit", "highway-name-path", "highway-name-minor", "highway-name-major", "airport", "label_other",
        "label_village", "label_town", "label_state", "label_city", "label_city_capital", "label_country_3",
        "label_country_2", "label_country_1",
        "water_name", "highway_name_other", "place_other", "place_suburb", "place_village", "place_town",
        "place_city", "place_city_large", "place_state", "place_country_other", "place_country_minor",
        "place_country_major",
    )

    private val cyrillic = setOf("ru", "uk", "kk")

    /**
     * The name fields to try, in order. Cyrillic readers get their own name, then the local one:
     * a Russian street keeps its Russian name. Latin readers get their own name, then the Latin
     * spelling OpenMapTiles carries for every non-Latin name, then the local one.
     */
    fun fields(language: String): List<String> {
        val code = when (language) { "in" -> "id"; "tl" -> "fil"; else -> language }
        return if (code in cyrillic) listOf("name:$code", "name") else listOf("name:$code", "name:latin", "name")
    }

    fun expression(language: String): Expression =
        Expression.coalesce(*fields(language).map { Expression.get(it) }.toTypedArray())

    /** A label printing a place or street name; `["get","ref"]` is a road number. */
    fun printsName(textField: String): Boolean = "\"name" in textField

    /** Every name label of a loaded style, in [language]. */
    fun localize(style: Style, language: String) {
        val text = expression(language)
        for (layer in style.layers) localize(layer, text)
    }

    /** A snapshotter's labels, once its style has loaded: it cannot list its layers, so [styleLabelLayers] names them. */
    fun localizeOnLoad(snapshotter: MapSnapshotter, language: String) {
        snapshotter.setObserver(object : MapSnapshotter.Observer {
            override fun onDidFinishLoadingStyle() = styleLabelLayers.forEach { localize(snapshotter.getLayer(it), language) }
            override fun onStyleImageMissing(imageName: String) = Unit
        })
    }

    /** One layer of a snapshotter's style ([styleLabelLayers]). */
    fun localize(layer: Layer?, language: String) = localize(layer, expression(language))

    private fun localize(layer: Layer?, text: Expression) {
        val symbol = layer as? SymbolLayer ?: return
        val field = symbol.textField
        val source = if (field.isExpression) field.expression.toString() else field.value?.toString().orEmpty()
        if (printsName(source)) symbol.setProperties(PropertyFactory.textField(text))
    }
}
