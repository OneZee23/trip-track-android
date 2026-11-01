package com.onezee.triptrack.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// iOS: AutoRecordTrigger.swift. Raw values belong to phone-local storage.
@Serializable
enum class AutoRecordTrigger(val rawValue: String) {
    @SerialName("motionOrStereo")
    motionOrStereo("motionOrStereo"),
    @SerialName("stereoOnly")
    stereoOnly("stereoOnly");

    companion object {
        fun parse(rawValue: String?): AutoRecordTrigger =
            entries.firstOrNull { it.rawValue == rawValue } ?: motionOrStereo
    }
}
