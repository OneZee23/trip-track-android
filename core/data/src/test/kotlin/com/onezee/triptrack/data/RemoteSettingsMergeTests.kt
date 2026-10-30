// iOS: RemoteSettingsMergeTests.swift, SettingsUnitWireTests.swift (the store half)
package com.onezee.triptrack.data

import com.onezee.triptrack.data.db.entity.UserSettingsEntity
import com.onezee.triptrack.model.SettingsSyncPayload
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * A full pull ALWAYS carries the settings row, so a naive «assign everything» would roll back XP,
 * level, streak, theme and language for the whole fleet in one day. Progress only goes up;
 * preferences follow `lastModifiedAt`.
 */
class RemoteSettingsMergeTests : RoomTestCase() {
    private suspend fun makeLocal(xp: Long, level: Int, best: Int, modified: Instant, theme: String = "dark", language: String = "ru"): UUID {
        val e = UserSettingsEntity(
            id = UUID.randomUUID(), profileXP = xp, profileLevel = level, bestStreak = best,
            lastModifiedAt = modified, themeMode = theme, language = language,
        )
        db.settingsDao().upsert(e)
        return e.id
    }

    private fun payload(
        id: UUID, xp: Int, level: Int, best: Int, modified: Instant, theme: String = "dark", language: String = "ru",
        distance: String = "km", volume: String = "liters",
    ) = SettingsSyncPayload(
        id = id, avatarEmoji = "😎", themeMode = theme, language = language, distanceUnit = distance,
        volumeUnit = volume, fuelConsumption = 7.8, fuelPrice = 56.0, fuelCurrency = "€", selectedVehicleId = null,
        profileLevel = level, profileXp = xp, currentStreak = 0, bestStreak = best, lastTripDate = null,
        conflictVersion = 1, lastModifiedAt = modified,
    )

    private suspend fun local(): UserSettingsEntity = assertNotNull(db.settingsDao().settings())

    /** A server row that is behind must not drag the phone's progress down to meet it. */
    @Test
    fun testServerNeverLowersProgress() = test {
        val id = makeLocal(5_000, 12, 9, Instant.now())
        repo.applyRemoteSettings(payload(id, 0, 1, 0, Instant.now().minusSeconds(3_600)))

        assertEquals(5_000, local().profileXP)
        assertEquals(12, local().profileLevel)
        assertEquals(9, local().bestStreak)
    }

    @Test
    fun testServerRaisesProgressWhenItIsAhead() = test {
        val id = makeLocal(100, 2, 1, Instant.now().minusSeconds(3_600))
        repo.applyRemoteSettings(payload(id, 9_000, 20, 30, Instant.now()))

        assertEquals(9_000, local().profileXP)
        assertEquals(20, local().profileLevel)
        assertEquals(30, local().bestStreak)
    }

    @Test
    fun testStalePreferencesAreIgnored() = test {
        val id = makeLocal(0, 1, 0, Instant.now(), theme = "light", language = "en")

        repo.applyRemoteSettings(payload(id, 0, 1, 0, Instant.now().minusSeconds(3_600), theme = "dark", language = "ru"))

        assertEquals("light", local().themeMode)
        assertEquals("en", local().language)
    }

    @Test
    fun testFreshPreferencesAreApplied() = test {
        val id = makeLocal(0, 1, 0, Instant.now().minusSeconds(3_600), theme = "light", language = "en")

        repo.applyRemoteSettings(payload(id, 0, 1, 0, Instant.now(), theme = "dark", language = "ru"))

        assertEquals("dark", local().themeMode)
        assertEquals("ru", local().language)
    }

    /** A STALE row must not re-point the identity every entity is stamped with. */
    @Test
    fun testStaleServerRowDoesNotRewriteTheLocalIdentity() = test {
        val originalId = makeLocal(0, 1, 0, Instant.now())

        repo.applyRemoteSettings(payload(UUID.randomUUID(), 0, 1, 0, Instant.now().minusSeconds(3_600)))

        assertEquals(originalId, local().id)
    }

    /** A fresher row may move it, and the store still holds ONE settings row. */
    @Test
    fun testAFresherServerRowMovesTheIdentityWithoutASecondRow() = test {
        makeLocal(0, 1, 0, Instant.now().minusSeconds(3_600))
        val serverId = UUID.randomUUID()

        repo.applyRemoteSettings(payload(serverId, 0, 1, 0, Instant.now()))

        assertEquals(serverId, local().id)
        assertEquals(1, countRows("UserSettingsEntity"), "the identity moved, the row did not double")
        assertEquals(synced, local().syncStatus)
    }

    /** First sync on a device without a settings row: the server row is adopted whole. */
    @Test
    fun testAFirstEverSettingsRowIsAdoptedWhole() = test {
        val serverId = UUID.randomUUID()
        repo.applyRemoteSettings(payload(serverId, 4_200, 11, 7, Instant.now().minusSeconds(86_400)))

        assertEquals(serverId, local().id)
        assertEquals(4_200, local().profileXP)
    }

    // MARK: SettingsUnitWireTests, store half

    /** A local save is dated, or an incoming pull reads it as older than itself and wipes it. */
    @Test
    fun testSavingStampsTheRow() = test {
        val store = SettingsStore(db)
        store.loadOrCreate()
        val before = Instant.now().minusSeconds(1)
        store.save { it.copy(distanceUnit = "miles") }
        assertTrue(assertNotNull(local().lastModifiedAt) > before)
        assertEquals(pendingUpload, local().syncStatus, "the stamp, not the flag, marks the edit")
    }

    @Test
    fun testIncomingDoesNotOverwriteAnEditWaitingToBeSent() = test {
        val store = SettingsStore(db)
        val row = store.loadOrCreate()
        store.save { it.copy(distanceUnit = "miles") }

        repo.applyRemoteSettings(payload(row.id, 0, 1, 0, Instant.now().minusSeconds(3_600), distance = "km"))

        assertEquals("miles", local().distanceUnit)
    }

    @Test
    fun testAGenuinelyNewerRemoteChoiceWins() = test {
        val store = SettingsStore(db)
        val row = store.loadOrCreate()
        store.save { it.copy(distanceUnit = "km") }
        db.settingsDao().upsert(local().copy(lastModifiedAt = Instant.now().minusSeconds(3_600)))

        repo.applyRemoteSettings(payload(row.id, 0, 1, 0, Instant.now(), distance = "miles"))

        assertEquals("miles", local().distanceUnit)
    }

    /** Avatar frame and the PRO badge belong to /auth/me: the settings pull never writes them. */
    @Test
    fun testPullLeavesTheAccountCosmeticsAlone() = test {
        val id = makeLocal(0, 1, 0, Instant.now().minusSeconds(3_600))
        db.settingsDao().upsert(local().copy(avatarFrame = "frame_gold", showPlusBadge = false))

        repo.applyRemoteSettings(payload(id, 0, 1, 0, Instant.now()))

        assertEquals("frame_gold", local().avatarFrame)
        assertEquals(false, local().showPlusBadge)
    }

    @Test
    fun testLoadOrCreateMakesOneRowWithAFreshIdentity() = test {
        val store = SettingsStore(db)
        val first = store.loadOrCreate()
        val second = store.loadOrCreate()
        assertEquals(first.id, second.id)
        assertEquals("km", first.distanceUnit)
        assertEquals("€", first.fuelCurrency)
        assertEquals(1, first.profileLevel)
        assertEquals(true, first.showPlusBadge)
    }
}
