package com.fairswap.core.communication

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.PostgresChangeFilter
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class FakeRealtimeChannelSubscriptionHandle : RealtimeChannelSubscriptionHandle {
    val statusFlow = MutableStateFlow<RealtimeChannel.Status>(RealtimeChannel.Status.UNSUBSCRIBED)
    val insertFlow = MutableSharedFlow<PostgresAction.Insert>(extraBufferCapacity = 64)

    var requestedSpec: RealtimeFilterSpec? = null
    val subscribeStarted = CompletableDeferred<Unit>()
    val removeCalled = CompletableDeferred<Unit>()
    val removeCallCount = AtomicInteger(0)

    override val status: Flow<RealtimeChannel.Status> = statusFlow

    override fun postgresChangeFlow(spec: RealtimeFilterSpec): Flow<PostgresAction.Insert> {
        requestedSpec = spec
        return insertFlow
    }

    override suspend fun subscribe() {
        subscribeStarted.complete(Unit)
    }

    override suspend fun remove() {
        removeCallCount.incrementAndGet()
        removeCalled.complete(Unit)
    }
}

internal class FakeRealtimeChannelClient : RealtimeChannelClient {
    val subscriptions = mutableMapOf<String, FakeRealtimeChannelSubscriptionHandle>()

    override fun getOrCreateSubscription(channelName: String, spec: RealtimeFilterSpec): RealtimeChannelSubscriptionHandle {
        return subscriptions.getOrPut(channelName) { FakeRealtimeChannelSubscriptionHandle() }
    }
}

internal class FakeRealtimeAdapter : CommunicationRealtime {
    val messageEventFlow = MutableSharedFlow<MessageRealtimeEvent>(extraBufferCapacity = 64)
    val notificationEventFlow = MutableSharedFlow<NotificationRealtimeEvent>(extraBufferCapacity = 64)

    var activeMessageConversationId: String? = null
    var activeNotificationUserId: String? = null
    val channelRemoved = AtomicBoolean(false)

    override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> {
        activeMessageConversationId = conversationId
        return messageEventFlow
    }

    override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> {
        activeNotificationUserId = session.userId
        return notificationEventFlow
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationRealtimeTest {

    private lateinit var fakeClient: FakeRealtimeChannelClient
    private lateinit var productionRealtime: SupabaseCommunicationRealtime
    private lateinit var fakeAdapter: FakeRealtimeAdapter
    private lateinit var sessionOwner: CommunicationSessionOwner
    private lateinit var gateway: FakeCommunicationGateway
    private lateinit var repository: DefaultCommunicationRepository

    @Before
    fun setUp() {
        fakeClient = FakeRealtimeChannelClient()
        productionRealtime = SupabaseCommunicationRealtime(fakeClient)
        fakeAdapter = FakeRealtimeAdapter()
        sessionOwner = CommunicationSessionOwner()
        gateway = FakeCommunicationGateway()
        repository = DefaultCommunicationRepository(gateway, fakeAdapter, sessionOwner)
    }

    @Test
    fun productionMessageSubscriptionReceivesConversationFilterTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        val targetConv = "conv-123"

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeMessageEvents(session, targetConv).collect {}
        }

        val handle = fakeClient.subscriptions["messages_conv-123"]
        assertNotNull(handle)
        assertEquals("messages", handle!!.requestedSpec?.table)
        assertEquals("conversation_id", handle.requestedSpec?.filterColumn)
        assertEquals("conv-123", handle.requestedSpec?.filterValue)
        job.cancel()
    }

    @Test
    fun productionNotificationSubscriptionReceivesUserFilterTest() = runTest {
        val session = sessionOwner.updateSession("user-456")!!

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeNotificationEvents(session).collect {}
        }

        val handle = fakeClient.subscriptions["notifications_user-456"]
        assertNotNull(handle)
        assertEquals("notifications", handle!!.requestedSpec?.table)
        assertEquals("user_id", handle.requestedSpec?.filterColumn)
        assertEquals("user-456", handle.requestedSpec?.filterValue)
        job.cancel()
    }

    @Test
    fun productionMalformedRawRowSkippedAndNextValidRowEmittedTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        val targetConv = "conv-1"

        val received = mutableListOf<MessageRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeMessageEvents(session, targetConv).collect {
                received.add(it)
            }
        }

        val handle = fakeClient.subscriptions["messages_conv-1"]!!

        // Malformed record (missing required fields for MessageRowDto)
        val malformedRecord = buildJsonObject {
            put("conversation_id", "conv-1")
            put("content", "invalid_content_no_id")
        }
        val malformedAction = PostgresAction.Insert(
            record = malformedRecord,
            columns = emptyList(),
            commitTimestamp = kotlin.time.Clock.System.now(),
            serializer = io.github.jan.supabase.serializer.KotlinXSerializer(),
        )

        // Valid record
        val validRecord = buildJsonObject {
            put("id", "m-2")
            put("conversation_id", "conv-1")
            put("sender_id", "s-1")
            put("client_request_id", "req-12345678")
            put("content", "Valid message")
            put("created_at", "2026-10-06T10:00:00Z")
            put("is_duplicate", false)
        }
        val validAction = PostgresAction.Insert(
            record = validRecord,
            columns = emptyList(),
            commitTimestamp = kotlin.time.Clock.System.now(),
            serializer = io.github.jan.supabase.serializer.KotlinXSerializer(),
        )

        handle.insertFlow.emit(malformedAction)
        handle.insertFlow.emit(validAction)

        assertEquals(1, received.size)
        assertTrue(received[0] is MessageRealtimeEvent.Inserted)
        assertEquals("m-2", (received[0] as MessageRealtimeEvent.Inserted).message.id)
        job.cancel()
    }

    @Test
    fun productionInitialSubscribedEmitsReadyForBackfillTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val received = mutableListOf<MessageRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeMessageEvents(session, "conv-1").collect {
                received.add(it)
            }
        }

        val handle = fakeClient.subscriptions["messages_conv-1"]!!
        handle.statusFlow.value = RealtimeChannel.Status.SUBSCRIBED

        assertEquals(1, received.size)
        assertEquals(MessageRealtimeEvent.ReadyForBackfill, received[0])
        job.cancel()
    }

    @Test
    fun productionReconnectSubscribedEmitsSecondReadyForBackfillTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val received = mutableListOf<NotificationRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeNotificationEvents(session).collect {
                received.add(it)
            }
        }

        val handle = fakeClient.subscriptions["notifications_user-1"]!!

        handle.statusFlow.value = RealtimeChannel.Status.SUBSCRIBED
        handle.statusFlow.value = RealtimeChannel.Status.UNSUBSCRIBED
        handle.statusFlow.value = RealtimeChannel.Status.SUBSCRIBED

        assertEquals(2, received.size)
        assertEquals(NotificationRealtimeEvent.ReadyForBackfill, received[0])
        assertEquals(NotificationRealtimeEvent.ReadyForBackfill, received[1])
        job.cancel()
    }

    @Test
    fun productionCollectorCancellationRemovesChannelExactlyOnceTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeNotificationEvents(session).collect {}
        }

        val handle = fakeClient.subscriptions["notifications_user-1"]!!
        handle.subscribeStarted.await()

        job.cancel()
        job.join()

        handle.removeCalled.await()
        assertEquals(1, handle.removeCallCount.get())
    }

    @Test
    fun productionCollectorCancellationPropagatesTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            productionRealtime.observeNotificationEvents(session).collect {}
        }

        val handle = fakeClient.subscriptions["notifications_user-1"]!!
        handle.subscribeStarted.await()

        job.cancel(CancellationException("Scope cancelled"))
        job.join()

        handle.removeCalled.await()
        assertEquals(1, handle.removeCallCount.get())
    }

    @Test
    fun repositoryWrongConversationRowRejectedDefenseInDepthTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        val targetConv = "conv-1"

        val received = mutableListOf<MessageRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessageEvents(session, targetConv).collect {
                received.add(it)
            }
        }

        val msgWrong = ChatMessage("m-1", "conv-2", "seller-1", "req-1", "Wrong conv", "2026-10-06T10:00:00Z")
        val msgRight = ChatMessage("m-2", "conv-1", "seller-1", "req-2", "Right conv", "2026-10-06T10:01:00Z")

        fakeAdapter.messageEventFlow.emit(MessageRealtimeEvent.Inserted(msgWrong))
        fakeAdapter.messageEventFlow.emit(MessageRealtimeEvent.Inserted(msgRight))

        assertEquals(1, received.size)
        assertTrue(received[0] is MessageRealtimeEvent.Inserted)
        assertEquals("m-2", (received[0] as MessageRealtimeEvent.Inserted).message.id)
        job.cancel()
    }

    @Test
    fun repositoryWrongNotificationUserRowRejectedDefenseInDepthTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val received = mutableListOf<NotificationRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeNotificationEvents(session).collect {
                received.add(it)
            }
        }

        val notifOther = InAppNotification("n-1", "user-2", "new_message", "Title", "Body", null, null, "2026-10-06T10:00:00Z")
        val notifMe = InAppNotification("n-2", "user-1", "new_message", "Title", "Body", null, null, "2026-10-06T10:01:00Z")

        fakeAdapter.notificationEventFlow.emit(NotificationRealtimeEvent.Inserted(notifOther))
        fakeAdapter.notificationEventFlow.emit(NotificationRealtimeEvent.Inserted(notifMe))

        assertEquals(1, received.size)
        assertTrue(received[0] is NotificationRealtimeEvent.Inserted)
        assertEquals("n-2", (received[0] as NotificationRealtimeEvent.Inserted).notification.id)
        job.cancel()
    }

    @Test
    fun repositoryStaleSessionRealtimeInsertSuppressedTest() = runTest {
        val s1 = sessionOwner.updateSession("buyer-1")!!

        val received = mutableListOf<MessageRealtimeEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeMessageEvents(s1, "conv-1").collect {
                received.add(it)
            }
        }

        // Session rotates
        sessionOwner.updateSession("buyer-2")

        val msg = ChatMessage("m-1", "conv-1", "seller-1", "req-1", "Hello", "2026-10-06T10:00:00Z")
        fakeAdapter.messageEventFlow.emit(MessageRealtimeEvent.Inserted(msg))

        assertTrue(received.isEmpty())
        job.cancel()
    }
}
