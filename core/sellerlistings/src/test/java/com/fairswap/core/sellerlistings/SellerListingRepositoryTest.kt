package com.fairswap.core.sellerlistings

import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class ControllableSellerListingGateway(
    var userId: String? = "user-A",
    var policyAccepted: Boolean = true,
    var errorToThrow: Throwable? = null,
) : SellerListingGateway {
    val listings = mutableMapOf<String, SellerListingRowDto>()
    val privateLocations = mutableMapOf<String, SellerPrivateLocationDto>()
    var insertCallCount = 0
    var updateCallCount = 0
    var submitCallCount = 0
    var transitionCallCount = 0
    var fetchPrivateLocationCallCount = 0

    var lastEnteredSellerId: String? = null

    // Checkpoint signals per test/request
    var fetchListingsEntered: CompletableDeferred<Unit>? = null
    var fetchListingsRelease: CompletableDeferred<Unit>? = null

    var fetchDetailEntered: CompletableDeferred<Unit>? = null
    var fetchDetailRelease: CompletableDeferred<Unit>? = null

    var fetchPrivateLocationEntered: CompletableDeferred<Unit>? = null
    var fetchPrivateLocationRelease: CompletableDeferred<Unit>? = null

    var readyMediaEntered: CompletableDeferred<Unit>? = null
    var readyMediaRelease: CompletableDeferred<Unit>? = null

    var policyEntered: CompletableDeferred<Unit>? = null
    var policyRelease: CompletableDeferred<Unit>? = null

    var updateEntered: CompletableDeferred<Unit>? = null
    var updateRelease: CompletableDeferred<Unit>? = null

    override suspend fun currentUserId(): String? = userId

    override suspend fun policyAccepted(): Boolean {
        policyEntered?.complete(Unit)
        policyRelease?.await()
        return policyAccepted
    }

    override suspend fun fetchCategories(): List<com.fairswap.core.discovery.Category> {
        errorToThrow?.let { throw it }
        return emptyList()
    }
    override suspend fun fetchCities(): List<com.fairswap.core.discovery.City> {
        errorToThrow?.let { throw it }
        return emptyList()
    }
    override suspend fun fetchLocalities(cityId: String): List<com.fairswap.core.discovery.Locality> {
        errorToThrow?.let { throw it }
        return emptyList()
    }

    override suspend fun fetchSellerListings(sellerId: String, limit: Int): List<SellerListingRowDto> {
        errorToThrow?.let { throw it }
        lastEnteredSellerId = sellerId
        fetchListingsEntered?.complete(Unit)
        fetchListingsRelease?.await()
        return listings.values.filter { it.sellerId == sellerId }
    }

    override suspend fun fetchReadyMediaCounts(listingIds: List<String>): Map<String, Int> {
        readyMediaEntered?.complete(Unit)
        readyMediaRelease?.await()
        return listingIds.associateWith { 1 }
    }

    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto? {
        errorToThrow?.let { throw it }
        lastEnteredSellerId = sellerId
        fetchDetailEntered?.complete(Unit)
        fetchDetailRelease?.await()
        return listings[listingId]?.takeIf { it.sellerId == sellerId }
    }

    override suspend fun fetchSellerPrivateLocation(listingId: String): SellerPrivateLocationDto? {
        errorToThrow?.let { throw it }
        fetchPrivateLocationCallCount++
        fetchPrivateLocationEntered?.complete(Unit)
        fetchPrivateLocationRelease?.await()
        return privateLocations[listingId]
    }

    override suspend fun fetchCategoryAttributes(categoryId: String): List<CategoryAttributeMetadata> {
        errorToThrow?.let { throw it }
        return listOf(
            CategoryAttributeMetadata(
                id = "attr-1",
                categoryId = categoryId,
                name = "brand",
                label = "Brand",
                valueType = "text",
                isRequired = false,
                options = emptyList(),
            )
        )
    }

    override suspend fun insertListing(insertDto: SellerListingInsertDto): SellerListingRowDto {
        errorToThrow?.let { throw it }
        insertCallCount++
        val current = userId ?: error("Signed out")
        val id = "22222222-3333-4444-5555-" + (listings.size + 1).toString().padStart(12, '0')
        val row = SellerListingRowDto(
            id = id,
            sellerId = current,
            title = insertDto.title,
            description = insertDto.description,
            categoryId = insertDto.categoryId,
            cityId = insertDto.cityId,
            localityId = insertDto.localityId,
            condition = insertDto.condition,
            priceType = insertDto.priceType,
            pricePaise = insertDto.pricePaise,
            categoryAttributes = insertDto.categoryAttributes,
            status = "draft",
            revision = 1,
            renewalCount = 0,
            createdAt = "2026-03-31T00:00:00Z",
            updatedAt = "2026-03-31T00:00:00Z",
        )
        listings[id] = row
        privateLocations[id] = SellerPrivateLocationDto(listingId = id, pinCode = insertDto.pinCode)
        return row
    }

    override suspend fun updateListing(
        listingId: String,
        sellerId: String,
        updateDto: SellerListingUpdateDto,
    ): SellerListingRowDto {
        errorToThrow?.let { throw it }
        updateCallCount++
        updateEntered?.complete(Unit)
        updateRelease?.await()
        val existing = listings[listingId] ?: error("Not found")
        val updatedStatus = when (existing.status) {
            "active", "reserved", "paused" -> "pending_review"
            "rejected" -> "draft"
            else -> existing.status
        }
        val updated = existing.copy(
            title = updateDto.title,
            description = updateDto.description,
            categoryId = updateDto.categoryId,
            cityId = updateDto.cityId,
            localityId = updateDto.localityId,
            condition = updateDto.condition,
            priceType = updateDto.priceType,
            pricePaise = updateDto.pricePaise,
            categoryAttributes = updateDto.categoryAttributes,
            status = updatedStatus,
            revision = existing.revision + 1,
            rejectionReason = if (existing.status == "rejected") null else existing.rejectionReason,
        )
        listings[listingId] = updated
        privateLocations[listingId] = SellerPrivateLocationDto(listingId = listingId, pinCode = updateDto.pinCode)
        return updated
    }

    override suspend fun submitListing(listingId: String) {
        errorToThrow?.let { throw it }
        submitCallCount++
        val existing = listings[listingId] ?: error("Not found")
        listings[listingId] = existing.copy(status = "pending_review")
    }

    override suspend fun transitionListing(listingId: String, action: String) {
        errorToThrow?.let { throw it }
        transitionCallCount++
        val existing = listings[listingId] ?: error("Not found")
        val newStatus = when (action) {
            "pause" -> "paused"
            "resume" -> "active"
            "reserve" -> "reserved"
            "unreserve" -> "active"
            "mark_sold" -> "sold"
            "renew" -> "pending_review"
            else -> existing.status
        }
        val newRenewalCount = if (action == "renew") existing.renewalCount + 1 else existing.renewalCount
        listings[listingId] = existing.copy(status = newStatus, renewalCount = newRenewalCount)
    }
}

class SellerListingRepositoryTest {

    private val validUuid = "11111111-2222-3333-4444-555555555555"
    private val validCityUuid = "66666666-7777-8888-9999-000000000000"

    @Test
    fun `SellerSessionOwner lifecycle A to A keeps gen, A to null to A creates new gen, A to B creates new gen`() {
        val owner = SellerSessionOwner()
        val sessionA1 = owner.updateSession("user-A")!!
        val sessionA2 = owner.updateSession("user-A")!!

        // A -> A preserves generation
        assertEquals(sessionA1, sessionA2)
        assertEquals(1L, sessionA2.generation)

        // A -> null -> A creates new generation
        owner.updateSession(null)
        val sessionA3 = owner.updateSession("user-A")!!
        assertNotEquals(sessionA1.generation, sessionA3.generation)
        assertEquals(2L, sessionA3.generation)

        // A -> B creates new generation
        val sessionB = owner.updateSession("user-B")!!
        assertNotEquals(sessionA3.generation, sessionB.generation)
        assertEquals(3L, sessionB.generation)
    }

    @Test
    fun `loadCategories returns Success emptyList when gateway returns empty list`() = runTest {
        val gateway = ControllableSellerListingGateway()
        val repo = SellerListingRepository(gateway)
        val res = repo.loadCategories()
        assertTrue(res is ReferenceDataResult.Success)
        assertEquals(emptyList<com.fairswap.core.discovery.Category>(), (res as ReferenceDataResult.Success).value)
    }

    @Test
    fun `loadCategories returns Failure on exception without leaking raw error`() = runTest {
        val secretError = "SECRET_CATEGORIES_ERROR_999"
        val gateway = ControllableSellerListingGateway(errorToThrow = RuntimeException(secretError))
        val repo = SellerListingRepository(gateway)
        val res = repo.loadCategories()
        assertTrue(res is ReferenceDataResult.Failure)
        val msg = (res as ReferenceDataResult.Failure).message
        assertFalse(msg.contains(secretError))
    }

    @Test
    fun `loadCities returns Failure on exception without leaking raw error`() = runTest {
        val secretError = "SECRET_CITIES_ERROR_999"
        val gateway = ControllableSellerListingGateway(errorToThrow = RuntimeException(secretError))
        val repo = SellerListingRepository(gateway)
        val res = repo.loadCities()
        assertTrue(res is ReferenceDataResult.Failure)
        val msg = (res as ReferenceDataResult.Failure).message
        assertFalse(msg.contains(secretError))
    }

    @Test
    fun `loadLocalities returns Failure on exception without leaking raw error`() = runTest {
        val secretError = "SECRET_LOCALITIES_ERROR_999"
        val gateway = ControllableSellerListingGateway(errorToThrow = RuntimeException(secretError))
        val repo = SellerListingRepository(gateway)
        val res = repo.loadLocalities(validCityUuid)
        assertTrue(res is ReferenceDataResult.Failure)
        val msg = (res as ReferenceDataResult.Failure).message
        assertFalse(msg.contains(secretError))
    }

    @Test
    fun `suppresses raw exception text from backend errors`() = runTest {
        val secretBackendError = "DISTINCTIVE_BACKEND_SECRET_ERROR_12345"
        val gateway = ControllableSellerListingGateway(
            userId = "user-123",
            errorToThrow = RuntimeException(secretBackendError),
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val loadResult = repo.loadDashboard(session)
        assertTrue(loadResult is SellerDashboardResult.Failure)
        val msg = (loadResult as SellerDashboardResult.Failure).message
        assertFalse("Raw exception text leaked!", msg.contains(secretBackendError))
        assertEquals("Unable to load seller listings. Please try again.", msg)
    }

    @Test
    fun `canRenderSellerState rejects state when live user ID differs from active session`() {
        val sessionA = SellerSession("user-A", 1L)
        assertFalse(
            canRenderSellerState(
                stateSession = sessionA,
                activeSession = sessionA,
                liveUserId = "user-B",
            )
        )
    }

    @Test
    fun `stale repository request capture cannot overwrite authoritative session owner`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-B")
        val repo = SellerListingRepository(gateway)
        val sessionB = repo.sessionOwner.updateSession("user-B")!!

        // Gateway user becomes user-A (stale mismatch)
        gateway.userId = "user-A"
        val loadRes = repo.loadDashboard(sessionB)

        // Authoritative session owner must remain user-B
        assertEquals(sessionB, repo.sessionOwner.currentSession())
        assertTrue("Expected AuthRequired but got $loadRes", loadRes is SellerDashboardResult.AuthRequired)
    }

    @Test
    fun `repository called while SellerSessionOwner is null returns AuthRequired and does not create session`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        val repo = SellerListingRepository(gateway)
        val sessionA = SellerSession("user-A", 1L)

        val loadRes = repo.loadDashboard(sessionA)

        assertNull(repo.sessionOwner.currentSession())
        assertTrue("Expected AuthRequired when sessionOwner is null but got $loadRes", loadRes is SellerDashboardResult.AuthRequired)
    }

    @Test
    fun `decodes CategoryAttributeDto with null options safely`() {
        val dto = CategoryAttributeDto(
            id = "attr-1",
            categoryId = validUuid,
            name = "color",
            label = "Color",
            valueType = "text",
            isRequired = false,
            options = null,
        )
        val domain = dto.toDomain()
        assertEquals(emptyList<String>(), domain.options)
    }

    @Test
    fun `sequential reads for same user preserve session generation stability`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val dashboardResult = repo.loadDashboard(session)
        val detailResult = repo.loadDetail("22222222-3333-4444-5555-000000000001", session)

        assertTrue("Dashboard failed: $dashboardResult", dashboardResult is SellerDashboardResult.Success)
        assertTrue("Detail failed: $detailResult", detailResult is SellerDetailResult.Success)
        assertEquals(session, repo.getCurrentSession())
    }

    @Test
    fun `overlapping dashboard and detail reads for same session succeed concurrently`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        gateway.fetchListingsEntered = CompletableDeferred()
        gateway.fetchListingsRelease = CompletableDeferred()
        gateway.fetchDetailEntered = CompletableDeferred()
        gateway.fetchDetailRelease = CompletableDeferred()

        try {
            val asyncDash = async { repo.loadDashboard(session) }
            runCurrent()
            gateway.fetchListingsEntered!!.await()

            val asyncDetail = async { repo.loadDetail("22222222-3333-4444-5555-000000000001", session) }
            runCurrent()
            gateway.fetchDetailEntered!!.await()

            assertFalse(asyncDash.isCompleted)
            assertFalse(asyncDetail.isCompleted)

            gateway.fetchListingsRelease!!.complete(Unit)
            gateway.fetchDetailRelease!!.complete(Unit)

            val dashRes = asyncDash.await()
            val detailRes = asyncDetail.await()

            assertTrue("Dashboard failed: $dashRes", dashRes is SellerDashboardResult.Success)
            assertTrue("Detail failed: $detailRes", detailRes is SellerDetailResult.Success)
            assertEquals(session, repo.getCurrentSession())
        } finally {
            gateway.fetchListingsRelease?.complete(Unit)
            gateway.fetchDetailRelease?.complete(Unit)
        }
    }

    @Test
    fun `start A load, suspend, switch to B, release A - result is AuthRequired`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-A",
            title = "User A Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.fetchListingsEntered = CompletableDeferred()
        gateway.fetchListingsRelease = CompletableDeferred()

        try {
            val asyncLoad = async { repo.loadDashboard(sessionA) }
            runCurrent()
            gateway.fetchListingsEntered!!.await()

            assertEquals("user-A", gateway.lastEnteredSellerId)
            assertFalse(asyncLoad.isCompleted)

            // Switch session to user-B while load is suspended
            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            // Release suspended load
            gateway.fetchListingsRelease!!.complete(Unit)

            val result = asyncLoad.await()
            assertTrue("Expected AuthRequired but got: $result", result is SellerDashboardResult.AuthRequired)
        } finally {
            gateway.fetchListingsRelease?.complete(Unit)
        }
    }

    @Test
    fun `session change while policy check suspended prevents write dispatch`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.policyEntered = CompletableDeferred()
        gateway.policyRelease = CompletableDeferred()

        try {
            val asyncCreate = async {
                repo.createDraft(
                    SellerListingInput(
                        title = "Pending Draft",
                        categoryId = validUuid,
                        cityId = validCityUuid,
                    ),
                    sessionA,
                )
            }
            runCurrent()
            gateway.policyEntered!!.await()

            assertEquals(0, gateway.insertCallCount)

            // Switch user before policy check resumes
            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            gateway.policyRelease!!.complete(Unit)

            val result = asyncCreate.await()
            assertTrue("Expected AuthRequired but got: $result", result is SellerMutationResult.AuthRequired)
            assertEquals(0, gateway.insertCallCount)
        } finally {
            gateway.policyRelease?.complete(Unit)
        }
    }

    @Test
    fun `saveEdit suspended after owner detail fetch prevents updateListing dispatch on session switch`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-A",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.fetchDetailEntered = CompletableDeferred()
        gateway.fetchDetailRelease = CompletableDeferred()

        try {
            val asyncSave = async {
                repo.saveEdit(
                    "22222222-3333-4444-5555-000000000001",
                    SellerListingInput(title = "Updated Title", categoryId = validUuid, cityId = validCityUuid),
                    sessionA,
                )
            }
            runCurrent()
            gateway.fetchDetailEntered!!.await()

            assertEquals(0, gateway.updateCallCount)

            // Switch user while fetchDetail is suspended
            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            gateway.fetchDetailRelease!!.complete(Unit)

            val res = asyncSave.await()
            assertTrue("Expected AuthRequired but got $res", res is SellerMutationResult.AuthRequired)
            assertEquals(0, gateway.updateCallCount)
        } finally {
            gateway.fetchDetailRelease?.complete(Unit)
        }
    }

    @Test
    fun `submit suspended after media fetch prevents submit rpc dispatch on session switch`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-A",
            title = "Item 1",
            description = "A valid detailed item description over 10 chars",
            condition = "like_new",
            categoryId = validUuid,
            cityId = validCityUuid,
            priceType = "free",
            pricePaise = 0,
            status = "draft",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.readyMediaEntered = CompletableDeferred()
        gateway.readyMediaRelease = CompletableDeferred()

        try {
            val asyncSubmit = async {
                repo.submitListing(
                    "22222222-3333-4444-5555-000000000001",
                    emptyList(),
                    sessionA,
                )
            }
            runCurrent()
            gateway.readyMediaEntered!!.await()

            assertEquals(0, gateway.submitCallCount)

            // Switch user while readyMedia fetch is suspended
            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            gateway.readyMediaRelease!!.complete(Unit)

            val res = asyncSubmit.await()
            assertTrue("Expected AuthRequired but got $res", res is SellerMutationResult.AuthRequired)
            assertEquals(0, gateway.submitCallCount)
        } finally {
            gateway.readyMediaRelease?.complete(Unit)
        }
    }

    @Test
    fun `lifecycle suspended after detail fetch prevents transition rpc dispatch on session switch`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-A",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.fetchDetailEntered = CompletableDeferred()
        gateway.fetchDetailRelease = CompletableDeferred()

        try {
            val asyncAction = async {
                repo.executeLifecycleAction(
                    "22222222-3333-4444-5555-000000000001",
                    SellerLifecycleAction.PAUSE,
                    sessionA,
                )
            }
            runCurrent()
            gateway.fetchDetailEntered!!.await()

            assertEquals(0, gateway.transitionCallCount)

            // Switch user while fetchDetail is suspended
            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            gateway.fetchDetailRelease!!.complete(Unit)

            val res = asyncAction.await()
            assertTrue("Expected AuthRequired but got $res", res is SellerMutationResult.AuthRequired)
            assertEquals(0, gateway.transitionCallCount)
        } finally {
            gateway.fetchDetailRelease?.complete(Unit)
        }
    }

    @Test
    fun `overlapping save and lifecycle work on same listing are rejected by repository lock`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        gateway.updateEntered = CompletableDeferred()
        gateway.updateRelease = CompletableDeferred()

        try {
            val asyncSave = async {
                repo.saveEdit(
                    "22222222-3333-4444-5555-000000000001",
                    SellerListingInput(title = "Item 1 Updated", categoryId = validUuid, cityId = validCityUuid),
                    session,
                )
            }
            runCurrent()
            gateway.updateEntered!!.await()

            assertFalse(asyncSave.isCompleted)

            // Try concurrent lifecycle action on the same listing while save is suspended
            val concurrentActionRes = repo.executeLifecycleAction(
                "22222222-3333-4444-5555-000000000001",
                SellerLifecycleAction.PAUSE,
                session,
            )

            assertTrue(concurrentActionRes is SellerMutationResult.Failure)
            val msg = (concurrentActionRes as SellerMutationResult.Failure).message
            assertTrue(msg.contains("already in progress"))
            assertEquals(0, gateway.transitionCallCount)

            gateway.updateRelease!!.complete(Unit)
            asyncSave.await()
        } finally {
            gateway.updateRelease?.complete(Unit)
        }
    }

    @Test
    fun `old token cleanup cannot release lock acquired by newer operation`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val tokenA = repo.acquireMutationLock("22222222-3333-4444-5555-000000000001", session)
        assertTrue(tokenA != null)

        // Release tokenA
        repo.releaseMutationLock(tokenA)

        // TokenB acquires lock for same listing
        val tokenB = repo.acquireMutationLock("22222222-3333-4444-5555-000000000001", session)
        assertTrue(tokenB != null)

        // Attempt late duplicate release of tokenA
        repo.releaseMutationLock(tokenA)

        // TokenB should still hold the lock, so a new lock acquisition must be rejected
        val tokenC = repo.acquireMutationLock("22222222-3333-4444-5555-000000000001", session)
        assertEquals(null, tokenC)

        repo.releaseMutationLock(tokenB)
    }

    @Test
    fun `session change notification does not clear executing operation lock`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Item 1",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session123 = repo.sessionOwner.updateSession("user-123")!!

        gateway.updateEntered = CompletableDeferred()
        gateway.updateRelease = CompletableDeferred()

        try {
            val asyncSave = async {
                repo.saveEdit(
                    "22222222-3333-4444-5555-000000000001",
                    SellerListingInput(title = "Item 1 Updated", categoryId = validUuid, cityId = validCityUuid),
                    session123,
                )
            }
            runCurrent()
            gateway.updateEntered!!.await()

            // Session notification arrives while operation is executing
            gateway.userId = "user-456"
            val session456 = repo.sessionOwner.updateSession("user-456")!!

            // Attempt another operation for the same listing while first operation is still executing
            val concurrentActionRes = repo.executeLifecycleAction(
                "22222222-3333-4444-5555-000000000001",
                SellerLifecycleAction.PAUSE,
                session456,
            )

            assertTrue("Concurrent operation should be rejected as busy, got: $concurrentActionRes", concurrentActionRes is SellerMutationResult.Failure)
            val msg = (concurrentActionRes as SellerMutationResult.Failure).message
            assertTrue(msg.contains("already in progress"))

            gateway.updateRelease!!.complete(Unit)
            asyncSave.await()
        } finally {
            gateway.updateRelease?.complete(Unit)
        }
    }

    @Test
    fun `exception releases only target listing lock leaving other listing locks intact`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Item X",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val tokenY = repo.acquireMutationLock("22222222-3333-4444-5555-000000000002", session)
        assertTrue(tokenY != null)

        gateway.errorToThrow = RuntimeException("Failing mutation")
        val resX = repo.executeLifecycleAction("22222222-3333-4444-5555-000000000001", SellerLifecycleAction.PAUSE, session)
        assertTrue(resX is SellerMutationResult.Failure)

        // Lock for X should be released after exception
        val tokenX2 = repo.acquireMutationLock("22222222-3333-4444-5555-000000000001", session)
        assertTrue(tokenX2 != null)

        // Lock for Y should still be held by tokenY
        val tokenY2 = repo.acquireMutationLock("22222222-3333-4444-5555-000000000002", session)
        assertEquals(null, tokenY2)

        repo.releaseMutationLock(tokenX2)
        repo.releaseMutationLock(tokenY)
    }

    @Test
    fun `independent mutations for listing X and listing Y can coexist`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val tokenX = repo.acquireMutationLock("22222222-3333-4444-5555-000000000001", session)
        val tokenY = repo.acquireMutationLock("22222222-3333-4444-5555-000000000002", session)

        assertTrue(tokenX != null)
        assertTrue(tokenY != null)
        assertNotEquals(tokenX?.listingId, tokenY?.listingId)

        repo.releaseMutationLock(tokenX)
        repo.releaseMutationLock(tokenY)
    }

    @Test
    fun `loadDashboard succeeds without private location fields and does not fetch private location RPC`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val result = repo.loadDashboard(session)
        assertTrue(result is SellerDashboardResult.Success)
        val summaries = (result as SellerDashboardResult.Success).listings
        assertEquals(1, summaries.size)
        assertEquals("Phone", summaries[0].title)
        assertEquals(0, gateway.fetchPrivateLocationCallCount)
    }

    @Test
    fun `loadDetail calls fetchSellerPrivateLocation and includes pinCode in SellerListingDetail`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        gateway.privateLocations["22222222-3333-4444-5555-000000000001"] = SellerPrivateLocationDto(
            listingId = "22222222-3333-4444-5555-000000000001",
            pinCode = "302001",
            landmark = "Near City Palace",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val result = repo.loadDetail("22222222-3333-4444-5555-000000000001", session)
        assertTrue(result is SellerDetailResult.Success)
        val detail = (result as SellerDetailResult.Success).listing
        assertEquals("302001", detail.pinCode)
        assertEquals(1, gateway.fetchPrivateLocationCallCount)
    }

    @Test
    fun `loadDetail handles null or empty private location safely`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-123",
            title = "Phone",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val result = repo.loadDetail("22222222-3333-4444-5555-000000000001", session)
        assertTrue(result is SellerDetailResult.Success)
        val detail = (result as SellerDetailResult.Success).listing
        assertNull(detail.pinCode)
        assertEquals(1, gateway.fetchPrivateLocationCallCount)
    }

    @Test
    fun `loadDetail fails closed if session changes during private location fetch`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-A")
        gateway.listings["22222222-3333-4444-5555-000000000001"] = SellerListingRowDto(
            id = "22222222-3333-4444-5555-000000000001",
            sellerId = "user-A",
            title = "Item A",
            categoryId = validUuid,
            cityId = validCityUuid,
            status = "active",
        )
        val repo = SellerListingRepository(gateway)
        val sessionA = repo.sessionOwner.updateSession("user-A")!!

        gateway.fetchPrivateLocationEntered = CompletableDeferred()
        gateway.fetchPrivateLocationRelease = CompletableDeferred()

        try {
            val asyncLoad = async { repo.loadDetail("22222222-3333-4444-5555-000000000001", sessionA) }
            runCurrent()
            gateway.fetchPrivateLocationEntered!!.await()

            repo.sessionOwner.updateSession("user-B")
            gateway.userId = "user-B"

            gateway.fetchPrivateLocationRelease!!.complete(Unit)

            val res = asyncLoad.await()
            assertTrue("Expected AuthRequired but got $res", res is SellerDetailResult.AuthRequired)
        } finally {
            gateway.fetchPrivateLocationRelease?.complete(Unit)
        }
    }

    @Test
    fun `createDraft fetches private location via RPC and includes returned pinCode in detail`() = runTest {
        val gateway = ControllableSellerListingGateway(userId = "user-123")
        val repo = SellerListingRepository(gateway)
        val session = repo.sessionOwner.updateSession("user-123")!!

        val input = SellerListingInput(
            title = "New Guitar Draft",
            categoryId = validUuid,
            cityId = validCityUuid,
            pinCode = "302002",
        )

        val result = repo.createDraft(input, session)
        assertTrue(result is SellerMutationResult.Success)
        val detail = (result as SellerMutationResult.Success).value
        assertEquals("302002", detail.pinCode)
        assertEquals(1, gateway.insertCallCount)
        assertEquals(1, gateway.fetchPrivateLocationCallCount)
    }

    @Test
    fun `sellerListingSafeColumns projection does not contain pin_code or landmark`() {
        val gateway = SupabaseSellerListingGateway(
            createSupabaseClient("https://example.supabase.co", "anon") {}
        )
        val projectionText = gateway.sellerListingSafeColumns.value
        assertFalse("Projection text must not contain pin_code: $projectionText", projectionText.contains("pin_code"))
        assertFalse("Projection text must not contain landmark: $projectionText", projectionText.contains("landmark"))
    }
}
