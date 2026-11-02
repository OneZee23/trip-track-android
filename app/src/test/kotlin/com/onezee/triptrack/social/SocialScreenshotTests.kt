// iOS: PublicProfileCoverTests, PublicMapFlowTests, PublicMapSummaryTests, StatsScreenShotTests,
// ForeignStatsOpeningTests, PublicAchievementsFlowTests, PublicLevelsFlowTests.
package com.onezee.triptrack.social

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.*
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.onezee.triptrack.feature.*
import com.onezee.triptrack.feature.feed.asTrip
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.feature.social.ui.*
import com.onezee.triptrack.feature.stats.*
import com.onezee.triptrack.feature.atlas.AtlasSelection
import com.onezee.triptrack.model.*
import com.onezee.triptrack.network.ApiError
import com.onezee.triptrack.network.PublicTripsResponse
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import com.onezee.triptrack.ui.navigation.rememberLocalViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import java.time.ZoneOffset
import java.util.UUID

@RunWith(ParameterizedRobolectricTestRunner::class)
class SocialScreenshotTests(private val dark:Boolean) : SocialScreenTest() {
    private val theme=if(dark)"dark" else "light"
    private fun shot(name:String) { compose.waitForIdle();compose.onRoot().captureRoboImage("src/test/screenshots/social_${name}_$theme.png") }
    private fun window(name:String) { compose.waitForIdle();captureScreenRoboImage("src/test/screenshots/social_${name}_$theme.png") }
    private fun profile()=showDestination(TripsDestination.PublicProfile(SocialFixtures.account,SocialFixtures.author),dark)
    @Test fun profileHeroAndJournal() {
        profile();shot("profile")
        compose.onNodeWithTag("profile_layout_grid").performScrollTo().performClick();shot("profile_grid")
        compose.onNodeWithTag("profile_layout_list").performClick()
        compose.onAllNodesWithTag("profile_month_header")[0].performScrollTo();shot("profile_journal")
    }
    @Test fun profileCoverAndViewer() {
        val bmp=android.graphics.Bitmap.createBitmap(800,450,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bmp);canvas.drawColor(android.graphics.Color.rgb(66,115,130))
        val paint=android.graphics.Paint().apply { color=android.graphics.Color.rgb(232,180,105) }
        canvas.drawCircle(610f,100f,54f,paint)
        val bytes=java.io.ByteArrayOutputStream().apply { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
        feature.remotePhotoBytes={bytes}
        api.profileValue=api.profileValue.copy(photos=listOf(ProfilePhotoDTO(UUID.randomUUID(),originalUrl="https://fixture.invalid/cover.png")))
        profile();shot("profile_cover")
        compose.onNodeWithTag("profile_cover_photo").performClick();window("profile_photo_viewer")
    }
    @Test fun blockHouseDialog() {
        profile();compose.onNodeWithTag("profile_more").performClick();compose.onNodeWithTag("profile_block").performClick()
        window("block_dialog")
    }
    @Test fun follows() { showDestination(TripsDestination.FollowList(SocialFixtures.account,FollowListMode.followers),dark);shot("followers") }
    @Test fun following() { showDestination(TripsDestination.FollowList(SocialFixtures.account,FollowListMode.following),dark);shot("following") }
    @Test fun followersEmpty() {
        api.peopleValue={_,_->SocialFollowersResponse(emptyList(),0)}
        showDestination(TripsDestination.FollowList(SocialFixtures.account,FollowListMode.followers),dark);shot("followers_empty")
    }
    @Test fun followersFailed() {
        api.peopleValue={_,_->throw ApiError.Transport("offline")}
        showDestination(TripsDestination.FollowList(SocialFixtures.account,FollowListMode.followers),dark);shot("followers_error")
    }
    @Test fun reportSent() {
        show(dark) { ReportSheet(ReportTarget.User(SocialFixtures.account),services) {} }
        compose.onNodeWithTag("report_reason_${ReportReason.entries.first().rawValue}").performClick()
        compose.onNodeWithTag("report_submit").performClick()
        compose.waitForIdle();compose.onNodeWithTag("report_sent").assertExists()
        window("report_sent")
    }
    @Test fun achievementsEmpty() {
        showDestination(TripsDestination.PublicAchievements(SocialFixtures.account,"Test Traveller",emptyList()),dark)
        compose.onNodeWithTag("achievements_filter_1").performClick()
        compose.onNodeWithTag("achievements_empty").assertExists()
        shot("achievements_empty")
    }
    @Test fun hiddenBadge() {
        val hidden=com.onezee.triptrack.logic.BadgeDefinitions.all.first { it.isHidden }.id
        showDestination(TripsDestination.PublicAchievements(SocialFixtures.account,"Test Traveller",emptyList()),dark)
        compose.onNodeWithTag("achievements_filter_2").performClick()
        compose.onNodeWithTag("achievement_$hidden").performClick()
        window("badge_hidden")
    }
    @Test fun garage() { showDestination(TripsDestination.PublicGarage(SocialFixtures.account,"Test Traveller"),dark);shot("garage") }
    @Test fun garageEmpty() { api.cars=emptyList();showDestination(TripsDestination.PublicGarage(SocialFixtures.account,"Test Traveller"),dark);shot("garage_empty") }
    @Test fun passport() { showDestination(TripsDestination.PublicCar(SocialFixtures.account,SocialFixtures.car.id,"Test Traveller",SocialFixtures.car),dark);shot("passport") }
    @Test fun passportHiddenMap() {
        api.cars=listOf(SocialFixtures.car.copy(mapVisible=false,photosVisible=false))
        api.publicPage={PublicTripsResponse(emptyList())}
        showDestination(TripsDestination.PublicCar(SocialFixtures.account,SocialFixtures.car.id,"Test Traveller"),dark);shot("passport_hidden")
    }
    @Test fun journeysEmpty() { showDestination(TripsDestination.PublicJourneys(SocialFixtures.account,"Test Traveller"),dark);shot("journeys_empty") }
    @Test fun achievementsAndDetail() {
        showDestination(TripsDestination.PublicAchievements(SocialFixtures.account,"Test Traveller",listOf("first_trip","iron_butt")),dark)
        shot("achievements")
        compose.onNodeWithTag("achievement_first_trip").performClick()
        window("badge_detail")
    }
    @Test fun levels() { showDestination(TripsDestination.PublicLevels(SocialFixtures.account,"Test Traveller",21,26400),dark);shot("levels") }
    private fun stats(remote:Boolean=true,configure:(StatsViewModel)->Unit={}) {
        show(dark) {
            CompositionLocalProvider(LocalInspectionMode provides true,LocalReduceMotion provides true) {
                val vm=rememberLocalViewModel("stats") { StatsViewModel(if(remote)StatsSource.Remote(api,SocialFixtures.account) else StatsSource.Own({SocialFixtures.cards.map { it.asTrip() }},{emptyList()}),Dispatchers.Main,{SocialFixtures.now},ZoneOffset.UTC) }
                LaunchedEffect(vm) { configure(vm) }
                StatsScreen(vm,if(remote)"Test Traveller" else null,{}, {_,_->})
            }
        }
    }
    @Test fun remoteStats() { stats();shot("stats_remote") }
    @Test fun ownStats() { stats(false);shot("stats_own") }
    @Test fun statsEmpty() { api.publicPage={PublicTripsResponse(emptyList())};stats();shot("stats_empty") }
    @Test fun statsError() { api.publicPage={throw ApiError.Transport("offline")};stats();shot("stats_error") }
    @Test fun statsPartial() {
        api.publicPage={if(it==null)PublicTripsResponse(listOf(SocialFixtures.publicTrip(0)),"next") else throw ApiError.Transport("offline")}
        stats();shot("stats_partial")
    }
    private fun map(selection:(PublicMapViewModel)->Unit={}) {
        val vm=PublicMapViewModel(SocialFixtures.account,null,api,Dispatchers.Main)
        onMain { vm.load().join();selection(vm) }
        show(dark) {
            CompositionLocalProvider(LocalSocial provides services,LocalReduceMotion provides true) {
                PublicMapScreen(TripsDestination.PublicMap(SocialFixtures.account,"Test Traveller"),services,feature.feedNavigator,vm)
            }
        }
    }
    @Test fun mapSummary() { map();shot("map_summary") }
    @Test fun mapRegion() { map { vm -> vm.state.value.exploration.regions.first().let { vm.select(AtlasSelection.Region(it.id),false) } };shot("map_region") }
    @Test fun mapTrips() { map { it.selectRoad(listOf(it.state.value.exploration.trips.first().id)) };shot("map_trips") }
    @Test fun mapEmpty() { api.publicPage={PublicTripsResponse(emptyList())};map();shot("map_empty") }
    @Test fun mapError() { api.publicPage={throw ApiError.Transport("offline")};map();shot("map_error") }
    @Test fun inbox() { showDestination(TripsDestination.Inbox,dark);shot("inbox") }
    @Test fun report() {
        show(dark) { ReportSheet(ReportTarget.User(SocialFixtures.account),services) {} }
        window("report")
    }
    @Test fun commentsEmpty() {
        show(dark) { TripCommentsSheet(SocialFixtures.trip(0).id,false,true,0,false,services,feature.feedNavigator) {} }
        window("comments_empty")
    }
    @Test fun commentsAndReply() {
        social.commentsPage={TripCommentsResponse(listOf(
            TripComment(UUID.fromString("77777777-7777-4777-8777-777777777777"),SocialFixtures.author,"Красивая дорога!",SocialFixtures.now,false),
            TripComment(UUID.fromString("77777777-7777-4777-8777-777777777778"),SocialFixtures.author,"Спасибо за маршрут",SocialFixtures.now.minusSeconds(3600),true)))}
        show(dark) { TripCommentsSheet(SocialFixtures.trip(0).id,false,true,2,false,services,feature.feedNavigator) {} }
        window("comments")
        compose.onAllNodesWithTag("comment_reply")[0].performClick();window("comment_reply")
    }
    @Test fun commentsArchived() {
        onMain { services.commentArchive.save(SocialFixtures.trip(0).id,listOf(TripComment(UUID.randomUUID(),SocialFixtures.author,"Сохранённое обсуждение",SocialFixtures.now,false))) }
        show(dark) { TripCommentsSheet(SocialFixtures.trip(0).id,true,false,1,false,services,feature.feedNavigator) {} }
        window("comments_archived")
    }
    @Test fun publicJourney() {
        social.journeyPage={SocialJourneyResponse(SocialJourneyHead(SocialFixtures.account,"По Франции",SocialFixtures.now.minusSeconds(86400),SocialFixtures.now,isPrivate=false,legCount=2,author=SocialFixtures.author),SocialFixtures.cards.take(2))}
        showDestination(TripsDestination.SocialJourney(SocialFixtures.account),dark);shot("public_journey")
    }
    @Test fun journeys() {
        api.journeysValue=listOf(PublicJourneyDto(SocialFixtures.account,"По Франции",SocialFixtures.now.minusSeconds(86400),SocialFixtures.now,legCount=2,
            legs=SocialFixtures.cards.take(2).map { PublicJourneyLeg(it.id,it.startDate,distance=it.distance,duration=it.duration,previewPolyline=it.previewPolyline) }))
        showDestination(TripsDestination.PublicJourneys(SocialFixtures.account,"Test Traveller"),dark);shot("journeys")
    }
    @Test fun inboxDecision() {
        api.notificationsValue=listOf(SocialFixtures.notice("companion_invite"))
        showDestination(TripsDestination.Inbox,dark);shot("inbox_invite")
        compose.onAllNodesWithText("LEAKED-TITLE").assertCountEquals(0)
    }
    @Test fun inboxReactions() {
        // The kind badge of a reaction is the drawn icon, a legacy key included (❤️ draws as 👍).
        api.notificationsValue=listOf(SocialFixtures.notice("reaction").copy(emoji="🔥"),SocialFixtures.notice("reaction").copy(emoji="❤️"),SocialFixtures.notice("comment"))
        showDestination(TripsDestination.Inbox,dark);shot("inbox_reactions")
    }
    @Test fun inboxEmpty() { api.notificationsValue=emptyList();showDestination(TripsDestination.Inbox,dark);shot("inbox_empty") }
    @Test fun profileClosed() { api.profileError=ApiError.UnknownServer("USER_NOT_FOUND","hidden");profile();shot("profile_closed") }
    @Test fun profileColdUnavailable() { api.profileError=ApiError.UnknownServer("USER_NOT_FOUND","hidden");showDestination(TripsDestination.PublicProfile(SocialFixtures.account),dark);shot("profile_unavailable") }
    @Test fun profileError() { api.profileError=ApiError.Transport("offline");profile();shot("profile_error") }
    @Test fun shareLinkSheet() {
        show(dark) { SharedTripLinkSheet(SocialFixtures.trip(0),"https://trip-track.app/s/abc123") {} };window("share_link")
    }
    @Test fun statsLoading() {
        api.publicPage={kotlinx.coroutines.awaitCancellation()};stats();shot("stats_loading")
    }
    @Test fun mapLoading() {
        api.publicPage={kotlinx.coroutines.awaitCancellation()}
        show(dark) { CompositionLocalProvider(LocalReduceMotion provides true) {
            val vm=rememberLocalViewModel("loading-map") { PublicMapViewModel(SocialFixtures.account,null,api,Dispatchers.Main) }
            PublicMapScreen(TripsDestination.PublicMap(SocialFixtures.account,"Test Traveller"),services,feature.feedNavigator,vm)
        } };shot("map_loading")
    }
    @Test fun inboxError() { api.inboxError=ApiError.Transport("offline");showDestination(TripsDestination.Inbox,dark);shot("inbox_error") }
    @Test fun passportPhotos() {
        val bmp=android.graphics.Bitmap.createBitmap(400,300,android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(66,115,130)) }
        val bytes=java.io.ByteArrayOutputStream().apply { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,this) }.toByteArray()
        feature.remotePhotoBytes={bytes}
        val first=PublicVehiclePhoto(UUID.fromString("10000000-0000-4000-8000-000000000011"),"https://fixture.invalid/car1.png",isMain=true)
        val second=PublicVehiclePhoto(UUID.fromString("10000000-0000-4000-8000-000000000012"),"https://fixture.invalid/car2.png",isMain=false)
        api.cars=listOf(SocialFixtures.car.copy(photos=listOf(first,second)))
        showDestination(TripsDestination.PublicCar(SocialFixtures.account,SocialFixtures.car.id,"Test Traveller"),dark);shot("passport_photos")
        compose.onNodeWithTag("public_vehicle_cover").performClick();window("vehicle_photo_viewer")
    }
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="dark={0}")
        fun themes():List<Array<Any>> = listOf(arrayOf(false),arrayOf(true))
    }
}
