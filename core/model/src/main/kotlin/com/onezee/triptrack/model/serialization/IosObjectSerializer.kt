// iOS: custom Codable implementations in Models/
package com.onezee.triptrack.model.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

abstract class IosObjectSerializer<T>(private val generated: () -> KSerializer<T>) : KSerializer<T> {
    override val descriptor: SerialDescriptor get() = generated().descriptor

    protected open fun read(value: JsonObject, json: Json): JsonObject = value
    protected open fun write(value: JsonObject): JsonObject = value
    protected open fun decoded(value: T, original: JsonObject): T = value

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected JSON")
        val original = input.decodeJsonElement().jsonObject
        return decoded(input.json.decodeFromJsonElement(generated(), read(original, input.json)), original)
    }

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as? JsonEncoder ?: throw SerializationException("Expected JSON")
        output.encodeJsonElement(write(output.json.encodeToJsonElement(generated(), value).jsonObject))
    }
}

internal fun JsonObject.withoutNulls(keys: Set<String>): JsonObject =
    JsonObject(filter { (key, value) -> key !in keys || value != JsonNull })

internal fun <T> MutableMap<String, JsonElement>.tolerate(key: String, serializer: KSerializer<T>, json: Json) {
    val value = get(key) ?: return
    if (value == JsonNull) return
    // Swift's try? decodeIfPresent rejects quoted booleans/numbers too.
    if (serializer.descriptor.kind in setOf(PrimitiveKind.BOOLEAN, PrimitiveKind.INT) &&
        value is JsonPrimitive && value.isString
    ) {
        remove(key)
        return
    }
    try {
        json.decodeFromJsonElement(serializer, value)
    } catch (_: IllegalArgumentException) {
        remove(key)
    }
}
