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

    @Test
    fun notificationEventGuardMatrixTest() {
        val sessionA = CommunicationSession("user-1", generation = 1L)
        val sessionAOldGen = CommunicationSession("user-1", generation = 0L)
        val sessionB = CommunicationSession("user-2", generation = 2L)

        val routeNotifs = NotificationsRoute
        val routeMessages = MessagesRoute
        val routeConversation = ConversationRoute("conv-123")
        val routeMyListings = MyListingsRoute

        // 1. Matching session + active session + live user + NotificationsRoute -> true
        assertTrue(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                topRoute = routeNotifs,
            )
        )

        // 2. Different CommunicationSession -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionB,
                liveUserId = "user-1",
                topRoute = routeNotifs,
            )
        )

        // 3. Same user but old generation -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionAOldGen,
                activeSession = sessionA,
                liveUserId = "user-1",
                topRoute = routeNotifs,
            )
        )

        // 4. Different live user -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-different",
                topRoute = routeNotifs,
            )
        )

        // 5. Null active session -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = null,
                liveUserId = "user-1",
                topRoute = routeNotifs,
            )
        )

        // 6. Null live user -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = null,
                topRoute = routeNotifs,
            )
        )

        // 7. Top route MessagesRoute -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                topRoute = routeMessages,
            )
        )

        // 8. Top route ConversationRoute -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                topRoute = routeConversation,
            )
        )

        // 9. Top route MyListingsRoute -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-1",
                topRoute = routeMyListings,
            )
        )

        // 10. Account-A event after active account-B switch -> false
        assertFalse(
            canConsumeNotificationEvent(
                eventSession = sessionA,
                activeSession = sessionB,
                liveUserId = "user-2",
                topRoute = routeNotifs,
            )
        )
    }
}
