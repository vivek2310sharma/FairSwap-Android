package com.fairswap.feature.sellerlistings

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.sellerlistings.CategoryAttributeMetadata
import com.fairswap.core.sellerlistings.SellerLifecycleAction
import com.fairswap.core.sellerlistings.SellerListingGateway
import com.fairswap.core.sellerlistings.SellerListingInput
import com.fairswap.core.sellerlistings.SellerListingInsertDto
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerListingRowDto
import com.fairswap.core.sellerlistings.SellerListingUpdateDto
import com.fairswap.core.sellerlistings.SellerMutationResult
import com.fairswap.core.sellerlistings.SellerPrivateLocationDto
import com.fairswap.core.sellerlistings.SellerSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeSellerListingGateway(
    var userId: String? = "user-123",
    var policyAccepted: Boolean = true,
) : SellerListingGateway {
    val listings = mutableMapOf<String, SellerListingRowDto>()
    var transitionCallCount = 0

    override suspend fun lookupPincode(pinCode: String): com.fairswap.core.sellerlistings.PincodeLookupResponseDto = com.fairswap.core.sellerlistings.PincodeLookupResponseDto(success = true, pincode = pinCode, cityId = "66666666-7777-8888-9999-000000000000")

    var fetchListingsCallCount = 0

    override suspend fun currentUserId(): String? = userId
    override suspend fun policyAccepted(): Boolean = policyAccepted
    override suspend fun fetchCategories(): List<Category> = emptyList()
    override suspend fun fetchCities(): List<City> = emptyList()
    override suspend fun fetchLocalities(cityId: String): List<Locality> = emptyList()

    override suspend fun fetchSellerListings(sellerId: String, limit: Int): List<SellerListingRowDto> =
        listings.values.filter { it.sellerId == sellerId }.also { fetchListingsCallCount++ }

    override suspend fun fetchReadyMediaCounts(listingIds: List<String>): Map<String, Int> =
        listingIds.associateWith { 1 }

    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto? =
        listings[listingId]?.takeIf { it.sellerId == sellerId }

    override suspend fun fetchSellerPrivateLocation(listingId: String): SellerPrivateLocationDto? = null

    override suspend fun fetchCategoryAttributes(categoryId: String): List<CategoryAttributeMetadata> = emptyList()

    override suspend fun insertListing(insertDto: SellerListingInsertDto): SellerListingRowDto {
        val current = userId ?: error("Signed out")
        val id = "22222222-3333-4444-5555-000000000001"
        val row = SellerListingRowDto(
            id = id,
            sellerId = current,
            title = insertDto.title,
            categoryId = insertDto.categoryId,
            cityId = insertDto.cityId,
            status = "draft",
        )
        listings[id] = row
        return row
    }

    override suspend fun updateListing(
        listingId: String,
        sellerId: String,
        updateDto: SellerListingUpdateDto,
    ): SellerListingRowDto {
        val existing = listings[listingId]!!
        val updated = existing.copy(title = updateDto.title)
        listings[listingId] = updated
        return updated
    }

    override suspend fun submitListing(listingId: String) {}

    override suspend fun transitionListing(listingId: String, action: String) {
        transitionCallCount++
        val existing = listings[listingId]
        if (existing != null) {
            listings[listingId] = existing.copy(status = "paused")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyListingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bind cancels running work and clears state on sign out`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-123")
        gateway.listings["l-1"] = SellerListingRowDto(
            id = "l-1",
            sellerId = "user-123",
            title = "Test Phone",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session1 = repo.sessionOwner.updateSession("user-123")
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(session1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.listings.size)

        // User signs out
        repo.sessionOwner.updateSession(null)
        viewModel.bind(null)
        testDispatcher.scheduler.advanceUntilIdle()

        val signedOutState = viewModel.state.value
        assertFalse(signedOutState.isLoading)
        assertTrue(signedOutState.listings.isEmpty())
        assertEquals("Signed out.", signedOutState.errorMessage)
    }

    @Test
    fun `bind purges account A data when switching to account B`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        gateway.listings["l-A"] = SellerListingRowDto(
            id = "l-A",
            sellerId = "user-A",
            title = "User A Item",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("User A Item", viewModel.state.value.listings[0].title)

        // Switch to User B
        gateway.userId = "user-B"
        val sessionB = repo.sessionOwner.updateSession("user-B")
        viewModel.bind(sessionB)
        testDispatcher.scheduler.advanceUntilIdle()

        val stateB = viewModel.state.value
        assertEquals("user-B", stateB.userId)
        assertTrue(stateB.listings.isEmpty())
    }

    @Test
    fun `lifecycle double tap before scheduler runs dispatches exactly one mutation`() = runTest {
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        val gateway = FakeSellerListingGateway(userId = "user-123")
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-123",
            title = "Phone 1",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(session)
        testDispatcher.scheduler.advanceUntilIdle()

        // Double tap Pause
        viewModel.executeLifecycleAction(validUuid1, SellerLifecycleAction.PAUSE)
        viewModel.executeLifecycleAction(validUuid1, SellerLifecycleAction.PAUSE)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, gateway.transitionCallCount)
    }

    @Test
    fun `tapping listing Y while mutation on X is active rejects Y without cancelling X`() = runTest {
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        val validUuid2 = "22222222-3333-4444-5555-000000000002"
        val gateway = FakeSellerListingGateway(userId = "user-123")
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-123",
            title = "Phone 1",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        gateway.listings[validUuid2] = SellerListingRowDto(
            id = validUuid2,
            sellerId = "user-123",
            title = "Phone 2",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(session)
        testDispatcher.scheduler.advanceUntilIdle()

        // Synchronously execute action on validUuid1
        viewModel.executeLifecycleAction(validUuid1, SellerLifecycleAction.PAUSE)
        assertEquals(validUuid1, viewModel.state.value.mutatingListingId)

        // Attempt action on validUuid2 while validUuid1 is active
        viewModel.executeLifecycleAction(validUuid2, SellerLifecycleAction.PAUSE)

        testDispatcher.scheduler.advanceUntilIdle()

        // Only validUuid1 should have been executed
        assertEquals(1, gateway.transitionCallCount)
    }

    @Test
    fun `old A operation finally block running after state rebound to B cannot clear B busy state`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Item 1",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Start operation under session A
        viewModel.executeLifecycleAction(validUuid1, SellerLifecycleAction.PAUSE)
        assertEquals(validUuid1, viewModel.state.value.mutatingListingId)

        // Switch to session B
        gateway.userId = "user-B"
        val sessionB = repo.sessionOwner.updateSession("user-B")!!
        viewModel.bind(sessionB)

        // State B set mutatingListingId to null
        assertEquals(null, viewModel.state.value.mutatingListingId)

        // Start operation under B
        val validUuid2 = "22222222-3333-4444-5555-000000000002"
        gateway.listings[validUuid2] = SellerListingRowDto(
            id = validUuid2,
            sellerId = "user-B",
            title = "Item 2",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        viewModel.executeLifecycleAction(validUuid2, SellerLifecycleAction.PAUSE)
        assertEquals(validUuid2, viewModel.state.value.mutatingListingId)

        testDispatcher.scheduler.advanceUntilIdle()

        // State B's mutatingListingId should remain owned by B
        assertEquals(null, viewModel.state.value.mutatingListingId)
    }

    @Test
    fun `dashboard invalidation signal triggers MyListingsViewModel refresh for matching session`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Original Title",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Original Title", viewModel.state.value.listings[0].title)

        // Mutate gateway item
        gateway.listings[validUuid1] = gateway.listings[validUuid1]!!.copy(title = "Updated Title")

        // Trigger dashboard invalidation
        repo.notifyDashboardInvalidated(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Dashboard state should automatically reflect updated title
        assertEquals("Updated Title", viewModel.state.value.listings[0].title)
    }

    @Test
    fun `dashboard invalidation signal for session B does not refresh session A dashboard`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Original Title",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Mutate gateway item
        gateway.listings[validUuid1] = gateway.listings[validUuid1]!!.copy(title = "Updated Title")

        // Trigger dashboard invalidation for session B (stale/different session)
        val sessionB = SellerSession("user-B", 2L)
        repo.notifyDashboardInvalidated(sessionB)
        testDispatcher.scheduler.advanceUntilIdle()

        // Session A dashboard must NOT refresh
        assertEquals("Original Title", viewModel.state.value.listings[0].title)
    }

    @Test
    fun `successful lifecycle mutation triggers dashboard reload EXACTLY ONCE`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Item 1",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial load called fetchSellerListings once
        assertEquals(1, gateway.fetchListingsCallCount)

        // Execute lifecycle action
        viewModel.executeLifecycleAction(validUuid1, SellerLifecycleAction.PAUSE)
        testDispatcher.scheduler.advanceUntilIdle()

        // Dashboard reload should have happened EXACTLY ONCE (call count goes from 1 to 2, NOT 3)
        assertEquals(2, gateway.fetchListingsCallCount)
    }

    @Test
    fun `successful saveEdit triggers dashboard refresh EXACTLY ONCE and updates dashboard state`() = runTest {
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        val gateway = FakeSellerListingGateway(userId = "user-A")
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Original Title",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial load called fetchSellerListings once
        assertEquals(1, gateway.fetchListingsCallCount)
        assertEquals("Original Title", viewModel.state.value.listings[0].title)

        // Perform saveEdit
        val validInput = SellerListingInput(
            title = "Updated Title From Editor",
            categoryId = "11111111-2222-3333-4444-555555555555",
            cityId = "66666666-7777-8888-9999-000000000000",
                pinCode = "123456",
        )
        val saveRes = repo.saveEdit(validUuid1, validInput, sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Save failed: $saveRes", saveRes is SellerMutationResult.Success)

        // fetchListingsCallCount must be EXACTLY 2 (1 initial + 1 dashboard refresh)
        assertEquals(2, gateway.fetchListingsCallCount)

        // Dashboard state must immediately reflect updated title
        assertEquals("Updated Title From Editor", viewModel.state.value.listings[0].title)
    }

    @Test
    fun `failed save or validation error emits zero dashboard invalidation`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialFetchCount = gateway.fetchListingsCallCount

        // Try saving edit on invalid listing ID
        val res = repo.saveEdit("invalid-id", SellerListingInput(), sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(res is SellerMutationResult.Failure)
        assertEquals(initialFetchCount, gateway.fetchListingsCallCount)
    }

    @Test
    fun `AuthRequired or stale session mutation emits zero dashboard invalidation`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val staleSession = SellerSession("user-A", 999L)
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialFetchCount = gateway.fetchListingsCallCount
        val validInput = SellerListingInput(
            title = "Valid Title",
            categoryId = "11111111-2222-3333-4444-555555555555",
            cityId = "66666666-7777-8888-9999-000000000000",
                pinCode = "123456",
        )

        // Try saving with stale session
        val res = repo.saveEdit("22222222-3333-4444-5555-000000000001", validInput, staleSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(res is SellerMutationResult.AuthRequired)
        assertEquals(initialFetchCount, gateway.fetchListingsCallCount)
    }

    @Test
    fun `PolicyReviewRequired mutation emits zero dashboard invalidation`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A", policyAccepted = false)
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = MyListingsViewModel(repo)

        viewModel.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialFetchCount = gateway.fetchListingsCallCount

        val res = repo.saveEdit(
            "22222222-3333-4444-5555-000000000001",
            SellerListingInput(title = "Valid Title", categoryId = "11111111-2222-3333-4444-555555555555", cityId = "66666666-7777-8888-9999-000000000000"),
            sessionA,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(res is SellerMutationResult.PolicyReviewRequired)
        assertEquals(initialFetchCount, gateway.fetchListingsCallCount)
    }

    @Test
    fun `missed consumer detects pending invalidation upon rebind or creation and reloads exactly once`() = runTest {
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        val gateway = FakeSellerListingGateway(userId = "user-A")
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Original Title",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        // 1. Initial dashboard ViewModel bound and loaded
        val initialVm = MyListingsViewModel(repo)
        initialVm.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, gateway.fetchListingsCallCount)

        // 2. Dashboard ViewModel unbound / signed out (simulating un-composed route)
        initialVm.bind(null)
        testDispatcher.scheduler.advanceUntilIdle()

        // 3. Perform saveEdit while dashboard ViewModel is unbound
        val validInput = SellerListingInput(
            title = "Durable Invalidation Title",
            categoryId = "11111111-2222-3333-4444-555555555555",
            cityId = "66666666-7777-8888-9999-000000000000",
                pinCode = "123456",
        )
        val saveRes = repo.saveEdit(validUuid1, validInput, sessionA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(saveRes is SellerMutationResult.Success)

        // 4. Create/rebind new dashboard ViewModel AFTER mutation completes
        val newDashboardVm = MyListingsViewModel(repo)
        newDashboardVm.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // 5. Assert pending invalidation detected and reloaded (1 initial + 1 new VM load = 2)
        assertEquals(2, gateway.fetchListingsCallCount)
        assertEquals("Durable Invalidation Title", newDashboardVm.state.value.listings[0].title)

        // 6. Subsequent re-binds at same version do not reload again
        newDashboardVm.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, gateway.fetchListingsCallCount)
    }

    @Test
    fun `account A invalidation cannot be consumed by account B`() = runTest {
        val gateway = FakeSellerListingGateway(userId = "user-A")
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        // Invalidate session A
        repo.notifyDashboardInvalidated(sessionA)

        // Switch active session to account B
        gateway.userId = "user-B"
        val sessionB = repo.sessionOwner.updateSession("user-B")!!
        val viewModelB = MyListingsViewModel(repo)
        viewModelB.bind(sessionB)
        testDispatcher.scheduler.advanceUntilIdle()

        // Account B initial load = 1 fetch
        assertEquals(1, gateway.fetchListingsCallCount)
    }

    @Test
    fun `two ViewModels sharing same repository instance both receive invalidation`() = runTest {
        val validUuid1 = "22222222-3333-4444-5555-000000000001"
        val gateway = FakeSellerListingGateway(userId = "user-A")
        gateway.listings[validUuid1] = SellerListingRowDto(
            id = validUuid1,
            sellerId = "user-A",
            title = "Item 1",
            categoryId = "cat-1",
            cityId = "city-1",
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        val vm1 = MyListingsViewModel(repo)
        val vm2 = MyListingsViewModel(repo)
        vm1.bind(sessionA)
        vm2.bind(sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // 2 initial loads
        assertEquals(2, gateway.fetchListingsCallCount)

        // Mutate via repo
        val validInput = SellerListingInput(
            title = "Shared Repo Title",
            categoryId = "11111111-2222-3333-4444-555555555555",
            cityId = "66666666-7777-8888-9999-000000000000",
                pinCode = "123456",
        )
        repo.saveEdit(validUuid1, validInput, sessionA)
        testDispatcher.scheduler.advanceUntilIdle()

        // Both ViewModels reflect updated title
        assertEquals("Shared Repo Title", vm1.state.value.listings[0].title)
        assertEquals("Shared Repo Title", vm2.state.value.listings[0].title)
    }
}
