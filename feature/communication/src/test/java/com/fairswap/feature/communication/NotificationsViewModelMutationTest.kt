package com.fairswap.feature.communication

import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.InAppNotification
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelMutationTest {

    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: kotlinx.coroutines.test.TestDispatcher
    private lateinit var repository: FakeFeatureCommunicationRepository
    private lateinit var viewModel: NotificationsViewModel
    private val heldGates = mutableListOf<CompletableDeferred<Unit>>()
    private val eventCollectors = mutableListOf<Job>()

    @Before
    fun setup() {
        testScheduler = TestCoroutineScheduler()
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        repository = FakeFeatureCommunicationRepository()
        viewModel = NotificationsViewModel(repository)
    }

    private fun trackedGate(): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { heldGates.add(it) }

    @After
    fun tearDown() {
        // JUnit invokes this after an assertion failure as well as after success.
        // Invalidation happens only here, after the test's behavioral assertions.
        try {
            eventCollectors.forEach { it.cancel() }
            if (::viewModel.isInitialized) {
                viewModel.bind(null)
                viewModel.viewModelScope.cancel()
            }
        } finally {
            try {
                // Release gates even when the test failed before its normal release.
                heldGates.forEach { it.complete(Unit) }
                if (::testScheduler.isInitialized) testScheduler.runCurrent()
            } finally {
                heldGates.clear()
                eventCollectors.clear()
                if (::repository.isInitialized) {
                    repository.nonCancellableMutationGates = false
                }
                Dispatchers.resetMain()
            }
        }
    }

    @Test
    fun rowMutation_NetworkUnavailable_retry_success() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // 1. Mark read fails with NetworkUnavailable
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))
        
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert row/readAt preserved, flag cleared, error set
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertEquals("You're offline or the network is unavailable. Try again.", viewModel.uiState.value.errorMessage)

        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(unread1), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1"), repository.markNotificationReadCalls.toList())
        assertEquals(1, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        // 2. Retry succeeds
        repository.markNotificationReadGates.remove("n1")
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(read1))
        
        viewModel.onNotificationTapped("n1")
        testScheduler.advanceUntilIdle()

        // Assert exact server readAt and no error
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertEquals(2, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1", session to "n1"), repository.markNotificationReadCalls.toList())
        assertEquals(2, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session, session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun rowMutation_ServiceUnavailable_retry_success() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // 1. Mark read fails with ServiceUnavailable
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))

        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert row/readAt preserved, flag cleared, error set
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertEquals("Messaging is temporarily unavailable. Try again.", viewModel.uiState.value.errorMessage)

        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(unread1), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1"), repository.markNotificationReadCalls.toList())
        assertEquals(1, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        // 2. Retry succeeds
        repository.markNotificationReadGates.remove("n1")
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(read1))

        viewModel.onNotificationTapped("n1")
        testScheduler.advanceUntilIdle()

        // Assert exact server readAt and no error
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertEquals(2, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1", session to "n1"), repository.markNotificationReadCalls.toList())
        assertEquals(2, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session, session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun bulkMutation_NetworkUnavailable_retry_success() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // 1. Mark All Read fails with NetworkUnavailable (call 1)
        val gateM1 = trackedGate()
        repository.markAllNotificationsReadGates[1] = gateM1
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.markAllRead()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isMarkingAllRead)
        gateM1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert failure preserves row/readAt and clears flags
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertEquals("You're offline or the network is unavailable. Try again.", viewModel.uiState.value.errorMessage)

        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(unread1), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markAllNotificationsReadCallCount.get())
        assertEquals(listOf(session), repository.markAllNotificationsReadCalls.toList())
        assertEquals(1, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        // 2. Retry succeeds (call 2 markAll, call 2 fetch)
        val gateM2 = trackedGate()
        repository.markAllNotificationsReadGates[2] = gateM2
        repository.markAllNotificationsReadResults[2] = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(read1))
        viewModel.markAllRead()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isMarkingAllRead)
        gateM2.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertEquals(2, repository.markAllNotificationsReadCallCount.get())
        assertEquals(listOf(session, session), repository.markAllNotificationsReadCalls.toList())
        assertEquals(2, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session, session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun bulkMutation_ServiceUnavailable_retry_success() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // 1. Mark All Read fails with ServiceUnavailable (call 1)
        val gateM1 = trackedGate()
        repository.markAllNotificationsReadGates[1] = gateM1
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
        viewModel.markAllRead()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isMarkingAllRead)
        gateM1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert failure preserves row/readAt and clears flags
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertEquals("Messaging is temporarily unavailable. Try again.", viewModel.uiState.value.errorMessage)

        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(unread1), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markAllNotificationsReadCallCount.get())
        assertEquals(listOf(session), repository.markAllNotificationsReadCalls.toList())
        assertEquals(1, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        // 2. Retry succeeds (call 2 markAll, call 2 fetch)
        val gateM2 = trackedGate()
        repository.markAllNotificationsReadGates[2] = gateM2
        repository.markAllNotificationsReadResults[2] = CommunicationResult.Success(Unit)
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(read1))
        viewModel.markAllRead()
        testScheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isMarkingAllRead)
        gateM2.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertEquals(2, repository.markAllNotificationsReadCallCount.get())
        assertEquals(listOf(session, session), repository.markAllNotificationsReadCalls.toList())
        assertEquals(2, repository.fetchNotificationsCallCount.get())
        assertEquals(listOf(session, session), repository.fetchNotificationRecords.map { it.session })
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun bulkMutation_ordinaryCancellation() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/1", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        // 1. Mark All (ordinary cancellable gate)
        val gate1 = trackedGate()
        repository.markAllNotificationsReadGates[1] = gate1
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Success(Unit)
        
        viewModel.markAllRead()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isMarkingAllRead)

        // Cancel job by unbinding/rebinding
        val markAllRecord = repository.markAllNotificationsReadRecords.find { it.callIndex == 1 }
        assertNotNull(markAllRecord)
        
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Assert cancelled in repository, returned == false
        assertTrue(markAllRecord!!.canceled)
        assertFalse(markAllRecord.returned)
        
        // S2 should not have flag set
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
    }

    @Test
    fun bulkMutation_cancellation_distinctLateResults() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/1", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        // 1. Mark All with non-cancellable gate for late-result testing
        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markAllNotificationsReadGates[1] = gate1
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Success(Unit)
        
        viewModel.markAllRead()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.isMarkingAllRead)

        val markAllRecord = repository.markAllNotificationsReadRecords.find { it.callIndex == 1 }
        assertNotNull(markAllRecord)
        
        // Explicitly cancel the job
        val actionJob = requireNotNull(markAllRecord!!.job)
        actionJob.cancel()
        testScheduler.runCurrent()

        // Assert cancellation was requested while job was still pending on gate
        assertTrue(actionJob.isCancelled)
        assertFalse(markAllRecord.returned)
        
        // Let it complete late
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert it actually returned and fully completed cancellation
        assertTrue(markAllRecord.returned)
        assertTrue(markAllRecord.canceled)
        repository.nonCancellableMutationGates = false
        
        // S1 should not have flag set
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
    }

    @Test
    fun bulkLateResultAfterDifferentAccountRebindDoesNotAffectNewState() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val unread2 = InAppNotification("n2", "user-2", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        // 1. Start bulk action on s1 with non-cancellable gate
        repository.nonCancellableMutationGates = true
        val gateA = trackedGate()
        repository.markAllNotificationsReadGates[1] = gateA
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Success(Unit)

        viewModel.markAllRead()
        testScheduler.runCurrent()

        val recordA = repository.markAllNotificationsReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)
        val actionJobA = requireNotNull(recordA!!.job)

        // 2. Rebind to different account s2
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread2))
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Assert A is canceled but incomplete and has not returned
        assertTrue(actionJobA.isCancelled)
        assertFalse(actionJobA.isCompleted)
        assertFalse(recordA.returned)

        // Snapshot s2's complete state, events, and call counts
        val stateBBefore = viewModel.uiState.value
        val eventsBefore = events.toList()
        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        assertEquals(s2, stateBBefore.boundSession)
        assertEquals(1, stateBBefore.notifications.size)
        assertEquals("n2", stateBBefore.notifications[0].id)

        // 3. Release A (completes late)
        gateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert A actually returned and completed
        assertTrue(recordA.returned)
        assertTrue(actionJobA.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert whole B state, events, and call counts are completely unchanged by A's late return
        assertEquals(stateBBefore, viewModel.uiState.value)
        assertEquals(eventsBefore, events.toList())
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        eventJob.cancel()
    }
    
    @Test
    fun rowMutation_ordinaryCancellation() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/1", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        // 1. Mark Read (ordinary cancellable gate)
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))

        // Cancel job by unbinding/rebinding
        val markRecord = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(markRecord)
        
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Assert cancelled in repository, returned == false
        assertTrue(markRecord!!.canceled)
        assertFalse(markRecord.returned)
        
        // S2 should not have flag set
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
    }

    @Test
    fun rowMutation_cancellation_distinctLateResults() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/1", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        // 1. Mark Read with non-cancellable gate for late-result testing
        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))

        val markRecord = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(markRecord)
        
        // Use requireNotNull to explicitly cancel the Job
        val actionJob = requireNotNull(markRecord!!.job)
        actionJob.cancel()
        testScheduler.runCurrent()

        // Assert cancellation was requested while job was still pending on gate
        assertTrue(actionJob.isCancelled)
        assertFalse(markRecord.returned)
        
        // Let it complete late
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert it actually returned and fully completed cancellation
        assertTrue(markRecord.returned)
        assertTrue(markRecord.canceled)
        repository.nonCancellableMutationGates = false
        
        // S1 should not have flag set
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
    }

    @Test
    fun rowLateResultAfterDifferentAccountRebindDoesNotAffectNewState() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val unread2 = InAppNotification("n2", "user-2", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        // 1. Start row action on s1 with non-cancellable gate
        repository.nonCancellableMutationGates = true
        val gateA = trackedGate()
        repository.markNotificationReadGates["n1"] = gateA
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()

        val recordA = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)
        val actionJobA = requireNotNull(recordA!!.job)

        // 2. Rebind to different account s2
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread2))
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Assert A is canceled but incomplete and has not returned
        assertTrue(actionJobA.isCancelled)
        assertFalse(actionJobA.isCompleted)
        assertFalse(recordA.returned)

        // Snapshot s2's complete state, events, and call counts
        val stateBBefore = viewModel.uiState.value
        val eventsBefore = events.toList()
        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        assertEquals(s2, stateBBefore.boundSession)
        assertEquals(1, stateBBefore.notifications.size)
        assertEquals("n2", stateBBefore.notifications[0].id)

        // 3. Release A (completes late)
        gateA.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert A actually returned and completed
        assertTrue(recordA.returned)
        assertTrue(actionJobA.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert whole B state, events, and call counts are completely unchanged by A's late return
        assertEquals(stateBBefore, viewModel.uiState.value)
        assertEquals(eventsBefore, events.toList())
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        eventJob.cancel()
    }

    @Test
    fun canceledRowLateNetworkFailureDoesNotAffectStateOrNavigate() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()

        val markRecord = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(markRecord)
        val actionJob = requireNotNull(markRecord!!.job)

        // Cancel job without rebinding or cancelling gate
        actionJob.cancel()
        testScheduler.runCurrent()

        assertTrue(actionJob.isCancelled)
        assertFalse(actionJob.isCompleted)
        assertFalse(markRecord.returned)

        // Complete gate late
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertTrue(markRecord.returned)
        assertTrue(actionJob.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert S unchanged, rows/readAt unchanged, no error, no event/handoff, no pending row ID, no unnecessary reload
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        assertEquals(listOf(unreadNotif), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1"), repository.markNotificationReadCalls.toList())
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        eventJob.cancel()
    }

    @Test
    fun canceledRowLateServiceFailureDoesNotAffectStateOrNavigate() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unreadNotif = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unreadNotif))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()

        val markRecord = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(markRecord)
        val actionJob = requireNotNull(markRecord!!.job)

        // Cancel job without rebinding or cancelling gate
        actionJob.cancel()
        testScheduler.runCurrent()

        assertTrue(actionJob.isCancelled)
        assertFalse(actionJob.isCompleted)
        assertFalse(markRecord.returned)

        // Complete gate late
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertTrue(markRecord.returned)
        assertTrue(actionJob.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert S unchanged, rows/readAt unchanged, no error, no event/handoff, no pending row ID, no unnecessary reload
        assertEquals(session, viewModel.uiState.value.boundSession)
        assertEquals(1, viewModel.uiState.value.notifications.size)
        assertNull(viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        assertEquals(listOf(unreadNotif), viewModel.uiState.value.notifications)
        assertEquals(1, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(session to "n1"), repository.markNotificationReadCalls.toList())
        assertTrue(viewModel.uiState.value.mutatingIds.isEmpty())
        assertFalse(viewModel.uiState.value.isMarkingAllRead)
        assertFalse(viewModel.uiState.value.isLoading)

        eventJob.cancel()
    }
    
    @Test
    fun oldCleanupMustNotClearNewerSameIdOperation() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/1", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        // Op A (Call 1)
        val gateA = trackedGate()
        repository.markNotificationReadGates["n1"] = gateA
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        
        val recordA = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)
        
        // Cancel A by cancelling its specific job
        recordA!!.job?.cancel()
        testScheduler.runCurrent()
        
        // Op B (Call 2) on same ID
        val gateB = trackedGate()
        repository.markNotificationReadGates["n1"] = gateB
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))
        
        // Let A complete (its cleanup runs)
        gateA.complete(Unit)
        testScheduler.runCurrent()
        
        // Op B should still be pending
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))
        
        // Let B complete
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread1.copy(readAt = "2026-10-06T10:05:00Z")))
        gateB.complete(Unit)
        testScheduler.advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
    }

    @Test
    fun oldCleanupMustNotClearNewerSameIdOperation_actualOverlap() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-1", 2L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val read1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", "2026-10-06T10:05:00Z", "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        // Op A (Call 1) for s1
        repository.nonCancellableMutationGates = true
        val gateA = trackedGate()
        repository.markNotificationReadGates["n1"] = gateA
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        
        val recordA = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)
        
        // Rebind to new generation s2 while A is still pending
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()
        
        // Op B (Call 2) for s2 on same ID "n1"
        val gateB = trackedGate()
        repository.markNotificationReadGates["n1"] = gateB
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        
        val recordB = repository.markNotificationReadRecords.find { it.callIndex == 2 }
        assertNotNull(recordB)
        
        // Prove B entered and is pending while A is still incomplete on gateA
        assertTrue(recordA!!.entered)
        assertFalse(recordA.returned)
        assertTrue(recordB!!.entered)
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))
        val actionJobA = requireNotNull(recordA.job)
        val actionJobB = requireNotNull(recordB.job)
        assertTrue(actionJobA.isCancelled)
        assertFalse(actionJobA.isCompleted)
        assertFalse(actionJobB.isCompleted)
        assertFalse(recordB.returned)
        assertEquals(s1, recordA.session)
        assertEquals(s2, recordB.session)
        assertEquals("n1", recordA.id)
        assertEquals("n1", recordB.id)
        val stateWhileBPending = viewModel.uiState.value
        val callsWhileBPending = repository.markNotificationReadCallCount.get()
        
        // Release A (A completes late)
        gateA.complete(Unit)
        testScheduler.runCurrent()
        
        assertTrue(recordA.returned)
        // Assert B's pending state remains, no error from A on new state
        assertTrue(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertNull(viewModel.uiState.value.errorMessage)
        
        assertTrue(actionJobA.isCompleted)
        assertFalse(actionJobB.isCompleted)
        assertEquals(stateWhileBPending, viewModel.uiState.value)

        // A's finalizer must not remove B's duplicate-operation protection.
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        assertEquals(callsWhileBPending, repository.markNotificationReadCallCount.get())
        assertEquals(listOf(s1 to "n1", s2 to "n1"), repository.markNotificationReadCalls.toList())
        assertEquals(stateWhileBPending, viewModel.uiState.value)

        // Finish B
        repository.fetchNotificationsResults[3] = CommunicationResult.Success(listOf(read1))
        repository.nonCancellableMutationGates = false
        gateB.complete(Unit)
        testScheduler.advanceUntilIdle()
        
        // Assert B finished, readAt updated, flags cleared
        assertFalse(viewModel.uiState.value.mutatingIds.contains("n1"))
        assertEquals("2026-10-06T10:05:00Z", viewModel.uiState.value.notifications[0].readAt)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf(read1), viewModel.uiState.value.notifications)
        assertTrue(recordB.returned)
        assertTrue(actionJobB.isCompleted)
    }

    @Test
    fun rowMutation_InvalidInput_displaysNotificationSpecificErrorMessage() = runTest(testScheduler) {
        val session = CommunicationSession("user-1", 1L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(session)
        testScheduler.advanceUntilIdle()

        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Failure(CommunicationFailure.InvalidInput)
        
        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()
        
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Assert notification-specific error message "Invalid request." instead of chat message advice
        assertEquals("Invalid request.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.notifications[0].readAt)
    }

    @Test
    fun rowMutation_cancellation_sameUserNewGeneration_distinctLateResults() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-1", 2L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val unread2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        // 1. Start row action on s1 with non-cancellable gate
        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markNotificationReadGates["n1"] = gate1
        repository.markNotificationReadResults["n1"] = CommunicationResult.Success(Unit)

        viewModel.onNotificationTapped("n1")
        testScheduler.runCurrent()

        val recordA = repository.markNotificationReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)

        // 2. Rebind to same user, new generation s2
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread2))
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Prove A is canceled but has not returned
        val actionJobA = requireNotNull(recordA!!.job)
        assertTrue(actionJobA.isCancelled)
        assertFalse(actionJobA.isCompleted)
        assertFalse(recordA.returned)

        // Snapshot s2's state
        val stateBBefore = viewModel.uiState.value
        val eventsBefore = events.toList()
        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        assertEquals(s2, stateBBefore.boundSession)
        assertEquals(1, stateBBefore.notifications.size)
        assertEquals("n2", stateBBefore.notifications[0].id)

        // 3. Release A (completes late)
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Prove A returned and completed cancellation
        assertTrue(recordA.returned)
        assertTrue(recordA.canceled)
        assertTrue(actionJobA.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert s2's session, rows, errors, flags, and events are unchanged
        assertEquals(stateBBefore, viewModel.uiState.value)
        assertEquals(eventsBefore, events.toList())
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        eventJob.cancel()
    }

    @Test
    fun bulkMutation_cancellation_sameUserNewGeneration_distinctLateResults() = runTest(testScheduler) {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-1", 2L)
        val convUuid = "123e4567-e89b-12d3-a456-426614174000"
        val unread1 = InAppNotification("n1", "user-1", "new_message", "N1", "B1", "/account/messages/$convUuid", null, "2026-10-06T10:00:00Z")
        val unread2 = InAppNotification("n2", "user-1", "listing_rejected", "N2", "B2", "/account/listings", null, "2026-10-06T10:05:00Z")

        repository.fetchNotificationsResults[1] = CommunicationResult.Success(listOf(unread1))
        viewModel.bind(s1)
        testScheduler.advanceUntilIdle()

        val events = mutableListOf<CommunicationNavigationEvent>()
        val eventJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }.also { eventCollectors.add(it) }

        // 1. Start bulk action on s1 with non-cancellable gate
        repository.nonCancellableMutationGates = true
        val gate1 = trackedGate()
        repository.markAllNotificationsReadGates[1] = gate1
        repository.markAllNotificationsReadResults[1] = CommunicationResult.Success(Unit)

        viewModel.markAllRead()
        testScheduler.runCurrent()

        val recordA = repository.markAllNotificationsReadRecords.find { it.callIndex == 1 }
        assertNotNull(recordA)

        // 2. Rebind to same user, new generation s2
        repository.fetchNotificationsResults[2] = CommunicationResult.Success(listOf(unread2))
        viewModel.bind(s2)
        testScheduler.advanceUntilIdle()

        // Prove A is canceled but has not returned
        val actionJobA = requireNotNull(recordA!!.job)
        assertTrue(actionJobA.isCancelled)
        assertFalse(actionJobA.isCompleted)
        assertFalse(recordA.returned)

        // Snapshot s2's state
        val stateBBefore = viewModel.uiState.value
        val eventsBefore = events.toList()
        val fetchCallsBefore = repository.fetchNotificationsCallCount.get()

        assertEquals(s2, stateBBefore.boundSession)
        assertEquals(1, stateBBefore.notifications.size)
        assertEquals("n2", stateBBefore.notifications[0].id)

        // 3. Release A (completes late)
        gate1.complete(Unit)
        testScheduler.advanceUntilIdle()

        // Prove A returned and completed cancellation
        assertTrue(recordA.returned)
        assertTrue(recordA.canceled)
        assertTrue(actionJobA.isCompleted)
        repository.nonCancellableMutationGates = false

        // Assert s2's session, rows, errors, flags, and events are unchanged
        assertEquals(stateBBefore, viewModel.uiState.value)
        assertEquals(eventsBefore, events.toList())
        assertEquals(fetchCallsBefore, repository.fetchNotificationsCallCount.get())

        eventJob.cancel()
    }
}
