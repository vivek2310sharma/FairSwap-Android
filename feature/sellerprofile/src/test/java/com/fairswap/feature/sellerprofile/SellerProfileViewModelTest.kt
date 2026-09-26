package com.fairswap.feature.sellerprofile

import com.fairswap.core.discovery.PublicDetailResult
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
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SellerProfileViewModelTest {
    private val id = "00000000-0000-0000-0000-000000000002"

    @Test
    fun successAndRetryFailureKeepProfileAndClearLoading() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource()
            val model = SellerProfileViewModel(source)
            model.load(id)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.profile)
            source.seller = { error("private backend detail") }
            model.retry()
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertNotNull(model.state.value.profile)
            assertEquals("Unable to load this seller. Retry.", model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun unavailableAndTimeoutAreTerminal() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = FakeSource().apply { seller = { PublicSellerResult.Unavailable } }
            val model = SellerProfileViewModel(source)
            model.load(id)
            advanceUntilIdle()
            assertTrue(model.state.value.unavailable)
            assertFalse(model.state.value.loading)
            source.seller = { awaitCancellation() }
            model.retry()
            runCurrent()
            advanceTimeBy(15_001)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals("Unable to load this seller. Retry.", model.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    private fun profile() = PublicSellerProfile(id, "Seller", null, "Jaipur", "Rajasthan", "2026-09-01T00:00:00Z")

    private inner class FakeSource : PublicMarketplaceDataSource {
        var seller: suspend () -> PublicSellerResult = { PublicSellerResult.Found(profile()) }
        override suspend fun listing(id: String) = PublicDetailResult.Unavailable
        override suspend fun seller(id: String) = seller.invoke()
    }
}
