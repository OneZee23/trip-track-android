// iOS: PhotoUploadStatus.swift
package com.onezee.triptrack.model

// iOS: PhotoUploadStatus.swift
enum class PhotoUploadStatus(val rawValue: Short) {
    localOnly(0),
    uploading(1),
    uploaded(2),
    failed(3);

    companion object {
        fun fromRawValue(value: Short): PhotoUploadStatus? = entries.find { it.rawValue == value }
    }
}
