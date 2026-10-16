package com.fairswap.core.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDestinationParserTest {

    @Test
    fun validNewMessageExactUuidPathNavigatesToConversation() {
        val validUuid = "123e4567-e89b-12d3-a456-426614174000"
        val link = "/account/messages/$validUuid"

        val dest = NotificationDestinationParser.parseDestination("new_message", link)

        assertEquals(NotificationDestination.Conversation(validUuid), dest)
    }

    @Test
    fun malformedNewMessageUuidReturnsNoDestination() {
        val link = "/account/messages/invalid-uuid"
        val dest = NotificationDestinationParser.parseDestination("new_message", link)

        assertNull(dest)
    }

    @Test
    fun newMessageExtraPathOrQueryReturnsNoDestination() {
        val validUuid = "123e4567-e89b-12d3-a456-426614174000"
        val link1 = "/account/messages/$validUuid/extra"
        val link2 = "https://external.com/account/messages/$validUuid"

        assertNull(NotificationDestinationParser.parseDestination("new_message", link1))
        assertNull(NotificationDestinationParser.parseDestination("new_message", link2))
    }

    @Test
    fun listingRejectedExactPathNavigatesToMyListings() {
        val link = "/account/listings"
        val dest = NotificationDestinationParser.parseDestination("listing_rejected", link)

        assertEquals(NotificationDestination.MyListings, dest)
    }

    @Test
    fun listingRejectedWrongPathReturnsNoDestination() {
        val link = "/account/listings/other"
        val dest = NotificationDestinationParser.parseDestination("listing_rejected", link)

        assertNull(dest)
    }

    @Test
    fun listingApprovedReturnsNoDestination() {
        val link = "/listings/some-slug"
        val dest = NotificationDestinationParser.parseDestination("listing_approved", link)

        assertNull(dest)
    }

    @Test
    fun reportUpdateReturnsNoDestination() {
        val link = "/account/reports/123"
        val dest = NotificationDestinationParser.parseDestination("report_update", link)

        assertNull(dest)
    }

    @Test
    fun supportReplyReturnsNoDestination() {
        val link = "/support/123"
        val dest = NotificationDestinationParser.parseDestination("support_reply", link)

        assertNull(dest)
    }

    @Test
    fun unknownFutureTypeReturnsNoDestination() {
        val link = "/account/some-new-feature"
        val dest = NotificationDestinationParser.parseDestination("future_type_enum", link)

        assertNull(dest)
    }

    @Test
    fun nullOrBlankLinkReturnsNoDestination() {
        assertNull(NotificationDestinationParser.parseDestination("new_message", null))
        assertNull(NotificationDestinationParser.parseDestination("new_message", ""))
    }
}
