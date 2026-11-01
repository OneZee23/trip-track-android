// iOS: TripFocus.checkpoint, TripDetailView.jumpToMoment, the sibling place chip in TripMomentsTimeline.
package com.onezee.triptrack.trips

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.onezee.triptrack.data.RoomPlaceStore
import com.onezee.triptrack.data.db.entity.TripEntity
import com.onezee.triptrack.feature.TripsDestination
import com.onezee.triptrack.model.TripFocus
import com.onezee.triptrack.model.GeoPoint
import com.onezee.triptrack.model.TripCheckpoint
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TripPlacesNavigationTests : TripsScreenTest() {
    @Test fun focusShowsTheCheckpointAndItsSiblingChipOpensThePlace() {
        val tripId = UUID.randomUUID()
        val target = onMain {
            db.tripDao().upsertTrip(TripEntity(tripId, startDate = NOW, endDate = NOW.plusSeconds(3600), distance = 10_000.0))
            val checkpoints = (1..10).map { index ->
                TripCheckpoint(timestamp = NOW.plusSeconds(index * 300L), latitude = 44.30 + index * .001,
                    longitude = 38.70, distanceFromStart = index * 1000.0, elapsedFromStart = index * 300.0).also {
                    repository.addCheckpoint(it, tripId)
                }
            }
            val cp = checkpoints.last()
            val place = RoomPlaceStore(db).upsertPlace("navigation-place", GeoPoint(cp.latitude, cp.longitude), null).place
            repository.setPlaceId(cp.id, place.id)
            cp.id to place.id
        }
        var opened: UUID? = null
        feature.openPlace = { opened = it }
        showFeed()
        push(TripsDestination.TripDetail(tripId, focus = TripFocus.Checkpoint(target.first)), feature.feedNavigator)
        compose.waitForIdle()
        compose.onNodeWithTag("checkpoint_place_chip").assertIsDisplayed().performClick()
        assertEquals(target.second, opened)
    }
}
