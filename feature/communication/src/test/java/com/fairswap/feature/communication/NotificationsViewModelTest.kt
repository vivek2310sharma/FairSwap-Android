package com.fairswap.feature.communication

import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.InAppNotification
import com.fairswap.core.communication.NotificationRealtimeEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {

    private lateinit var repository: FakeFeatureCommunicationRepository
    private lateinit var viewModel: NotificationsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeFeatureCommunicationRepository()
        viewModel = NotificationsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bindSessionLoadsNotifications() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "New Message", "Hello", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))

        viewModel.bind(session)

        val state = viewModel.uiState.value
        assertEquals(session, state.boundSession)
        assertEquals(1, state.notifications.size)
        assertEquals("New Message", state.notifications[0].title)
        assertTrue(state.hasUnread)
    }

    @Test
    fun refreshFailureRetainsPreviouslySuccessfulNotifications() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "New Message", "Hello", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))

        viewModel.bind(session)
        assertEquals(1, viewModel.uiState.value.notifications.size)

        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(1, state.notifications.size)
        assertNull(state.notifications[0].readAt)
        assertEquals("You're offline or the network is unavailable. Try again.", state.errorMessage)
        assertFalse(state.isLoading)
    }

    @Test
    fun sessionChangeClearsPreviousDataImmediately() = runTest {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "Alert 1", "Body 1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val notif2 = InAppNotification("n2", "user-2", "listing_rejected", "Alert 2", "Body 2", "/account/listings", null, "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(s1)
        assertEquals(1, viewModel.uiState.value.notifications.size)

        repository.notificationsResult = CommunicationResult.Success(listOf(notif2))
        viewModel.bind(s2)

        val state = viewModel.uiState.value
        assertEquals(s2, state.boundSession)
        assertEquals(1, state.notifications.size)
        assertEquals("n2", state.notifications[0].id)
    }

    @Test
    fun immediateCompletionClearsPendingStateWithoutLeavingCompletedJobLock() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "Alert 1", "Body", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)

        // Mark-read returns NetworkUnavailable immediately without suspension
        repository.markNotificationReadResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)

        viewModel.onNotificationTapped("n1")

        // Verify pending state cleared
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertEquals(1, repository.markNotificationReadCallCount.get())

        // Retrying same unread ID calls repository again (no completed-job lock remains)
        viewModel.onNotificationTapped("n1")
        assertEquals(2, repository.markNotificationReadCallCount.get())
    }

    @Test
    fun concurrentMarkReadForDifferentIdsAreIndependentAndDoNotCancelEachOther() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val n1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val n2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(n1, n2))
        viewModel.bind(session)

        val gate1 = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadGates["n2"] = gate2

        // Tap notification 1 -> starts mutation 1
        viewModel.onNotificationTapped("n1")
        assertTrue("n1" in viewModel.uiState.value.mutatingIds)

        // Tap notification 1 again while active -> duplicate tap ignored (1 call)
        viewModel.onNotificationTapped("n1")
        assertEquals(1, repository.markNotificationReadCallCount.get())

        // Tap notification 2 while n1 is suspended -> starts mutation 2 without cancelling n1
        viewModel.onNotificationTapped("n2")
        assertEquals(2, repository.markNotificationReadCallCount.get())
        assertTrue("n1" in viewModel.uiState.value.mutatingIds)
        assertTrue("n2" in viewModel.uiState.value.mutatingIds)

        // Releasing n2 clears only n2's pending state
        gate2.complete(Unit)
        assertFalse("n2" in viewModel.uiState.value.mutatingIds)
        assertTrue("n1" in viewModel.uiState.value.mutatingIds)

        // Releasing n1 clears n1
        gate1.complete(Unit)
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
    }

    @Test
    fun rebindWithSuspendedRowMutationsCancelsOldJobsAndClearsPendingState() = runTest {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val n1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val n2 = InAppNotification("n2", "user-2", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(n1))
        viewModel.bind(s1)

        val gate1 = CompletableDeferred<Unit>()
        repository.markNotificationReadGates["n1"] = gate1
        viewModel.onNotificationTapped("n1")
        assertTrue("n1" in viewModel.uiState.value.mutatingIds)

        // Rebind to s2 while n1 is suspended
        repository.notificationsResult = CommunicationResult.Success(listOf(n2))
        viewModel.bind(s2)

        // Verify pending state cleared immediately for new session
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertEquals(s2, viewModel.uiState.value.boundSession)

        // Complete old n1 mutation -> does not alter s2 state
        gate1.complete(Unit)
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertEquals("n2", viewModel.uiState.value.notifications[0].id)
    }

    @Test
    fun olderFetchResultCannotOverwriteNewerAcceptedSnapshot() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val readNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        val fetchGate1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGate = fetchGate1
        viewModel.bind(session)

        // Trigger newer fetch R2 that completes with readNotif snapshot
        repository.fetchNotificationsGate = null
        repository.notificationsResult = CommunicationResult.Success(listOf(readNotif))
        viewModel.refresh()

        // R2 completes and is applied
        assertFalse(viewModel.uiState.value.hasUnread)

        // Now older R1 completes with unreadNotif
        repository.notificationsResult = CommunicationResult.Success(listOf(unreadNotif))
        fetchGate1.complete(Unit)

        // R1 is rejected because revision 1 < lastAppliedRevision 2
        assertFalse(viewModel.uiState.value.hasUnread)
    }

    @Test
    fun onVisibleBeforeBindIsRememberedAndStartsVisibleLifecycleOnBind() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))

        // onVisible called BEFORE bind
        viewModel.onVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())

        // bind called -> remembered visibility starts visible lifecycle and subscription
        viewModel.bind(session)
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertEquals(1, viewModel.uiState.value.notifications.size)
    }

    @Test
    fun visibleFetchCompletesBeforeRealtimeSubscriptionBegins() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val fetchGate = CompletableDeferred<Unit>()
        repository.fetchNotificationsGate = fetchGate

        viewModel.bind(session)
        viewModel.onVisible()

        // While fetch is suspended, active collectors = 0
        assertEquals(0, repository.activeNotificationCollectorsCount.get())

        // Complete fetch
        fetchGate.complete(Unit)

        // After fetch completes, active collectors = 1
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun activeRealtimeCollectorsFollowLifecycleZeroOneZeroOne() = runTest {
        val session = CommunicationSession("user-1", 1L)

        // bind alone -> 0 active collectors
        viewModel.bind(session)
        assertEquals(0, repository.activeNotificationCollectorsCount.get())

        // first onVisible -> 1 active collector
        viewModel.onVisible()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // onNotVisible -> 0 active collectors
        viewModel.onNotVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())

        // second onVisible -> 1 active collector
        viewModel.onVisible()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertEquals(2, repository.observedNotificationSubscriptions.size)
    }

    @Test
    fun realtimeReadyForBackfillAndInsertedTriggersAuthoritativeReconciliation() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "Alert", "Body", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))

        viewModel.bind(session)
        viewModel.onVisible()

        val initialCalls = repository.fetchNotificationsCallCount.get()

        // Inserted for same user -> triggers fetch
        val notif2 = InAppNotification("n2", "user-1", "listing_rejected", "Alert 2", "Body 2", "/account/listings", null, "2026-10-06T10:05:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1, notif2))

        repository.notificationEventFlow.emit(NotificationRealtimeEvent.Inserted(notif2))
        assertEquals(initialCalls + 1, repository.fetchNotificationsCallCount.get())
        assertEquals(2, viewModel.uiState.value.notifications.size)

        // Inserted for WRONG user -> no extra fetch or mutation
        val wrongUserNotif = InAppNotification("n3", "wrong-user", "listing_rejected", "Alert 3", "Body 3", "/account/listings", null, "2026-10-06T10:06:00Z")
        repository.notificationEventFlow.emit(NotificationRealtimeEvent.Inserted(wrongUserNotif))
        assertEquals(initialCalls + 1, repository.fetchNotificationsCallCount.get())
        assertEquals(2, viewModel.uiState.value.notifications.size)

        // ReadyForBackfill -> triggers fetch
        repository.notificationEventFlow.emit(NotificationRealtimeEvent.ReadyForBackfill)
        assertEquals(initialCalls + 2, repository.fetchNotificationsCallCount.get())
    }

    @Test
    fun unreadTapAwaitsMarkReadAndAuthoritativeReloadBeforeNavigating() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "New Message", "Hello", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val readNotif = InAppNotification("n1", "user-1", "new_message", "New Message", "Hello", "/account/messages/$convUuid", "2026-10-06T10:01:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        val markReadGate = CompletableDeferred<Unit>()
        repository.markNotificationReadGates["n1"] = markReadGate

        viewModel.onNotificationTapped("n1")
        assertEquals(1, repository.markNotificationReadCallCount.get())
        assertEquals(0, events.size) // No navigation before mark-read completes

        // Complete mark-read and provide updated reload result
        repository.notificationsResult = CommunicationResult.Success(listOf(readNotif))
        markReadGate.complete(Unit)

        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToConversation)
        val nav = events[0] as CommunicationNavigationEvent.NavigateToConversation
        assertEquals(convUuid, nav.conversationId)
        job.cancel()
    }

    @Test
    fun alreadyReadNotificationTapDoesNotTriggerMarkReadMutation() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val readNotif = InAppNotification("n1", "user-1", "new_message", "New Message", "Hello", "/account/messages/$convUuid", "2026-10-06T10:01:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(readNotif))
        viewModel.bind(session)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.onNotificationTapped("n1")

        assertEquals(0, repository.markNotificationReadCallCount.get())
        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToConversation)
        val nav = events[0] as CommunicationNavigationEvent.NavigateToConversation
        assertEquals(session, nav.session)
        assertEquals(convUuid, nav.conversationId)
        job.cancel()
    }

    @Test
    fun markAllReadWithUnreadRowsCallsRepositoryOnceAndReloadsAuthoritatively() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "Alert 1", "Body", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "Alert 1", "Body", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        assertTrue(viewModel.uiState.value.hasUnread)

        val gate = CompletableDeferred<Unit>()
        repository.markAllNotificationsReadGate = gate

        viewModel.markAllRead()
        assertEquals(1, repository.markAllNotificationsReadCallCount.get())

        // Duplicate markAllRead while suspended is ignored
        viewModel.markAllRead()
        assertEquals(1, repository.markAllNotificationsReadCallCount.get())

        repository.notificationsResult = CommunicationResult.Success(listOf(read1))
        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.hasUnread)
    }

    @Test
    fun markAllReadWithZeroUnreadCausesZeroMutationCalls() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val read1 = InAppNotification("n1", "user-1", "new_message", "Alert 1", "Body", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(read1))
        viewModel.bind(session)
        assertFalse(viewModel.uiState.value.hasUnread)

        viewModel.markAllRead()

        assertEquals(0, repository.markAllNotificationsReadCallCount.get())
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
    }

    @Test
    fun authRequiredEmitsHandoffEvent() = runTest {
        val session = CommunicationSession("user-1", 1L)
        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.AuthRequired)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.bind(session)

        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToAuth)
        val authEvent = events[0] as CommunicationNavigationEvent.NavigateToAuth
        assertEquals(session, authEvent.session)
        assertTrue(events.none { it is CommunicationNavigationEvent.NavigateToConversation })
        job.cancel()
    }

    @Test
    fun sameUserNewerGenerationRejectsStaleOldGenerationResults() = runTest {
        val sessionGen1 = CommunicationSession("user-1", generation = 1L)
        val sessionGen2 = CommunicationSession("user-1", generation = 2L)

        val gate1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGate = gate1

        viewModel.bind(sessionGen1)

        // Switch to generation 2 while generation 1 is suspended
        repository.fetchNotificationsGate = null
        val notifGen2 = InAppNotification("n2", "user-1", "listing_rejected", "Gen 2 Alert", "Body 2", "/account/listings", null, "2026-10-06T10:00:00Z")
        repository.notificationsResult = CommunicationResult.Success(listOf(notifGen2))
        viewModel.bind(sessionGen2)

        // Complete old generation 1 fetch
        gate1.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(sessionGen2, state.boundSession)
        assertEquals(1, state.notifications.size)
        assertEquals("n2", state.notifications[0].id)
    }

    @Test
    fun policyReviewAndAccountRestrictedEmitAuthHandoff() = runTest {
        val session = CommunicationSession("user-1", 1L)

        // PolicyReviewRequired
        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.PolicyReviewRequired)
        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.bind(session)
        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToAuth)

        // AccountRestricted
        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.AccountRestricted)
        viewModel.refresh()
        assertEquals(2, events.size)
        assertTrue(events[1] is CommunicationNavigationEvent.NavigateToAuth)

        job.cancel()
    }

    @Test
    fun destinationRoutingForMyListingsAndMalformedLinks() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val validListings = InAppNotification("n1", "user-1", "listing_rejected", "Rejected", "Body", "/account/listings", "2026-10-06T10:00:00Z", "2026-10-06T10:00:00Z")
        val malformedNotif = InAppNotification("n2", "user-1", "new_message", "Msg", "Body", "/account/messages/invalid-uuid", "2026-10-06T10:00:00Z", "2026-10-06T10:00:00Z")
        val unsupportedType = InAppNotification("n3", "user-1", "unknown_type", "Title", "Body", "/account/listings", "2026-10-06T10:00:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(validListings, malformedNotif, unsupportedType))
        viewModel.bind(session)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // 1. Valid listing_rejected -> NavigateToMyListings
        viewModel.onNotificationTapped("n1")
        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToMyListings)

        // 2. Malformed UUID -> zero navigation event
        viewModel.onNotificationTapped("n2")
        assertEquals(1, events.size)

        // 3. Unsupported type -> zero navigation event
        viewModel.onNotificationTapped("n3")
        assertEquals(1, events.size)

        job.cancel()
    }

    @Test
    fun cancellationExceptionRethrownAndNeverMappedToFailure() = runTest {
        val session = CommunicationSession("user-1", 1L)
        viewModel.bind(session)

        repository.throwCancellationOnFetchNotifications = true

        try {
            viewModel.performFetchNotificationsInternal(session)
            fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("Job cancelled", e.message)
        }

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun regressionR1_olderRefreshCompletingAfterMutationReloadDoesNotOverwriteState() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val readNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        // Initial fetch (call 1) succeeds with unreadNotif
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)
        assertTrue(viewModel.uiState.value.hasUnread)

        // Manual refresh R1 (call 2) starts and suspends
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateR1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unreadNotif))
        val refreshJobR1 = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refresh() }

        // Tap unread notification -> mark-read succeeds, triggers post-mutation reload R2 (call 3)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(readNotif))
        viewModel.onNotificationTapped("n1")

        // R2 completes and read state is updated
        assertFalse(viewModel.uiState.value.hasUnread)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)

        // Now release R1 (call 2) and prove R1 entered and returned
        gateR1.complete(Unit)
        refreshJobR1.join()

        val recordR1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordR1)
        assertTrue(recordR1!!.entered)
        assertTrue(recordR1.returned)

        // R1 must NOT overwrite R2's read state!
        assertFalse(viewModel.uiState.value.hasUnread)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun regressionR2_supersededVisibleFetchIsCanceledAndReplacedByNewerRefresh() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val notif2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        // Bind initial state (call 1)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)

        // Call onVisible -> starts visible lifecycle fetch V1 (call 2) and suspends
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateV1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()

        val recordV1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordV1)
        assertTrue(recordV1!!.entered)

        // Start manual refresh R2 (call 3) -> cancels V1 and lets R2 complete with newer snapshot
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1, notif2))
        viewModel.refresh()

        // Prove V1 was demonstrably canceled
        assertTrue(recordV1.canceled)
        assertEquals(2, viewModel.uiState.value.notifications.size)

        // Releasing V1's gate does not introduce duplicate rows, errors, or extra collector
        gateV1.complete(Unit)
        assertEquals(2, viewModel.uiState.value.notifications.size)

        // Exactly one active notification collector exists while visible
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // Call onNotVisible -> 0 active collectors
        viewModel.onNotVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun hideDuringSuspendedVisibleFetchCancelsFetchAndClearsLoading() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // 1 & 2. Bind and start visible lifecycle fetch V1 with fallback gate
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGate = gateV1
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))

        viewModel.bind(session)
        viewModel.onVisible()
        testScheduler.advanceUntilIdle()

        // Prove V1 entered, isLoading == true, zero active collectors
        val recordV1 = repository.fetchNotificationRecords.last()
        assertTrue(recordV1.entered)
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(0, repository.activeNotificationCollectorsCount.get())

        // 5. Hide screen while V1 is suspended
        viewModel.onNotVisible()
        gateV1.cancel()
        testScheduler.advanceUntilIdle()

        // 6, 7, 8, 9, 10. Assert V1 canceled, 0 collectors, isLoading == false, no error, notifications empty
        assertTrue(recordV1.canceled)
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.notifications.isEmpty())

        // 11. Re-enter visibility -> starts NEW gated visible fetch V2
        val gateV2 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGate = gateV2

        viewModel.onVisible()
        testScheduler.advanceUntilIdle()

        // 12. Assert isLoading == true while V2 is pending
        assertTrue(viewModel.uiState.value.isLoading)

        // 13. Complete V2
        gateV2.complete(Unit)
        testScheduler.advanceUntilIdle()

        // 14. Assert loading ends, 1 collector active, rows loaded
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertEquals(1, viewModel.uiState.value.notifications.size)

        // 15. Hide screen -> 0 collectors
        viewModel.onNotVisible()
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun regressionR3_successfulRetryAfterVisibleLoadFailureRestoresRealtime() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // 1 & 2. Bind and enter visible lifecycle; make initial fetch fail with NetworkUnavailable
        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.bind(session)
        viewModel.onVisible()
        testScheduler.advanceUntilIdle()

        // Verify error is displayed
        assertEquals("You're offline or the network is unavailable. Try again.", viewModel.uiState.value.errorMessage)

        // 3, 4, 5. Invoke refresh (retry); make retry succeed
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        // 6. Verify rows load, old error clears, loading ends
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)

        // 7. Verify exactly one active notification collector exists while visible
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 8. Hide screen -> 0 collectors
        viewModel.onNotVisible()
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun partA1_olderRefreshFailureCannotOverwriteNewerAcceptedSuccess() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val readNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        // Initial fetch (call 1) succeeds with unreadNotif
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)

        // Manual refresh R1 (call 2) starts and suspends, configured to return NetworkUnavailable
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateR1
        repository.fetchNotificationsResults[2] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        val refreshJobR1 = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refresh() }

        // Tap unread notification -> mark-read succeeds, triggers post-mutation reload R2 (call 3)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(readNotif))
        viewModel.onNotificationTapped("n1")

        // R2 completes and read state is updated
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)

        // Now release R1 (call 2)
        gateR1.complete(Unit)
        refreshJobR1.join()

        // Assert R2's rows/readAt and null error message remain unchanged
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun partA2_obsoleteAuthFailureCannotEmitStaleHandoffAfterAcceptedSuccess() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // R1 (call 2) starts and suspends, configured to return AuthRequired
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateR1
        repository.fetchNotificationsResults[2] = CommunicationResult.Failure(CommunicationFailure.AuthRequired)
        val refreshJobR1 = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.refresh() }

        // Newer refresh R2 (call 3) succeeds
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.refresh()
        assertEquals(0, events.size)

        // Now release R1
        gateR1.complete(Unit)
        refreshJobR1.join()

        // Assert obsolete R1 AuthRequired cannot emit stale handoff
        assertEquals(0, events.size)
        assertNull(viewModel.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun partA3_olderSuccessCannotUndoAcceptedAuthFailureOrStartSubscription() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)

        // V1 (call 2) starts and suspends with Success
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateV1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()

        // Reload R2 (call 3) returns AccountRestricted and is accepted
        repository.fetchNotificationsResults[3] = CommunicationResult.Failure(CommunicationFailure.AccountRestricted)
        viewModel.refresh()

        // Release older V1 success
        gateV1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert V1 cannot undo AccountRestricted failure or start a subscription
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun partA4_firstVisibleFetchFailureStartsZeroCollectorsAndRetryRestoresSubscription() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // First visible fetch fails with NetworkUnavailable
        repository.notificationsResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.bind(session)
        viewModel.onVisible()

        // Assert 0 active collectors and error message displayed
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
        assertEquals("You're offline or the network is unavailable. Try again.", viewModel.uiState.value.errorMessage)

        // Explicit retry succeeds
        repository.notificationsResult = CommunicationResult.Success(listOf(notif1))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        // Assert 1 active collector established and error cleared
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun partA5_markReadSuccessWithReloadFailureRetainsRowsAndSuppressesDestinationEvent() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // Mark-read succeeds (call 1 mark-read), but its authoritative reload (fetchNotifications call 2) fails with ServiceUnavailable
        repository.markNotificationReadResult = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[2] = CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)

        viewModel.onNotificationTapped("n1")
        testScheduler.advanceUntilIdle()

        // Assert rows retained, safe feedback set, and 0 destination events emitted
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("Messaging is temporarily unavailable. Try again.", viewModel.uiState.value.errorMessage)
        assertEquals(0, events.size)

        // Now test non-auth non-retryable failure
        repository.fetchNotificationsResults[3] = CommunicationResult.Failure(CommunicationFailure.InvalidInput)
        viewModel.onNotificationTapped("n1")
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("Invalid request.", viewModel.uiState.value.errorMessage)
        assertEquals(0, events.size)

        job.cancel()
    }

    @Test
    fun partB1_actualCollectorTeardownIsAwaitedBeforeReplacementStarts() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // 1. Establish collector A (call 1 bind, call 2 onVisible)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 2 & 3. Hold collector A's teardown open via teardown gate
        val teardownGateA = CompletableDeferred<Unit>()
        repository.notificationTeardownGate = teardownGateA

        // Trigger replacement B (call 3)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1))
        viewModel.refresh()

        // 4. Assert B has NOT established a new collector while A's teardown is held
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 5 & 6 & 7. Release A's teardown -> B establishes its collector, peak collectors <= 1
        repository.notificationTeardownGate = null
        teardownGateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertTrue(repository.peakActiveNotificationCollectorsCount.get() <= 1)

        // Hide -> 0 collectors
        viewModel.onNotVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun regressionH1_replacementLoadingSurvivesOldJobCleanup() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val notif2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        // 1. Finish initial bind fetch (call 1)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)

        // 2. Enter visibility and establish one active collector (call 2)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 3. Trigger refresh with a NEW fetch V2 (call 3) that remains suspended
        val gateV2 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[3] = gateV2
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1, notif2))
        viewModel.refresh()

        // 4 & 5 & 6. Assert new fetch genuinely entered and isLoading remains true
        val recordV2 = repository.fetchNotificationRecords.find { it.callIndex == 3 }
        assertNotNull(recordV2)
        assertTrue(recordV2!!.entered)
        assertTrue(viewModel.uiState.value.isLoading)

        // 7 & 8. Complete new fetch V2 -> isLoading becomes false, 1 collector active
        gateV2.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(2, viewModel.uiState.value.notifications.size)
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun regressionH2_immediateReplacementNeverOverlapsCollectors() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val notif2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        // 1. Establish one active collector
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 2 & 3. Configure replacement fetch to succeed immediately and refresh
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1, notif2))
        viewModel.refresh()

        // 4. Assert peak active collectors never exceeds 1
        assertTrue(repository.peakActiveNotificationCollectorsCount.get() <= 1)
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 5. Hide -> active collectors becomes 0
        viewModel.onNotVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun regressionH3_queuedHideCleanupAndReentry() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        val testRepository = FakeFeatureCommunicationRepository()
        val testViewModel = NotificationsViewModel(testRepository)

        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // Finish initial bind fetch (call 1)
        testRepository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        testViewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // Start gated visible fetch V1 (call 2)
        val gateV1 = CompletableDeferred<Unit>()
        testRepository.fetchNotificationsGates[2] = gateV1
        testRepository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))

        testViewModel.onVisible()
        testScheduler.runCurrent()

        // Assert V1 entered, loading true, 0 collectors
        val recordV1 = testRepository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordV1)
        assertTrue(recordV1!!.entered)
        assertTrue(testViewModel.uiState.value.isLoading)
        assertEquals(0, testRepository.activeNotificationCollectorsCount.get())

        // Call onNotVisible()
        testViewModel.onNotVisible()
        gateV1.cancel()
        testScheduler.advanceUntilIdle()

        // Assert V1 canceled, 0 collectors, loading false, no new error, rows preserved
        assertTrue(recordV1.canceled)
        assertEquals(0, testRepository.activeNotificationCollectorsCount.get())
        assertFalse(testViewModel.uiState.value.isLoading)
        assertNull(testViewModel.uiState.value.errorMessage)
        assertEquals(1, testViewModel.uiState.value.notifications.size)

        // Re-entry: start new gated visible operation V2 (call 3)
        val gateV2 = CompletableDeferred<Unit>()
        testRepository.fetchNotificationsGates[3] = gateV2
        testRepository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1))

        testViewModel.onVisible()
        testScheduler.runCurrent()

        // Assert loading remains true while V2 pending
        assertTrue(testViewModel.uiState.value.isLoading)

        // Complete V2
        gateV2.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Verify loading ends and 1 collector active
        assertFalse(testViewModel.uiState.value.isLoading)
        assertEquals(1, testRepository.activeNotificationCollectorsCount.get())

        // Hide -> 0 collectors
        testViewModel.onNotVisible()
        testScheduler.advanceUntilIdle()
        assertEquals(0, testRepository.activeNotificationCollectorsCount.get())

        Dispatchers.resetMain()
    }

    @Test
    fun regressionH4_newerMutationSnapshotMustNotEliminateVisibleSubscription() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        // 1. Finish initial bind with unread rows (call 1)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)

        // 2. Call onVisible(); suspend its fetch V1 (call 2)
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateV1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread1))
        viewModel.onVisible()

        // 3, 4, 5. Execute markAllRead(); complete mutation and authoritative reload R2 (call 3)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(read1))
        viewModel.markAllRead()

        // Assert R2's exact readAt is displayed
        assertFalse(viewModel.uiState.value.hasUnread)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)

        // 6. Release V1 with its older unread snapshot and prove V1 returned
        gateV1.complete(Unit)
        testScheduler.advanceUntilIdle()

        val recordV1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordV1)
        assertTrue(recordV1!!.entered)

        // 7 & 8. Assert V1 does not overwrite rows/readAt
        assertFalse(viewModel.uiState.value.hasUnread)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)

        // 9. Assert still-current visible lifecycle establishes one collector
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // 10. Hide -> 0 collectors
        viewModel.onNotVisible()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun partF01_sessionChangeRejectsOldEventDelivery() = runTest {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)

        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.notificationsResult = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // Tap notification to start mark read
        val markReadGate = CompletableDeferred<Unit>()
        repository.markNotificationReadGates["n1"] = markReadGate
        viewModel.onNotificationTapped("n1")

        // Switch session before completion
        viewModel.bind(s2)

        // Complete the old mark read operation
        repository.notificationsResult = CommunicationResult.Success(listOf(read1))
        markReadGate.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert 0 events emitted for old session
        assertEquals(0, events.size)

        // Now test same user, new generation
        val s3 = CommunicationSession("user-1", 3L)
        repository.notificationsResult = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1) // rebind old
        testScheduler.advanceUntilIdle()
        
        val markReadGate2 = CompletableDeferred<Unit>()
        repository.markNotificationReadGates["n1"] = markReadGate2
        viewModel.onNotificationTapped("n1")
        
        viewModel.bind(s3) // new generation
        
        markReadGate2.complete(Unit)
        testScheduler.advanceUntilIdle()
        
        assertEquals(0, events.size)

        job.cancel()
    }

    @Test
    fun scenarioP1_suspendedVisibleSuccessV1NewerMarkAllReloadAccountRestricted() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // Call onVisible -> V1 (call 2) starts and suspends on gate
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateV1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread1))
        viewModel.onVisible()
        testScheduler.runCurrent()

        // Execute markAllRead -> markAll (call 1) succeeds, triggers reload R2 (call 3) returning AccountRestricted
        repository.markAllNotificationsReadResult = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[3] = CommunicationResult.Failure(CommunicationFailure.AccountRestricted)
        viewModel.markAllRead()
        testScheduler.advanceUntilIdle()

        // Verify R2 produced NavigateToAuth
        assertTrue(events.any { it is CommunicationNavigationEvent.NavigateToAuth })

        // Complete V1
        gateV1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert V1 actually returned
        val recordV1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordV1)
        assertTrue(recordV1!!.returned)

        // Assert 0 collectors active
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
        job.cancel()
    }

    @Test
    fun scenarioP2_suspendedVisibleSuccessV1NewerMarkAllReloadSuccess() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // V1 (call 2) starts and suspends on gate
        val gateV1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateV1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread1))
        viewModel.onVisible()
        testScheduler.runCurrent()

        // markAllRead -> R2 (call 3) returns read1
        repository.markAllNotificationsReadResult = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(read1))
        viewModel.markAllRead()
        testScheduler.advanceUntilIdle()

        // Exact server readAt retained
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)

        // Complete V1
        gateV1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Exact server readAt retained and 1 collector active
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun scenarioP3_suspendedOldAuthRequiredRefreshNewerSuccessfulMutationReload() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // Refresh R1 (call 2) starts and suspends on gate, returns AuthRequired
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateR1
        repository.fetchNotificationsResults[2] = CommunicationResult.Failure(CommunicationFailure.AuthRequired)
        viewModel.refresh()
        testScheduler.runCurrent()

        // Mutation markRead -> reload R2 (call 3) returns read1
        repository.markNotificationReadResult = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(read1))
        viewModel.onNotificationTapped("n1")
        testScheduler.advanceUntilIdle()

        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)

        // Complete R1 (call 2)
        gateR1.complete(Unit)
        testScheduler.advanceUntilIdle()

        val recordR1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordR1)
        assertTrue(recordR1!!.returned)

        // Assert no obsolete handoff emitted by R1
        assertTrue(events.none { it is CommunicationNavigationEvent.NavigateToAuth })
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        job.cancel()
    }

    @Test
    fun scenarioP4_realHeldCollectorTeardownRepeatedReplacementAndHideReentry() = runTest {
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")

        // Establish collector A (invocation ID 1)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        testScheduler.advanceUntilIdle()
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // Hold collector A's teardown open via teardown gate
        val gateA = CompletableDeferred<Unit>()
        repository.collectorTeardownGates[1] = gateA

        // Trigger replacement B, hide, and re-entry C
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1))
        viewModel.refresh()
        viewModel.onNotVisible()
        repository.fetchNotificationsResults[4] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        testScheduler.runCurrent()

        // While gateA is held, active collectors is 1 (A), B and C have not started collection
        assertEquals(1, repository.activeNotificationCollectorsCount.get())

        // Release gateA
        repository.collectorTeardownGates.remove(1)
        gateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Only C collects alone; peak active <= 1
        assertEquals(1, repository.activeNotificationCollectorsCount.get())
        assertTrue(repository.peakActiveNotificationCollectorsCount.get() <= 1)

        // Hide -> 0 collectors
        viewModel.onNotVisible()
        testScheduler.advanceUntilIdle()
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
    }

    @Test
    fun scenarioR1_activeVisibleCollectorReconciliationCancelledLateRowsReturnedAndRejected() = runTest {
        repository.nonCancellableFetchGates = true
        val session = CommunicationSession("user-1", 1L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val lateNotif = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(session)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        testScheduler.advanceUntilIdle()

        // Realtime event ReadyForBackfill triggers reconciliation R1 (call 3) which suspends on gate
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[3] = gateR1
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notif1, lateNotif))
        repository.notificationEventFlow.tryEmit(NotificationRealtimeEvent.ReadyForBackfill)
        testScheduler.runCurrent()

        // Hide / cancel lifecycle
        viewModel.onNotVisible()
        testScheduler.advanceUntilIdle()

        // Release gateR1
        gateR1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert R1 returned
        val recordR1 = repository.fetchNotificationRecords.find { it.callIndex == 3 }
        assertNotNull(recordR1)
        assertTrue(recordR1!!.returned)

        // Assert late rows were NOT applied
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("n1", viewModel.uiState.value.notifications[0].id)
    }

    @Test
    fun scenarioR2_heldTeardownNewSessionFetchAndCancelledWaiterClearsLoading() = runTest {
        repository.nonCancellableFetchGates = true
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val notif1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/123e4567-e89b-12d3-a456-426614174000", null, "2026-10-06T10:00:00Z")
        val notifS2 = InAppNotification("n2", "user-2", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        // Establish collector A (call 1 bind, call 2 onVisible)
        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(notif1))
        viewModel.bind(s1)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(notif1))
        viewModel.onVisible()
        testScheduler.advanceUntilIdle()

        // Hold collector A's teardown
        val gateA = CompletableDeferred<Unit>()
        repository.collectorTeardownGates[1] = gateA

        // Hide S1
        viewModel.onNotVisible()

        // Bind S2 -> non-visible initial fetch (call 3) suspends on gate
        val gateS2 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[3] = gateS2
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(notifS2))
        viewModel.bind(s2)

        // Re-enter S2 -> visible lifecycle B (call 4) waits for mutex
        val gateB = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[4] = gateB
        repository.fetchNotificationsResults[4] = CommunicationResult.Success(listOf(notifS2))
        viewModel.onVisible()
        testScheduler.runCurrent()

        // Complete S2 initial fetch
        gateS2.complete(Unit)
        testScheduler.runCurrent()

        // Hide / cancel B before mutex is acquired
        viewModel.onNotVisible()

        // Release A's teardown
        repository.collectorTeardownGates.remove(1)
        gateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert all collectors closed, isLoading == false, S2 rows preserved
        assertEquals(0, repository.activeNotificationCollectorsCount.get())
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertEquals("n2", viewModel.uiState.value.notifications[0].id)
    }

    @Test
    fun scenarioR3_unreadTapAuthoritativeReloadCancelledLateSuccessSuppressesDestination() = runTest {
        repository.nonCancellableFetchGates = true
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        // Tap unread -> markRead succeeds (call 1 markRead), reload R1 (call 2 fetch) suspends on gate
        val gateR1 = CompletableDeferred<Unit>()
        repository.fetchNotificationsGates[2] = gateR1
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(read1))
        repository.markNotificationReadResult = CommunicationResult.Success(Unit)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()

        // Cancel the actual row-action job while keeping the same bound session
        val markReadRecord = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(markReadRecord)
        requireNotNull(markReadRecord!!.job).cancel()
        testScheduler.runCurrent()

        // Complete R1
        gateR1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert bound session remains unchanged
        assertEquals(session, viewModel.uiState.value.boundSession)

        // Assert R1 returned
        val recordR1 = repository.fetchNotificationRecords.find { it.callIndex == 2 }
        assertNotNull(recordR1)
        assertTrue(recordR1!!.returned)

        // Assert 0 destination events emitted, no false error, no stuck ID
        assertEquals(0, events.size)
        assertNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertEquals("n1", viewModel.uiState.value.notifications[0].id)
        assertNull(viewModel.uiState.value.notifications[0].readAt) // Remains unread due to cancellation of reload state application

        job.cancel()
    }
}
