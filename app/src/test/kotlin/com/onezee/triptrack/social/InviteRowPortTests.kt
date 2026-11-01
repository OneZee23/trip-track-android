// iOS: NotificationsInviteRowTests.swift; same kinds, conditions and five-page cap.
package com.onezee.triptrack.social

import com.onezee.triptrack.feature.social.*
import com.onezee.triptrack.model.*
import kotlin.test.*
import org.junit.Test

class InviteRowPortTests {
    private val item=SocialFixtures.notice("companion_invite")
    @Test fun pendingIsDecision() { assertTrue(NotificationInviteRows.isDecision(item,null,InvitePreviewState.Loading)) }
    @Test fun acceptedIsInfo() { assertFalse(NotificationInviteRows.isDecision(item,CompanionStatus.accepted,InvitePreviewState.Loading)) }
    @Test fun declinedIsInfo() { assertFalse(NotificationInviteRows.isDecision(item,CompanionStatus.declined,InvitePreviewState.Loading)) }
    @Test fun unknownKindIsGeneric() { assertFalse(NotificationInviteRows.isDecision(item.copy(kind="some_future_kind_v7"),null,InvitePreviewState.Loading)) }
    @Test fun companionAcceptedIsInfo() { assertFalse(NotificationInviteRows.isDecision(item.copy(kind="companion_accepted"),null,InvitePreviewState.Loading)) }
    @Test fun goneInviteIsInfoWithoutLocalResponse() { assertFalse(NotificationInviteRows.isDecision(item,null,InvitePreviewState.Unavailable)) }
    @Test fun cancelledIsNotFailure() { assertNull(NotificationInviteRows.stateAfterFailure(true,false)) }
    @Test fun cancelledWinsOverGone() { assertNull(NotificationInviteRows.stateAfterFailure(true,true)) }
    @Test fun goneBecomesUnavailable() { assertEquals(InvitePreviewState.Unavailable,NotificationInviteRows.stateAfterFailure(false,true)) }
    @Test fun ordinaryFailureIsRetryable() { assertEquals(InvitePreviewState.Failed,NotificationInviteRows.stateAfterFailure(false,false)) }
    @Test fun stopsOnFailedPage() { assertFalse(NotificationInviteRows.shouldContinuePaging(false,true,true,1,5)) }
    @Test fun stopsWhenFound() { assertFalse(NotificationInviteRows.shouldContinuePaging(true,false,true,1,5)) }
    @Test fun stopsWhenNoMore() { assertFalse(NotificationInviteRows.shouldContinuePaging(false,false,false,1,5)) }
    @Test fun stopsAtPageCap() { assertFalse(NotificationInviteRows.shouldContinuePaging(false,false,true,5,5)) }
    @Test fun continuesOnlyWhenEligible() { assertTrue(NotificationInviteRows.shouldContinuePaging(false,false,true,1,5)) }
}
