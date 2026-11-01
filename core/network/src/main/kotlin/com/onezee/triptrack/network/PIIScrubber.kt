// iOS: Common/PIISensitiveKeys.swift
package com.onezee.triptrack.network

import java.net.URI
import kotlinx.serialization.json.*

object PIISensitiveKeys {
    val all: Set<String> = setOf(
        "refreshToken", "accessToken", "identityToken", "apnsToken", "deviceToken", "password", "jwt",
        "nonce", "rawNonce", "email", "userEmail", "displayName", "userName", "thumbnailUrl", "originalUrl",
        "remoteUrl", "url", "homeLatitude", "homeLongitude", "device_app_hash", "signedTransaction",
        "signedRenewalInfo", "jws", "appAccountToken", "originalTransactionId",
    )

    fun matches(key: String): Boolean = all.any {
        key.equals(it, ignoreCase = true) || key.endsWith(".$it", ignoreCase = true) || key.endsWith("_$it", ignoreCase = true)
    }
}

// iOS: Common/PIIScrubber.swift
object PIIScrubber {
    const val redactedMarker = "<redacted>"
    const val idMarker = "<id>"

    fun redact(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, item) ->
            if (PIISensitiveKeys.matches(key)) JsonPrimitive(redactedMarker) else redact(item)
        })
        is JsonArray -> JsonArray(value.map(::redact))
        else -> value
    }

    fun redact(value: Map<String, Any?>): Map<String, Any?> = value.mapValues { (key, item) ->
        if (PIISensitiveKeys.matches(key)) redactedMarker else redactValue(item)
    }

    private fun redactValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { (key, item) ->
            key to if (key is String && PIISensitiveKeys.matches(key)) redactedMarker else redactValue(item)
        }
        is List<*> -> value.map(::redactValue)
        is Array<*> -> value.map(::redactValue)
        else -> value
    }

    fun redactUrl(raw: String): String = try {
        // SDK outbox events can carry an already-scrubbed breadcrumb. URI rejects literal angle
        // brackets, whereas iOS URLComponents accepts them: encode our marker before parsing.
        val uri = URI(raw.replace(idMarker, "%3Cid%3E"))
        val host = uri.host ?: return redactedMarker
        val path = uri.path.orEmpty().split('/').filter(String::isNotEmpty).joinToString("/") {
            if (it.length <= 24 && it.matches(Regex("[a-zA-Z_.-]+"))) it else idMarker
        }
        "${uri.scheme ?: "https"}://$host" + if (path.isEmpty()) "" else "/$path"
    } catch (_: Exception) { redactedMarker }
}

// iOS: Networking/APILogger.swift
class ApiLogger(private val sink: (String) -> Unit = {}) {
    fun request(method: String, url: String, body: ByteArray?) {
        sink("$method ${PIIScrubber.redactUrl(url)} body=${body?.let(::preview) ?: "-"}")
    }

    fun response(status: Int) { sink("HTTP $status") }

    companion object {
        const val previewLimit = 2048

        // Parsing a truncated JSON string could expose a cut token. Mask values
        // before truncation, including escaped quotes and an unfinished tail.
        fun preview(data: ByteArray): String {
            var head = data.copyOfRange(0, minOf(data.size, (previewLimit + 256) * 4)).toString(Charsets.UTF_8)
            head = redact(head)
            return head.take(previewLimit) + if (data.size > previewLimit) "…[${data.size} bytes]" else ""
        }

        fun redact(raw: String): String = buildString {
            var index = 0
            while (index < raw.length) {
                if (raw[index] != '"') { append(raw[index++]); continue }
                val end = stringEnd(raw, index)
                var colon = end
                while (colon < raw.length && raw[colon].isWhitespace()) colon++
                val key = runCatching { NetworkJson.wire.decodeFromString(kotlinx.serialization.serializer<String>(), raw.substring(index, end)) }.getOrNull()
                if (colon < raw.length && raw[colon] == ':' && key != null && PIISensitiveKeys.matches(key)) {
                    append(raw.substring(index, colon + 1)); append("\"***\"")
                    index = colon + 1
                    while (index < raw.length && raw[index].isWhitespace()) index++
                    index = valueEnd(raw, index)
                } else {
                    append(raw.substring(index, end)); index = end
                }
            }
        }

        private fun stringEnd(raw: String, start: Int): Int {
            var index = start + 1
            while (index < raw.length) {
                when (raw[index++]) {
                    '\\' -> if (index < raw.length) index++
                    '"' -> return index
                }
            }
            return index
        }

        private fun valueEnd(raw: String, start: Int): Int {
            if (start >= raw.length) return start
            if (raw[start] == '"') return stringEnd(raw, start)
            if (raw[start] != '{' && raw[start] != '[') {
                var index = start
                while (index < raw.length && raw[index] !in ",}]") index++
                return index
            }
            var depth = 0
            var index = start
            while (index < raw.length) {
                when (raw[index]) {
                    '"' -> { index = stringEnd(raw, index); continue }
                    '{', '[' -> depth++
                    '}', ']' -> if (--depth == 0) return index + 1
                }
                index++
            }
            return index
        }
    }
}
