package com.fairswap.app.ui

import androidx.navigation3.runtime.NavKey
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.sellerlistings.SellerMediaUploadFile
import com.fairswap.core.sellerlistings.SellerSession
import com.fairswap.feature.sellerlistings.MyListingsEvent
import com.fairswap.feature.sellerlistings.SellerListingEditorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairSwapAppTest {

    private val sellerSessionA = SellerSession("user-A", 1L)
    private val sellerSessionB = SellerSession("user-B", 2L)

    @Test
    fun `event from Create entry1 returns false when top is Create entry2`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = CreateSellerListingRoute(entryId = "entry-2"),
        )
        assertFalse(result)
    }

    @Test
    fun `event from Edit listingX entry1 returns false when top is Edit listingX entry2`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-2"),
        )
        assertFalse(result)
    }

    @Test
    fun `event returns false when liveUserId differs from activeSession`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-B",
            topRoute = CreateSellerListingRoute(entryId = "entry-1"),
        )
        assertFalse(result)
    }

    @Test
    fun `event returns false when top is MyListingsRoute or non-editor route`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = MyListingsRoute,
        )
        assertFalse(result)
    }

    @Test
    fun `exact Create entry session live-user match returns true`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = CreateSellerListingRoute(entryId = "entry-1"),
        )
        assertTrue(result)
    }

    @Test
    fun `exact Edit entry session live-user match returns true`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-1",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-1"),
        )
        assertTrue(result)
    }

    @Test
    fun `stale editor event entryId A cannot affect top route entryId B`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sellerSessionA,
            eventEntryId = "entry-A",
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-B"),
        )
        assertFalse(result)
    }

    @Test
    fun `consuming valid Saved or Submitted event pops top editor route and leaves single existing MyListingsRoute`() {
        val lowerStack = listOf<NavKey>(ListingDetailRoute("l-1"), MyListingsRoute)
        val initialStack = (lowerStack + EditSellerListingRoute("listing-X", "entry-X")).toMutableList()

        assertTrue(
            canConsumeSellerEditorEvent(
                eventSession = sellerSessionA,
                eventEntryId = "entry-X",
                activeSession = sellerSessionA,
                liveUserId = "user-A",
                topRoute = initialStack.lastOrNull(),
            )
        )
        handleSellerEditorEvent(
            event = SellerListingEditorEvent.Saved(
                session = sellerSessionA,
                entryId = "entry-X",
                listingId = "listing-X",
            ),
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = initialStack.lastOrNull(),
            backStack = initialStack,
        )

        assertEquals(lowerStack, initialStack)
        assertEquals(1, initialStack.count { it == MyListingsRoute })
    }

    @Test
    fun `consuming valid Created event replaces Create route with EditSellerListingRoute`() {
        val lowerStack = listOf<NavKey>(ListingDetailRoute("lower-listing"), MyListingsRoute)
        val originalEntry = CreateSellerListingRoute("entry-1")
        val initialStack = (lowerStack + originalEntry).toMutableList()

        assertTrue(
            canConsumeSellerEditorEvent(
                eventSession = sellerSessionA,
                eventEntryId = originalEntry.entryId,
                activeSession = sellerSessionA,
                liveUserId = "user-A",
                topRoute = initialStack.lastOrNull(),
            )
        )
        handleSellerEditorEvent(
            event = SellerListingEditorEvent.Created(
                session = sellerSessionA,
                entryId = originalEntry.entryId,
                listingId = "new-listing-id",
            ),
            activeSession = sellerSessionA,
            liveUserId = "user-A",
            topRoute = initialStack.lastOrNull(),
            backStack = initialStack,
        )

        assertTrue(initialStack.lastOrNull() is EditSellerListingRoute)
        val editEntry = initialStack.last() as EditSellerListingRoute
        assertEquals("new-listing-id", editEntry.listingId)
        assertTrue(editEntry.entryId.isNotBlank())
        assertTrue(editEntry.entryId != originalEntry.entryId)
        assertEquals(lowerStack + editEntry, initialStack)
        assertFalse(initialStack.contains(originalEntry))
        assertEquals(1, initialStack.count { it == MyListingsRoute })
    }

    @Test
    fun `handleSellerEditorEvent processes Saved and Submitted by popping editor route`() {
        val lowerStack = listOf<NavKey>(ListingDetailRoute("lower-listing"), MyListingsRoute)
        val editorRoutes = listOf<NavKey>(
            CreateSellerListingRoute("entry-1"),
            EditSellerListingRoute("l-1", "entry-1"),
        )
        val events = listOf<SellerListingEditorEvent>(
            SellerListingEditorEvent.Saved(
                session = sellerSessionA, entryId = "entry-1", listingId = "l-1",
            ),
            SellerListingEditorEvent.Submitted(session = sellerSessionA, entryId = "entry-1"),
        )
        for (route in editorRoutes) {
            for (event in events) {
                val stack = (lowerStack + route).toMutableList()
                handleSellerEditorEvent(
                    event = event,
                    activeSession = sellerSessionA,
                    liveUserId = "user-A",
                    topRoute = stack.lastOrNull(),
                    backStack = stack,
                )
                assertEquals("event=$event route=$route", lowerStack, stack)
                assertEquals(1, stack.count { it == MyListingsRoute })
            }
        }
    }

    @Test
    fun `handleSellerEditorEvent auth policy restricted events add AuthRoute when guarded`() {
        val events = listOf<SellerListingEditorEvent>(
            SellerListingEditorEvent.AuthRequired(session = sellerSessionA, entryId = "entry-1"),
            SellerListingEditorEvent.PolicyReviewRequired(session = sellerSessionA, entryId = "entry-1"),
            SellerListingEditorEvent.Restricted(session = sellerSessionA, entryId = "entry-1"),
        )
        val editorRoutes = listOf<NavKey>(
            CreateSellerListingRoute("entry-1"),
            EditSellerListingRoute("listing-X", "entry-1"),
        )
        val lowerStack = listOf<NavKey>(ListingDetailRoute("lower-listing"), MyListingsRoute)

        data class RejectedIdentity(
            val label: String,
            val activeSession: SellerSession?,
            val liveUserId: String?,
        )
        val rejectedIdentities = listOf(
            RejectedIdentity("different account", sellerSessionB, "user-B"),
            RejectedIdentity("older generation", SellerSession("user-A", 2L), "user-A"),
            RejectedIdentity("wrong live user", sellerSessionA, "user-B"),
            RejectedIdentity("signed out", sellerSessionA, null),
            RejectedIdentity("no active session", null, "user-A"),
        )
        val rejectedRoutes = listOf<NavKey>(
            CreateSellerListingRoute("entry-2"),
            EditSellerListingRoute("listing-X", "entry-2"),
            MyListingsRoute,
            ListingDetailRoute("not-an-editor"),
        )

        for (event in events) {
            for (route in editorRoutes) {
                val initial = lowerStack + route
                val stack = initial.toMutableList()
                handleSellerEditorEvent(
                    event = event,
                    activeSession = sellerSessionA,
                    liveUserId = "user-A",
                    topRoute = stack.lastOrNull(),
                    backStack = stack,
                )
                val expected = initial + AuthRoute
                assertEquals("eligible event=$event route=$route", expected, stack)

                // A repeated handoff must not append another AuthRoute.
                handleSellerEditorEvent(
                    event = event,
                    activeSession = sellerSessionA,
                    liveUserId = "user-A",
                    topRoute = stack.lastOrNull(),
                    backStack = stack,
                )
                assertEquals("duplicate event=$event route=$route", expected, stack)
                assertEquals(1, stack.count { it == AuthRoute })

                for (identity in rejectedIdentities) {
                    val rejectedStack = initial.toMutableList()
                    handleSellerEditorEvent(
                        event = event,
                        activeSession = identity.activeSession,
                        liveUserId = identity.liveUserId,
                        topRoute = rejectedStack.lastOrNull(),
                        backStack = rejectedStack,
                    )
                    assertEquals("${identity.label} event=$event route=$route", initial, rejectedStack)
                }
            }
            for (route in rejectedRoutes) {
                val initial = lowerStack + route
                val stack = initial.toMutableList()
                handleSellerEditorEvent(
                    event = event,
                    activeSession = sellerSessionA,
                    liveUserId = "user-A",
                    topRoute = stack.lastOrNull(),
                    backStack = stack,
                )
                assertEquals("wrong entry/route event=$event route=$route", initial, stack)
            }
        }
    }

    @Test
    fun `handleMyListingsEvent consumes AuthRequired only for matching session and user`() {
        val events = listOf<MyListingsEvent>(
            MyListingsEvent.AuthRequired(session = sellerSessionA),
            MyListingsEvent.PolicyReviewRequired(session = sellerSessionA),
        )
        val initial = listOf<NavKey>(ListingDetailRoute("lower-listing"), MyListingsRoute)

        data class RejectedIdentity(
            val label: String,
            val activeSession: SellerSession?,
            val liveUserId: String?,
        )
        val rejectedIdentities = listOf(
            RejectedIdentity("different account", sellerSessionB, "user-B"),
            RejectedIdentity("older generation", SellerSession("user-A", 2L), "user-A"),
            RejectedIdentity("wrong live user", sellerSessionA, "user-B"),
            RejectedIdentity("signed out", sellerSessionA, null),
            RejectedIdentity("no active session", null, "user-A"),
        )
        for (event in events) {
            val stack = initial.toMutableList()
            handleMyListingsEvent(
                event = event,
                activeSession = sellerSessionA,
                liveUserId = "user-A",
                topRoute = stack.lastOrNull(),
                backStack = stack,
            )
            val expected = initial + AuthRoute
            assertEquals("eligible event=$event", expected, stack)
            handleMyListingsEvent(
                event = event,
                activeSession = sellerSessionA,
                liveUserId = "user-A",
                topRoute = stack.lastOrNull(),
                backStack = stack,
            )
            assertEquals("duplicate event=$event", expected, stack)
            assertEquals(1, stack.count { it == AuthRoute })

            for (identity in rejectedIdentities) {
                val rejectedStack = initial.toMutableList()
                handleMyListingsEvent(
                    event = event,
                    activeSession = identity.activeSession,
                    liveUserId = identity.liveUserId,
                    topRoute = rejectedStack.lastOrNull(),
                    backStack = rejectedStack,
                )
                assertEquals("${identity.label} event=$event", initial, rejectedStack)
            }
        }
    }

    @Test
    fun `createSellerEditorCallbacks forwards all eleven media and retry callbacks`() {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        fun record(action: String, vararg arguments: Any?) {
            calls.add(action to arguments.toList())
        }
        fun assertSingleCall(action: String, arguments: List<Any?>, invoke: () -> Unit) {
            calls.clear()
            invoke()
            assertEquals(action, listOf(action to arguments), calls)
        }
        val callbacks = createSellerEditorCallbacks(
            onRetryInitialization = { record("retryInitialization") },
            onRetryCategoryAttributes = { record("retryCategoryAttributes") },
            onRetryLocalities = { record("retryLocalities") },
            onValidatedPhotos = { record("queueUploads", it) },
            onSetCover = { record("setCoverPhoto", it) },
            onDeleteMedia = { record("deleteMedia", it) },
            onMoveLeft = { record("moveMediaLeft", it) },
            onMoveRight = { record("moveMediaRight", it) },
            onRetryMedia = { record("retryMediaSnapshot") },
            onImageError = { first, second -> record("onPrivateImageLoadFailed", first, second) },
            onLocalMediaError = { record("setMediaErrorMessage", it) },
        )

        assertSingleCall("retryInitialization", emptyList()) { callbacks.onRetryInitialization() }
        assertSingleCall("retryCategoryAttributes", emptyList()) { callbacks.onRetryCategoryAttributes() }
        assertSingleCall("retryLocalities", emptyList()) { callbacks.onRetryLocalities() }
        val dummyFiles = listOf(SellerMediaUploadFile("photo.jpg", ByteArray(0), "image/jpeg"))
        assertSingleCall("queueUploads", listOf(dummyFiles)) { callbacks.onValidatedPhotos(dummyFiles) }
        assertTrue(calls.single().second.single() === dummyFiles)
        assertSingleCall("setCoverPhoto", listOf("m-1")) { callbacks.onSetCover("m-1") }
        assertSingleCall("deleteMedia", listOf("m-2")) { callbacks.onDeleteMedia("m-2") }
        assertSingleCall("moveMediaLeft", listOf("m-3")) { callbacks.onMoveLeft("m-3") }
        assertSingleCall("moveMediaRight", listOf("m-4")) { callbacks.onMoveRight("m-4") }
        assertSingleCall("retryMediaSnapshot", emptyList()) { callbacks.onRetryMedia() }
        assertSingleCall("onPrivateImageLoadFailed", listOf("m-5", "load error")) {
            callbacks.onImageError("m-5", "load error")
        }
        assertSingleCall("setMediaErrorMessage", listOf("file too large")) {
            callbacks.onLocalMediaError("file too large")
        }
    }

    @Test
    fun sellerSessionEventGuardValidatesExactSessionAndUser() {
        assertTrue(
            canConsumeSellerSessionEvent(
                eventSession = sellerSessionA,
                activeSession = sellerSessionA,
                liveUserId = "user-A"
            )
        )
        assertFalse(
            canConsumeSellerSessionEvent(
                eventSession = sellerSessionA,
                activeSession = sellerSessionB,
                liveUserId = "user-B"
            )
        )
    }

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
