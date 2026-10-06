package com.fairswap.core.communication

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

const val ANDROID_CLIENT_CONVERSATION_LIMIT = 50
const val ANDROID_CLIENT_PAGE_SIZE = 30
const val ANDROID_CLIENT_NOTIFICATION_LIMIT = 100
const val MAX_LATEST_MESSAGE_CONCURRENCY = 6

object CommunicationUnreadCalculator {
    fun calculateIsUnread(
        userId: String,
        latestMessage: ChatMessage?,
        lastReadMessageId: String?,
    ): Boolean {
        if (latestMessage == null) return false
        if (latestMessage.senderId == userId) return false
        if (lastReadMessageId == null) return true
        return latestMessage.id != lastReadMessageId
    }
}

interface CommunicationRepository {
    suspend fun findExistingConversation(session: CommunicationSession, listingId: String): CommunicationResult<ConversationSummary?>
    suspend fun fetchConversation(session: CommunicationSession, conversationId: String): CommunicationResult<ConversationSummary>
    suspend fun fetchInbox(session: CommunicationSession): CommunicationResult<List<ConversationSummary>>
    suspend fun fetchNewestMessages(session: CommunicationSession, conversationId: String): CommunicationResult<List<ChatMessage>>
    suspend fun fetchOlderMessages(session: CommunicationSession, conversationId: String, cursor: MessageHistoryCursor): CommunicationResult<List<ChatMessage>>
    suspend fun startConversation(session: CommunicationSession, listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResult>
    suspend fun sendMessage(session: CommunicationSession, conversationId: String, content: String, clientRequestId: String): CommunicationResult<ChatMessage>
    suspend fun markConversationRead(session: CommunicationSession, conversationId: String, messageId: String): CommunicationResult<Unit>
    suspend fun fetchNotifications(session: CommunicationSession): CommunicationResult<List<InAppNotification>>
    suspend fun markNotificationRead(session: CommunicationSession, notificationId: String): CommunicationResult<Unit>
    suspend fun markAllNotificationsRead(session: CommunicationSession): CommunicationResult<Unit>
    fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent>
    fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent>
}

class DefaultCommunicationRepository internal constructor(
    private val gateway: CommunicationGateway,
    private val realtime: CommunicationRealtime,
    private val sessionOwner: CommunicationSessionOwner,
) : CommunicationRepository {

    private val markReadCache = object : LinkedHashMap<String, Boolean>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > 200
        }
    }

    private val inFlightMarkRead = mutableMapOf<String, CompletableDeferred<CommunicationResult<Unit>>>()

    private fun validateSession(session: CommunicationSession): CommunicationFailure? {
        return if (sessionOwner.isSessionActive(session)) null else CommunicationFailure.AuthRequired
    }

    private fun validateMessageInput(content: String, clientRequestId: String): CommunicationFailure? {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || trimmed.length > 2000) return CommunicationFailure.InvalidInput
        if (clientRequestId.length < 8 || clientRequestId.length > 64) return CommunicationFailure.InvalidInput
        return null
    }

    override suspend fun findExistingConversation(session: CommunicationSession, listingId: String): CommunicationResult<ConversationSummary?> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.findExistingConversation(listingId, session.userId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val row = res.value ?: return CommunicationResult.Success(null)
                assembleSingleSummary(session, row)
            }
        }
    }

    override suspend fun fetchConversation(session: CommunicationSession, conversationId: String): CommunicationResult<ConversationSummary> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.fetchConversation(conversationId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> assembleSingleSummary(session, res.value)
        }
    }

    override suspend fun fetchInbox(session: CommunicationSession): CommunicationResult<List<ConversationSummary>> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        val convsRes = gateway.fetchInboxConversations(session.userId, ANDROID_CLIENT_CONVERSATION_LIMIT)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        if (convsRes is CommunicationResult.Failure) return convsRes
        val convRows = (convsRes as CommunicationResult.Success).value

        if (convRows.isEmpty()) return CommunicationResult.Success(emptyList())

        return coroutineScope {
            try {
                val counterpartyIds = convRows.mapNotNull {
                    if (it.buyerId == session.userId) it.sellerId else if (it.sellerId == session.userId) it.buyerId else null
                }.distinct()
                val convIds = convRows.map { it.id }

                val profilesDeferred = async { gateway.fetchParticipantProfiles(counterpartyIds) }
                val readStatesDeferred = async { gateway.fetchReadStates(convIds, session.userId) }

                val semaphore = Semaphore(MAX_LATEST_MESSAGE_CONCURRENCY)
                val latestMessagesDeferred = convRows.map { row ->
                    async {
                        semaphore.withPermit {
                            gateway.fetchLatestMessage(row.id)
                        }
                    }
                }

                val profilesRes = profilesDeferred.await()
                val readStatesRes = readStatesDeferred.await()
                val latestMessagesRes = latestMessagesDeferred.awaitAll()

                validateSession(session)?.let { return@coroutineScope CommunicationResult.Failure(it) }

                if (profilesRes is CommunicationResult.Failure) return@coroutineScope profilesRes
                if (readStatesRes is CommunicationResult.Failure) return@coroutineScope readStatesRes

                val firstLatestFailed = latestMessagesRes.firstOrNull { it is CommunicationResult.Failure }
                if (firstLatestFailed != null) return@coroutineScope firstLatestFailed as CommunicationResult.Failure

                val profileMap = (profilesRes as CommunicationResult.Success).value.associateBy { it.id }
                val readStateMap = (readStatesRes as CommunicationResult.Success).value.associateBy { it.conversationId }

                val summaries = convRows.mapIndexed { index, row ->
                    if (row.buyerId != session.userId && row.sellerId != session.userId) {
                        return@coroutineScope CommunicationResult.Failure(CommunicationFailure.NotParticipant)
                    }

                    val counterpartyId = if (row.buyerId == session.userId) row.sellerId else row.buyerId
                    val counterpartyName = counterpartyId?.let { profileMap[it]?.displayName }
                    val latestMsgDto = (latestMessagesRes[index] as CommunicationResult.Success).value
                    val latestMsg = latestMsgDto?.let {
                        ChatMessage(
                            id = it.id,
                            conversationId = it.conversationId,
                            senderId = it.senderId,
                            clientRequestId = it.clientRequestId,
                            content = it.content,
                            createdAt = it.createdAt,
                            isDuplicate = it.isDuplicate,
                        )
                    }
                    val lastReadMsgId = readStateMap[row.id]?.lastReadMessageId
                    val isUnread = CommunicationUnreadCalculator.calculateIsUnread(
                        userId = session.userId,
                        latestMessage = latestMsg,
                        lastReadMessageId = lastReadMsgId,
                    )

                    ConversationSummary(
                        id = row.id,
                        listingId = row.listingId,
                        listingTitle = row.listing?.title,
                        buyerId = row.buyerId,
                        sellerId = row.sellerId,
                        counterpartyId = counterpartyId,
                        counterpartyName = counterpartyName,
                        lastMessageAt = row.lastMessageAt,
                        createdAt = row.createdAt,
                        latestMessage = latestMsg,
                        isUnread = isUnread,
                    )
                }

                CommunicationResult.Success(summaries)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
            }
        }
    }

    override suspend fun fetchNewestMessages(session: CommunicationSession, conversationId: String): CommunicationResult<List<ChatMessage>> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.fetchNewestMessages(conversationId, ANDROID_CLIENT_PAGE_SIZE)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val domainMessages = res.value.map {
                    ChatMessage(
                        id = it.id,
                        conversationId = it.conversationId,
                        senderId = it.senderId,
                        clientRequestId = it.clientRequestId,
                        content = it.content,
                        createdAt = it.createdAt,
                        isDuplicate = it.isDuplicate,
                    )
                }.reversed()
                CommunicationResult.Success(domainMessages)
            }
        }
    }

    override suspend fun fetchOlderMessages(session: CommunicationSession, conversationId: String, cursor: MessageHistoryCursor): CommunicationResult<List<ChatMessage>> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.fetchOlderMessages(conversationId, cursor, ANDROID_CLIENT_PAGE_SIZE)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val domainMessages = res.value.map {
                    ChatMessage(
                        id = it.id,
                        conversationId = it.conversationId,
                        senderId = it.senderId,
                        clientRequestId = it.clientRequestId,
                        content = it.content,
                        createdAt = it.createdAt,
                        isDuplicate = it.isDuplicate,
                    )
                }.reversed()
                CommunicationResult.Success(domainMessages)
            }
        }
    }

    override suspend fun startConversation(session: CommunicationSession, listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResult> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        validateMessageInput(content, clientRequestId)?.let { return CommunicationResult.Failure(it) }

        val res = gateway.startConversation(listingId, content, clientRequestId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val dto = res.value
                val msg = ChatMessage(
                    id = dto.message.id,
                    conversationId = dto.message.conversationId,
                    senderId = dto.message.senderId,
                    clientRequestId = dto.message.clientRequestId,
                    content = dto.message.content,
                    createdAt = dto.message.createdAt,
                    isDuplicate = dto.message.isDuplicate,
                )
                CommunicationResult.Success(
                    StartConversationResult(
                        conversationId = dto.conversationId,
                        isNewConversation = dto.isNewConversation,
                        message = msg,
                    )
                )
            }
        }
    }

    override suspend fun sendMessage(session: CommunicationSession, conversationId: String, content: String, clientRequestId: String): CommunicationResult<ChatMessage> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        validateMessageInput(content, clientRequestId)?.let { return CommunicationResult.Failure(it) }

        val res = gateway.sendMessage(conversationId, content, clientRequestId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val dto = res.value
                CommunicationResult.Success(
                    ChatMessage(
                        id = dto.id,
                        conversationId = dto.conversationId,
                        senderId = dto.senderId,
                        clientRequestId = dto.clientRequestId,
                        content = dto.content,
                        createdAt = dto.createdAt,
                        isDuplicate = dto.isDuplicate,
                    )
                )
            }
        }
    }

    override suspend fun markConversationRead(session: CommunicationSession, conversationId: String, messageId: String): CommunicationResult<Unit> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        val cacheKey = "${session.userId}_${session.generation}_${conversationId}_$messageId"
        synchronized(markReadCache) {
            if (markReadCache.containsKey(cacheKey)) {
                return CommunicationResult.Success(Unit)
            }
        }

        val (deferred, isLeader) = synchronized(inFlightMarkRead) {
            val existing = inFlightMarkRead[cacheKey]
            if (existing != null) {
                Pair(existing, false)
            } else {
                val newDeferred = CompletableDeferred<CommunicationResult<Unit>>()
                inFlightMarkRead[cacheKey] = newDeferred
                Pair(newDeferred, true)
            }
        }

        if (isLeader) {
            try {
                val res = gateway.markConversationRead(conversationId, messageId)
                val activeFailure = validateSession(session)
                val finalRes = activeFailure?.let { CommunicationResult.Failure(it) } ?: res

                if (finalRes is CommunicationResult.Success) {
                    synchronized(markReadCache) {
                        markReadCache[cacheKey] = true
                    }
                }
                deferred.complete(finalRes)
            } catch (e: CancellationException) {
                synchronized(inFlightMarkRead) {
                    inFlightMarkRead.remove(cacheKey)
                }
                deferred.completeExceptionally(e)
                throw e
            } catch (e: Exception) {
                val failure = CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
                deferred.complete(failure)
            } finally {
                synchronized(inFlightMarkRead) {
                    inFlightMarkRead.remove(cacheKey)
                }
            }
        }

        return try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        }
    }

    override suspend fun fetchNotifications(session: CommunicationSession): CommunicationResult<List<InAppNotification>> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.fetchNotifications(session.userId, ANDROID_CLIENT_NOTIFICATION_LIMIT)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return when (res) {
            is CommunicationResult.Failure -> res
            is CommunicationResult.Success -> {
                val domainNotifs = res.value.map {
                    InAppNotification(
                        id = it.id,
                        userId = it.userId,
                        type = it.type,
                        title = it.title,
                        body = it.body,
                        link = it.link,
                        readAt = it.readAt,
                        createdAt = it.createdAt,
                    )
                }
                CommunicationResult.Success(domainNotifs)
            }
        }
    }

    override suspend fun markNotificationRead(session: CommunicationSession, notificationId: String): CommunicationResult<Unit> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.markNotificationRead(notificationId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        return res
    }

    override suspend fun markAllNotificationsRead(session: CommunicationSession): CommunicationResult<Unit> {
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val res = gateway.markAllNotificationsRead()
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        return res
    }

    override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> {
        if (!sessionOwner.isSessionActive(session)) return emptyFlow()
        return realtime.observeMessageEvents(session, conversationId).filter { event ->
            if (!sessionOwner.isSessionActive(session)) return@filter false
            when (event) {
                is MessageRealtimeEvent.Inserted -> event.message.conversationId == conversationId
                is MessageRealtimeEvent.ReadyForBackfill -> true
            }
        }
    }

    override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> {
        if (!sessionOwner.isSessionActive(session)) return emptyFlow()
        return realtime.observeNotificationEvents(session).filter { event ->
            if (!sessionOwner.isSessionActive(session)) return@filter false
            when (event) {
                is NotificationRealtimeEvent.Inserted -> event.notification.userId == session.userId
                is NotificationRealtimeEvent.ReadyForBackfill -> true
            }
        }
    }

    private suspend fun assembleSingleSummary(session: CommunicationSession, row: ConversationRowDto): CommunicationResult<ConversationSummary> {
        if (row.buyerId != session.userId && row.sellerId != session.userId) {
            return CommunicationResult.Failure(CommunicationFailure.NotParticipant)
        }

        val counterpartyId = if (row.buyerId == session.userId) row.sellerId else row.buyerId
        val counterpartyName = counterpartyId?.let {
            val profilesRes = gateway.fetchParticipantProfiles(listOf(it))
            validateSession(session)?.let { err -> return CommunicationResult.Failure(err) }
            when (profilesRes) {
                is CommunicationResult.Failure -> return profilesRes
                is CommunicationResult.Success -> profilesRes.value.firstOrNull()?.displayName
            }
        }
        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        val readStatesRes = gateway.fetchReadStates(listOf(row.id), session.userId)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val lastReadMsgId = when (readStatesRes) {
            is CommunicationResult.Failure -> return readStatesRes
            is CommunicationResult.Success -> readStatesRes.value.firstOrNull()?.lastReadMessageId
        }

        val latestMsgRes = gateway.fetchLatestMessage(row.id)
        validateSession(session)?.let { return CommunicationResult.Failure(it) }
        val latestMsgDto = when (latestMsgRes) {
            is CommunicationResult.Failure -> return latestMsgRes
            is CommunicationResult.Success -> latestMsgRes.value
        }
        val latestMsg = latestMsgDto?.let {
            ChatMessage(
                id = it.id,
                conversationId = it.conversationId,
                senderId = it.senderId,
                clientRequestId = it.clientRequestId,
                content = it.content,
                createdAt = it.createdAt,
                isDuplicate = it.isDuplicate,
            )
        }

        val isUnread = CommunicationUnreadCalculator.calculateIsUnread(
            userId = session.userId,
            latestMessage = latestMsg,
            lastReadMessageId = lastReadMsgId,
        )

        validateSession(session)?.let { return CommunicationResult.Failure(it) }

        return CommunicationResult.Success(
            ConversationSummary(
                id = row.id,
                listingId = row.listingId,
                listingTitle = row.listing?.title,
                buyerId = row.buyerId,
                sellerId = row.sellerId,
                counterpartyId = counterpartyId,
                counterpartyName = counterpartyName,
                lastMessageAt = row.lastMessageAt,
                createdAt = row.createdAt,
                latestMessage = latestMsg,
                isUnread = isUnread,
            )
        )
    }
}
