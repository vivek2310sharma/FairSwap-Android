package com.fairswap.feature.favourites

import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicSellerResult
import com.fairswap.core.favourites.FavouriteLoadResult
import com.fairswap.core.favourites.FavouritePage
import com.fairswap.core.favourites.FavouritesGateway
import com.fairswap.core.favourites.FavouritesRepository
import com.fairswap.core.favourites.FavouriteStateStore
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FavouritesViewModelTest {
    @Test fun initialSuccessAndRefreshFailureRetainCardsAndHideBackendError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val vm = FavouritesViewModel(repository(source))
            vm.load(); advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertEquals(1, vm.state.value.cards.size)
            source.throwFailure = true
            vm.refresh(); advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertEquals(1, vm.state.value.cards.size)
            assertTrue(vm.state.value.stale)
            assertFalse(vm.state.value.error.orEmpty().contains("private server"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedNextPageRetainsCardsAndTimeoutReleasesLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val vm = FavouritesViewModel(repository(source))
            vm.load(); advanceUntilIdle()
            source.throwFailure = true
            vm.loadMore(); advanceUntilIdle()
            assertFalse(vm.state.value.loadingMore)
            assertEquals(1, vm.state.value.cards.size)
            source.throwFailure = false
            source.await = true
            vm.refresh(); runCurrent(); advanceTimeBy(15_001); runCurrent()
            assertFalse(vm.state.value.loading)
            assertTrue(vm.state.value.error != null)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun clearUnavailableSuccessAndFailureUpdateOnlySafeSubset() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val vm = FavouritesViewModel(repository(source))
            vm.load(); advanceUntilIdle()
            vm.clearUnavailable(); advanceUntilIdle()
            assertEquals(0, vm.state.value.unavailableCount)
            assertEquals(1, vm.state.value.cards.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun clearFailurePreservesUnavailableStateAndCancellationReleasesLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { hideCards = true }
            val vm = FavouritesViewModel(repository(source))
            vm.load(); advanceUntilIdle()
            assertEquals(1, vm.state.value.unavailableCount)
            source.deleteFailure = true
            vm.clearUnavailable(); advanceUntilIdle()
            assertEquals(1, vm.state.value.unavailableCount)
            assertEquals("Unable to clear unavailable saved items. Retry.", vm.state.value.error)
            source.await = false
            source.cancel = true
            vm.refresh(); runCurrent()
            assertFalse(vm.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }

    private fun repository(source: FakeSource) = FavouritesRepository(source, source, FavouriteStateStore())

    private class FakeSource : FavouritesGateway, PublicMarketplaceDataSource {
        var throwFailure = false
        var await = false
        var cancel = false
        var hideCards = false
        var deleteFailure = false
        override suspend fun currentUserId() = "user"
        override suspend fun policyAccepted() = true
        override suspend fun isVisible(listingId: String) = true
        override suspend fun page(userId: String, offset: Int, size: Int): List<com.fairswap.core.favourites.FavouriteRow> {
            if (throwFailure) error("private server response")
            if (await) awaitCancellation()
            if (cancel) throw kotlinx.coroutines.CancellationException("private cancellation")
            return if (offset == 0) listOf(com.fairswap.core.favourites.FavouriteRow(ID)) else emptyList()
        }
        override suspend fun existing(userId: String, listingIds: List<String>) = setOf(ID)
        override suspend fun insert(userId: String, listingId: String) = Unit
        override suspend fun delete(userId: String, listingIds: List<String>) { if (deleteFailure) error("private delete response") }
        override suspend fun listing(id: String) = PublicDetailResult.Unavailable
        override suspend fun seller(id: String) = PublicSellerResult.Unavailable
        override suspend fun publicCards(ids: List<String>) = if (hideCards) emptyMap() else mapOf(ID to ListingCard(ID, "Bike", "₹1", "Good", "Cycles", "Jaipur", null))
        companion object { const val ID = "00000000-0000-0000-0000-000000000001" }
    }
}
