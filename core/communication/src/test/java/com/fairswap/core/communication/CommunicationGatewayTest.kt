package com.fairswap.core.communication

import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.UnknownHostException

class CommunicationGatewayTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun productionOlderMessagesConsumesKeysetSpecTest() {
        val cursor = MessageHistoryCursor("2026-10-06T10:00:00Z", "msg-123")
        val spec = KeysetQueryBuilder.getSpec(cursor)

        assertEquals("2026-10-06T10:00:00Z", spec.createdAt)
        assertEquals("msg-123", spec.id)

        val builder = io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder(
            propertyConversionMethod = io.github.jan.supabase.postgrest.PropertyConversionMethod.CAMEL_CASE_TO_SNAKE_CASE
        )
        KeysetQueryBuilder.applyKeysetFilter(builder, cursor)

        val orParams = builder.params["or"] as? List<*>
        assertNotNull(orParams)
        assertEquals(1, orParams!!.size)
        assertEquals(
            "(created_at.lt.\"2026-10-06T10:00:00Z\",and(created_at.eq.\"2026-10-06T10:00:00Z\",id.lt.msg-123))",
            orParams[0].toString()
        )
    }

    @Test
    fun errorMapperClassifiesPolicyAcceptanceRequired() {
        val ex = RuntimeException("POLICY_ACCEPTANCE_REQUIRED: Re-acceptance of Public Beta policies is required")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.PolicyReviewRequired, failure)
    }

    @Test
    fun errorMapperClassifiesRateLimitExceeded() {
        val ex = RuntimeException("RATE_LIMIT_EXCEEDED: Maximum 60 messages per hour")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.RateLimitExceeded, failure)
    }

    @Test
    fun errorMapperClassifiesMessagingUnavailableAndConcealsBlockDirection() {
        val ex = RuntimeException("MESSAGING_UNAVAILABLE: Unable to send message to this recipient")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.MessagingUnavailable, failure)
    }

    @Test
    fun errorMapperClassifiesListingUnavailable() {
        val ex = RuntimeException("LISTING_UNAVAILABLE: New inquiries require an active, approved listing")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.ListingUnavailable, failure)
    }

    @Test
    fun errorMapperClassifiesAuthRequired() {
        val ex = RuntimeException("Not authenticated")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.AuthRequired, failure)
    }

    @Test
    fun errorMapperClassifiesAccountRestricted() {
        val ex = RuntimeException("Account restricted: messaging requires active account")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.AccountRestricted, failure)
    }

    @Test
    fun errorMapperClassifiesConversationUnavailable() {
        val ex1 = RuntimeException("Conversation not found")
        val ex2 = RuntimeException("Message does not belong to this conversation")

        assertEquals(CommunicationFailure.ConversationUnavailable, CommunicationErrorMapper.mapThrowable(ex1))
        assertEquals(CommunicationFailure.ConversationUnavailable, CommunicationErrorMapper.mapThrowable(ex2))
    }

    @Test
    fun errorMapperClassifiesNotParticipant() {
        val ex = RuntimeException("Forbidden: not a participant in this conversation")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.NotParticipant, failure)
    }

    @Test
    fun errorMapperClassifiesInvalidInput() {
        val ex1 = RuntimeException("Message length must be between 1 and 2000 characters")
        val ex2 = RuntimeException("Invalid client_request_id")
        val ex3 = RuntimeException("Self-chat is prohibited: You cannot contact your own listing")

        assertEquals(CommunicationFailure.InvalidInput, CommunicationErrorMapper.mapThrowable(ex1))
        assertEquals(CommunicationFailure.InvalidInput, CommunicationErrorMapper.mapThrowable(ex2))
        assertEquals(CommunicationFailure.InvalidInput, CommunicationErrorMapper.mapThrowable(ex3))
    }

    @Test
    fun errorMapperClassifiesNetworkUnavailable() {
        val ex = UnknownHostException("Unable to resolve host")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.NetworkUnavailable, failure)
    }

    @Test
    fun errorMapperClassifiesUnknownErrorAsServiceUnavailable() {
        val ex = RuntimeException("Some internal database crash details")
        val failure = CommunicationErrorMapper.mapThrowable(ex)

        assertEquals(CommunicationFailure.ServiceUnavailable, failure)
    }

    @Test
    fun errorMapperAlwaysRethrowsCancellationException() {
        val ex = CancellationException("Job was cancelled")
        try {
            CommunicationErrorMapper.mapThrowable(ex)
            fail("Should have rethrown CancellationException")
        } catch (e: CancellationException) {
            assertEquals("Job was cancelled", e.message)
        }
    }

    @Test
    fun dtoParsingForStartConversationAndDuplicateSend() {
        val rawJson = """
            {
                "conversation_id": "c1111111-1111-1111-1111-111111111111",
                "is_new_conversation": false,
                "message": {
                    "id": "m2222222-2222-2222-2222-222222222222",
                    "conversation_id": "c1111111-1111-1111-1111-111111111111",
                    "sender_id": "u3333333-3333-3333-3333-333333333333",
                    "client_request_id": "req-12345678",
                    "content": "Hello again",
                    "created_at": "2026-10-06T10:00:00Z",
                    "is_duplicate": true
                }
            }
        """.trimIndent()

        val dto = json.decodeFromString<StartConversationResponseDto>(rawJson)

        assertEquals("c1111111-1111-1111-1111-111111111111", dto.conversationId)
        assertFalse(dto.isNewConversation)
        assertEquals("m2222222-2222-2222-2222-222222222222", dto.message.id)
        assertTrue(dto.message.isDuplicate)
        assertEquals("req-12345678", dto.message.clientRequestId)
    }

    @Test
    fun dtoParsingForNotificationRowDto() {
        val rawJson = """
            {
                "id": "n1111111-1111-1111-1111-111111111111",
                "user_id": "u1111111-1111-1111-1111-111111111111",
                "type": "future_enum_type_123",
                "title": "New Alert",
                "body": "Alert body text",
                "link": "/account/messages/123e4567-e89b-12d3-a456-426614174000",
                "read_at": null,
                "created_at": "2026-10-06T12:00:00Z"
            }
        """.trimIndent()

        val dto = json.decodeFromString<NotificationRowDto>(rawJson)

        assertEquals("n1111111-1111-1111-1111-111111111111", dto.id)
        assertEquals("future_enum_type_123", dto.type)
        assertEquals("New Alert", dto.title)
    }
}
