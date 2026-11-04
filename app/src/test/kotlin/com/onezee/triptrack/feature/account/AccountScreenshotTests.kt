package com.onezee.triptrack.feature.account

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.R
import com.onezee.triptrack.data.sync.SyncAction
import com.onezee.triptrack.data.sync.SyncEntityType
import com.onezee.triptrack.designsystem.components.SheetScope
import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.theme.AppTheme
import com.onezee.triptrack.designsystem.theme.TripTrackTheme
import com.onezee.triptrack.feature.onboarding.OnboardingContent
import com.onezee.triptrack.model.DistanceUnit
import com.onezee.triptrack.sync.SyncOperation
import com.onezee.triptrack.sync.SyncQueueState
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The account, sync status, sign-in and onboarding screens on a phone (Pixel 7 width and density),
 * light and dark, in Russian. Sheets are drawn as their column, without the dialog window.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ru-w411dp-h914dp-420dpi")
class AccountScreenshotTests(private val dark: Boolean) {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : TestWatcher() {
        override fun starting(description: Description) {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val theme = if (dark) "dark" else "light"

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TripTrackTheme(isDark = dark, language = AppLanguage.ru, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(AppTheme.colors.bg)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/${name}_$theme.png")
    }

    @Composable
    private fun SheetFrame(content: @Composable SheetScope.() -> Unit) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.bg)) {
            val column = this
            val scope = remember(column) { object : SheetScope, ColumnScope by column { override fun dismiss() = Unit } }
            scope.content()
        }
    }

    private val signedIn = AuthState(
        isSignedIn = true, accountId = UUID.fromString("a0000000-0000-4000-8000-00000000000a"),
        userName = "Быстрый Путешественник 42", userEmail = "traveler@example.com", isPublicProfile = true,
    )

    private val trip = UUID.fromString("0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b")
    private val photo = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d")

    @Test fun accountSync() = shot("account_sync") {
        SheetFrame {
            CloudSyncContent(
                state = AccountScreenState(
                    auth = signedIn, cloudSyncEnabled = true, settingsLoaded = true, blockedCount = 2,
                    queue = SyncQueueState(pending = listOf(SyncOperation(SyncEntityType.trip, trip, SyncAction.upload),
                        SyncOperation(SyncEntityType.photo, photo, SyncAction.upload))),
                ),
                onClose = {}, onCloudSync = {}, onOpenStatus = {}, onPublicProfile = {}, onOpenBlocked = {}, onSignOut = {},
                onWipeServer = {}, onDeleteAccount = {},
            )
        }
    }

    @Test fun accountSyncAfterFailures() = shot("account_sync_failures") {
        SheetFrame {
            CloudSyncContent(
                state = AccountScreenState(auth = signedIn.copy(userEmail = null, isPublicProfile = null), settingsLoaded = true,
                    wipeServerFailures = 2, deleteAccountFailed = true, isWipingServer = true),
                onClose = {}, onCloudSync = {}, onOpenStatus = {}, onPublicProfile = {}, onOpenBlocked = {}, onSignOut = {},
                onWipeServer = {}, onDeleteAccount = {},
            )
        }
    }

    @Test fun syncStatus() = shot("sync_status") {
        SheetFrame {
            SyncStatusContent(
                AccountScreenState(
                    auth = signedIn, cloudSyncEnabled = true,
                    counts = SyncCounts(tripsSynced = 12, tripsPending = 3, vehiclesSynced = 2, photosUploaded = 30, photosPartial = 2,
                        photosLocal = 1, photosFailed = 1, settingsExists = true, settingsSynced = false),
                    queue = SyncQueueState(
                        pending = listOf(SyncOperation(SyncEntityType.photo, photo, SyncAction.upload)),
                        failed = listOf(SyncOperation(SyncEntityType.journey, trip, SyncAction.update, retryCount = 2, lastError = "VALIDATION_FAILED")),
                        currentOperation = SyncOperation(SyncEntityType.trip, trip, SyncAction.upload),
                        isSyncing = true,
                    ),
                ),
                onRetry = {},
            )
        }
    }

    @Test fun signInPrompt() = shot("sign_in_prompt") {
        SheetFrame { SignInPromptBody(SignInAction.sync, isAuthenticating = false, onSignIn = { _, _ -> }) }
    }

    @Test fun signInPromptError() = shot("sign_in_prompt_error") {
        SheetFrame {
            SignInPromptBody(SignInAction.publish, isAuthenticating = false, onSignIn = { _, _ -> },
                initialError = RuntimeEnvironment.getApplication().getString(com.onezee.triptrack.designsystem.R.string.android_sign_in_google_failed))
        }
    }

    @Test fun signInPromptLoading() = shot("sign_in_prompt_loading") {
        SheetFrame { SignInPromptBody(SignInAction.generic, isAuthenticating = true, onSignIn = { _, _ -> }) }
    }

    @Test fun guestCard() = shot("guest_card") {
        Column(Modifier.padding(16.dp)) { GuestSignInCard(expired = false, isAuthenticating = false, onSignIn = { _, _, _ -> }) }
    }

    @Test fun sessionExpiredCard() = shot("guest_card_expired") {
        Column(Modifier.padding(16.dp)) { GuestSignInCard(expired = true, isAuthenticating = false, onSignIn = { _, _, _ -> }) }
    }

    @Test fun blockedList() = shot("blocked_list") {
        SheetFrame {
            BlockedListContent(
                users = listOf(
                    com.onezee.triptrack.model.BlockedUser(UUID.fromString("c0000000-0000-4000-8000-00000000000c"), "Тихий Странник 512", "🚐", 7, "2026-09-14T10:00:00.000Z"),
                    com.onezee.triptrack.model.BlockedUser(UUID.fromString("d0000000-0000-4000-8000-00000000000d"), null, null, 3, null),
                ),
                loading = false, failed = false, pending = null, onRetry = {}, onUnblock = {},
            )
        }
    }

    @Test fun blockedListEmpty() = shot("blocked_list_empty") {
        SheetFrame { BlockedListContent(users = emptyList(), loading = false, failed = false, pending = null, onRetry = {}, onUnblock = {}) }
    }

    @Test fun blockedListFailed() = shot("blocked_list_failed") {
        SheetFrame { BlockedListContent(users = emptyList(), loading = false, failed = true, pending = null, onRetry = {}, onUnblock = {}) }
    }

    @Test fun onboardingWelcome() = onboarding(0)
    @Test fun onboardingValue() = onboarding(1)
    @Test fun onboardingLocation() = onboarding(2)
    @Test fun onboardingBackground() = onboarding(3)
    @Test fun onboardingNotifications() = shot("onboarding_4") {
        OnboardingContent(page = 5, distanceUnit = DistanceUnit.km, isRequesting = false, locationGranted = false,
            backgroundGranted = false, notificationsGranted = false, onContinue = {})
    }
    private fun onboarding(page: Int) = shot("onboarding_$page") {
        OnboardingContent(page = page, distanceUnit = DistanceUnit.km, isRequesting = false, locationGranted = page == 3,
            backgroundGranted = false, notificationsGranted = false, onContinue = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun themes(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
