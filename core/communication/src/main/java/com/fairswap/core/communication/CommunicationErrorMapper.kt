package com.fairswap.core.communication

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import java.net.ConnectException
import java.net.UnknownHostException

object CommunicationErrorMapper {
    fun mapThrowable(e: Throwable): CommunicationFailure {
        if (e is CancellationException) throw e

        if (e is HttpRequestTimeoutException || e is UnknownHostException || e is ConnectException) {
            return CommunicationFailure.NetworkUnavailable
        }

        val message = e.message.orEmpty()
        val restMessage = (e as? RestException)?.error ?: message

        return when {
            message.contains("POLICY_ACCEPTANCE_REQUIRED") || restMessage.contains("POLICY_ACCEPTANCE_REQUIRED") ->
                CommunicationFailure.PolicyReviewRequired

            message.contains("RATE_LIMIT_EXCEEDED") || restMessage.contains("RATE_LIMIT_EXCEEDED") ->
                CommunicationFailure.RateLimitExceeded

            message.contains("MESSAGING_UNAVAILABLE") || restMessage.contains("MESSAGING_UNAVAILABLE") ->
                CommunicationFailure.MessagingUnavailable

            message.contains("LISTING_UNAVAILABLE") || restMessage.contains("LISTING_UNAVAILABLE") ->
                CommunicationFailure.ListingUnavailable

            message.contains("Not authenticated") || restMessage.contains("Not authenticated") ->
                CommunicationFailure.AuthRequired

            message.contains("Account restricted") || restMessage.contains("Account restricted") ->
                CommunicationFailure.AccountRestricted

            message.contains("Conversation not found") || restMessage.contains("Conversation not found") ||
                    message.contains("Message does not belong to this conversation") || restMessage.contains("Message does not belong to this conversation") ->
                CommunicationFailure.ConversationUnavailable

            message.contains("Forbidden: not a participant") || restMessage.contains("Forbidden: not a participant") ->
                CommunicationFailure.NotParticipant

            message.contains("Message length must be between") || restMessage.contains("Message length must be between") ||
                    message.contains("Invalid client_request_id") || restMessage.contains("Invalid client_request_id") ||
                    message.contains("Self-chat is prohibited") || restMessage.contains("Self-chat is prohibited") ->
                CommunicationFailure.InvalidInput

            else -> CommunicationFailure.ServiceUnavailable
        }
    }
}
