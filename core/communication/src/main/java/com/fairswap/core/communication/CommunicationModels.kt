package com.fairswap.core.communication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class CommunicationSession(
    val userId: String,
    val generation: Long,
)

data class ConversationSummary(
    val id: String,
    val listingId: String,
    val listingTitle: String?,
    val buyerId: String?,
    val sellerId: String?,
    val counterpartyId: String?,
    val counterpartyName: String?,
    val lastMessageAt: String,
    val createdAt: String,
    val latestMessage: ChatMessage?,
    val isUnread: Boolean,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String?,
    val clientRequestId: String,
    val content: String,
    val createdAt: String,
    val isDuplicate: Boolean = false,
)

data class StartConversationResult(
    val conversationId: String,
    val isNewConversation: Boolean,
    val message: ChatMessage,
)

data class MessageHistoryCursor(
    val createdAt: String,
    val id: String,
)

data class ConversationReadState(
    val conversationId: String,
    val userId: String,
    val lastReadMessageId: String,
    val lastReadAt: String,
)

data class InAppNotification(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val link: String?,
    val readAt: String?,
    val createdAt: String,
)

sealed interface MessageRealtimeEvent {
    data object ReadyForBackfill : MessageRealtimeEvent
    data class Inserted(val message: ChatMessage) : MessageRealtimeEvent
}

sealed interface NotificationRealtimeEvent {
    data object ReadyForBackfill : NotificationRealtimeEvent
    data class Inserted(val notification: InAppNotification) : NotificationRealtimeEvent
}

sealed interface CommunicationFailure {
    data object AuthRequired : CommunicationFailure
    data object PolicyReviewRequired : CommunicationFailure
    data object AccountRestricted : CommunicationFailure
    data object RateLimitExceeded : CommunicationFailure
    data object MessagingUnavailable : CommunicationFailure
    data object ListingUnavailable : CommunicationFailure
    data object ConversationUnavailable : CommunicationFailure
    data object NotParticipant : CommunicationFailure
    data object InvalidInput : CommunicationFailure
    data object NetworkUnavailable : CommunicationFailure
    data object ServiceUnavailable : CommunicationFailure
}

sealed interface CommunicationResult<out T> {
    data class Success<T>(val value: T) : CommunicationResult<T>
    data class Failure(val error: CommunicationFailure) : CommunicationResult<Nothing>
}

@Serializable
internal data class ListingSummaryDto(
    val id: String,
    val title: String? = null,
)

@Serializable
internal data class ConversationRowDto(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("buyer_id") val buyerId: String? = null,
    @SerialName("seller_id") val sellerId: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String,
    @SerialName("created_at") val createdAt: String,
    val listing: ListingSummaryDto? = null,
)

@Serializable
internal data class MessageRowDto(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("client_request_id") val clientRequestId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_duplicate") val isDuplicate: Boolean = false,
)

@Serializable
internal data class ConversationReadStateRowDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_message_id") val lastReadMessageId: String,
    @SerialName("last_read_at") val lastReadAt: String,
)

@Serializable
internal data class NotificationRowDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val link: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal data class ParticipantProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
internal data class StartConversationResponseDto(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("is_new_conversation") val isNewConversation: Boolean,
    val message: MessageRowDto,
)
