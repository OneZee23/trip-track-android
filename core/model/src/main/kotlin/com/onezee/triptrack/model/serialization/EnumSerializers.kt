// iOS: TripOrigin.swift, TripEnergyMode.swift, HomeSettings.swift, CompanionsDTOs.swift
package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object TripOriginSerializer : KSerializer<TripOrigin> {
    override val descriptor = PrimitiveSerialDescriptor("TripOrigin", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TripOrigin) = encoder.encodeString(value.rawValue)
    override fun deserialize(decoder: Decoder): TripOrigin =
        TripOrigin.fromRawValue(decoder.decodeString()) ?: TripOrigin.recorded
}

object RadiusSerializer : KSerializer<HomeSettings.Radius> {
    override val descriptor = PrimitiveSerialDescriptor("HomeSettings.Radius", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: HomeSettings.Radius) = encoder.encodeInt(value.rawValue)
    override fun deserialize(decoder: Decoder): HomeSettings.Radius =
        HomeSettings.Radius.fromRawValue(decoder.decodeInt()) ?: throw SerializationException("Unknown HomeSettings.Radius")
}

object TripEnergyModeSerializer : KSerializer<TripEnergyMode> {
    override val descriptor = PrimitiveSerialDescriptor("TripEnergyMode", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TripEnergyMode) = encoder.encodeString(value.rawValue)
    override fun deserialize(decoder: Decoder): TripEnergyMode =
        TripEnergyMode.fromRawValue(decoder.decodeString()) ?: TripEnergyMode.auto
}

object CompanionStatusSerializer : KSerializer<CompanionStatus> {
    override val descriptor = PrimitiveSerialDescriptor("CompanionStatus", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: CompanionStatus) = encoder.encodeInt(value.rawValue)
    override fun deserialize(decoder: Decoder): CompanionStatus =
        CompanionStatus.fromRawValue(decoder.decodeInt()) ?: throw SerializationException("Unknown CompanionStatus")
}

