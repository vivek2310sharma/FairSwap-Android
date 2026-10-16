package com.fairswap.feature.discovery

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.DiscoveryDataSource
import com.fairswap.core.discovery.SearchPage
import com.fairswap.core.discovery.Locality
import com.fairswap.core.discovery.SearchFilters
import com.fairswap.core.discovery.ListingCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryRecentSearchTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeDiscoveryDataSource
    private lateinit var viewModel: DiscoveryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDiscoveryDataSource()
        viewModel = DiscoveryViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun applyFilters_withQuery_addsToRecentSearches() = runTest {
        viewModel.editFilters { it.copy(query = "Laptop") }
        viewModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Laptop"), viewModel.state.value.recentSearches)
    }

    @Test
    fun selectRecentSearch_populatesQueryAndApplies() = runTest {
        viewModel.editFilters { it.copy(query = "Laptop") }
        viewModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.editFilters { it.copy(query = "Phone") }
        viewModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Phone", "Laptop"), viewModel.state.value.recentSearches)

        viewModel.selectRecentSearch("Laptop")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Laptop", viewModel.state.value.applied.query)
    }

    @Test
    fun clearRecentSearches_emptiesRecentSearches() = runTest {
        viewModel.editFilters { it.copy(query = "Laptop") }
        viewModel.applyFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearRecentSearches()

        assertEquals(emptyList<String>(), viewModel.state.value.recentSearches)
    }
}

class FakeDiscoveryDataSource : DiscoveryDataSource {
    override suspend fun categories(): List<Category> = emptyList()
    override suspend fun cities(): List<City> = emptyList()
    override suspend fun localities(cityId: String): List<Locality> = emptyList()
    override suspend fun search(filters: SearchFilters, page: Int): SearchPage = SearchPage(items = emptyList<ListingCard>(), totalCount = 0, page = page, totalPages = 0)
}
