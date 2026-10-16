package com.fairswap.feature.communication

import com.fairswap.core.communication.CommunicationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationUiTextTest {

    @Test
    fun userFacingMessagesAreSafeAndNonTechnical() {
        assertEquals(
            "You're sending messages too quickly. Try again shortly.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.RateLimitExceeded)
        )
        assertEquals(
            "Messaging is unavailable for this conversation.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.MessagingUnavailable)
        )
        assertEquals(
            "This listing is no longer available for new messages.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.ListingUnavailable)
        )
        assertEquals(
            "This conversation is unavailable.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.ConversationUnavailable)
        )
        assertEquals(
            "This conversation is unavailable.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.NotParticipant)
        )
        assertEquals(
            "Enter a message between 1 and 2000 characters.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.InvalidInput)
        )
        assertEquals(
            "You're offline or the network is unavailable. Try again.",
            CommunicationUiText.toUserFacingMessage(CommunicationFailure.NetworkUnavailable)
        )
    }

    @Test
    fun timeFormatterHandlesIsoTimestampSafely() {
        val iso = "2026-10-06T10:15:00Z"
        val formatted = CommunicationTimeFormatter.formatShortTime(iso)
        assertFalse(formatted.isBlank())

        val malformed = "invalid-date-string"
        assertEquals("", CommunicationTimeFormatter.formatShortTime(malformed))
    }
}
