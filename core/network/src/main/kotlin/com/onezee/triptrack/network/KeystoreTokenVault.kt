// iOS: KeychainHelper.swift (device-only, after first unlock, scoped atomic writes)
package com.onezee.triptrack.network

import android.content.Context
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl

class KeystoreTokenVault(context: Context, apiBaseUrl: String = ApiEndpoint.defaultBaseUrl) : SecureVault {
    private val appContext = context.applicationContext
    override val apiHost: String = apiBaseUrl.toHttpUrl().host
    private val service = TokenStore.composeService(appContext.packageName, apiHost)
    private val scopeHash = MessageDigest.getInstance("SHA-256").digest(service.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(appContext.noBackupFilesDir, "auth-$scopeHash"))
    private val alias = "triptrack.auth.$scopeHash"
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    @Synchronized
    override fun read(): SecureRead<Map<String, String>> {
        // Credential-encrypted files can appear absent before the first unlock.
        if (!unlocked()) return SecureRead.Unavailable("userLocked")
        return try {
            val bytes = file.openRead().use { it.readBytes() }
            val key = existingKey() ?: return SecureRead.Unavailable("keyMissing")
            val plain = CredentialCipher.decrypt(bytes, key, service)
            SecureRead.Value(NetworkJson.wire.decodeFromString(serializer, plain.toString(Charsets.UTF_8)))
        } catch (_: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                SecureRead.Unavailable("fileUnreadable")
            } else SecureRead.Missing
        } catch (_: Exception) {
            // Corruption and key invalidation are unknown state; never delete
            // either the ciphertext or the key to make a read succeed.
            SecureRead.Unavailable("readFailed")
        }
    }

    @Synchronized
    override fun write(values: Map<String, String>) {
        if (!unlocked()) throw SecureStorageException()
        try {
            val key = existingKey() ?: createKey()
            val plain = NetworkJson.wire.encodeToString(serializer, values).toByteArray(Charsets.UTF_8)
            val encrypted = CredentialCipher.encrypt(plain, key, service)
            val stream = file.startWrite()
            try {
                stream.write(encrypted)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        } catch (error: Exception) {
            throw SecureStorageException(error)
        }
    }

    private fun unlocked(): Boolean = appContext.getSystemService(UserManager::class.java).isUserUnlocked

    private fun existingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(alias, null) as SecretKey?
    }

    private fun createKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build())
        generateKey()
    }
}
