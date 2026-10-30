// iOS: MockURLProtocol.swift, KeychainWriteTests.swift
package com.onezee.triptrack.network

import com.onezee.triptrack.model.AccountDTO
import com.onezee.triptrack.model.LoginResponse
import java.util.UUID
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse

internal val accountId: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")

internal class FakeVault : SecureVault {
    var values: Map<String, String>? = null
    @Volatile var unavailable = false
    var failWrites = false
    @Synchronized override fun read(): SecureRead<Map<String, String>> = if (unavailable) SecureRead.Unavailable("locked")
        else values?.let { SecureRead.Value(it.toMap()) } ?: SecureRead.Missing
    @Synchronized override fun write(values: Map<String, String>) {
        if (unavailable || failWrites) throw SecureStorageException()
        this.values = values.toMap()
    }
}

internal fun signedInStore(vault: FakeVault = FakeVault()): TokenStore = TokenStore(vault).apply {
    acceptLogin(LoginResponse("expired", "valid", AccountDTO(accountId, avatarEmoji = "🚗", displayName = "Test driver"), false))
}

internal fun fixture(name: String): String = checkNotNull(object {}.javaClass.getResourceAsStream("/ios/$name.json"))
    .bufferedReader().use { it.readText() }

internal fun jsonResponse(body: String, status: Int = 200): MockResponse = MockResponse.Builder().code(status)
    .addHeader("Content-Type", "application/json").body(body).build()
internal fun ok(body: String = "{\"v\":1}"): MockResponse = jsonResponse("{\"status\":\"ok\",\"payload\":$body}")
internal fun rejected(code: String = "USER_NOT_AUTH"): MockResponse = jsonResponse("{\"status\":\"error\",\"code\":\"$code\",\"message\":\"test\"}")
internal fun refreshed(access: String = "new", refresh: String = "newRefresh"): MockResponse = ok("{\"accessToken\":\"$access\",\"refreshToken\":\"$refresh\"}")

// iOS: APIClientTokenRefreshTests.swift
@Serializable internal data class Payload(val v: Int)
// iOS: APIClientTokenRefreshTests.swift
@Serializable internal data class Req(val x: Int)
