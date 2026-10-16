package com.fairswap.feature.communication

import com.fairswap.core.communication.ChatMessage
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.ConversationSummary
import com.fairswap.core.communication.MessageRealtimeEvent
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    private lateinit var repository: FakeFeatureCommunicationRepository
    private lateinit var reqIdProvider: FakeRequestIdProvider
    private lateinit var viewModel: ThreadViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeFeatureCommunicationRepository()
        reqIdProvider = FakeRequestIdProvider()
        viewModel = ThreadViewModel(repository, reqIdProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bindLoadsConversationAndNewestMessagesInChronologicalOrder() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val m1 = ChatMessage("msg-1", "conv-1", "seller-1", "req-1", "First", "2026-10-06T10:00:00Z")
        val m2 = ChatMessage("msg-2", "conv-1", "buyer-1", "req-2", "Second", "2026-10-06T10:01:00Z")

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(listOf(m1, m2))

        viewModel.bind(session, "conv-1")

        val state = viewModel.uiState.value
        assertEquals("conv-1", state.conversationId)
        assertEquals("Guitar", state.summary?.listingTitle)
        assertEquals(2, state.messages.size)
        assertEquals("msg-1", state.messages[0].id)
        assertEquals("msg-2", state.messages[1].id)
    }

    @Test
    fun sessionChangeClearsOldStateImmediately() = runTest {
        val s1 = CommunicationSession("buyer-1", 1L)
        val s2 = CommunicationSession("buyer-2", 2L)
        val conv1 = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val conv2 = ConversationSummary("conv-2", "list-2", "Bike", "buyer-2", "seller-2", "seller-2", "Bob", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        repository.conversationResult = CommunicationResult.Success(conv1)
        viewModel.bind(s1, "conv-1")
        assertEquals("conv-1", viewModel.uiState.value.conversationId)
        assertEquals("Guitar", viewModel.uiState.value.summary?.listingTitle)

        repository.conversationResult = CommunicationResult.Success(conv2)
        viewModel.bind(s2, "conv-2")

        val state = viewModel.uiState.value
        assertEquals("conv-2", state.conversationId)
        assertEquals("Bike", state.summary?.listingTitle)
    }

    @Test
    fun realtimeArrivesWhileRpcSuspendedAndPreventsResurrectingFalseFailure() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        // 1. bind thread
        viewModel.bind(session, "conv-1")

        // 2. onVisible()
        viewModel.onVisible()

        // 3. enter composer text
        viewModel.onComposerTextChanged("Suspended RPC message")

        // 4. call sendMessage() with suspended RPC gate
        val gate = CompletableDeferred<Unit>()
        repository.sendMessageGate = gate
        repository.sendMessageResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)

        val sendJob = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sendMessage() }

        // 5. verify send RPC has started and capture client_request_id A
        assertEquals(1, repository.sendMessageCallCount.get())
        val reqIdA = repository.capturedSendMessageClientRequestId
        assertNotNull(reqIdA)
        assertTrue(viewModel.uiState.value.isSending)

        // 6. keep the RPC suspended, 7. emit authoritative Realtime message with client_request_id A
        val realtimeMsg = ChatMessage("msg-authoritative-100", "conv-1", "buyer-1", reqIdA!!, "Suspended RPC message", "2026-10-06T10:05:00Z")
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.Inserted(realtimeMsg))

        // 8. prove the pending logical send resolves
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals("", viewModel.uiState.value.composerText)
        assertNull(viewModel.uiState.value.pendingRequestId)

        // 9. release RPC with NetworkUnavailable failure
        gate.complete(Unit)
        sendJob.join()

        // 10. prove no false failure is resurrected, 1 authoritative bubble exists
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isSending)
        assertNull(finalState.sendError)
        assertEquals(1, finalState.messages.size)
        assertEquals("msg-authoritative-100", finalState.messages[0].id)
    }

    @Test
    fun realtimeArrivesBeforeRpcAndMatchesPendingClientRequestId() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()
        viewModel.onComposerTextChanged("Hello from buyer")

        // Set up RPC send failure to test if Realtime delivery prevents resurrecting false error
        repository.sendMessageResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)

        // Realtime arrives BEFORE RPC finishes
        val realtimeMsg = ChatMessage("msg-100", "conv-1", "buyer-1", "test-req-id-1", "Hello from buyer", "2026-10-06T10:05:00Z")
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.Inserted(realtimeMsg))

        // Send message RPC fails afterwards
        viewModel.sendMessage()

        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("msg-100", state.messages[0].id)
        assertFalse(state.isSending)
        assertNull(state.sendError) // No false failure resurrected!
    }

    @Test
    fun readyForBackfillTriggersAuthoritativeNewestSelect() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        val newMsg = ChatMessage("msg-99", "conv-1", "seller-1", "req-99", "Backfilled message", "2026-10-06T10:10:00Z")
        repository.newestMessagesResult = CommunicationResult.Success(listOf(newMsg))

        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.ReadyForBackfill)

        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("msg-99", state.messages[0].id)
    }

    @Test
    fun visibilityLifecycleManagesRealtimeSubscriptions() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        // 1. bind() -> zero Realtime subscriptions
        viewModel.bind(session, "conv-1")
        assertEquals(0, repository.observedSubscriptions.size)

        // 2. first onVisible() -> one subscription + reconciliation
        viewModel.onVisible()
        assertEquals(1, repository.observedSubscriptions.size)
        assertEquals("conv-1", repository.observedSubscriptions[0].second)

        // 3. onNotVisible() -> old subscription cancelled
        viewModel.onNotVisible()

        // 4. event while hidden -> not consumed
        val hiddenMsg = ChatMessage("msg-hidden", "conv-1", "seller-1", "req-hidden", "Hidden message", "2026-10-06T10:00:00Z")
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.Inserted(hiddenMsg))
        assertTrue(viewModel.uiState.value.messages.none { it.id == "msg-hidden" })

        // 5. second onVisible() -> a NEW subscription is established
        viewModel.onVisible()
        assertEquals(2, repository.observedSubscriptions.size)
        assertEquals("conv-1", repository.observedSubscriptions[1].second)
    }

    @Test
    fun staleSameUserDifferentConversationResultsIgnored() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv1 = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val conv2 = ConversationSummary("conv-2", "list-2", "Bike", "buyer-1", "seller-2", "seller-2", "Bob", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        // 1. Bind session S to conversation A
        repository.conversationResult = CommunicationResult.Success(conv1)
        viewModel.bind(session, "conv-1")

        // 2. Call onVisible()
        viewModel.onVisible()

        // 3. Prove subscription A exists
        assertTrue(repository.observedSubscriptions.any { it.first == session && it.second == "conv-1" })

        // 4. Rebind same session S to conversation B
        repository.conversationResult = CommunicationResult.Success(conv2)
        viewModel.bind(session, "conv-2")

        // 5. Establish correct B visibility state
        viewModel.onVisible()
        assertTrue(repository.observedSubscriptions.any { it.first == session && it.second == "conv-2" })

        // 6. Emit an event through the OLD conversation-A subscription
        val m1 = ChatMessage("m1", "conv-1", "seller-1", "req-1", "Stale conv1 msg", "2026-10-06T10:00:00Z")
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.Inserted(m1))

        // 7. Prove A's event cannot mutate B
        assertEquals("conv-2", viewModel.uiState.value.conversationId)
        assertTrue(viewModel.uiState.value.messages.none { it.id == "m1" })

        // 8. Emit B event
        val m2 = ChatMessage("m2", "conv-2", "seller-2", "req-2", "Active conv2 msg", "2026-10-06T10:05:00Z")
        repository.getConversationEventFlow("conv-2").emit(MessageRealtimeEvent.Inserted(m2))

        // 9. Prove B event is consumed
        assertTrue(viewModel.uiState.value.messages.any { it.id == "m2" })
    }

    @Test
    fun olderPagePaginationUsesOldestMessageCursorAndDisablesWhenSmall() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        // Initial page has 30 items so canLoadOlder is true
        val initialPage = (1..30).map { i ->
            val timestamp = if (i == 1) "2026-10-06T09:00:00Z" else "2026-10-06T09:00:${i.toString().padStart(2, '0')}Z"
            val id = if (i == 1) "msg-oldest" else "msg-init-$i"
            ChatMessage(id, "conv-1", "seller-1", "req-init-$i", "Init $i", timestamp)
        }

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(initialPage)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        assertTrue(viewModel.uiState.value.canLoadOlder)

        // Page smaller than 30 (size = 5)
        val olderPage = (1..5).map { i ->
            ChatMessage("msg-history-$i", "conv-1", "seller-1", "req-h-$i", "History $i", "2026-10-06T08:00:0${i}Z")
        }
        repository.olderMessagesResult = CommunicationResult.Success(olderPage)

        viewModel.loadOlderMessages()

        // Cursor matches oldest loaded message
        val cursor = repository.recordedOlderCursors.last()
        assertEquals("2026-10-06T09:00:00Z", cursor.createdAt)
        assertEquals("msg-oldest", cursor.id)

        // Result < ANDROID_CLIENT_PAGE_SIZE (30) -> canLoadOlder becomes false
        assertFalse(viewModel.uiState.value.canLoadOlder)
    }

    @Test
    fun paginationSameTimestampTieBreakUsesCorrectOldestKeysetPair() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        // Multiple messages sharing the oldest createdAt timestamp
        val m1 = ChatMessage("msg-aaa", "conv-1", "seller-1", "req-1", "A", "2026-10-06T09:00:00Z")
        val m2 = ChatMessage("msg-bbb", "conv-1", "seller-1", "req-2", "B", "2026-10-06T09:00:00Z")
        val initialMessages = (1..30).map { i ->
            if (i == 1) m1 else if (i == 2) m2 else ChatMessage("msg-$i", "conv-1", "seller-1", "req-$i", "Text $i", "2026-10-06T09:${i.toString().padStart(2, '0')}:00Z")
        }

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(initialMessages)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        viewModel.loadOlderMessages()

        val cursor = repository.recordedOlderCursors.last()
        assertEquals("2026-10-06T09:00:00Z", cursor.createdAt)
        assertEquals("msg-aaa", cursor.id)
    }

    @Test
    fun olderPageDeduplicatesExistingMessageAndPreservesChronologicalOrder() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        val msgX = ChatMessage("msg-X", "conv-1", "seller-1", "req-x", "Message X", "2026-10-06T09:00:00Z")
        val initialPage = (1..30).map { i ->
            if (i == 1) msgX else ChatMessage("msg-init-$i", "conv-1", "seller-1", "req-$i", "Init $i", "2026-10-06T09:${i.toString().padStart(2, '0')}:00Z")
        }

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(initialPage)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        // Older page returns msgX AGAIN plus additional older message msg-Y
        val msgY = ChatMessage("msg-Y", "conv-1", "seller-1", "req-y", "Older Message Y", "2026-10-06T08:00:00Z")
        repository.olderMessagesResult = CommunicationResult.Success(listOf(msgY, msgX))

        viewModel.loadOlderMessages()

        val messages = viewModel.uiState.value.messages
        assertEquals(1, messages.count { it.id == "msg-X" })
        assertEquals(1, messages.count { it.id == "msg-Y" })
        assertEquals("msg-Y", messages.first().id)
    }

    @Test
    fun staleInFlightOlderPageResultFromConvACannotMutateConvB() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val convA = ConversationSummary("conv-A", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val convB = ConversationSummary("conv-B", "list-2", "Bike", "buyer-1", "seller-2", "seller-2", "Bob", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)

        val pageA = (1..30).map { i -> ChatMessage("msg-a-$i", "conv-A", "seller-1", "req-a-$i", "A $i", "2026-10-06T09:${i.toString().padStart(2, '0')}:00Z") }
        val pageB = (1..30).map { i -> ChatMessage("msg-b-$i", "conv-B", "seller-2", "req-b-$i", "B $i", "2026-10-06T09:${i.toString().padStart(2, '0')}:00Z") }

        // 1. Bind to conv-A
        repository.conversationResult = CommunicationResult.Success(convA)
        repository.newestMessagesResult = CommunicationResult.Success(pageA)
        viewModel.bind(session, "conv-A")
        viewModel.onVisible()

        // 2 & 3. Start loadOlderMessages for conv-A with suspended gate
        val gateA = CompletableDeferred<Unit>()
        repository.fetchOlderMessagesGate = gateA
        val olderMsgA = ChatMessage("msg-a-older", "conv-A", "seller-1", "req-old-a", "Older A", "2026-10-06T08:00:00Z")
        repository.olderMessagesResult = CommunicationResult.Success(listOf(olderMsgA))

        val olderJobA = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadOlderMessages() }

        // 5 & 6. Rebind same session S to conversation B
        repository.conversationResult = CommunicationResult.Success(convB)
        repository.newestMessagesResult = CommunicationResult.Success(pageB)
        viewModel.bind(session, "conv-B")
        viewModel.onVisible()

        // Snapshot authoritative state of B before releasing A's older-page result
        val snapshotBeforeACompletes = viewModel.uiState.value
        val bMessageIdsBefore = snapshotBeforeACompletes.messages.map { it.id }

        // 7. Release A's older page result
        gateA.complete(Unit)
        olderJobA.join()

        // 8. Prove full B state immunity: stale A completion cannot alter B's state
        val stateAfterACompletes = viewModel.uiState.value
        assertEquals("conv-B", stateAfterACompletes.conversationId)
        assertEquals(snapshotBeforeACompletes.summary, stateAfterACompletes.summary)
        assertEquals(bMessageIdsBefore, stateAfterACompletes.messages.map { it.id })
        assertTrue(stateAfterACompletes.messages.none { it.id == "msg-a-older" })
        assertEquals(snapshotBeforeACompletes.canLoadOlder, stateAfterACompletes.canLoadOlder)
        assertEquals(snapshotBeforeACompletes.isLoadingOlder, stateAfterACompletes.isLoadingOlder)
        assertEquals(snapshotBeforeACompletes.messagingUnavailableError, stateAfterACompletes.messagingUnavailableError)
    }

    @Test
    fun ownLatestMessageDoesNotMarkReadButCounterpartyMessageDoes() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val myLatestMsg = ChatMessage("msg-2", "conv-1", "buyer-1", "req-2", "My latest msg", "2026-10-06T10:01:00Z")

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(listOf(myLatestMsg))

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        // Case A: Newest authoritative message is from current user -> 0 markConversationRead calls
        assertEquals(0, repository.markConversationReadCallCount.get())

        // Case B: Newest authoritative message is from counterparty -> 1 markConversationRead call with correct message ID
        val counterpartyMsg = ChatMessage("msg-3", "conv-1", "seller-1", "req-3", "Counterparty reply", "2026-10-06T10:02:00Z")
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.Inserted(counterpartyMsg))

        assertEquals(1, repository.markConversationReadCallCount.get())
        val lastCall = repository.markConversationReadCalls.last()
        assertEquals("conv-1", lastCall.second)
        assertEquals("msg-3", lastCall.third)
    }

    @Test
    fun nullSenderIdDoesNotMarkRead() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val nullSenderMsg = ChatMessage("msg-system", "conv-1", null, "req-sys", "System notification", "2026-10-06T10:01:00Z")

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(listOf(nullSenderMsg))

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        // Case C: Newest authoritative senderId is null -> 0 markConversationRead calls
        assertEquals(0, repository.markConversationReadCallCount.get())
    }

    @Test
    fun failedMarkReadRetriesAfterLaterReconciliation() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        val counterpartyMsg = ChatMessage("msg-5", "conv-1", "seller-1", "req-5", "Counterparty message", "2026-10-06T10:01:00Z")

        repository.conversationResult = CommunicationResult.Success(conv)
        repository.newestMessagesResult = CommunicationResult.Success(listOf(counterpartyMsg))

        // First attempt fails
        repository.markConversationReadResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)

        viewModel.bind(session, "conv-1")
        viewModel.onVisible()

        val callsBeforeRetry = repository.markConversationReadCallCount.get()
        assertTrue(callsBeforeRetry > 0)

        // Later reconciliation occurs when markRead is successful
        repository.markConversationReadResult = CommunicationResult.Success(Unit)
        repository.getConversationEventFlow("conv-1").emit(MessageRealtimeEvent.ReadyForBackfill)
        testScheduler.advanceUntilIdle()

        assertEquals(callsBeforeRetry + 1, repository.markConversationReadCallCount.get())
        assertEquals("msg-5", repository.markConversationReadCalls.last().third)
    }

    @Test
    fun newerEditedComposerTextSurvivesOlderRpcCompletion() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        viewModel.bind(session, "conv-1")
        viewModel.onComposerTextChanged("First message")

        // First send fails
        repository.sendMessageResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.sendMessage()
        val reqId1 = viewModel.uiState.value.pendingRequestId
        assertEquals("test-req-id-1", reqId1)

        // User edits text before retrying -> creates new logical edit
        viewModel.onComposerTextChanged("Edited second message")
        assertNull(viewModel.uiState.value.pendingRequestId)

        // Late RPC completion or Realtime for reqId1 arrives
        val oldMsg = ChatMessage("m1", "conv-1", "buyer-1", "test-req-id-1", "First message", "2026-10-06T10:00:00Z")
        repository.messageEventFlow.emit(MessageRealtimeEvent.Inserted(oldMsg))

        // "Edited second message" composer text must NOT be cleared by older reqId1 completion!
        assertEquals("Edited second message", viewModel.uiState.value.composerText)
    }

    @Test
    fun composerEditWhileRpcInFlightPreservesNewText() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        val conv = ConversationSummary("conv-1", "list-1", "Guitar", "buyer-1", "seller-1", "seller-1", "Alice", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z", null, false)
        repository.conversationResult = CommunicationResult.Success(conv)

        // 1. Send logical message A with request ID A
        viewModel.bind(session, "conv-1")
        viewModel.onVisible()
        viewModel.onComposerTextChanged("Logical Message A")

        // 2. Suspend RPC A
        val gateA = CompletableDeferred<Unit>()
        repository.sendMessageGate = gateA
        val msgA = ChatMessage("msg-a", "conv-1", "buyer-1", "test-req-id-1", "Logical Message A", "2026-10-06T10:00:00Z")
        repository.sendMessageResult = CommunicationResult.Success(msgA)

        val sendJobA = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sendMessage() }
        val reqIdA = repository.capturedSendMessageClientRequestId
        assertEquals("test-req-id-1", reqIdA)

        // 3. User edits composer to logical message B while A is still in flight
        viewModel.onComposerTextChanged("Logical Message B")

        // 4. Complete A via RPC or Realtime
        gateA.complete(Unit)
        sendJobA.join()

        // 5 & 6. Composer must still contain B; completion of A must not clear or replace B
        assertEquals("Logical Message B", viewModel.uiState.value.composerText)

        // 7. Sending B must use a new client_request_id B
        val gateB = CompletableDeferred<Unit>()
        repository.sendMessageGate = gateB
        val msgB = ChatMessage("msg-b", "conv-1", "buyer-1", "test-req-id-2", "Logical Message B", "2026-10-06T10:01:00Z")
        repository.sendMessageResult = CommunicationResult.Success(msgB)

        val sendJobB = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sendMessage() }
        val reqIdB = repository.capturedSendMessageClientRequestId
        assertEquals("test-req-id-2", reqIdB)

        gateB.complete(Unit)
        sendJobB.join()
    }
}
