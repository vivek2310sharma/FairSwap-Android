package com.fairswap.feature.communication

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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal data class CollectorInvocationRecord(
    val id: Int,
    val session: CommunicationSession,
    var started: Boolean = false,
    var teardownEntered: Boolean = false,
    var teardownCompleted: Boolean = false
)

class FakeFeatureCommunicationRepository : CommunicationRepository {
    var inboxResult: CommunicationResult<List<ConversationSummary>> = CommunicationResult.Success(emptyList())
    var conversationResult: CommunicationResult<ConversationSummary>? = null
    var newestMessagesResult: CommunicationResult<List<ChatMessage>> = CommunicationResult.Success(emptyList())
    var olderMessagesResult: CommunicationResult<List<ChatMessage>> = CommunicationResult.Success(emptyList())
    var startConversationResult: CommunicationResult<StartConversationResult>? = null
    var sendMessageResult: CommunicationResult<ChatMessage>? = null

    val fetchInboxCallCount = AtomicInteger(0)
    val sendMessageCallCount = AtomicInteger(0)
    val startConversationCallCount = AtomicInteger(0)
    var fetchInboxGate: CompletableDeferred<Unit>? = null

    val messageEventFlow = MutableSharedFlow<MessageRealtimeEvent>(extraBufferCapacity = 64)
    val notificationEventFlow = MutableSharedFlow<NotificationRealtimeEvent>(extraBufferCapacity = 64)

    override suspend fun findExistingConversation(session: CommunicationSession, listingId: String): CommunicationResult<ConversationSummary?> {
        return CommunicationResult.Success(null)
    }

    override suspend fun fetchConversation(session: CommunicationSession, conversationId: String): CommunicationResult<ConversationSummary> {
        return conversationResult ?: CommunicationResult.Failure(CommunicationFailure.ConversationUnavailable)
    }

    override suspend fun fetchInbox(session: CommunicationSession): CommunicationResult<List<ConversationSummary>> {
        fetchInboxCallCount.incrementAndGet()
        fetchInboxGate?.await()
        return inboxResult
    }

    override suspend fun fetchNewestMessages(session: CommunicationSession, conversationId: String): CommunicationResult<List<ChatMessage>> {
        return newestMessagesResult
    }

    val recordedOlderCursors = mutableListOf<MessageHistoryCursor>()
    var fetchOlderMessagesGate: CompletableDeferred<Unit>? = null

    override suspend fun fetchOlderMessages(session: CommunicationSession, conversationId: String, cursor: MessageHistoryCursor): CommunicationResult<List<ChatMessage>> {
        synchronized(recordedOlderCursors) {
            recordedOlderCursors.add(cursor)
        }
        fetchOlderMessagesGate?.await()
        return olderMessagesResult
    }

    var throwCancellationOnStartConversation = false
    var throwCancellationOnSendMessage = false

    override suspend fun startConversation(session: CommunicationSession, listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResult> {
        if (throwCancellationOnStartConversation) throw kotlinx.coroutines.CancellationException("Job cancelled")
        startConversationCallCount.incrementAndGet()
        return startConversationResult ?: CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
    }

    var sendMessageGate: CompletableDeferred<Unit>? = null
    var capturedSendMessageClientRequestId: String? = null

    override suspend fun sendMessage(session: CommunicationSession, conversationId: String, content: String, clientRequestId: String): CommunicationResult<ChatMessage> {
        if (throwCancellationOnSendMessage) throw kotlinx.coroutines.CancellationException("Job cancelled")
        sendMessageCallCount.incrementAndGet()
        capturedSendMessageClientRequestId = clientRequestId
        sendMessageGate?.await()
        return sendMessageResult ?: CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
    }

    val markConversationReadCallCount = AtomicInteger(0)
    val markConversationReadCalls = mutableListOf<Triple<CommunicationSession, String, String>>()
    var markConversationReadResult: CommunicationResult<Unit> = CommunicationResult.Success(Unit)

    override suspend fun markConversationRead(session: CommunicationSession, conversationId: String, messageId: String): CommunicationResult<Unit> {
        markConversationReadCallCount.incrementAndGet()
        synchronized(markConversationReadCalls) {
            markConversationReadCalls.add(Triple(session, conversationId, messageId))
        }
        return markConversationReadResult
    }

    var notificationsResult: CommunicationResult<List<InAppNotification>> = CommunicationResult.Success(emptyList())
    var markNotificationReadResult: CommunicationResult<Unit> = CommunicationResult.Success(Unit)
    var markAllNotificationsReadResult: CommunicationResult<Unit> = CommunicationResult.Success(Unit)

    val fetchNotificationsCallCount = AtomicInteger(0)
    val markNotificationReadCallCount = AtomicInteger(0)
    val markAllNotificationsReadCallCount = AtomicInteger(0)

    val markNotificationReadCalls = mutableListOf<Pair<CommunicationSession, String>>()
    val markAllNotificationsReadCalls = mutableListOf<CommunicationSession>()

    var fetchNotificationsGate: CompletableDeferred<Unit>? = null
    var markNotificationReadGate: CompletableDeferred<Unit>? = null
    var markAllNotificationsReadGate: CompletableDeferred<Unit>? = null

        var throwCancellationOnFetchNotifications = false
    var nonCancellableFetchGates = false
    var nonCancellableMutationGates = false

    val observedNotificationSubscriptions = mutableListOf<CommunicationSession>()
    val fetchNotificationsGates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    val fetchNotificationsResults = mutableMapOf<Int, CommunicationResult<List<InAppNotification>>>()

    data class FetchInvocationRecord(
        val callIndex: Int,
        val session: CommunicationSession,
        val response: CommunicationResult<List<InAppNotification>>,
        var entered: Boolean = false,
        var returned: Boolean = false,
        var canceled: Boolean = false,
    )

    val fetchNotificationRecords = mutableListOf<FetchInvocationRecord>()

    override suspend fun fetchNotifications(session: CommunicationSession): CommunicationResult<List<InAppNotification>> {
        if (throwCancellationOnFetchNotifications) throw kotlinx.coroutines.CancellationException("Job cancelled")
        val callIndex = fetchNotificationsCallCount.incrementAndGet()
        val gate = fetchNotificationsGates[callIndex] ?: fetchNotificationsGate
        val response = fetchNotificationsResults[callIndex] ?: notificationsResult

        val record = FetchInvocationRecord(
            callIndex = callIndex,
            session = session,
            response = response,
            entered = true,
        )
        synchronized(fetchNotificationRecords) {
            fetchNotificationRecords.add(record)
        }

        val job = kotlin.coroutines.coroutineContext[Job]
        job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                record.canceled = true
                gate?.cancel(cause)
            }
        }

        if (nonCancellableFetchGates && gate != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    gate.await()
                } catch (_: Exception) {}
                record.returned = true
            }
            return response
        } else {
            try {
                gate?.await()
                record.returned = true
                return response
            } catch (e: kotlinx.coroutines.CancellationException) {
                record.canceled = true
                throw e
            }
        }
    }

    val markNotificationReadGates = mutableMapOf<String, CompletableDeferred<Unit>>()
    val markNotificationReadResults = mutableMapOf<String, CommunicationResult<Unit>>()

    data class MutationInvocationRecord(
        val callIndex: Int,
        val session: CommunicationSession,
        val id: String?,
        val response: CommunicationResult<Unit>,
        val job: Job?,
        var entered: Boolean = false,
        var returned: Boolean = false,
        var canceled: Boolean = false,
    )

    val markNotificationReadRecords = mutableListOf<MutationInvocationRecord>()

    override suspend fun markNotificationRead(session: CommunicationSession, notificationId: String): CommunicationResult<Unit> {
        val index = markNotificationReadCallCount.incrementAndGet()
        synchronized(markNotificationReadCalls) {
            markNotificationReadCalls.add(Pair(session, notificationId))
        }
        val gate = markNotificationReadGates[notificationId] ?: markNotificationReadGate
        val response = markNotificationReadResults[notificationId] ?: markNotificationReadResult
        val job = kotlin.coroutines.coroutineContext[Job]
        val record = MutationInvocationRecord(
            callIndex = index,
            session = session,
            id = notificationId,
            response = response,
            job = job,
            entered = true
        )
        synchronized(markNotificationReadRecords) {
            markNotificationReadRecords.add(record)
        }

        job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                record.canceled = true
            }
        }

        if (nonCancellableMutationGates && gate != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    gate.await()
                } catch (e: Exception) {
                    println("DEBUG_GATE_AWAIT_EX: $e")
                }
                record.returned = true
                println("DEBUG_RECORD_RETURNED_SET: record=${record.callIndex}")
            }
            return response
        } else {
            try {
                gate?.await()
                record.returned = true
                return response
            } catch (e: kotlinx.coroutines.CancellationException) {
                record.canceled = true
                throw e
            }
        }
    }

    val markAllNotificationsReadGates = mutableMapOf<Long, CompletableDeferred<Unit>>()
    val markAllNotificationsReadResults = mutableMapOf<Long, CommunicationResult<Unit>>()

    val markAllNotificationsReadRecords = mutableListOf<MutationInvocationRecord>()

    override suspend fun markAllNotificationsRead(session: CommunicationSession): CommunicationResult<Unit> {
        val index = markAllNotificationsReadCallCount.incrementAndGet()
        synchronized(markAllNotificationsReadCalls) {
            markAllNotificationsReadCalls.add(session)
        }
        val gate = markAllNotificationsReadGates[index.toLong()] ?: markAllNotificationsReadGate
        val response = markAllNotificationsReadResults[index.toLong()] ?: markAllNotificationsReadResult
        val job = kotlin.coroutines.coroutineContext[Job]
        val record = MutationInvocationRecord(
            callIndex = index,
            session = session,
            id = null,
            response = response,
            job = job,
            entered = true
        )
        synchronized(markAllNotificationsReadRecords) {
            markAllNotificationsReadRecords.add(record)
        }

        job?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                record.canceled = true
            }
        }

        if (nonCancellableMutationGates && gate != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    gate.await()
                } catch (e: Exception) {
                    println("DEBUG_GATE_AWAIT_EX: $e")
                }
                record.returned = true
                println("DEBUG_RECORD_RETURNED_SET: record=${record.callIndex}")
            }
            return response
        } else {
            try {
                gate?.await()
                record.returned = true
                return response
            } catch (e: kotlinx.coroutines.CancellationException) {
                record.canceled = true
                throw e
            }
        }
    }

    val observedSubscriptions = mutableListOf<Pair<CommunicationSession, String>>()
    private val conversationFlows = mutableMapOf<String, MutableSharedFlow<MessageRealtimeEvent>>()

    fun getConversationEventFlow(conversationId: String): MutableSharedFlow<MessageRealtimeEvent> {
        return synchronized(conversationFlows) {
            conversationFlows.getOrPut(conversationId) { MutableSharedFlow(extraBufferCapacity = 64) }
        }
    }

    override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> {
        synchronized(observedSubscriptions) {
            observedSubscriptions.add(Pair(session, conversationId))
        }
        return getConversationEventFlow(conversationId)
    }

    val activeNotificationCollectorsCount = AtomicInteger(0)
    val peakActiveNotificationCollectorsCount = AtomicInteger(0)
    val collectorInvocationCount = AtomicInteger(0)
    internal val collectorInvocationRecords = mutableListOf<CollectorInvocationRecord>()
    val collectorTeardownGates = mutableMapOf<Int, CompletableDeferred<Unit>>()
    var notificationTeardownGate: CompletableDeferred<Unit>? = null

    override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> {
        val collectorId = collectorInvocationCount.incrementAndGet()
        val record = CollectorInvocationRecord(id = collectorId, session = session, started = true)
        synchronized(collectorInvocationRecords) {
            collectorInvocationRecords.add(record)
        }
        synchronized(observedNotificationSubscriptions) {
            observedNotificationSubscriptions.add(session)
        }
        return flow {
            val current = activeNotificationCollectorsCount.incrementAndGet()
            peakActiveNotificationCollectorsCount.updateAndGet { maxOf(it, current) }
            try {
                notificationEventFlow.collect { emit(it) }
            } finally {
                record.teardownEntered = true
                val gate = collectorTeardownGates[collectorId] ?: notificationTeardownGate
                if (gate != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        try {
                            gate.await()
                        } catch (_: Exception) {}
                    }
                }
                record.teardownCompleted = true
                activeNotificationCollectorsCount.decrementAndGet()
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private lateinit var repository: FakeFeatureCommunicationRepository
    private lateinit var viewModel: InboxViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeFeatureCommunicationRepository()
        viewModel = InboxViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bindSessionLoadsInbox() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("c1", "l1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.inboxResult = CommunicationResult.Success(listOf(conv))

        viewModel.bindSession(session)

        val state = viewModel.uiState.value
        assertEquals(session, state.boundSession)
        assertEquals(1, state.conversations.size)
        assertEquals("Guitar", state.conversations[0].listingTitle)
    }

    @Test
    fun sessionChangeClearsPreviousRowsImmediately() = runTest {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val conv1 = ConversationSummary("c1", "l1", "Guitar", "user-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val conv2 = ConversationSummary("c2", "l2", "Bike", "user-2", "seller-2", "seller-2", "Bob", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        repository.inboxResult = CommunicationResult.Success(listOf(conv1))
        viewModel.bindSession(s1)
        assertEquals(1, viewModel.uiState.value.conversations.size)
        assertEquals("c1", viewModel.uiState.value.conversations[0].id)

        repository.inboxResult = CommunicationResult.Success(listOf(conv2))
        viewModel.bindSession(s2)
        assertEquals(1, viewModel.uiState.value.conversations.size)
        assertEquals("c2", viewModel.uiState.value.conversations[0].id) // s1's row c1 replaced by s2's row c2
    }

    @Test
    fun stalePreviousSessionResultIgnored() = runTest {
        val s1 = CommunicationSession("user-1", 1L)
        val s2 = CommunicationSession("user-2", 2L)
        val gate = CompletableDeferred<Unit>()
        repository.fetchInboxGate = gate

        viewModel.bindSession(s1)
        viewModel.bindSession(s2) // Rebind before s1 completes

        gate.complete(Unit)

        assertEquals(s2, viewModel.uiState.value.boundSession)
    }

    @Test
    fun refreshFailureRetainsPreviousSuccessfulRows() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("c1", "l1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.inboxResult = CommunicationResult.Success(listOf(conv))

        viewModel.bindSession(session)
        assertEquals(1, viewModel.uiState.value.conversations.size)

        repository.inboxResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(1, state.conversations.size) // Retains previous rows
        assertEquals("You're offline or the network is unavailable. Try again.", state.errorMessage)
    }

    @Test
    fun buyingAndSellingFiltersWorkWithoutExtraBackendCalls() = runTest {
        val session = CommunicationSession("user-me", 1L)
        val buyingConv = ConversationSummary("c1", "l1", "Guitar", "user-me", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val sellingConv = ConversationSummary("c2", "l2", "Bike", "buyer-2", "user-me", "buyer-2", "Bob", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.inboxResult = CommunicationResult.Success(listOf(buyingConv, sellingConv))

        viewModel.bindSession(session)
        val initialCalls = repository.fetchInboxCallCount.get()

        viewModel.setFilter(InboxFilter.BUYING)
        assertEquals(1, viewModel.uiState.value.filteredConversations.size)
        assertEquals("c1", viewModel.uiState.value.filteredConversations[0].id)

        viewModel.setFilter(InboxFilter.SELLING)
        assertEquals(1, viewModel.uiState.value.filteredConversations.size)
        assertEquals("c2", viewModel.uiState.value.filteredConversations[0].id)

        assertEquals(initialCalls, repository.fetchInboxCallCount.get()) // Zero extra fetches
    }

    @Test
    fun authRequiredEmitsHandoffEvent() = runTest {
        val session = CommunicationSession("user-1", 1L)
        repository.inboxResult = CommunicationResult.Failure(CommunicationFailure.AuthRequired)

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.bindSession(session)

        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToAuth)
        job.cancel()
    }
}
