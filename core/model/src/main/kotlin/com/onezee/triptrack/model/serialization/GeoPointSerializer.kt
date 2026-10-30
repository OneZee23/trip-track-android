// iOS: Discovery.swift, RiddleCatalog.swift (coordinate CodingKeys)
package com.onezee.triptrack.model.serialization

import com.onezee.triptrack.model.GeoPoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

// Internal keyed representation; each containing model restores its own iOS JSON shape.
object GeoPointSerializer : KSerializer<GeoPoint> {
    override val descriptor = buildClassSerialDescriptor("GeoPoint") {
        element<Double>("latitude")
        element<Double>("longitude")
    }

    override fun serialize(encoder: Encoder, value: GeoPoint) = encoder.encodeStructure(descriptor) {
        encodeDoubleElement(descriptor, 0, value.latitude)
        encodeDoubleElement(descriptor, 1, value.longitude)
    }

    override fun deserialize(decoder: Decoder): GeoPoint = decoder.decodeStructure(descriptor) {
        var latitude: Double? = null
        var longitude: Double? = null
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> latitude = decodeDoubleElement(descriptor, 0)
                1 -> longitude = decodeDoubleElement(descriptor, 1)
                else -> throw SerializationException("Unexpected coordinate field: $index")
            }
        }
        GeoPoint(latitude ?: throw SerializationException("Missing latitude"), longitude ?: throw SerializationException("Missing longitude"))
    }
}
