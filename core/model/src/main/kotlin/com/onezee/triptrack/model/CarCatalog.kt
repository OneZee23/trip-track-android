// iOS: CarCatalog.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

// iOS: CarCatalog.swift
object CarCatalog {

    // iOS: CarCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Document(
        val schema: Int,
        val makes: List<Make>,
    )

    // iOS: CarCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Make(
        val id: String,
        val name: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val nameRu: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val aliases: List<String>? = null,
        val models: List<Model>,
    )

    // iOS: CarCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Model(
        val id: String,
        val name: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val aliases: List<String>? = null,
        val generations: List<Generation>,
    )

    // iOS: CarCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Generation(
        val id: String,
        val name: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val yearStart: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val yearEnd: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val bodies: List<String>? = null,
        val variants: List<Variant>,
    )

    // iOS: CarCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Variant(
        val id: String,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val label: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val fuel: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val displacementCc: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val powerHp: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val torqueNm: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val transmission: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val gears: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val drivetrain: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val zeroToHundredS: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val topSpeedKmh: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val curbWeightKg: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val fuelCombinedL100: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val electricRangeKm: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val batteryKwh: Double? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val yearStart: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val yearEnd: Int? = null,
    )

    // iOS: CarCatalog.swift
    enum class CatalogError {
        unsupportedSchema,
    }

    // iOS: CarCatalog.swift
    data class ModelHit(
        val make: Make,
        val model: Model,
        val yearStart: Int? = null,
        val yearEnd: Int? = null,
        val matchedYear: Int? = null,
    )
}
