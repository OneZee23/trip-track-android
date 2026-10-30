// iOS: APIEnvelope.swift
package com.onezee.triptrack.network

import kotlinx.serialization.Serializable

// iOS: APIEnvelope.swift
@Serializable
enum class ApiStatus { ok, error }

// iOS: APIEnvelope.swift
@Serializable
data class ApiEnvelope<T>(
    val status: ApiStatus,
    val payload: T? = null,
    val code: String? = null,
    val message: String? = null,
    val serverVersion: Int? = null,
    val serverLastModifiedAt: String? = null,
)
