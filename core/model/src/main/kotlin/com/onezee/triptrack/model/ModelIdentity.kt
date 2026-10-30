// iOS: Place.swift, Discovery.swift (frozen UUID v5 namespaces)
package com.onezee.triptrack.model

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

internal fun uuidV5(namespace: UUID, name: String): UUID {
    val bytes = ByteBuffer.allocate(16).putLong(namespace.mostSignificantBits).putLong(namespace.leastSignificantBits).array()
    val digest = MessageDigest.getInstance("SHA-1").digest(bytes + name.toByteArray(Charsets.UTF_8))
    digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
    digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
    val buffer = ByteBuffer.wrap(digest)
    return UUID(buffer.long, buffer.long)
}
