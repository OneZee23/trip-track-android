// iOS: KeychainHelper.swift (authenticated, device-scoped credential protection)
package com.onezee.triptrack.network

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object CredentialCipher {
    fun encrypt(plain: ByteArray, key: SecretKey, service: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(service.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(plain)
        check(cipher.iv.size == 12)
        return byteArrayOf(1) + cipher.iv + encrypted
    }

    fun decrypt(bytes: ByteArray, key: SecretKey, service: String): ByteArray {
        require(bytes.size >= 1 + 12 + 16 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(service.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes.copyOfRange(13, bytes.size))
    }
}
