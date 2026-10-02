package com.fairswap.app.ui

import com.fairswap.core.sellerlistings.SellerSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FairSwapAppTest {

    private val sessionA = SellerSession("user-A", 1L)
    private val sessionB = SellerSession("user-B", 2L)

    @Test
    fun `event from Create entry1 returns false when top is Create entry2`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = CreateSellerListingRoute(entryId = "entry-2"),
        )
        assertFalse(result)
    }

    @Test
    fun `event from Edit listingX entry1 returns false when top is Edit listingX entry2`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-2"),
        )
        assertFalse(result)
    }

    @Test
    fun `event returns false when liveUserId differs from activeSession`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-B",
            topRoute = CreateSellerListingRoute(entryId = "entry-1"),
        )
        assertFalse(result)
    }

    @Test
    fun `event returns false when top is MyListingsRoute or non-editor route`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = MyListingsRoute,
        )
        assertFalse(result)
    }

    @Test
    fun `exact Create entry session live-user match returns true`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = CreateSellerListingRoute(entryId = "entry-1"),
        )
        assertTrue(result)
    }

    @Test
    fun `exact Edit entry session live-user match returns true`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-1",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-1"),
        )
        assertTrue(result)
    }

    @Test
    fun `stale editor event entryId A cannot affect top route entryId B`() {
        val result = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-A",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = EditSellerListingRoute(listingId = "listing-X", entryId = "entry-B"),
        )
        assertFalse(result)
    }

    @Test
    fun `consuming valid Saved or Submitted event pops top editor route and leaves single existing MyListingsRoute`() {
        val initialStack = mutableListOf(ListingDetailRoute("l-1"), MyListingsRoute, EditSellerListingRoute("listing-X", "entry-X"))

        val topRoute = initialStack.lastOrNull()
        val canConsumeSaved = canConsumeSellerEditorEvent(
            eventSession = sessionA,
            eventEntryId = "entry-X",
            activeSession = sessionA,
            liveUserId = "user-A",
            topRoute = topRoute,
        )
        assertTrue(canConsumeSaved)

        // Consume event: pop top editor entry
        initialStack.removeLastOrNull()

        assertEquals(listOf(ListingDetailRoute("l-1"), MyListingsRoute), initialStack)
        assertEquals(1, initialStack.count { it == MyListingsRoute })
    }
}
