package com.onezee.triptrack.logic

import kotlin.test.Test
import kotlin.test.assertEquals

// iOS: VehicleModTags.swift
class VehicleModTagsTests {
    @Test fun limitsEachTagByGraphemeCluster() {
        val family = "👨‍👩‍👧‍👦"
        assertEquals(family.repeat(24), VehicleModTags.limited(family.repeat(25)))
    }

    @Test fun sanitizesAndAppliesProExpiry() {
        val tags = VehicleModTags.sanitized(listOf(" A ", "A", "B", "C", "D"))
        assertEquals(listOf("A", "B", "C"), tags)
        assertEquals(tags, VehicleModTags.effective(tags, isPlus = true))
        assertEquals(emptyList(), VehicleModTags.effective(tags, isPlus = false))
    }
}
