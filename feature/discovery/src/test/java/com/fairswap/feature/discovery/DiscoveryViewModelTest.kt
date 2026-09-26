package com.fairswap.feature.discovery

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.DiscoveryDataSource
import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.Locality
import com.fairswap.core.discovery.ResultErrorKind
import com.fairswap.core.discovery.SearchFilters
import com.fairswap.core.discovery.SearchPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {
    @Test
    fun initialSuccessClearsLoadingWithoutRequestingLocalities() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val model = DiscoveryViewModel(source)
            advanceUntilIdle()
            assertFalse(model.state.value.loadingFirstPage)
            assertFalse(model.state.value.loadingMetadata)
            assertEquals(1, model.state.value.results.items.size)
            assertEquals(0, source.localityCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun initialFailureClearsLoadingAndShowsGenericError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { search = { _, _ -> error("backend private detail") } }
            val model = DiscoveryViewModel(source)
            advanceUntilIdle()
            assertFalse(model.state.value.loadingFirstPage)
            assertEquals(ResultErrorKind.FIRST_PAGE, model.state.value.results.errorKind)
            assertFalse(model.state.value.results.error.orEmpty().contains("backend private detail"))
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun failedRefreshKeepsPreviouslyLoadedResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val model = DiscoveryViewModel(source)
            advanceUntilIdle()
            source.search = { _, _ -> error("backend private detail") }
            model.refresh()
            advanceUntilIdle()
            assertFalse(model.state.value.loadingFirstPage)
            assertEquals(1, model.state.value.results.items.size)
            assertTrue(model.state.value.results.isStale)
            assertEquals(ResultErrorKind.FIRST_PAGE, model.state.value.results.errorKind)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancelledFirstPageCannotLeaveReplacementLoadClaimed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            source.search = { _, page ->
                if (++source.searchCalls == 1) awaitCancellation() else source.page(page)
            }
            val model = DiscoveryViewModel(source)
            runCurrent()
            assertTrue(model.state.value.loadingFirstPage)
            model.applyFilters()
            advanceUntilIdle()
            assertFalse(model.state.value.loadingFirstPage)
            assertEquals(1, model.state.value.results.items.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun timedOutSearchAndMetadataReachGenericRetryStates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply {
                search = { _, _ -> awaitCancellation() }
                categories = { awaitCancellation() }
            }
            val model = DiscoveryViewModel(source)
            runCurrent()
            advanceTimeBy(15_001)
            runCurrent()
            assertFalse(model.state.value.loadingFirstPage)
            assertFalse(model.state.value.loadingMetadata)
            assertEquals(ResultErrorKind.FIRST_PAGE, model.state.value.results.errorKind)
            assertEquals("Unable to load filters. Retry.", model.state.value.metadataError)
            assertEquals(0, source.localityCalls)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun concurrentLoadMoreRequestsClaimOnlyOnePageAndKeepItemsOnFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val pending = CompletableDeferred<SearchPage>()
            source.search = { _, page ->
                source.searchCalls++
                if (page == 1) source.page(1, totalPages = 2) else pending.await()
            }
            val model = DiscoveryViewModel(source)
            advanceUntilIdle()
            model.loadMore()
            model.loadMore()
            runCurrent()
            assertTrue(model.state.value.loadingMore)
            assertEquals(2, source.searchCalls)
            pending.completeExceptionally(IllegalStateException("private backend detail"))
            advanceUntilIdle()
            assertFalse(model.state.value.loadingMore)
            assertEquals(1, model.state.value.results.items.size)
            assertEquals(ResultErrorKind.NEXT_PAGE, model.state.value.results.errorKind)
        } finally { Dispatchers.resetMain() }
    }

    private class FakeSource : DiscoveryDataSource {
        var searchCalls = 0
        var localityCalls = 0
        var search: suspend (SearchFilters, Int) -> SearchPage = { _, page ->
            searchCalls++
            page(page)
        }
        var categories: suspend () -> List<Category> = { emptyList() }

        fun page(page: Int, totalPages: Int = 1) = SearchPage(
            items = listOf(ListingCard("listing-$page", "Bike", "Free", "Good", "Cycles", "Jaipur", null)),
            totalCount = totalPages,
            page = page,
            totalPages = totalPages,
        )

        override suspend fun search(filters: SearchFilters, page: Int) = search.invoke(filters, page)
        override suspend fun categories() = categories.invoke()
        override suspend fun cities() = emptyList<City>()
        override suspend fun localities(cityId: String): List<Locality> {
            localityCalls++
            return emptyList()
        }
    }
}
