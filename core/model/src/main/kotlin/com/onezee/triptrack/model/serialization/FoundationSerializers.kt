// iOS: Foundation Codable; Utils/ISODate.swift
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.onezee.triptrack.model.serialization

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("Foundation.UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString().uppercase())
    override fun deserialize(decoder: Decoder): UUID {
        val raw = decoder.decodeString()
        // java.util.UUID also accepts shortened groups, which Foundation rejects.
        if (!raw.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"))) {
            throw SerializationException("Invalid UUID")
        }
        return UUID.fromString(raw)
    }
}

object InstantSerializer : KSerializer<Instant> {
    private const val referenceEpoch = 978307200L
    override val descriptor = PrimitiveSerialDescriptor("Foundation.Date", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Instant) {
        val strategy = encoder.serializersModule.getContextual(Instant::class)
        if (strategy != null) {
            encoder.encodeSerializableValue(strategy, value)
        } else {
            encoder.encodeDouble(value.epochSecond - referenceEpoch + value.nano / 1_000_000_000.0)
        }
    }

    override fun deserialize(decoder: Decoder): Instant {
        val strategy = decoder.serializersModule.getContextual(Instant::class)
        if (strategy != null) return decoder.decodeSerializableValue(strategy)
        val seconds = decoder.decodeDouble()
        if (!seconds.isFinite()) throw SerializationException("Invalid Foundation date")
        val whole = floor(seconds).toLong()
        // Rounded to the microsecond: around 2026 a double carries seconds to about 0.1 µs, so a stored
        // 17:11:58.594 came back as .593999982 and no longer matched its track point. The hidden section
        // ending at that point then left the point visible after a save (POCO, 5 Nov 2026).
        val micros = ((seconds - whole) * 1_000_000).roundToLong()
        return Instant.ofEpochSecond(whole + referenceEpoch, 0).plusNanos(micros * 1_000)
    }
}

object IsoInstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("ISODate", PrimitiveKind.STRING)
    private val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(formatter.format(value))
    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString().replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        return try {
            OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (error: java.time.DateTimeException) {
            throw SerializationException("Invalid ISO date", error)
        }
    }
}

object Base64Serializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("Foundation.Data", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))
    override fun deserialize(decoder: Decoder): ByteArray = try {
        Base64.getDecoder().decode(decoder.decodeString())
    } catch (error: IllegalArgumentException) {
        throw SerializationException("Invalid base64 data", error)
    }
}
