package com.onezee.triptrack.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdminAccessTests {
    @Test fun serverAdminFlagMustBeLoadedBeforeShowingTheCard() {
        assertFalse(AdminAccess.canShowAdminCard(loaded = false, isAdmin = true))
        assertFalse(AdminAccess.canShowAdminCard(loaded = true, isAdmin = false))
        assertTrue(AdminAccess.canShowAdminCard(loaded = true, isAdmin = true))
    }
}
