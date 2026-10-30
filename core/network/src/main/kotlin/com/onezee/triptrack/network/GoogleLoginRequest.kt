// iOS: AuthDTOs.swift (LoginRequest); Android Google login addition
package com.onezee.triptrack.network

import kotlinx.serialization.Required
import kotlinx.serialization.Serializable

@Serializable
data class GoogleLoginRequest(
    val identityToken: String,
    val localUserId: String,
    val nonce: String,
    val deviceName: String? = null,
    val appVersion: String,
    @Required val platform: String = "android",
) {
    init { require(platform == "android") }
}
