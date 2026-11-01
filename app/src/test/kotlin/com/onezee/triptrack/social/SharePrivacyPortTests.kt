// iOS: TripDetailSharePrivacyTests.swift (the four sharing cases).
package com.onezee.triptrack.social
import com.onezee.triptrack.feature.social.canOfferShare
import kotlin.test.*
import org.junit.Test
class SharePrivacyPortTests {
    @Test fun ownPublic() { assertTrue(canOfferShare(true,false)) }
    @Test fun ownPrivatePublishesAndShares() { assertTrue(canOfferShare(true,true)) }
    @Test fun foreignPublic() { assertTrue(canOfferShare(false,false)) }
    @Test fun foreignPrivateNeverShares() { assertFalse(canOfferShare(false,true)) }
}
