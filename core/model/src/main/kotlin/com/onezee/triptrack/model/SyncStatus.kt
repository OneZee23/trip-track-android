// iOS: SyncStatus.swift
package com.onezee.triptrack.model

// iOS: SyncStatus.swift
enum class SyncStatus(val rawValue: Short) {
    pendingUpload(0),
    synced(1),
    pendingDelete(2);

    companion object {
        fun fromRawValue(value: Short): SyncStatus? = entries.find { it.rawValue == value }
    }
}
