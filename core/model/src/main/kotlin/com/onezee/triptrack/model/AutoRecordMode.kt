// iOS: AutoRecordMode.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@file:UseSerializers(UUIDSerializer::class)

package com.onezee.triptrack.model

import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.util.UUID
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: AutoRecordMode.swift
@Serializable
enum class AutoRecordMode(val rawValue: String) {
    @SerialName("off")
    off("off"),
    @SerialName("remind")
    remind("remind"),
    @SerialName("auto")
    auto("auto");

    companion object {
        fun fromRawValue(value: String): AutoRecordMode? = entries.find { it.rawValue == value }
    }
}

// iOS: AutoRecordMode.swift
@Serializable
@kotlinx.serialization.json.JsonIgnoreUnknownKeys
data class SavedBluetoothDevice(
    val uuid: String,
    val name: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var vehicleId: UUID? = null,
)
