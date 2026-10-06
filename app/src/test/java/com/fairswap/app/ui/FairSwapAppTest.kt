package com.fairswap.app.ui

import com.fairswap.core.communication.CommunicationSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairSwapAppTest {

    @Test
    fun messagingEventGuardValidatesExactSessionUserAndRoute() {
        val sessionA = CommunicationSession("user-1", 1L)
        val sessionStale = CommunicationSession("user-1", 0L)
        val sessionB = CommunicationSession("user-2", 2L)

        val routeDetail = ListingDetailRoute("list-100")
        val routeOtherDetail = ListingDetailRoute("list-200")
        val routeInquiry = NewInquiryRoute("list-100")

        // 1. Exact session + exact user + matching listing route -> accepted
        assertTrue(
            canConsumeMessagingEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                eventListingId = "list-100",
                topRoute = routeDetail,
            )
        )

        // 2. Stale communication generation -> rejected
        assertFalse(
            canConsumeMessagingEvent(
                eventSession = sessionStale,
                activeSession = sessionA,
                liveUserId = "user-1",
                eventListingId = "list-100",
                topRoute = routeDetail,
            )
        )

        // 3. Different user -> rejected
        assertFalse(
            canConsumeMessagingEvent(
                eventSession = sessionA,
                activeSession = sessionB,
                liveUserId = "user-2",
                eventListingId = "list-100",
                topRoute = routeDetail,
            )
        )

        // 4. Mismatched listing route -> rejected
        assertFalse(
            canConsumeMessagingEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                eventListingId = "list-100",
                topRoute = routeOtherDetail,
            )
        )

        // 5. NewInquiry route matching listing -> accepted
        assertTrue(
            canConsumeMessagingEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                eventListingId = "list-100",
                topRoute = routeInquiry,
            )
        )
    }

    @Test
    fun messagingEventFromAccountARejectedAfterAccountBSwitch() {
        val sessionAccountA = CommunicationSession("user-account-a", 1L)
        val sessionAccountB = CommunicationSession("user-account-b", 2L)
        val routeDetail = ListingDetailRoute("list-100")

        // Account A event emitted when active session is Account B -> REJECTED
        assertFalse(
            canConsumeMessagingEvent(
                eventSession = sessionAccountA,
                activeSession = sessionAccountB,
                liveUserId = "user-account-b",
                eventListingId = "list-100",
                topRoute = routeDetail,
            )
        )
    }
}
