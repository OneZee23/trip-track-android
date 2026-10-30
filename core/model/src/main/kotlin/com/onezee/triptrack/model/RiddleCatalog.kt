// iOS: RiddleCatalog.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(GeoPointSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.GeoPointSerializer
import com.onezee.triptrack.model.serialization.RiddleSerializer
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: RiddleCatalog.swift
@Serializable
enum class RiddleType(val rawValue: String) {
    @SerialName("pass")
    pass("pass"),
    @SerialName("lighthouse")
    lighthouse("lighthouse"),
    @SerialName("border")
    border("border"),
    @SerialName("ferry")
    ferry("ferry"),
    @SerialName("dam")
    dam("dam"),
    @SerialName("bridge")
    bridge("bridge"),
    @SerialName("viewpoint")
    viewpoint("viewpoint"),
    @SerialName("observatory")
    observatory("observatory"),
    @SerialName("seaRoad")
    seaRoad("seaRoad"),
    @SerialName("extreme")
    extreme("extreme"),
    @SerialName("centre")
    centre("centre"),
    @SerialName("tripoint")
    tripoint("tripoint");

    companion object {
        fun fromRawValue(value: String): RiddleType? = entries.find { it.rawValue == value }
    }
}

// iOS: RiddleCatalog.swift
@KeepGeneratedSerializer
@Serializable(with = RiddleSerializer::class)
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class Riddle(
    val id: String,
    @SerialName("t")
    val type: RiddleType,
    @SerialName("c")
    val coordinate: GeoPoint,
    @EncodeDefault
    @SerialName("n")
    val name: String = "",
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("r")
    val regionId: String? = null,
)

// iOS: RiddleCatalog.swift
object BundleRiddleCatalog {

    // iOS: RiddleCatalog.swift
    @Serializable
    @kotlinx.serialization.json.JsonIgnoreUnknownKeys
    data class Payload(
        val riddles: List<Riddle>,
    )
}
