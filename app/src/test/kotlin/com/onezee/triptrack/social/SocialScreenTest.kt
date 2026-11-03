// iOS: PublicProfileView's offline URLProtocol fixture, hosted in the real trip navigation stack.
package com.onezee.triptrack.social

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import com.onezee.triptrack.feature.*
import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.trips.TripsScreenTest
import com.onezee.triptrack.ui.navigation.TabNavHost
import com.onezee.triptrack.designsystem.theme.LocalReduceMotion
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment

abstract class SocialScreenTest : TripsScreenTest() {
    internal val api=FakeSocialApi()
    /** The viewer's stored flag, read by the own preview of the profile (iOS SettingsManager.profileCountry). */
    protected var ownCountryValue=""
    protected lateinit var services: SocialFeature
    @Before fun socialWorld() {
        signIn()
        services=makeSocial(api)
    }
    internal fun makeSocial(api:SocialApi, loadRemoteConfig:suspend()->com.onezee.triptrack.network.RemoteConfig?={null}, page:suspend(String)->String={""})=SocialFeature(api,feature,null,{owner},
        RuntimeEnvironment.getApplication().getSharedPreferences("social-tests",Context.MODE_PRIVATE),page,Dispatchers.Main,{SocialFixtures.now},
        ownCountry={ownCountryValue}, ownAuthor={com.onezee.triptrack.model.SocialAuthor(owner,"Driver","🚗",7)}, commentArchive=MemoryCommentArchive(),
        loadRemoteConfig=loadRemoteConfig)
    @After fun socialClosed() { services.close() }
    protected fun showDestination(destination:TripsDestination,dark:Boolean=false) {
        onMain { feature.feedNavigator.push(destination) }
        show(dark) {
            CompositionLocalProvider(LocalSocial provides services,LocalInspectionMode provides true,LocalReduceMotion provides true) {
                TabNavHost(feature.feedNavigator,root={}) { TripsDestinationScreen(it,feature,feature.feedNavigator) }
            }
        }
    }
}
