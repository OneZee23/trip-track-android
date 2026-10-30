// iOS: SIWANonce.swift (randomNonceString and sha256)
package com.onezee.triptrack.network

import java.security.MessageDigest
import java.security.SecureRandom

data class GoogleNonce(val raw: String, val sha256: String) {
    companion object {
        private const val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"

        fun create(length: Int = 32, random: SecureRandom = SecureRandom()): GoogleNonce {
            require(length > 0)
            return fromRaw(buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } })
        }

        // The backend hashes raw; Credential Manager receives sha256.
        fun fromRaw(raw: String): GoogleNonce = GoogleNonce(raw, MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) })
    }
}
