package com.fairswap.feature.sellerlistings

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.sellerlistings.CategoryAttributeMetadata
import com.fairswap.core.sellerlistings.PincodeDistrict
import com.fairswap.core.sellerlistings.PincodeDistrictDto
import com.fairswap.core.sellerlistings.PincodeLocality
import com.fairswap.core.sellerlistings.PincodeLocalityDto
import com.fairswap.core.sellerlistings.PincodeLookupFailureReason
import com.fairswap.core.sellerlistings.PincodeLookupResponseDto
import com.fairswap.core.sellerlistings.SellerListingDetail
import com.fairswap.core.sellerlistings.SellerListingGateway
import com.fairswap.core.sellerlistings.SellerListingInsertDto
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerListingRowDto
import com.fairswap.core.sellerlistings.SellerListingStatus
import com.fairswap.core.sellerlistings.SellerListingUpdateDto
import com.fairswap.core.sellerlistings.SellerPrivateLocationDto
import com.fairswap.core.sellerlistings.formatUnavailableReferenceLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val TestCreateRoute = "TestCreateRoute"
private val validListingUuid = "22222222-3333-4444-5555-000000000001"
private val validLocalityUuid = "88888888-9999-0000-1111-222222222222"

private class FakeEditorGateway(
    var userId: String? = "user-123",
    var categoryError: Throwable? = null,
    var cityError: Throwable? = null,
    var categoryAttrError: Throwable? = null,
    var localityError: Throwable? = null,
) : SellerListingGateway {
    val listings = mutableMapOf<String, SellerListingRowDto>()
    var insertCallCount = 0
    var lookupCallCount = 0

    var nextLookupResponse: PincodeLookupResponseDto? = null
    var lookupEntered: CompletableDeferred<Unit>? = null
    var lookupRelease: CompletableDeferred<Unit>? = null

    override suspend fun lookupPincode(pinCode: String): PincodeLookupResponseDto {
        lookupCallCount++
        lookupEntered?.complete(Unit)
        lookupRelease?.await()
        return nextLookupResponse ?: PincodeLookupResponseDto(
            success = true,
            pincode = pinCode,
            cityId = "66666666-7777-8888-9999-000000000000",
            district = "Bengaluru",
            state = "Karnataka",
            localities = listOf(
                PincodeLocalityDto(id = validLocalityUuid, name = "Koramangala", district = "Bengaluru")
            )
        )
    }

    var fetchCategoriesEntered: CompletableDeferred<Unit>? = null
    var fetchCategoriesRelease: CompletableDeferred<Unit>? = null

    var fetchCitiesEntered: CompletableDeferred<Unit>? = null
    var fetchCitiesRelease: CompletableDeferred<Unit>? = null

    var fetchAttrEntered: CompletableDeferred<Unit>? = null
    var fetchAttrRelease: CompletableDeferred<Unit>? = null

    var fetchLocalitiesEntered: CompletableDeferred<Unit>? = null
    var fetchLocalitiesRelease: CompletableDeferred<Unit>? = null

    override suspend fun currentUserId(): String? = userId
    override suspend fun policyAccepted(): Boolean = true

    override suspend fun fetchCategories(): List<Category> {
        fetchCategoriesEntered?.complete(Unit)
        fetchCategoriesRelease?.await()
        categoryError?.let { throw it }
        return listOf(
            Category("cat-1", "Electronics", "electronics")
        )
    }

    override suspend fun fetchCities(): List<City> {
        fetchCitiesEntered?.complete(Unit)
        fetchCitiesRelease?.await()
        cityError?.let { throw it }
        return listOf(
            City("66666666-7777-8888-9999-000000000000", "Bengaluru", "Karnataka")
        )
    }

    override suspend fun fetchLocalities(cityId: String): List<Locality> {
        fetchLocalitiesEntered?.complete(Unit)
        fetchLocalitiesRelease?.await()
        localityError?.let { throw it }
        return listOf(
            Locality(validLocalityUuid, "Koramangala")
        )
    }

    override suspend fun fetchSellerListings(sellerId: String, limit: Int): List<SellerListingRowDto> = emptyList()
    override suspend fun fetchReadyMediaCounts(listingIds: List<String>): Map<String, Int> = emptyMap()

    val privateLocations = mutableMapOf<String, SellerPrivateLocationDto>()

    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto? =
        listings[listingId]?.takeIf { it.sellerId == sellerId }

    override suspend fun fetchSellerPrivateLocation(listingId: String): SellerPrivateLocationDto? =
        privateLocations[listingId]

    override suspend fun fetchCategoryAttributes(categoryId: String): List<CategoryAttributeMetadata> {
        fetchAttrEntered?.complete(Unit)
        fetchAttrRelease?.await()
        categoryAttrError?.let { throw it }
        return if (categoryId == "11111111-2222-3333-4444-111111111111") listOf(
            CategoryAttributeMetadata("a1", categoryId, "brandA", "Brand A", "text", false, emptyList())
        ) else if (categoryId == "11111111-2222-3333-4444-222222222222") listOf(
            CategoryAttributeMetadata("b1", categoryId, "brandB", "Brand B", "text", false, emptyList())
        ) else emptyList()
    }

    override suspend fun insertListing(insertDto: SellerListingInsertDto): SellerListingRowDto {
        insertCallCount++
        val id = validListingUuid
        val row = SellerListingRowDto(
            id = id,
            sellerId = userId!!,
            title = insertDto.title,
            categoryId = insertDto.categoryId,
            cityId = insertDto.cityId,
            localityId = insertDto.localityId,
            status = "draft",
        )
        listings[id] = row
        privateLocations[id] = SellerPrivateLocationDto(listingId = id, pinCode = insertDto.pinCode, landmark = insertDto.landmark)
        return row
    }

    override suspend fun updateListing(
        listingId: String,
        sellerId: String,
        updateDto: SellerListingUpdateDto,
    ): SellerListingRowDto {
        val existing = listings[listingId]!!
        val updated = existing.copy(
            title = updateDto.title,
            categoryId = updateDto.categoryId,
            cityId = updateDto.cityId,
            localityId = updateDto.localityId,
        )
        listings[listingId] = updated
        privateLocations[listingId] = SellerPrivateLocationDto(listingId = listingId, pinCode = updateDto.pinCode, landmark = updateDto.landmark)
        return updated
    }

    override suspend fun submitListing(listingId: String) {}
    override suspend fun transitionListing(listingId: String, action: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class SellerListingEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val validUuid = "11111111-2222-3333-4444-555555555555"
    private val validCityUuid = "66666666-7777-8888-9999-000000000000"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `less than six digits does not lookup`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("12345")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, gateway.lookupCallCount)
        assertTrue(viewModel.state.value.pincodeUiState is PincodeUiState.Idle)
    }

    @Test
    fun `exactly six digits performs one lookup`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("560001")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, gateway.lookupCallCount)
        assertTrue(viewModel.state.value.pincodeUiState is PincodeUiState.ResolvedSingleDistrict)
    }

    @Test
    fun `stale lookup response discarded`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        gateway.lookupEntered = CompletableDeferred()
        gateway.lookupRelease = CompletableDeferred()

        viewModel.updatePinCode("560001")
        runCurrent()

        // User changes PIN while first lookup is in flight
        viewModel.updatePinCode("560002")

        gateway.lookupRelease?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("560002", viewModel.state.value.input.pinCode)
    }

    @Test
    fun `session change discards response`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(sessionA, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        gateway.lookupEntered = CompletableDeferred()
        gateway.lookupRelease = CompletableDeferred()

        viewModel.updatePinCode("560001")
        runCurrent()

        gateway.userId = "user-B"
        val sessionB = repo.sessionOwner.updateSession("user-B")!!
        viewModel.bind(sessionB, listingId = null, entryId = TestCreateRoute)

        gateway.lookupRelease?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.state.value.input.pinCode)
    }

    @Test
    fun `editor entry change discards response`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = "entry-1")
        testDispatcher.scheduler.advanceUntilIdle()

        gateway.lookupEntered = CompletableDeferred()
        gateway.lookupRelease = CompletableDeferred()

        viewModel.updatePinCode("560001")
        runCurrent()

        viewModel.bind(session, listingId = null, entryId = "entry-2")

        gateway.lookupRelease?.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.state.value.input.pinCode)
    }

    @Test
    fun `single district resolves`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("560001")
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.state.value.pincodeUiState
        assertTrue(uiState is PincodeUiState.ResolvedSingleDistrict)
        assertEquals(validCityUuid, viewModel.state.value.input.cityId)
    }

    @Test
    fun `multi-district enters RequiresDistrictChoice even though top-level city_id is null`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "123456",
            state = "Haryana",
            district = null,
            cityId = null,
            districts = listOf(
                PincodeDistrictDto(name = "Gurugram", cityId = validCityUuid),
                PincodeDistrictDto(name = "Faridabad", cityId = "77777777-8888-9999-0000-111111111111")
            ),
            localities = listOf(
                PincodeLocalityDto(id = "99999999-1111-2222-3333-444444444444", name = "Loc 1", district = "Gurugram"),
                PincodeLocalityDto(id = "99999999-1111-2222-3333-555555555555", name = "Loc 2", district = "Faridabad")
            )
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.state.value.pincodeUiState
        assertTrue(uiState is PincodeUiState.RequiresDistrictChoice)
        assertNull(viewModel.state.value.input.cityId)
    }

    @Test
    fun `selected district uses district city_id`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "123456",
            state = "Haryana",
            district = null,
            cityId = null,
            districts = listOf(
                PincodeDistrictDto(name = "Gurugram", cityId = validCityUuid),
                PincodeDistrictDto(name = "Faridabad", cityId = "77777777-8888-9999-0000-111111111111")
            ),
            localities = listOf(
                PincodeLocalityDto(id = "99999999-1111-2222-3333-444444444444", name = "Loc 1", district = "Gurugram")
            )
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        val reqState = viewModel.state.value.pincodeUiState as PincodeUiState.RequiresDistrictChoice
        viewModel.selectDistrict(reqState.districts[0])

        val resolvedState = viewModel.state.value.pincodeUiState
        assertTrue(resolvedState is PincodeUiState.ResolvedDistrict)
        assertEquals(validCityUuid, viewModel.state.value.input.cityId)
    }

    @Test
    fun `missing compatibility city produces typed error`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "123456",
            state = "UnknownState",
            district = "UnknownDistrict",
            cityId = null
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        val errState = viewModel.state.value.pincodeUiState as PincodeUiState.Error
        assertEquals(PincodeLookupFailureReason.COMPATIBILITY_CITY_UNAVAILABLE, errState.reason)
    }

    @Test
    fun `district filters localities`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "123456",
            state = "Haryana",
            district = null,
            cityId = null,
            districts = listOf(
                PincodeDistrictDto(name = "Gurugram", cityId = validCityUuid),
                PincodeDistrictDto(name = "Faridabad", cityId = "77777777-8888-9999-0000-111111111111")
            ),
            localities = listOf(
                PincodeLocalityDto(id = "99999999-1111-2222-3333-444444444444", name = "Loc Gurugram", district = "Gurugram"),
                PincodeLocalityDto(id = "99999999-1111-2222-3333-555555555555", name = "Loc Faridabad", district = "Faridabad")
            )
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        val reqState = viewModel.state.value.pincodeUiState as PincodeUiState.RequiresDistrictChoice
        viewModel.selectDistrict(reqState.districts[0])

        val resolvedState = viewModel.state.value.pincodeUiState as PincodeUiState.ResolvedDistrict
        assertEquals(1, resolvedState.localities.size)
        assertEquals("Loc Gurugram", resolvedState.localities[0].name)
    }

    @Test
    fun `valid persisted locality preserved`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560001",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = validLocalityUuid, name = "Koramangala", district = "Bengaluru")
            )
        )
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = validLocalityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)
    }

    @Test
    fun `stale persisted locality clears to null without auto replacement`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560001",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = "99999999-0000-1111-2222-333333333333", name = "Indiranagar", district = "Bengaluru")
            )
        )
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = "77777777-0000-1111-2222-333333333333",
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.input.localityId)
    }

    @Test
    fun `single locality auto selects only for a genuinely new selection`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560001",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = validLocalityUuid, name = "Unique Area", district = "Bengaluru")
            )
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("560001")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)
    }

    @Test
    fun `changed PIN invalidates a previous locality and a single new candidate is NOT silently substituted`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560001",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = validLocalityUuid, name = "Koramangala", district = "Bengaluru")
            )
        )
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = validLocalityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        // Bind existing listing with validLocalityUuid
        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)

        // User changes PIN to "560002"
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560002",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = "99999999-0000-1111-2222-333333333333", name = "Indiranagar", district = "Bengaluru")
            )
        )
        viewModel.updatePinCode("560002")
        testDispatcher.scheduler.advanceUntilIdle()

        // Since validLocalityUuid was not in candidates for 560002, it MUST clear to null and NOT auto-select "99999999-0000-1111-2222-333333333333"
        assertNull(viewModel.state.value.input.localityId)
    }

    @Test
    fun `multi-district edit with stale prior locality does not auto-select replacement after district selection`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "123456",
            state = "Haryana",
            district = null,
            cityId = null,
            districts = listOf(
                PincodeDistrictDto(name = "Gurugram", cityId = "11111111-1111-1111-1111-111111111111"),
                PincodeDistrictDto(name = "Faridabad", cityId = "77777777-8888-9999-0000-111111111111")
            ),
            localities = listOf(
                PincodeLocalityDto(id = "99999999-1111-2222-3333-444444444444", name = "Loc 1", district = "Gurugram")
            )
        )
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = validLocalityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "123456")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        val reqState = viewModel.state.value.pincodeUiState as PincodeUiState.RequiresDistrictChoice
        viewModel.selectDistrict(reqState.districts[0])

        // Since validLocalityUuid is stale for Gurugram, it MUST clear to null and NOT auto-select "99999999-1111-2222-3333-444444444444"
        assertNull(viewModel.state.value.input.localityId)
    }

    @Test
    fun `existing listing with no prior locality may auto-select one candidate`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = true,
            pincode = "560001",
            state = "Karnataka",
            district = "Bengaluru",
            cityId = validCityUuid,
            localities = listOf(
                PincodeLocalityDto(id = validLocalityUuid, name = "Koramangala", district = "Bengaluru")
            )
        )
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = null, // No prior locality
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        // Since prior locality was null and candidates has exactly 1 locality, auto-select is allowed
        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)
    }

    @Test
    fun `network failure preserves typed PIN`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(success = false, error = "NETWORK_ERROR")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("560001")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("560001", viewModel.state.value.input.pinCode)
        val errState = viewModel.state.value.pincodeUiState as PincodeUiState.Error
        assertEquals(PincodeLookupFailureReason.NETWORK_OR_FETCH_FAILURE, errState.reason)
    }

    @Test
    fun `new Save disabled until resolved`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSaveReady)

        viewModel.updatePinCode("560001")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isSaveReady)
    }

    @Test
    fun `existing unchanged location remains saveable on transient hydration failure`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(success = false, error = "NETWORK_ERROR")
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            localityId = validLocalityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isSaveReady)
    }

    @Test
    fun `changed PIN never reuses old city`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("123") // Typed partial PIN
        assertNull(viewModel.state.value.input.cityId)
        assertFalse(viewModel.state.value.isSaveReady)
    }

    @Test
    fun `landmark only edit remains saveable`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "draft"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateLandmark("Near Park")
        assertTrue(viewModel.state.value.isSaveReady)
    }

    @Test
    fun `location only edit does not show re review warning`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateLandmark("Updated Landmark")
        assertFalse(viewModel.state.value.requiresReReviewOnEdit)
    }

    @Test
    fun `material content edit does show re review warning`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.listings[validListingUuid] = SellerListingRowDto(
            id = validListingUuid,
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active"
        )
        gateway.privateLocations[validListingUuid] = SellerPrivateLocationDto(listingId = validListingUuid, pinCode = "560001")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = validListingUuid, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("Updated Title")
        assertTrue(viewModel.state.value.requiresReReviewOnEdit)
    }

    @Test
    fun `raw backend message never reaches UI state`() = runTest {
        val gateway = FakeEditorGateway()
        gateway.nextLookupResponse = PincodeLookupResponseDto(
            success = false,
            error = "INVALID_PINCODE",
            message = "RAW_SECRET_POSTGRES_INTERNAL_MESSAGE_12345"
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePinCode("000000")
        testDispatcher.scheduler.advanceUntilIdle()

        val errState = viewModel.state.value.pincodeUiState as PincodeUiState.Error
        assertEquals(PincodeLookupFailureReason.INVALID_PINCODE, errState.reason)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun `double save before scheduled coroutine work runs dispatches exactly one mutation`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("Double Save Test")
        viewModel.updateCategory(validUuid)
        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        // Call saveDraft twice synchronously
        viewModel.saveDraft(TestCreateRoute)
        viewModel.saveDraft(TestCreateRoute)

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, gateway.insertCallCount)
    }

    @Test
    fun `saveDraft captures tap-time input before subsequent user edits`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("Tap Time Title")
        viewModel.updateCategory(validUuid)
        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        // Tap Save Draft
        viewModel.saveDraft(TestCreateRoute)

        // Change input immediately before scheduled coroutine executes
        viewModel.updateTitle("Modified Title After Tap")

        testDispatcher.scheduler.advanceUntilIdle()

        // Dispatched insertion title must match the tap-time title "Tap Time Title"
        assertEquals("Tap Time Title", gateway.listings[validListingUuid]?.title)
    }

    @Test
    fun `unsaved changes block submission`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("New Laptop")
        viewModel.updateCategory(validUuid)
        viewModel.updatePinCode("123456")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isFormDirty)

        // Try submitting dirty form
        viewModel.submitListing(TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Save changes before submitting for review.", viewModel.state.value.errorMessage)
    }

    @Test
    fun `reselecting same category does not erase attributes`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        viewModel.updateCategory(validUuid)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateCategoryAttribute("brand", JsonPrimitive("Dell"))
        assertEquals("Dell", (viewModel.state.value.input.categoryAttributes["brand"] as? JsonPrimitive)?.content)

        // Reselect same category
        viewModel.updateCategory(validUuid)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Dell", (viewModel.state.value.input.categoryAttributes["brand"] as? JsonPrimitive)?.content)
    }

    @Test
    fun `opening dashboard and editor under same session does not rotate session generation`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val initialSession = repo.sessionOwner.updateSession("user-123")!!

        val dashboardVm = MyListingsViewModel(repo)
        dashboardVm.bind(initialSession)
        testDispatcher.scheduler.advanceUntilIdle()

        val editorVm = SellerListingEditorViewModel(repo)
        editorVm.bind(initialSession, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        // Shared session generation should remain stable
        assertEquals(initialSession.generation, repo.sessionOwner.currentSession()!!.generation)
    }

    @Test
    fun `repeated same session token refresh notifications preserve unsaved editor draft`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateTitle("Unsaved Draft Title")

        // Trigger same-session re-bind notification
        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        // Draft should be preserved!
        assertEquals("Unsaved Draft Title", viewModel.state.value.input.title)
    }

    @Test
    fun `bind with new session purges draft form inputs immediately`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(sessionA, listingId = null, entryId = TestCreateRoute)
        viewModel.updateTitle("Secret User A Draft")

        // Switch user to user-B
        gateway.userId = "user-B"
        val sessionB = repo.sessionOwner.updateSession("user-B")!!
        viewModel.bind(sessionB, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.state.value.input.title)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `bind with DIFFERENT entry ID does not inherit prior editor draft`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        // First editor entry
        viewModel.bind(session, listingId = null, entryId = "entry-1")
        viewModel.updateTitle("Draft in Entry 1")

        // Second editor entry for same user
        viewModel.bind(session, listingId = null, entryId = "entry-2")
        testDispatcher.scheduler.advanceUntilIdle()

        // Entry 2 should be fresh empty draft!
        assertEquals("", viewModel.state.value.input.title)
    }

    @Test
    fun `reselecting same city preserves existing localityId`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        viewModel.updateCity(validCityUuid)
        viewModel.updateLocality(validLocalityUuid)

        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)

        // Reselect same city
        viewModel.updateCity(validCityUuid)

        // localityId should remain validLocalityUuid
        assertEquals(validLocalityUuid, viewModel.state.value.input.localityId)
    }

    @Test
    fun `selecting different city clears old localityId immediately`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        viewModel.updateCity(validCityUuid)
        viewModel.updateLocality(validLocalityUuid)

        // Select different city
        viewModel.updateCity("77777777-8888-9999-0000-111111111111")

        // localityId should be cleared immediately
        assertNull(viewModel.state.value.input.localityId)
    }

    @Test
    fun `existing listing with categoriesEmpty renders in editor mode`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val state = SellerListingEditorUiState(
            session = session,
            listingId = validListingUuid,
            persistedListing = SellerListingDetail(
                id = validListingUuid,
                sellerId = "user-123",
                slug = "slug",
                categoryId = "cat-1",
                cityId = "city-1",
                localityId = null,
                title = "Existing Item",
                description = null,
                condition = null,
                priceType = "free",
                pricePaise = 0,
                formattedPrice = "Free",
                pinCode = null,
                landmark = null,
                categoryAttributes = JsonObject(emptyMap()),
                status = SellerListingStatus.ACTIVE,
                revision = 1,
                renewalCount = 0,
                rejectionReason = null,
                removalReason = null,
                coverPhotoId = null,
                readyMediaCount = 1,
                createdAt = "",
                updatedAt = "",
            ),
            isLoading = false,
            categoriesEmpty = true,
        )

        assertTrue(state.isEditing)
        assertFalse(state.categoriesEmpty && !state.isEditing)
    }

    @Test
    fun `category initialization failure sets terminal error and not categoriesEmpty`() = runTest {
        val gateway = FakeEditorGateway(categoryError = RuntimeException("SECRET_BACKEND_ERR"))
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.initializationError != null)
        assertFalse(state.categoriesEmpty)
        assertFalse(state.initializationError!!.contains("SECRET_BACKEND_ERR"))
    }

    @Test
    fun `city initialization failure sets terminal error and not citiesEmpty`() = runTest {
        val gateway = FakeEditorGateway(cityError = RuntimeException("SECRET_CITY_ERR"))
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.initializationError != null)
        assertFalse(state.citiesEmpty)
        assertFalse(state.initializationError!!.contains("SECRET_CITY_ERR"))
    }

    @Test
    fun `retry initialization succeeds after initial failure`() = runTest {
        val gateway = FakeEditorGateway(categoryError = RuntimeException("FAIL"))
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.initializationError != null)

        // Fix gateway error and retry
        gateway.categoryError = null
        viewModel.retryInitialization()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNull(state.initializationError)
        assertFalse(state.isLoading)
        assertEquals(1, state.categories.size)
        assertEquals(1, state.cities.size)
    }

    @Test
    fun `stale Category A response cannot overwrite Category B metadata`() = runTest {
        val catA = "11111111-2222-3333-4444-111111111111"
        val catB = "11111111-2222-3333-4444-222222222222"
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        // Configure checkpoint for Category A
        val attrAEntered = CompletableDeferred<Unit>()
        val attrARelease = CompletableDeferred<Unit>()
        gateway.fetchAttrEntered = attrAEntered
        gateway.fetchAttrRelease = attrARelease

        viewModel.updateCategory(catA)
        runCurrent()
        attrAEntered.await() // Cat-A request in flight!

        // Clear checkpoints for Cat-B
        gateway.fetchAttrEntered = null
        gateway.fetchAttrRelease = null

        // User selects Cat-B while Cat-A is in flight
        viewModel.updateCategory(catB)

        // Release Cat-A's lock AFTER updateCategory("cat-B")
        attrARelease.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state belongs to Cat-B
        assertEquals(catB, viewModel.state.value.input.categoryId)
        assertEquals("Brand B", viewModel.state.value.attributeMetadata.firstOrNull()?.label)
    }

    @Test
    fun `stale City A locality response cannot overwrite City B localities`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        // Configure checkpoint for City A
        val locAEntered = CompletableDeferred<Unit>()
        val locARelease = CompletableDeferred<Unit>()
        gateway.fetchLocalitiesEntered = locAEntered
        gateway.fetchLocalitiesRelease = locARelease

        viewModel.updateCity(validCityUuid)
        runCurrent()
        locAEntered.await() // City-A locality request in flight!

        // Clear checkpoints for City B
        gateway.fetchLocalitiesEntered = null
        gateway.fetchLocalitiesRelease = null

        viewModel.updateCity("77777777-8888-9999-0000-111111111111")

        // Release City A locality request
        locARelease.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify city B selected
        assertEquals("77777777-8888-9999-0000-111111111111", viewModel.state.value.input.cityId)
    }

    @Test
    fun `locality failure preserves cityId localityId and sets localityLoadFailed`() = runTest {
        val gateway = FakeEditorGateway()
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!
        val viewModel = SellerListingEditorViewModel(repo)

        viewModel.bind(session, listingId = null, entryId = TestCreateRoute)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateCity(validCityUuid)
        viewModel.updateLocality(validLocalityUuid)
        testDispatcher.scheduler.advanceUntilIdle()

        // Configure locality error and retry
        gateway.localityError = RuntimeException("LOCALITY_ERR")
        viewModel.retryLocalities()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(validCityUuid, state.input.cityId)
        assertEquals(validLocalityUuid, state.input.localityId)
        assertTrue(state.localityLoadFailed)

        // Retry successfully with localities
        gateway.localityError = null
        viewModel.retryLocalities()
        testDispatcher.scheduler.advanceUntilIdle()

        val okState = viewModel.state.value
        assertEquals(validCityUuid, okState.input.cityId)
        assertEquals(validLocalityUuid, okState.input.localityId) // Preserved as legacy selection!
        assertFalse(okState.localityLoadFailed)
    }

    @Test
    fun `formatUnavailableReferenceLabel returns active name when present and unavailable label without UUID when missing`() {
        val activeMap = mapOf("cat-1" to "Electronics")

        val activeResult = formatUnavailableReferenceLabel(
            selectedId = "cat-1",
            activeItemsNameMap = activeMap,
            defaultPlaceholder = "Select Category",
            typeLabel = "category",
        )
        assertEquals("Electronics", activeResult)

        val missingResult = formatUnavailableReferenceLabel(
            selectedId = "11111111-2222-3333-4444-555555555555",
            activeItemsNameMap = activeMap,
            defaultPlaceholder = "Select Category",
            typeLabel = "category",
        )
        assertEquals("Previously selected category (unavailable)", missingResult)
        assertFalse(missingResult.contains("11111111"))
    }
}
