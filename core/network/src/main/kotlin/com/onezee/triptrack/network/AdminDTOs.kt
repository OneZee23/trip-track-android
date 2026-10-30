// iOS: Views/Admin/AdminModel.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Views/Admin/AdminModel.swift
@Serializable
data class AdminSummary(
    val accounts: Int,
    val accountsNew7d: Int,
    val accountsActive7d: Int,
    val proActive: Int,
    val proTrial: Int,
    val proPaid: Int,
    val trips: Int,
    val tripsManual: Int,
)

// iOS: Views/Admin/AdminModel.swift
@Serializable
data class AdminSubscription(
    val txTail: String,
    val product: String,
    val status: String,
    val isTrial: Boolean,
    val autoRenew: Boolean,
    val expiresAt: Instant? = null,
    val createdAt: Instant,
    val attached: Boolean,
    val isAdmin: Boolean,
    val language: String? = null,
    val accountCreatedAt: Instant? = null,
    val trips: Int,
)

// iOS: Views/Admin/AdminModel.swift
@Serializable
data class AdminEvent(
    val txTail: String? = null,
    val type: String,
    val subtype: String? = null,
    val outcome: String? = null,
    val at: Instant,
)

// iOS: Views/Admin/AdminModel.swift
@Serializable
data class AdminFlag(
    val name: String,
    val enabled: Boolean? = null,
    val builtIn: Boolean? = null,
)

// iOS: Views/Admin/AdminModel.swift
@Serializable
data class AdminFlagRequest(
    val name: String,
    val enabled: Boolean,
)

// iOS: AdminItems in AdminModel.swift
@Serializable
data class AdminItems<T>(val items: List<T>)
