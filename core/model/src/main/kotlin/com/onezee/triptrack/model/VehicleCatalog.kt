// iOS: VehicleCatalog.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

// iOS: VehicleCatalog.swift
object VehicleCatalog {

    // iOS: VehicleCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Model(
        val name: String,
        val bodies: List<String>,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val aliases: List<String>? = null,
    )

    // iOS: VehicleCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Make(
        val name: String,
        val models: List<Model>,
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val aliases: List<String>? = null,
    )

    // iOS: VehicleCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class File(
        val version: Int,
        val makes: List<Make>,
    )
}
