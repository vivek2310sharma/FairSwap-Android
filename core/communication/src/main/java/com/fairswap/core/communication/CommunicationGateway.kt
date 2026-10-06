package com.fairswap.core.communication

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder

internal data class KeysetQuerySpec(
    val createdAt: String,
    val id: String,
)

internal object KeysetQueryBuilder {
    fun applyKeysetFilter(builder: PostgrestFilterBuilder, cursor: MessageHistoryCursor) {
        val spec = getSpec(cursor)
        builder.or {
            lt("created_at", spec.createdAt)
            and {
                eq("created_at", spec.createdAt)
                lt("id", spec.id)
            }
        }
    }

    fun getSpec(cursor: MessageHistoryCursor): KeysetQuerySpec {
        return KeysetQuerySpec(cursor.createdAt, cursor.id)
    }
}

internal interface CommunicationGateway {
    suspend fun findExistingConversation(listingId: String, buyerId: String): CommunicationResult<ConversationRowDto?>
    suspend fun fetchConversation(conversationId: String): CommunicationResult<ConversationRowDto>
    suspend fun fetchInboxConversations(userId: String, limit: Int): CommunicationResult<List<ConversationRowDto>>
    suspend fun fetchParticipantProfiles(profileIds: List<String>): CommunicationResult<List<ParticipantProfileDto>>
    suspend fun fetchReadStates(conversationIds: List<String>, userId: String): CommunicationResult<List<ConversationReadStateRowDto>>
    suspend fun fetchLatestMessage(conversationId: String): CommunicationResult<MessageRowDto?>
    suspend fun fetchNewestMessages(conversationId: String, limit: Int): CommunicationResult<List<MessageRowDto>>
    suspend fun fetchOlderMessages(conversationId: String, cursor: MessageHistoryCursor, limit: Int): CommunicationResult<List<MessageRowDto>>
    suspend fun startConversation(listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResponseDto>
    suspend fun sendMessage(conversationId: String, content: String, clientRequestId: String): CommunicationResult<MessageRowDto>
    suspend fun markConversationRead(conversationId: String, messageId: String): CommunicationResult<Unit>
    suspend fun fetchNotifications(userId: String, limit: Int): CommunicationResult<List<NotificationRowDto>>
    suspend fun markNotificationRead(notificationId: String): CommunicationResult<Unit>
    suspend fun markAllNotificationsRead(): CommunicationResult<Unit>
}

internal class SupabaseCommunicationGateway(
    private val supabase: SupabaseClient,
) : CommunicationGateway {

    override suspend fun findExistingConversation(listingId: String, buyerId: String): CommunicationResult<ConversationRowDto?> {
        return try {
            val result = supabase.from("conversations")
                .select(columns = Columns.list("id, listing_id, buyer_id, seller_id, last_message_at, created_at, listing:listings(id, title)")) {
                    filter {
                        eq("listing_id", listingId)
                        eq("buyer_id", buyerId)
                    }
                    limit(1)
                }
                .decodeList<ConversationRowDto>()
                .firstOrNull()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchConversation(conversationId: String): CommunicationResult<ConversationRowDto> {
        return try {
            val result = supabase.from("conversations")
                .select(columns = Columns.list("id, listing_id, buyer_id, seller_id, last_message_at, created_at, listing:listings(id, title)")) {
                    filter { eq("id", conversationId) }
                }
                .decodeSingle<ConversationRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchInboxConversations(userId: String, limit: Int): CommunicationResult<List<ConversationRowDto>> {
        return try {
            val result = supabase.from("conversations")
                .select(columns = Columns.list("id, listing_id, buyer_id, seller_id, last_message_at, created_at, listing:listings(id, title)")) {
                    filter {
                        or {
                            eq("buyer_id", userId)
                            eq("seller_id", userId)
                        }
                    }
                    order("last_message_at", order = Order.DESCENDING)
                    order("id", order = Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<ConversationRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchParticipantProfiles(profileIds: List<String>): CommunicationResult<List<ParticipantProfileDto>> {
        if (profileIds.isEmpty()) return CommunicationResult.Success(emptyList())
        return try {
            val result = supabase.from("profiles")
                .select(columns = Columns.list("id, display_name")) {
                    filter {
                        isIn("id", profileIds)
                    }
                }
                .decodeList<ParticipantProfileDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchReadStates(conversationIds: List<String>, userId: String): CommunicationResult<List<ConversationReadStateRowDto>> {
        if (conversationIds.isEmpty()) return CommunicationResult.Success(emptyList())
        return try {
            val result = supabase.from("conversation_read_states")
                .select(columns = Columns.list("conversation_id, user_id, last_read_message_id, last_read_at")) {
                    filter {
                        isIn("conversation_id", conversationIds)
                        eq("user_id", userId)
                    }
                }
                .decodeList<ConversationReadStateRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchLatestMessage(conversationId: String): CommunicationResult<MessageRowDto?> {
        return try {
            val result = supabase.from("messages")
                .select(columns = Columns.list("id, conversation_id, sender_id, client_request_id, content, created_at")) {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", order = Order.DESCENDING)
                    order("id", order = Order.DESCENDING)
                    limit(1)
                }
                .decodeList<MessageRowDto>()
                .firstOrNull()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchNewestMessages(conversationId: String, limit: Int): CommunicationResult<List<MessageRowDto>> {
        return try {
            val result = supabase.from("messages")
                .select(columns = Columns.list("id, conversation_id, sender_id, client_request_id, content, created_at")) {
                    filter { eq("conversation_id", conversationId) }
                    order("created_at", order = Order.DESCENDING)
                    order("id", order = Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<MessageRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchOlderMessages(conversationId: String, cursor: MessageHistoryCursor, limit: Int): CommunicationResult<List<MessageRowDto>> {
        return try {
            val result = supabase.from("messages")
                .select(columns = Columns.list("id, conversation_id, sender_id, client_request_id, content, created_at")) {
                    filter {
                        eq("conversation_id", conversationId)
                        KeysetQueryBuilder.applyKeysetFilter(this, cursor)
                    }
                    order("created_at", order = Order.DESCENDING)
                    order("id", order = Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<MessageRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun startConversation(listingId: String, content: String, clientRequestId: String): CommunicationResult<StartConversationResponseDto> {
        return try {
            val params = buildJsonObject {
                put("listing_id", listingId)
                put("content", content)
                put("client_request_id", clientRequestId)
            }
            val result = supabase.postgrest.rpc("start_conversation", params)
                .decodeAs<StartConversationResponseDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun sendMessage(conversationId: String, content: String, clientRequestId: String): CommunicationResult<MessageRowDto> {
        return try {
            val params = buildJsonObject {
                put("conversation_id", conversationId)
                put("content", content)
                put("client_request_id", clientRequestId)
            }
            val result = supabase.postgrest.rpc("send_message", params)
                .decodeAs<MessageRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun markConversationRead(conversationId: String, messageId: String): CommunicationResult<Unit> {
        return try {
            val params = buildJsonObject {
                put("conversation_id", conversationId)
                put("message_id", messageId)
            }
            supabase.postgrest.rpc("mark_conversation_read", params)
            CommunicationResult.Success(Unit)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun fetchNotifications(userId: String, limit: Int): CommunicationResult<List<NotificationRowDto>> {
        return try {
            val result = supabase.from("notifications")
                .select(columns = Columns.list("id, user_id, type, title, body, link, read_at, created_at")) {
                    filter { eq("user_id", userId) }
                    order("created_at", order = Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<NotificationRowDto>()
            CommunicationResult.Success(result)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun markNotificationRead(notificationId: String): CommunicationResult<Unit> {
        return try {
            val params = buildJsonObject {
                put("notification_id", notificationId)
            }
            supabase.postgrest.rpc("mark_notification_read", params)
            CommunicationResult.Success(Unit)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }

    override suspend fun markAllNotificationsRead(): CommunicationResult<Unit> {
        return try {
            supabase.postgrest.rpc("mark_all_notifications_read")
            CommunicationResult.Success(Unit)
        } catch (e: Exception) {
            CommunicationResult.Failure(CommunicationErrorMapper.mapThrowable(e))
        }
    }
}
