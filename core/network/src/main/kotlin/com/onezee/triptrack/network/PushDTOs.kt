// iOS: Services/PushNotificationManager.swift, Views/Profile/NotificationSwitches.swift
@file:UseSerializers(UUIDSerializer::class, IsoInstantSerializer::class)

package com.onezee.triptrack.network

import com.onezee.triptrack.model.*
import com.onezee.triptrack.model.serialization.IsoInstantSerializer
import com.onezee.triptrack.model.serialization.UUIDSerializer
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// iOS: Services/PushNotificationManager.swift, Views/Profile/NotificationSwitches.swift
@Serializable
data class DeviceTokenRequest(
    val apnsToken: String,
    val localUserId: String,
    val environment: String,
)

// iOS: Services/PushNotificationManager.swift, Views/Profile/NotificationSwitches.swift
@Serializable
data class NotificationPrefsResponse(
    val notifyReactions: Boolean,
    val notifyFollows: Boolean,
    val notifyComments: Boolean? = null,
    val notifyWeeklyRecap: Boolean,
    val notifyCompanions: Boolean? = null,
    val isAdmin: Boolean? = null,
    val notifyNewAccounts: Boolean? = null,
)

// iOS: Services/PushNotificationManager.swift, Views/Profile/NotificationSwitches.swift
@Serializable
data class NotificationPrefsUpdateRequest(
    val notifyReactions: Boolean? = null,
    val notifyFollows: Boolean? = null,
    val notifyComments: Boolean? = null,
    val notifyWeeklyRecap: Boolean? = null,
    val notifyCompanions: Boolean? = null,
    val notifyNewAccounts: Boolean? = null,
)

// iOS: Services/PushNotificationManager.swift, Views/Profile/NotificationSwitches.swift
@Serializable
data class PushData(
    val category: String? = null,
    val actorAccountId: UUID? = null,
    val tripId: UUID? = null,
    val emoji: String? = null,
    val badgeId: String? = null,
    val accountId: UUID? = null,
    val totalAccounts: Int? = null,
    val kind: String? = null,
)

