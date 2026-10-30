// iOS: VehicleCatalogSelection.swift
package com.onezee.triptrack.model

// iOS: VehicleCatalogSelection.swift
data class VehicleCatalogSelection(
    val make: String,
    val model: String,
    val body: String,
    var year: Int? = null,
    var catalogModelId: String? = null,
    var catalogGenerationId: String? = null,
    var catalogVariantId: String? = null,
)
