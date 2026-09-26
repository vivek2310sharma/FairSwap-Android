package com.fairswap.feature.listingdetail

import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicListingDetail
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicSellerProfile
import com.fairswap.core.discovery.PublicSellerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListingDetailViewModelTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val sellerId = "00000000-0000-0000-0000-000000000002"

    @Test
    fun successClearsLoadingAndRetryKeepsDataOnFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source))
            model.load(id)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
            source.listing = { error("private backend detail") }
            model.retry()
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
            assertFalse(model.state.value.error.orEmpty().contains("private backend detail"))
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun unavailableAndExceptionHaveDistinctTerminalStates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { listing = { PublicDetailResult.Unavailable } }
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source))
            model.load(id)
            advanceUntilIdle()
            assertTrue(model.state.value.unavailable)
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            source.listing = { error("private backend detail") }
            model.retry()
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertEquals("Unable to load this listing. Retry.", model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun timeoutAndCancellationReleaseLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { listing = { awaitCancellation() } }
            val model = ListingDetailViewModel(source, fakeFavouritesRepository(source))
            model.load(id)
            runCurrent()
            advanceTimeBy(15_001)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals("Unable to load this listing. Retry.", model.state.value.error)
            model.load("00000000-0000-0000-0000-000000000003")
            runCurrent()
            source.listing = { PublicDetailResult.Found(detail()) }
            model.load(id)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }

    private fun detail() = PublicListingDetail(
        id, sellerId, "Bike", null, "Good", "fixed", 1250, "₹12.50", "Cycles", "Jaipur", null,
        emptyList(), null, "2026-09-01T00:00:00Z",
        PublicSellerProfile(sellerId, "Seller", null, null, null, "2026-09-01T00:00:00Z"), emptyList(),
    )

    private fun fakeFavouritesRepository(source: FakeSource) = com.fairswap.core.favourites.FavouritesRepository(
        FakeFavouritesGateway(), source, com.fairswap.core.favourites.FavouriteStateStore()
    )

    private inner class FakeFavouritesGateway : com.fairswap.core.favourites.FavouritesGateway {
        override suspend fun currentUserId() = null
        override suspend fun policyAccepted() = false
        override suspend fun isVisible(listingId: String) = false
        override suspend fun page(userId: String, offset: Int, size: Int) = emptyList<com.fairswap.core.favourites.FavouriteRow>()
        override suspend fun existing(userId: String, listingIds: List<String>) = emptySet<String>()
        override suspend fun insert(userId: String, listingId: String) = Unit
        override suspend fun delete(userId: String, listingIds: List<String>) = Unit
    }

    private inner class FakeSource : PublicMarketplaceDataSource {
        var listing: suspend () -> PublicDetailResult = { PublicDetailResult.Found(detail()) }
        override suspend fun listing(id: String) = listing.invoke()
        override suspend fun seller(id: String) = PublicSellerResult.Unavailable
        override suspend fun publicCards(ids: List<String>) = emptyMap<String, com.fairswap.core.discovery.ListingCard>()
    }
}
