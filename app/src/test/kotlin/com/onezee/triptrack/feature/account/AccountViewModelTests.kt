// iOS: CloudSyncView.cloudSyncBinding / signOutActions / performDeleteAccount (the screen's decisions)
package com.onezee.triptrack.feature.account

import com.onezee.triptrack.data.RoomSettingsStore
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.data.sync.SyncRequest
import com.onezee.triptrack.data.sync.SyncRequestSink
import com.onezee.triptrack.feature.settings.HomeZone
import com.onezee.triptrack.feature.settings.SettingsEffects
import com.onezee.triptrack.feature.settings.SettingsManager
import com.onezee.triptrack.model.AutoRecordMode
import com.onezee.triptrack.model.SavedBluetoothDevice
import com.onezee.triptrack.model.SyncStatus
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

class AccountViewModelTests : AccountTestCase() {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cloudChanges = CopyOnWriteArrayList<Boolean>()
    private lateinit var settingsManager: SettingsManager
    private lateinit var viewModel: AccountViewModel
    private val t0: Instant = Instant.parse("2026-10-01T09:00:00Z")

    private val effects = object : SettingsEffects {
        override suspend fun cloudSyncChanged(enabled: Boolean) { cloudChanges += enabled }
        override suspend fun homeZoneChanged(before: HomeZone?, after: HomeZone?) = Unit
        override suspend fun autoRecordChanged(mode: AutoRecordMode, timeout: Int, stereos: List<SavedBluetoothDevice>) = Unit
        override suspend fun publicMapChanged(enabled: Boolean) = Unit
    }

    private val sources = object : AccountScreenSources {
        override suspend fun avatarEmoji(): String = "🚙"
        override suspend fun counts(): SyncCounts = SyncCounts.read(db)
        override suspend fun lastSyncedAt(): Instant? = null
        override suspend fun blockedCount(): Int = 2
    }

    @Before fun setUpViewModel() {
        settingsManager = SettingsManager(RoomSettingsStore(db) { t0 }, prefs, object : SyncRequestSink {
            override fun enqueue(request: SyncRequest) = Unit
            override fun cancel(entityId: UUID, entityType: SyncEntityType) = Unit
        }, effects, dispatcher, deviceLocale = Locale.forLanguageTag("en-RU"))
        val host = SyncHost({ error("no engine in these tests") }, {}, { accountSettings.cloudSyncEnabled }, scope)
        viewModel = AccountViewModel(auth, settingsManager, host, prefs, sources, dispatcher)
        await { settingsManager.state.value.loaded }
    }

    @After fun closeViewModel() { viewModel.dispose(); dispatcher.close() }

    private fun await(condition: () -> Boolean) = runBlocking { withTimeout(5_000) { while (!condition()) delay(10) } }

    /** Carried over from the first-pass CloudSyncViewModel test (iOS: cloudSyncBinding). */
    @Test fun firstCloudEnableRequiresConfirmationAndOffClearsTheQueue() {
        await { viewModel.state.value.settingsLoaded }
        viewModel.setCloudSync(true)
        assertEquals(AccountDialog.EnableCloudSync, viewModel.state.value.dialog)
        assertFalse(settingsManager.state.value.cloudSyncEnabled)
        viewModel.dismissDialog()
        assertNull(viewModel.state.value.dialog)

        viewModel.setCloudSync(true); viewModel.confirmEnableCloudSync()
        await { viewModel.state.value.cloudSyncEnabled }
        assertEquals("true", prefs.string(AccountSettings.FIRST_TOGGLE_KEY))
        await { cloudChanges == listOf(true) }

        // OFF goes straight through, and the settings effect empties the queue.
        viewModel.setCloudSync(false)
        await { !viewModel.state.value.cloudSyncEnabled }
        await { cloudChanges == listOf(true, false) }

        // The consent is asked once per account.
        viewModel.setCloudSync(true)
        assertNull(viewModel.state.value.dialog)
        await { viewModel.state.value.cloudSyncEnabled }
    }

    /** iOS: the count is read when the row is tapped and decides which question the one dialog asks. */
    @Test fun signOutAsksAboutPublishedTripsOnlyWhenThereAreSome() {
        seedSession(accountA)
        viewModel.requestSignOut()
        await { viewModel.state.value.dialog != null }
        assertEquals(AccountDialog.SignOut(0), viewModel.state.value.dialog)
        viewModel.dismissDialog()
        runBlocking {
            db.tripDao().upsertTrip(TripEntity(id = UUID.randomUUID(), startDate = t0, endDate = t0.plusSeconds(60), isPrivate = false,
                serverCreatedAt = t0, syncStatus = SyncStatus.synced.rawValue))
        }
        viewModel.requestSignOut()
        await { viewModel.state.value.dialog != null }
        assertEquals(AccountDialog.SignOut(1), viewModel.state.value.dialog)
    }

    @Test fun aSignOutClosesTheScreen() {
        seedSession(accountA); routeQuietAccount(accountA)
        viewModel.signOut(hidePublic = false)
        await { viewModel.state.value.closed }
        await { !viewModel.state.value.auth.isSignedIn }
    }

    /** A refusal keeps the account and says so in place; the screen stays. */
    @Test fun aRefusedDeletionShowsUnderTheCardAndKeepsTheScreen() {
        seedSession(accountA); routes["/auth/delete-account"] = { envelopeError("UNKNOWN_SERVER_ERROR") }
        var failed = 0
        viewModel.requestDeleteAccount()
        assertEquals(AccountDialog.DeleteAccount, viewModel.state.value.dialog)
        viewModel.deleteAccount { failed++ }
        await { viewModel.state.value.deleteAccountFailed }
        assertFalse(viewModel.state.value.closed); assertFalse(viewModel.state.value.isDeletingAccount)
        assertEquals(1, failed)
        assertEquals(0, wiped)
    }

    @Test fun aPartialWipeIsReportedUnderTheCard() {
        seedSession(accountA)
        runBlocking {
            db.tripDao().upsertTrip(TripEntity(id = UUID.randomUUID(), startDate = t0, endDate = t0.plusSeconds(60), isPrivate = false,
                serverCreatedAt = t0, syncStatus = SyncStatus.synced.rawValue))
        }
        routes["/trips/delete"] = { envelopeError("VALIDATION_FAILED") }
        routes["/secrets/forget-all"] = { ok() }
        prefs.set(AccountSettings.CLOUD_KEY, "true"); prefs.set(AccountSettings.FIRST_TOGGLE_KEY, "true")
        viewModel.wipeServer()
        await { viewModel.state.value.wipeServerFailures != null }
        assertEquals(1, viewModel.state.value.wipeServerFailures)
        assertFalse(viewModel.state.value.isWipingServer)
        assertFalse(accountSettings.cloudSyncEnabled, "the wipe switches Cloud Sync off first")
        assertEquals("true", prefs.string(AccountSettings.FIRST_TOGGLE_KEY), "the consent once given stays")
    }

    @Test fun theScreenShowsTheBlockedCountAndAvatarOfASignedInPerson() {
        seedSession(accountA); routeQuietAccount(accountA)
        await { viewModel.state.value.auth.isSignedIn }
        viewModel.refresh()
        await { viewModel.state.value.blockedCount == 2 && viewModel.state.value.avatarEmoji == "🚙" }
    }
}
