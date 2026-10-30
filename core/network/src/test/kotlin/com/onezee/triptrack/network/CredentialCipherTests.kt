// iOS: KeychainHelperTests.swift; Android AES-GCM implementation
package com.onezee.triptrack.network

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.test.*
import org.junit.Test

class CredentialCipherTests {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test fun encryptionRoundTripsAndUsesFreshNonce() {
        val key = key()
        val plain = "test-credential".toByteArray()
        val first = CredentialCipher.encrypt(plain, key, "app.auth.api.example")
        val second = CredentialCipher.encrypt(plain, key, "app.auth.api.example")
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("test-credential"))
        assertContentEquals(plain, CredentialCipher.decrypt(first, key, "app.auth.api.example"))
    }

    @Test fun tamperingWrongKeyAndWrongScopeCannotDecrypt() {
        val key = key()
        val bytes = CredentialCipher.encrypt("test".toByteArray(), key, "app.auth.api.example")
        val tampered = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertFailsWith<AEADBadTagException> { CredentialCipher.decrypt(tampered, key, "app.auth.api.example") }
        assertFailsWith<AEADBadTagException> { CredentialCipher.decrypt(bytes, key(), "app.auth.api.example") }
        assertFailsWith<AEADBadTagException> { CredentialCipher.decrypt(bytes, key, "app.dev.auth.api.example") }
    }
}
