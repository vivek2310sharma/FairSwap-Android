package com.fairswap.feature.discovery

import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.ResultsState
import com.fairswap.core.discovery.SearchFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiscoveryUiStateTest {
    @Test
    fun applyingNewFiltersClearsOldPagination() {
        val state = DiscoveryUiState(
            results = ResultsState(
                items = listOf(ListingCard("a", "A", "Free", "Good", "Cycles", "Jaipur", null)),
                loadedPage = 2,
                totalPages = 3,
            ),
            loadingMore = true,
        )
        val changed = state.apply(SearchFilters(query = "table"))
        assertEquals("table", changed.applied.query)
        assertEquals(emptyList<ListingCard>(), changed.results.items)
        assertEquals(0, changed.results.loadedPage)
        assertFalse(changed.loadingMore)
    }
}
