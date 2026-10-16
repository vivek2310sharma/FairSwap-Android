package com.fairswap.core.communication

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

internal class FakeCommunicationGateway : CommunicationGateway {
    var inboxConversations = mutableListOf<ConversationRowDto>()
    var profiles = mutableListOf<ParticipantProfileDto>()
    var readStates = mutableListOf<ConversationReadStateRowDto>()
    var latestMessages = mutableMapOf<String, MessageRowDto?>()
    var newestMessages = mutableListOf<MessageRowDto>()
    var olderMessages = mutableListOf<MessageRowDto>()
    var notifications = mutableListOf<NotificationRowDto>()

    val markReadCallCount = AtomicInteger(0)
    val latestMessageFetchCount = AtomicInteger(0)
    var maxConcurrentLatestMessageRequests = AtomicInteger(0)
    private val activeLatestMessageRequests = AtomicInteger(0)

    var profileFetchFailure: CommunicationFailure? = null
    var readStateFetchFailure: CommunicationFailure? = null
    var latestMessageFetchFailure: CommunicationFailure? = null
    var markReadFailure: CommunicationFailure? = null

    var startConversationSuspendGate: CompletableDeferred<Unit>? = null
    var fetchInboxSuspendGate: CompletableDeferred<Unit>? = null
    var fetchParticipantProfilesGate: CompletableDeferred<Unit>? = null
    var markReadStarted: CompletableDeferred<Unit>? = null
    var markReadRelease: CompletableDeferred<Unit>? = null

    var latestMessageDelayMs: Long = 0L

    override suspend fun findExistingConversation(listingId: String, buyerId: String): CommunicationResult<ConversationRowDto?> {
        val conv = inboxConversations.firstOrNull { it.listingId == listingId && it.buyerId == buyerId }
        return CommunicationResult.Success(conv)
    }

    override suspend fun fetchConversation(conversationId: String): CommunicationResult<ConversationRowDto> {
        val conv = inboxConversations.firstOrNull { it.id == conversationId }
            ?: return CommunicationResult.Failure(CommunicationFailure.ConversationUnavailable)
        return CommunicationResult.Success(conv)
    }

    override suspend fun fetchInboxConversations(userId: String, limit: Int): CommunicationResult<List<ConversationRowDto>> {
        fetchInboxSuspendGate?.await()
        val filtered = inboxConversations.filter { it.buyerId == userId || it.sellerId == userId }.take(limit)
        return CommunicationResult.Success(filtered)
    }

    override suspend fun fetchParticipantProfiles(profileIds: List<String>): CommunicationResult<List<ParticipantProfileDto>> {
        fetchParticipantProfilesGate?.await()
        profileFetchFailure?.let { return CommunicationResult.Failure(it) }
        val filtered = profiles.filter { profileIds.contains(it.id) }
        return CommunicationResult.Success(filtered)
    }

    override suspend fun fetchReadStates(conversationIds: List<String>, userId: String): CommunicationResult<List<ConversationReadStateRowDto>> {
        readStateFetchFailure?.let { return CommunicationResult.Failure(it) }
        val filtered = readStates.filter { conversationIds.contains(it.conversationId) && it.userId == userId }
        return CommunicationResult.Success(filtered)
    }

    override suspend fun fetchLatestMessage(conversationId: String): CommunicationResult<MessageRowDto?> {
        latestMessageFetchFailure?.let { return CommunicationResult.Failure(it) }
        val current = activeLatestMessageRequests.incrementAndGet()
        while (true) {
            val max = maxConcurrentLatestMessageRequests.get()
            if (current <= max || maxConcurrentLatestMessageRequests.compareAndSet(max, current)) break
        }
        if (latestMessageDelayMs > 0) delay(latestMessageDelayMs)
        val msg = latestMessages[conversationId]
        activeLatestMessageRequests.decrementAndGet()
        latestMessageFetchCount.incrementAndGet()
        return CommunicationResult.Success(msg)
    }

    override suspend fun fetchNewestMessages(conversationId: String, limit: Int): CommunicationResult<List<MessageRowDto>> {
        return CommunicationResult.Success(newestMessages.take(limit))
    }

    override suspend fun fetchOlderMessages(conversationId: String, cursor: MessageHistoryCursor, limit: Int): CommunicationResult<List<MessageRowDto>> {
        return CommunicationResult.Success(olderMessages.take(limit))
    }

    override suspend fun startConversation(listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResponseDto> {
        startConversationSuspendGate?.await()
        val msgDto = MessageRowDto("msg-1", "conv-1", "buyer-1", clientRequestId, content, "2026-10-06T10:00:00Z")
        val response = StartConversationResponseDto("conv-1", true, msgDto)
        return CommunicationResult.Success(response)
    }

    override suspend fun sendMessage(conversationId: String, content: String, clientRequestId: String): CommunicationResult<MessageRowDto> {
        val msgDto = MessageRowDto("msg-2", conversationId, "sender-1", clientRequestId, content, "2026-10-06T10:01:00Z")
        return CommunicationResult.Success(msgDto)
    }

    override suspend fun markConversationRead(conversationId: String, messageId: String): CommunicationResult<Unit> {
        markReadCallCount.incrementAndGet()
        markReadStarted?.complete(Unit)
        markReadRelease?.await()
        markReadFailure?.let { return CommunicationResult.Failure(it) }
        return CommunicationResult.Success(Unit)
    }

    override suspend fun fetchNotifications(userId: String, limit: Int): CommunicationResult<List<NotificationRowDto>> {
        return CommunicationResult.Success(notifications.filter { it.userId == userId }.take(limit))
    }

    override suspend fun markNotificationRead(notificationId: String): CommunicationResult<Unit> {
        return CommunicationResult.Success(Unit)
    }

    override suspend fun markAllNotificationsRead(): CommunicationResult<Unit> {
        return CommunicationResult.Success(Unit)
    }
}

internal class FakeCommunicationRealtime : CommunicationRealtime {
    override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> = emptyFlow()
    override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationRepositoryTest {

    private lateinit var gateway: FakeCommunicationGateway
    private lateinit var realtime: FakeCommunicationRealtime
    private lateinit var sessionOwner: CommunicationSessionOwner
    private lateinit var repository: DefaultCommunicationRepository

    @Before
    fun setUp() {
        gateway = FakeCommunicationGateway()
        realtime = FakeCommunicationRealtime()
        sessionOwner = CommunicationSessionOwner()
        repository = DefaultCommunicationRepository(gateway, realtime, sessionOwner)
    }

    @Test
    fun nullableBuyerDecodeTest() = runTest {
        val session = sessionOwner.updateSession("seller-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", buyerId = null, sellerId = "seller-1", lastMessageAt = "2026-10-06T10:00:00Z", createdAt = "2026-10-06T09:00:00Z")
        )

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        val summaries = (result as CommunicationResult.Success).value
        assertEquals(1, summaries.size)
        assertNull(summaries[0].buyerId)
        assertNull(summaries[0].counterpartyId)
        assertNull(summaries[0].counterpartyName) // No UI string injection in core
    }

    @Test
    fun nullableSellerDecodeTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", buyerId = "buyer-1", sellerId = null, lastMessageAt = "2026-10-06T10:00:00Z", createdAt = "2026-10-06T09:00:00Z")
        )

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        val summaries = (result as CommunicationResult.Success).value
        assertEquals(1, summaries.size)
        assertNull(summaries[0].sellerId)
        assertNull(summaries[0].counterpartyId)
    }

    @Test
    fun nullCounterpartySummaryTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", buyerId = "buyer-1", sellerId = "seller-deleted", lastMessageAt = "2026-10-06T10:00:00Z", createdAt = "2026-10-06T09:00:00Z")
        )

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        val summaries = (result as CommunicationResult.Success).value
        assertEquals(1, summaries.size)
        assertEquals("seller-deleted", summaries[0].counterpartyId)
        assertNull(summaries[0].counterpartyName) // Nullable when unresolved
    }

    @Test
    fun nonparticipantConversationFailsClosedTest() = runTest {
        val session = sessionOwner.updateSession("user-unrelated")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", buyerId = "buyer-1", sellerId = "seller-1", lastMessageAt = "2026-10-06T10:00:00Z", createdAt = "2026-10-06T09:00:00Z")
        )

        val result = repository.fetchConversation(session, "conv-1")

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.NotParticipant, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun sameUserGenerationStableTest() {
        val s1 = sessionOwner.updateSession("user-1")
        val s2 = sessionOwner.updateSession("user-1")

        assertNotNull(s1)
        assertEquals(s1, s2)
    }

    @Test
    fun logoutReLoginGenerationChangesTest() {
        val s1 = sessionOwner.updateSession("user-1")
        sessionOwner.updateSession(null)
        val s2 = sessionOwner.updateSession("user-1")

        assertNotNull(s1)
        assertNotNull(s2)
        assertTrue(s2!!.generation > s1!!.generation)
    }

    @Test
    fun identitySwitchGenerationChangesTest() {
        val s1 = sessionOwner.updateSession("user-1")
        val s2 = sessionOwner.updateSession("user-2")

        assertNotNull(s1)
        assertNotNull(s2)
        assertTrue(s2!!.generation > s1!!.generation)
    }

    @Test
    fun staleFetchConversationAssemblyResultAfterIdentityReplacementIgnoredTest() = runTest {
        val sessionA = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", buyerId = "buyer-1", sellerId = "seller-1", lastMessageAt = "2026-10-06T10:00:00Z", createdAt = "2026-10-06T09:00:00Z")
        )

        val profilesGate = CompletableDeferred<Unit>()
        gateway.fetchParticipantProfilesGate = profilesGate

        val deferredSummary = async { repository.fetchConversation(sessionA, "conv-1") }

        // SessionA starts fetchConversation, gateway fetches row, then suspends on profilesGate
        // Now rotate session to sessionB
        sessionOwner.updateSession("buyer-2")
        profilesGate.complete(Unit)

        val result = deferredSummary.await()

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.AuthRequired, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun staleReadResultAfterIdentityReplacementIgnoredTest() = runTest {
        val s1 = sessionOwner.updateSession("user-1")!!
        val gate = CompletableDeferred<Unit>()
        gateway.fetchInboxSuspendGate = gate

        val deferredRead = async { repository.fetchInbox(s1) }

        // Session rotates to user-2 before gate completes
        sessionOwner.updateSession("user-2")
        gate.complete(Unit)

        val result = deferredRead.await()

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.AuthRequired, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun staleSendResultAfterIdentityReplacementIgnoredTest() = runTest {
        val s1 = sessionOwner.updateSession("user-1")!!
        val gate = CompletableDeferred<Unit>()
        gateway.startConversationSuspendGate = gate

        val deferredSend = async { repository.startConversation(s1, "list-1", "Hello", "req-12345678") }

        // Session rotates to user-2 before gate completes
        sessionOwner.updateSession("user-2")
        gate.complete(Unit)

        val result = deferredSend.await()

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.AuthRequired, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun inputTrimLengthValidationTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val emptyRes = repository.sendMessage(session, "conv-1", "   ", "req-12345678")
        val longRes = repository.sendMessage(session, "conv-1", "a".repeat(2001), "req-12345678")

        assertTrue(emptyRes is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.InvalidInput, (emptyRes as CommunicationResult.Failure).error)

        assertTrue(longRes is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.InvalidInput, (longRes as CommunicationResult.Failure).error)
    }

    @Test
    fun clientRequestIdLengthValidationTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val shortReqRes = repository.sendMessage(session, "conv-1", "Hello", "req")
        val longReqRes = repository.sendMessage(session, "conv-1", "Hello", "r".repeat(65))

        assertTrue(shortReqRes is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.InvalidInput, (shortReqRes as CommunicationResult.Failure).error)

        assertTrue(longReqRes is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.InvalidInput, (longReqRes as CommunicationResult.Failure).error)
    }

    @Test
    fun startConversationPreservesConversationIdIsNewConversationMessageTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!

        val result = repository.startConversation(session, "list-1", "First inquiry", "req-12345678")

        assertTrue(result is CommunicationResult.Success)
        val res = (result as CommunicationResult.Success).value
        assertEquals("conv-1", res.conversationId)
        assertTrue(res.isNewConversation)
        assertEquals("msg-1", res.message.id)
        assertEquals("First inquiry", res.message.content)
    }

    @Test
    fun repositoryTimestampTiePageReturnsChronologicalIdOrderTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        gateway.newestMessages.addAll(
            listOf(
                MessageRowDto("msg-b", "conv-1", "u-1", "req-2", "B", "2026-10-06T10:00:00Z"),
                MessageRowDto("msg-a", "conv-1", "u-1", "req-1", "A", "2026-10-06T10:00:00Z"),
            )
        )

        val result = repository.fetchNewestMessages(session, "conv-1")

        assertTrue(result is CommunicationResult.Success)
        val msgs = (result as CommunicationResult.Success).value
        assertEquals(2, msgs.size)
        // Repository reverses postgrest DESC query to chronological ASC order
        assertEquals("msg-a", msgs[0].id)
        assertEquals("msg-b", msgs[1].id)
    }

    @Test
    fun olderKeysetExactPredicateTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        gateway.olderMessages.add(
            MessageRowDto("msg-0", "conv-1", "u-1", "req-0", "Previous", "2026-10-06T09:59:00Z")
        )

        val cursor = MessageHistoryCursor("2026-10-06T10:00:00Z", "msg-1")
        val result = repository.fetchOlderMessages(session, "conv-1", cursor)

        assertTrue(result is CommunicationResult.Success)
        val msgs = (result as CommunicationResult.Success).value
        assertEquals(1, msgs.size)
        assertEquals("msg-0", msgs[0].id)
    }

    @Test
    fun timestampTieIdOrderingTest() {
        val m1 = ChatMessage("msg-a", "conv-1", "u-1", "req-1", "A", "2026-10-06T10:00:00Z")
        val m2 = ChatMessage("msg-b", "conv-1", "u-1", "req-2", "B", "2026-10-06T10:00:00Z")

        val merged = ChatMessageMerger.mergeMessages(listOf(m2), listOf(m1))

        assertEquals("msg-a", merged[0].id)
        assertEquals("msg-b", merged[1].id)
    }

    @Test
    fun inboxLimit50Test() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        for (i in 1..60) {
            gateway.inboxConversations.add(
                ConversationRowDto("conv-$i", "list-$i", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
            )
        }

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        val list = (result as CommunicationResult.Success).value
        assertEquals(50, list.size)
    }

    @Test
    fun latestMessageConcurrencyMeasuredAndNeverExceeds6Test() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.latestMessageDelayMs = 10L
        for (i in 1..12) {
            gateway.inboxConversations.add(
                ConversationRowDto("conv-$i", "list-$i", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
            )
        }

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        assertTrue(gateway.maxConcurrentLatestMessageRequests.get() <= 6)
    }

    @Test
    fun latestMessageConcurrencyAllowsMoreThan1WhenWorkExistsTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.latestMessageDelayMs = 20L
        for (i in 1..8) {
            gateway.inboxConversations.add(
                ConversationRowDto("conv-$i", "list-$i", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
            )
        }

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Success)
        assertTrue(gateway.maxConcurrentLatestMessageRequests.get() > 1)
    }

    @Test
    fun profileFetchFailurePropagatesTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
        )
        gateway.profileFetchFailure = CommunicationFailure.ServiceUnavailable

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.ServiceUnavailable, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun readStateFetchFailurePropagatesTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
        )
        gateway.readStateFetchFailure = CommunicationFailure.ServiceUnavailable

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.ServiceUnavailable, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun latestMessageFetchFailurePropagatesTest() = runTest {
        val session = sessionOwner.updateSession("buyer-1")!!
        gateway.inboxConversations.add(
            ConversationRowDto("conv-1", "list-1", "buyer-1", "seller-1", "2026-10-06T10:00:00Z", "2026-10-06T09:00:00Z")
        )
        gateway.latestMessageFetchFailure = CommunicationFailure.ServiceUnavailable

        val result = repository.fetchInbox(session)

        assertTrue(result is CommunicationResult.Failure)
        assertEquals(CommunicationFailure.ServiceUnavailable, (result as CommunicationResult.Failure).error)
    }

    @Test
    fun unreadPointerRulesTest() {
        val myId = "buyer-1"
        val otherId = "seller-1"

        // 1) No latest message -> false
        assertFalse(CommunicationUnreadCalculator.calculateIsUnread(myId, null, "msg-1"))

        // 2) Own latest message -> false
        val ownMessage = ChatMessage("msg-2", "conv-1", myId, "req-1", "Hi", "2026-10-06T10:00:00Z")
        assertFalse(CommunicationUnreadCalculator.calculateIsUnread(myId, ownMessage, "msg-1"))

        // 3) Counterparty message and no read state -> true
        val otherMessage = ChatMessage("msg-3", "conv-1", otherId, "req-2", "Hello", "2026-10-06T10:01:00Z")
        assertTrue(CommunicationUnreadCalculator.calculateIsUnread(myId, otherMessage, null))

        // 4) Counterparty message and pointer != latest -> true
        assertTrue(CommunicationUnreadCalculator.calculateIsUnread(myId, otherMessage, "msg-2"))

        // 5) Counterparty message and pointer == latest -> false
        assertFalse(CommunicationUnreadCalculator.calculateIsUnread(myId, otherMessage, "msg-3"))
    }

    @Test
    fun simultaneousMarkReadGatewayCallsEquals1Test() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gateway.markReadStarted = started
        gateway.markReadRelease = release

        val d1 = async { repository.markConversationRead(session, "conv-1", "msg-1") }
        started.await() // Ensure caller A has entered gateway mark-read

        val d2 = async { repository.markConversationRead(session, "conv-1", "msg-1") }

        assertEquals(1, gateway.markReadCallCount.get())
        assertFalse(d1.isCompleted)
        assertFalse(d2.isCompleted)

        release.complete(Unit)

        val r1 = d1.await()
        val r2 = d2.await()

        assertTrue(r1 is CommunicationResult.Success)
        assertTrue(r2 is CommunicationResult.Success)
        assertEquals(1, gateway.markReadCallCount.get()) // Final gateway call count remains 1
    }

    @Test
    fun failedMarkReadRetryTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        gateway.markReadFailure = CommunicationFailure.NetworkUnavailable

        val r1 = repository.markConversationRead(session, "conv-1", "msg-1")
        assertTrue(r1 is CommunicationResult.Failure)
        assertEquals(1, gateway.markReadCallCount.get())

        gateway.markReadFailure = null
        val r2 = repository.markConversationRead(session, "conv-1", "msg-1")
        assertTrue(r2 is CommunicationResult.Success)
        assertEquals(2, gateway.markReadCallCount.get()) // Failed attempt allows new call
    }

    @Test
    fun cancelledMarkReadRetryTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gateway.markReadStarted = started
        gateway.markReadRelease = release

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.markConversationRead(session, "conv-1", "msg-1")
        }

        started.await() // Wait until gateway mark-read has definitely started
        assertEquals(1, gateway.markReadCallCount.get())

        job.cancel(CancellationException("Cancelled leader"))
        release.complete(Unit)

        gateway.markReadStarted = null
        gateway.markReadRelease = null
        val res = repository.markConversationRead(session, "conv-1", "msg-1")

        assertTrue(res is CommunicationResult.Success)
        assertEquals(2, gateway.markReadCallCount.get()) // Retry performed new gateway call
    }

    @Test
    fun messageAuthoritativeIdDedupTest() {
        val m1 = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z")
        val m2 = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z")

        val merged = ChatMessageMerger.mergeMessages(listOf(m1), listOf(m2))

        assertEquals(1, merged.size)
        assertEquals("msg-1", merged[0].id)
    }

    @Test
    fun rpcRealtimeRaceDedupTest() {
        val rpcMsg = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z", isDuplicate = false)
        val realtimeMsg = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z", isDuplicate = false)

        val merged = ChatMessageMerger.mergeMessages(listOf(rpcMsg), listOf(realtimeMsg))

        assertEquals(1, merged.size)
        assertEquals("msg-1", merged[0].id)
    }

    @Test
    fun isDuplicateReconciliationTest() {
        val existing = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z", isDuplicate = false)
        val duplicateRpc = ChatMessage("msg-1", "conv-1", "u-1", "req-1", "Hello", "2026-10-06T10:00:00Z", isDuplicate = true)

        val merged = ChatMessageMerger.mergeMessages(listOf(existing), listOf(duplicateRpc))

        assertEquals(1, merged.size)
        assertTrue(merged[0].isDuplicate)
    }

    @Test
    fun sameClientRequestIdCorrelationTest() = runTest {
        val session = sessionOwner.updateSession("user-1")!!
        val clientReqId = "custom-client-req-id-8888"

        val res = repository.sendMessage(session, "conv-1", "Hello", clientReqId)

        assertTrue(res is CommunicationResult.Success)
        val msg = (res as CommunicationResult.Success).value
        assertEquals(clientReqId, msg.clientRequestId)
    }
}
