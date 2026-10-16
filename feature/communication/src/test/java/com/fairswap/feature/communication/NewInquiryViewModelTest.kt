package com.fairswap.feature.communication

import com.fairswap.core.communication.ChatMessage
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.StartConversationResult
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
import java.util.concurrent.atomic.AtomicInteger

class FakeRequestIdProvider : CommunicationRequestIdProvider {
    val counter = AtomicInteger(0)
    override fun generateRequestId(): String = "test-req-id-${counter.incrementAndGet()}"
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewInquiryViewModelTest {

    private lateinit var repository: FakeFeatureCommunicationRepository
    private lateinit var reqIdProvider: FakeRequestIdProvider
    private lateinit var viewModel: NewInquiryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeFeatureCommunicationRepository()
        reqIdProvider = FakeRequestIdProvider()
        viewModel = NewInquiryViewModel(repository, reqIdProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noRpcAndNoRequestIdAllocationOnBlankMessage() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        viewModel.bind(session, "list-1")
        viewModel.onComposerTextChanged("   ")

        viewModel.sendInquiry()

        assertEquals(0, repository.startConversationCallCount.get())
        assertEquals(0, reqIdProvider.counter.get())
        assertNull(viewModel.uiState.value.pendingRequestId)
    }

    @Test
    fun networkUnavailableSameRequestIdReusedOnRetryAndNewIdOnEdit() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        viewModel.bind(session, "list-1")
        viewModel.onComposerTextChanged("Hi is this available?")

        // 1. Initial send generates ID 1
        repository.startConversationResult = CommunicationResult.Failure(CommunicationFailure.NetworkUnavailable)
        viewModel.sendInquiry()
        assertEquals(1, repository.startConversationCallCount.get())
        val reqId1 = viewModel.uiState.value.pendingRequestId
        assertEquals("test-req-id-1", reqId1)

        // 2. Explicit retry without edit uses ID 1
        viewModel.sendInquiry()
        assertEquals(2, repository.startConversationCallCount.get())
        assertEquals("test-req-id-1", viewModel.uiState.value.pendingRequestId)

        // 3. Edit abandons ID 1
        viewModel.onComposerTextChanged("Hi is this still available?")
        assertNull(viewModel.uiState.value.pendingRequestId)

        // 4. Next send generates ID 2
        viewModel.sendInquiry()
        assertEquals(3, repository.startConversationCallCount.get())
        assertEquals("test-req-id-2", viewModel.uiState.value.pendingRequestId)
    }

    @Test
    fun serviceUnavailableSameRequestIdReusedOnRetryAndNewIdOnEdit() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        viewModel.bind(session, "list-1")
        viewModel.onComposerTextChanged("Hi is this available?")

        // 1. Initial send generates ID 1
        repository.startConversationResult = CommunicationResult.Failure(CommunicationFailure.ServiceUnavailable)
        viewModel.sendInquiry()
        assertEquals(1, repository.startConversationCallCount.get())
        val reqId1 = viewModel.uiState.value.pendingRequestId
        assertEquals("test-req-id-1", reqId1)

        // 2. Explicit retry without edit uses ID 1
        viewModel.sendInquiry()
        assertEquals(2, repository.startConversationCallCount.get())
        assertEquals("test-req-id-1", viewModel.uiState.value.pendingRequestId)

        // 3. Edit abandons ID 1
        viewModel.onComposerTextChanged("Hi is this still available?")
        assertNull(viewModel.uiState.value.pendingRequestId)

        // 4. Next send generates ID 2
        viewModel.sendInquiry()
        assertEquals(3, repository.startConversationCallCount.get())
        assertEquals("test-req-id-2", viewModel.uiState.value.pendingRequestId)
    }

    @Test
    fun successEmitsConversationIdNavigationEvent() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        viewModel.bind(session, "list-1")
        viewModel.onComposerTextChanged("Inquiry message")

        val msg = ChatMessage("m1", "conv-88", "buyer-1", "test-req-id-1", "Inquiry message", "2026-10-06T10:00:00Z")
        repository.startConversationResult = CommunicationResult.Success(
            StartConversationResult("conv-88", true, msg)
        )

        val events = mutableListOf<CommunicationNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events.add(it) }
        }

        viewModel.sendInquiry()

        assertEquals(1, events.size)
        assertTrue(events[0] is CommunicationNavigationEvent.NavigateToConversation)
        val nav = events[0] as CommunicationNavigationEvent.NavigateToConversation
        assertEquals("conv-88", nav.conversationId)
        assertEquals("list-1", nav.listingId)
        job.cancel()
    }

    @Test
    fun cancellationExceptionRethrownAndNeverMappedToFailure() = runTest {
        val session = CommunicationSession("buyer-1", 1L)
        viewModel.bind(session, "list-1")
        viewModel.onComposerTextChanged("Test message")

        repository.throwCancellationOnStartConversation = true

        try {
            viewModel.performSendInquiryInternal(session, "list-1", "Test message", "req-1")
            org.junit.Assert.fail("Expected CancellationException")
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("Job cancelled", e.message)
        }

        assertNull(viewModel.uiState.value.sendError)
    }
}
