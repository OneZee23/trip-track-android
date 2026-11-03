// iOS: ProfileCardShotTests.profile-foreign-first and profile-road-foreign-day: the stranger's hero on the
// numbers the Я hero already has (3 November audit, ME-13 and ME-27)
package com.onezee.triptrack.social

import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.model.ProfilePhotoDTO
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ForeignHeroLookTests : SocialScreenTest() {
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot()

    private fun openProfile(dark: Boolean = false) = showDestination(TripsDestination.PublicProfile(SocialFixtures.account, SocialFixtures.author), dark)

    @Test fun theCoverCountsItsPhotosUnderTheBackButton() {
        api.profileValue = api.profileValue.copy(photos = listOf(
            ProfilePhotoDTO(UUID.randomUUID(), originalUrl = "https://fixture.invalid/one.png"),
            ProfilePhotoDTO(UUID.randomUUID(), originalUrl = "https://fixture.invalid/two.png"),
        ))
        openProfile()
        compose.waitForIdle()
        // ME-27: «1 / 2» 16 in from the edge and 54 under the status bar, that is 50 under the top of the back button.
        compose.onNodeWithTag("profile_cover_counter").assertTextEquals("1 / 2")
        val counter = bounds("profile_cover_counter")
        val back = bounds("social_back")
        assertTrue(abs(16f - counter.left.value) <= 0.75f, "counter left ${counter.left.value}")
        assertTrue(abs(50f - (counter.top - back.top).value) <= 0.75f, "counter ${counter.top.value} under back ${back.top.value}")
    }

    @Test fun aStrangerWithoutPhotosHasNoCounter() {
        api.profileValue = api.profileValue.copy(photos = emptyList())
        openProfile()
        compose.waitForIdle()
        compose.onNodeWithTag("profile_cover_counter").assertDoesNotExist()
    }

    /** The free road card: the scene fades into the card colour at its foot, as the iPhone's does (ME-13). */
    private fun freeRoad(dark: Boolean) {
        api.profileValue = api.profileValue.copy(profileBackground = null, avatarFrame = null, hasPlus = false, photos = emptyList())
        openProfile(dark)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/social_profile_road_${if (dark) "dark" else "light"}.png")
    }

    @Test fun theFreeRoadCardLight() = freeRoad(false)

    @Test fun theFreeRoadCardDark() = freeRoad(true)
}
