package com.fairswap.feature.listingdetail

import com.fairswap.core.communication.ChatMessage
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.ConversationSummary
import com.fairswap.core.communication.InAppNotification
import com.fairswap.core.communication.MessageHistoryCursor
import com.fairswap.core.communication.MessageRealtimeEvent
import com.fairswap.core.communication.NotificationRealtimeEvent
import com.fairswap.core.communication.StartConversationResult
import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicListingDetail
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicSellerProfile
import com.fairswap.core.discovery.PublicSellerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListingDetailViewModelTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val sellerId = "00000000-0000-0000-0000-000000000002"

    @Test
    fun successClearsLoadingAndRetryKeepsDataOnFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val commRepo = FakeCommunicationRepository()
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
            source.listing = { error("private backend detail") }
            model.retry()
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
            assertFalse(model.state.value.error.orEmpty().contains("private backend detail"))
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun unavailableAndExceptionHaveDistinctTerminalStates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { listing = { PublicDetailResult.Unavailable } }
            val commRepo = FakeCommunicationRepository()
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)
            advanceUntilIdle()
            assertTrue(model.state.value.unavailable)
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            source.listing = { error("private backend detail") }
            model.retry()
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertEquals("Unable to load this listing. Retry.", model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun timeoutAndCancellationReleaseLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { listing = { awaitCancellation() } }
            val commRepo = FakeCommunicationRepository()
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)
            runCurrent()
            advanceTimeBy(15_001)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals("Unable to load this listing. Retry.", model.state.value.error)
            model.load("00000000-0000-0000-0000-000000000003")
            runCurrent()
            source.listing = { PublicDetailResult.Found(detail()) }
            model.load(id)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun signedOutMessageSellerEmitsNavigateToAuth() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val source = FakeSource()
            val commRepo = FakeCommunicationRepository()
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)

            val events = mutableListOf<ListingDetailMessagingEvent>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                model.messagingEvents.collect { events.add(it) }
            }

            model.onMessageSellerClicked(null)

            assertEquals(1, events.size)
            assertTrue(events[0] is ListingDetailMessagingEvent.NavigateToAuth)
            job.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun existingConversationNavigatesToConversationRoute() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val source = FakeSource()
            val commRepo = FakeCommunicationRepository()
            val existingConv = ConversationSummary("c1", id, "Bike", "buyer-1", sellerId, sellerId, "Seller", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
            commRepo.existingConversationResult = CommunicationResult.Success(existingConv)

            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)

            val session = CommunicationSession("buyer-1", 1L)
            val events = mutableListOf<ListingDetailMessagingEvent>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                model.messagingEvents.collect { events.add(it) }
            }

            model.onMessageSellerClicked(session)

            assertEquals(1, events.size)
            assertTrue(events[0] is ListingDetailMessagingEvent.NavigateToConversation)
            val nav = events[0] as ListingDetailMessagingEvent.NavigateToConversation
            assertEquals("c1", nav.conversationId)
            job.cancel()
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun noConversationNavigatesToNewInquiryRoute() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val source = FakeSource()
            val commRepo = FakeCommunicationRepository()
            commRepo.existingConversationResult = CommunicationResult.Success(null)

            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source), commRepo)
            model.load(id)

            val session = CommunicationSession("buyer-1", 1L)
            val events = mutableListOf<ListingDetailMessagingEvent>()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                model.messagingEvents.collect { events.add(it) }
            }

            model.onMessageSellerClicked(session)

            assertEquals(1, events.size)
            assertTrue(events[0] is ListingDetailMessagingEvent.NavigateToNewInquiry)
            val nav = events[0] as ListingDetailMessagingEvent.NavigateToNewInquiry
            assertEquals(id, nav.listingId)
            job.cancel()
        } finally { Dispatchers.resetMain() }
    }

    private fun detail() = PublicListingDetail(
        id, sellerId, "Bike", null, "Good", "fixed", 1250, "₹12.50", "Cycles", "Jaipur", null,
        emptyList(), null, "2026-09-01T00:00:00Z",
        PublicSellerProfile(sellerId, "Seller", null, null, null, "2026-09-01T00:00:00Z"), emptyList(),
    )

    private fun fakeFavouritesRepository(source: FakeSource) = com.fairswap.core.favourites.FavouritesRepository(
        FakeFavouritesGateway(), source, com.fairswap.core.favourites.FavouriteStateStore()
    )

    private inner class FakeFavouritesGateway : com.fairswap.core.favourites.FavouritesGateway {
        override suspend fun currentUserId() = null
        override suspend fun policyAccepted() = false
        override suspend fun isVisible(listingId: String) = false
        override suspend fun page(userId: String, offset: Int, size: Int) = emptyList<com.fairswap.core.favourites.FavouriteRow>()
        override suspend fun existing(userId: String, listingIds: List<String>) = emptySet<String>()
        override suspend fun insert(userId: String, listingId: String) = Unit
        override suspend fun delete(userId: String, listingIds: List<String>) = Unit
    }

    private inner class FakeSource : PublicMarketplaceDataSource {
        var listing: suspend () -> PublicDetailResult = { PublicDetailResult.Found(detail()) }
        override suspend fun listing(id: String) = listing.invoke()
        override suspend fun seller(id: String) = PublicSellerResult.Unavailable
        override suspend fun publicCards(ids: List<String>) = emptyMap<String, com.fairswap.core.discovery.ListingCard>()
    }

    private inner class FakeCommunicationRepository : CommunicationRepository {
        var existingConversationResult: CommunicationResult<ConversationSummary?> = CommunicationResult.Success(null)
        val findExistingCallCount = AtomicInteger(0)

        override suspend fun findExistingConversation(session: CommunicationSession, listingId: String): CommunicationResult<ConversationSummary?> {
            findExistingCallCount.incrementAndGet()
            return existingConversationResult
        }

        override suspend fun fetchConversation(session: CommunicationSession, conversationId: String): CommunicationResult<ConversationSummary> {
            return CommunicationResult.Failure(CommunicationFailure.ConversationUnavailable)
        }

        override suspend fun fetchInbox(session: CommunicationSession): CommunicationResult<List<ConversationSummary>> {
            return CommunicationResult.Success(emptyList())
        }

        override suspend fun fetchNewestMessages(session: CommunicationSession, conversationId: String): CommunicationResult<List<ChatMessage>> {
            return CommunicationResult.Success(emptyList())
        }

        override suspend fun fetchOlderMessages(session: CommunicationSession, conversationId: String, cursor: MessageHistoryCursor): CommunicationResult<List<ChatMessage>> {
            return CommunicationResult.Success(emptyList())
        }

        override suspend fun startConversation(session: CommunicationSession, listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResult> {
            return CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
        }

        override suspend fun sendMessage(session: CommunicationSession, conversationId: String, content: String, clientRequestId: String): CommunicationResult<ChatMessage> {
            return CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
        }

        override suspend fun markConversationRead(session: CommunicationSession, conversationId: String, messageId: String): CommunicationResult<Unit> {
            return CommunicationResult.Success(Unit)
        }

        override suspend fun fetchNotifications(session: CommunicationSession): CommunicationResult<List<InAppNotification>> {
            return CommunicationResult.Success(emptyList())
        }

        override suspend fun markNotificationRead(session: CommunicationSession, notificationId: String): CommunicationResult<Unit> {
            return CommunicationResult.Success(Unit)
        }

        override suspend fun markAllNotificationsRead(session: CommunicationSession): CommunicationResult<Unit> {
            return CommunicationResult.Success(Unit)
        }

        override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> = emptyFlow()

        override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> = emptyFlow()
    }
}
