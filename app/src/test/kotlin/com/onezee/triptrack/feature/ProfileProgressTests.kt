// iOS: ProfileProgressSyncTests.swift (persisted 25_730 XP, fresh phone and older level-only responses).
package com.onezee.triptrack.feature

import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.feature.me.*
import com.onezee.triptrack.model.ProfileProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileProgressTests : WorldTest() {
    @Test fun freshDefaultsNeverBecomeAProgressReset() {
        assertEquals(ProfileProgress(), profileProgress(UserSettingsEntity(userId)))
        val legacy = profileProgress(UserSettingsEntity(userId, profileLevel = 21))
        assertEquals(21, legacy.level); assertNull(legacy.xp)
    }
    @Test fun persistedRewardsWinAndRemoteMergeDoesNotStampAUserEdit() = runTest(dispatcher) {
        val old = UserSettingsEntity(userId, profileXP = 25_730, profileLevel = 21, lastModifiedAt = t0)
        db.settingsDao().upsert(old)
        val store = ProfileProgressStore(db)
        store.merge(1, 0)
        assertEquals(old, db.settingsDao().settings())
        store.merge(21, 26_000)
        assertEquals(26_000L, db.settingsDao().settings()?.profileXP)
        assertEquals(t0, db.settingsDao().settings()?.lastModifiedAt)
    }
    @Test fun accountSwitchGuardRejectsAPreviousAccountsResponse() = runTest(dispatcher) {
        val old = UserSettingsEntity(userId)
        db.settingsDao().upsert(old)
        ProfileProgressStore(db).merge(21, 25_730) { false }
        assertEquals(old, db.settingsDao().settings())
    }
}
