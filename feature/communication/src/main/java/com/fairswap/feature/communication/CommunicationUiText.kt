package com.fairswap.feature.communication

import com.fairswap.core.communication.CommunicationFailure
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CommunicationUiText {
    fun toUserFacingMessage(failure: CommunicationFailure): String {
        return when (failure) {
            CommunicationFailure.RateLimitExceeded ->
                "You're sending messages too quickly. Try again shortly."
            CommunicationFailure.MessagingUnavailable ->
                "Messaging is unavailable for this conversation."
            CommunicationFailure.ListingUnavailable ->
                "This listing is no longer available for new messages."
            CommunicationFailure.ConversationUnavailable,
            CommunicationFailure.NotParticipant ->
                "This conversation is unavailable."
            CommunicationFailure.InvalidInput ->
                "Enter a message between 1 and 2000 characters."
            CommunicationFailure.NetworkUnavailable ->
                "You're offline or the network is unavailable. Try again."
            CommunicationFailure.AuthRequired,
            CommunicationFailure.PolicyReviewRequired,
            CommunicationFailure.AccountRestricted,
            CommunicationFailure.ServiceUnavailable ->
                "Messaging is temporarily unavailable. Try again."
        }
    }

    fun toNotificationUserFacingMessage(failure: CommunicationFailure): String {
        return when (failure) {
            CommunicationFailure.InvalidInput ->
                "Invalid request."
            else -> toUserFacingMessage(failure)
        }
    }
}

object CommunicationTimeFormatter {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

    fun formatShortTime(isoTimestamp: String): String {
        if (isoTimestamp.isBlank()) return ""
        return try {
            val instant = Instant.parse(isoTimestamp)
            timeFormatter.format(instant)
        } catch (_: Exception) {
            ""
        }
    }

    fun formatDateTime(isoTimestamp: String): String {
        if (isoTimestamp.isBlank()) return ""
        return try {
            val instant = Instant.parse(isoTimestamp)
            dateFormatter.format(instant)
        } catch (_: Exception) {
            ""
        }
    }
}
