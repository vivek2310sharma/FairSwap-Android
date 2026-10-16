package com.fairswap.core.communication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.PostgresChangeFilter
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

internal data class RealtimeFilterSpec(
    val table: String,
    val filterColumn: String,
    val filterValue: String,
)

interface CommunicationRealtime {
    fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent>
    fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent>
}

internal interface RealtimeChannelSubscriptionHandle {
    val status: Flow<RealtimeChannel.Status>
    fun postgresChangeFlow(spec: RealtimeFilterSpec): Flow<PostgresAction.Insert>
    suspend fun subscribe()
    suspend fun remove()
}

internal interface RealtimeChannelClient {
    fun getOrCreateSubscription(channelName: String, spec: RealtimeFilterSpec): RealtimeChannelSubscriptionHandle
}

internal class SupabaseRealtimeChannelClient(
    private val supabase: SupabaseClient,
) : RealtimeChannelClient {
    override fun getOrCreateSubscription(channelName: String, spec: RealtimeFilterSpec): RealtimeChannelSubscriptionHandle {
        val channel = supabase.channel(channelName)
        return object : RealtimeChannelSubscriptionHandle {
            override val status: Flow<RealtimeChannel.Status> = channel.status

            override fun postgresChangeFlow(spec: RealtimeFilterSpec): Flow<PostgresAction.Insert> {
                return channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    applySpec(spec)
                }
            }

            override suspend fun subscribe() {
                channel.subscribe(blockUntilSubscribed = false)
            }

            override suspend fun remove() {
                supabase.realtime.removeChannel(channel)
            }
        }
    }
}

class SupabaseCommunicationRealtime internal constructor(
    private val channelClient: RealtimeChannelClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : CommunicationRealtime {

    constructor(supabase: SupabaseClient) : this(SupabaseRealtimeChannelClient(supabase))

    override fun observeMessageEvents(session: CommunicationSession, conversationId: String): Flow<MessageRealtimeEvent> = channelFlow {
        val channelName = "messages_$conversationId"
        val spec = RealtimeFilterSpec("messages", "conversation_id", conversationId)
        val handle = channelClient.getOrCreateSubscription(channelName, spec)

        val statusJob = launch {
            handle.status.collect { status ->
                if (status == RealtimeChannel.Status.SUBSCRIBED) {
                    send(MessageRealtimeEvent.ReadyForBackfill)
                }
            }
        }

        val insertFlow = handle.postgresChangeFlow(spec)

        val insertJob = launch {
            insertFlow.collect { insertAction ->
                val recordConversationId = insertAction.record["conversation_id"]?.jsonPrimitive?.content
                if (recordConversationId == conversationId) {
                    try {
                        val dto = json.decodeFromJsonElement<MessageRowDto>(insertAction.record)
                        val msg = ChatMessage(
                            id = dto.id,
                            conversationId = dto.conversationId,
                            senderId = dto.senderId,
                            clientRequestId = dto.clientRequestId,
                            content = dto.content,
                            createdAt = dto.createdAt,
                            isDuplicate = dto.isDuplicate,
                        )
                        send(MessageRealtimeEvent.Inserted(msg))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Ignore malformed rows without crashing
                    }
                }
            }
        }

        handle.subscribe()

        try {
            awaitClose()
        } finally {
            statusJob.cancel()
            insertJob.cancel()
            withContext(NonCancellable) {
                try {
                    handle.remove()
                } catch (_: Exception) {
                    // cleanup failure only
                }
            }
        }
    }

    override fun observeNotificationEvents(session: CommunicationSession): Flow<NotificationRealtimeEvent> = channelFlow {
        val channelName = "notifications_${session.userId}"
        val spec = RealtimeFilterSpec("notifications", "user_id", session.userId)
        val handle = channelClient.getOrCreateSubscription(channelName, spec)

        val statusJob = launch {
            handle.status.collect { status ->
                if (status == RealtimeChannel.Status.SUBSCRIBED) {
                    send(NotificationRealtimeEvent.ReadyForBackfill)
                }
            }
        }

        val insertFlow = handle.postgresChangeFlow(spec)

        val insertJob = launch {
            insertFlow.collect { insertAction ->
                val recordUserId = insertAction.record["user_id"]?.jsonPrimitive?.content
                if (recordUserId == session.userId) {
                    try {
                        val dto = json.decodeFromJsonElement<NotificationRowDto>(insertAction.record)
                        val notif = InAppNotification(
                            id = dto.id,
                            userId = dto.userId,
                            type = dto.type,
                            title = dto.title,
                            body = dto.body,
                            link = dto.link,
                            readAt = dto.readAt,
                            createdAt = dto.createdAt,
                        )
                        send(NotificationRealtimeEvent.Inserted(notif))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Ignore malformed rows without crashing
                    }
                }
            }
        }

        handle.subscribe()

        try {
            awaitClose()
        } finally {
            statusJob.cancel()
            insertJob.cancel()
            withContext(NonCancellable) {
                try {
                    handle.remove()
                } catch (_: Exception) {
                    // cleanup failure only
                }
            }
        }
    }
}

internal fun PostgresChangeFilter.applySpec(spec: RealtimeFilterSpec) {
    table = spec.table
    filter = "${spec.filterColumn}=eq.${spec.filterValue}"
}
