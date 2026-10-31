// iOS: VehicleCatalog.swift. The bundled fallback also covers motorcycles, mopeds and bicycles.
package com.onezee.triptrack.feature.garage

import com.onezee.triptrack.model.VehicleCatalog
import com.onezee.triptrack.model.VehicleType
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.Json

class LegacyVehicleCatalog(private val source: () -> String = {
    requireNotNull(LegacyVehicleCatalog::class.java.getResourceAsStream("/VehicleCatalog.json")).use { it.readBytes().decodeToString() }
}) {
    val makes: List<VehicleCatalog.Make> by lazy { runCatching { Json.decodeFromString<VehicleCatalog.File>(source()).makes }.getOrDefault(emptyList()) }
    fun makes(type: VehicleType): List<VehicleCatalog.Make> {
        val allowed = VehicleAvatar.styles(type)
        return makes.mapNotNull { make -> make.models.filter { model -> model.bodies.any { it in allowed } }
            .takeIf { it.isNotEmpty() }?.let { make.copy(models = it) } }
    }
    fun search(query: String, type: VehicleType): List<VehicleCatalog.Make> {
        val q = fold(query)
        val all = makes(type)
        if (q.isEmpty()) return all
        fun matches(name: String, aliases: List<String>?) = (listOf(name) + aliases.orEmpty()).any { q in fold(it) }
        return all.mapNotNull { make ->
            if (matches(make.name, make.aliases)) make
            else make.models.filter { matches(it.name, it.aliases) }.takeIf { it.isNotEmpty() }?.let { make.copy(models = it) }
        }
    }
    fun defaultBody(model: VehicleCatalog.Model, type: VehicleType) = model.bodies.firstOrNull { it in VehicleAvatar.styles(type) } ?: VehicleAvatar.styles(type).first()
    private fun fold(value: String) = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
