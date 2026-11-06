package com.onezee.triptrack.feature.settings

import android.content.Context
import com.onezee.triptrack.designsystem.R
import com.onezee.triptrack.feature.me.PlusEntitlementApi
import com.onezee.triptrack.feature.me.PlusEntitlementStore
import com.onezee.triptrack.model.MeResponse
import com.onezee.triptrack.model.PlusStatusResponse
import com.onezee.triptrack.network.ApiError
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PromoCodeErrorTests {
    @Test fun eachBackendPromoCodeHasItsOwnLocalizedMessage() {
        val mappings = mapOf(
            "PROMO_CODE_NOT_FOUND" to R.string.promo_not_found,
            "PROMO_CODE_ALREADY_REDEEMED" to R.string.promo_already_redeemed,
            "PROMO_CODE_INACTIVE" to R.string.promo_inactive,
            "PROMO_CODE_EXPIRED" to R.string.promo_expired,
            "PROMO_CODE_USED_UP" to R.string.promo_used_up,
            "TOO_MANY_REQUESTS" to R.string.promo_too_many,
            "VALIDATION_FAILED" to R.string.promo_validation,
        )
        mappings.forEach { (code, id) -> assertEquals(code, id, promoError(code)) }
    }

    @Test fun expiredOrMissingSessionUsesTheSignedOutState() {
        assertTrue(promoSessionExpired(ApiError.UserNotAuth))
        assertTrue(promoSessionExpired(ApiError.InvalidRefreshToken))
        assertEquals(false, promoSessionExpired(ApiError.Network(com.onezee.triptrack.network.NetworkFailure.offline, java.net.UnknownHostException())))
    }

    @Test fun alreadyRedeemedRefreshesTheCurrentAccountStatus() = runTest {
        val api = ActivePlusApi()
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("promo-already-redeemed", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        val store = PlusEntitlementStore(preferences, api, backgroundScope)
        val account = UUID.fromString("11111111-1111-4111-8111-111111111111")
        store.setAccount(account)
        runCurrent()
        val callsBeforeRefresh = api.statusCalls

        val current = refreshAfterAlreadyRedeemed(store)

        assertEquals(callsBeforeRefresh + 1, api.statusCalls)
        assertTrue(current?.active == true)
        assertEquals(account, current?.accountId)
    }

    private class ActivePlusApi : PlusEntitlementApi {
        var statusCalls = 0
        override suspend fun status(): PlusStatusResponse {
            statusCalls++
            return PlusStatusResponse(active = true, until = Instant.now().plusSeconds(3_600))
        }
        override suspend fun me() = MeResponse(isPublic = true)
    }
}
