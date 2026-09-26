package com.fairswap.core.favourites

import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicSellerResult
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesRepositoryTest {
    private val idA = "00000000-0000-0000-0000-000000000001"
    private val idB = "00000000-0000-0000-0000-000000000002"

    @Test fun signedOutAddAndRemoveDoNotMutate() = runTest {
        val gateway = FakeGateway().apply { user = null }
        val repository = repository(gateway)
        assertEquals(FavouriteMutationResult.AuthRequired, repository.setFavourite(idA, true))
        assertEquals(FavouriteMutationResult.AuthRequired, repository.setFavourite(idA, false))
        assertEquals(0, gateway.inserts + gateway.deletes)
    }

    @Test fun policyFalseOrFailureFailsClosed() = runTest {
        val gateway = FakeGateway().apply { accepted = false }
        assertEquals(FavouriteMutationResult.PolicyReviewRequired, repository(gateway).setFavourite(idA, true))
        assertEquals(0, gateway.inserts)
        gateway.policyError = true
        assertEquals(FavouriteMutationResult.Failure, repository(gateway).setFavourite(idA, false))
        assertEquals(0, gateway.deletes)
    }

    @Test fun invalidIdRejectedAndAddChecksVisibilityAndUsesSessionUser() = runTest {
        val gateway = FakeGateway().apply { visible = false }
        val repository = repository(gateway)
        assertEquals(FavouriteMutationResult.InvalidListing, repository.setFavourite("invalid", true))
        assertEquals(FavouriteMutationResult.ListingUnavailable, repository.setFavourite(idA, true))
        assertEquals(0, gateway.inserts)
        gateway.visible = true
        assertEquals(FavouriteMutationResult.Updated(true), repository.setFavourite(idA, true))
        assertEquals("user-a" to idA, gateway.inserted.single())
    }

    @Test fun desiredStateIsIdempotentAndRemoveDoesNotRequireVisibility() = runTest {
        val gateway = FakeGateway().apply { saved += idA; visible = true }
        val repository = repository(gateway)
        assertEquals(FavouriteMutationResult.Updated(true), repository.setFavourite(idA, true))
        assertEquals(1, gateway.insertAttempts)
        assertEquals(0, gateway.inserts)
        gateway.visible = false
        assertEquals(FavouriteMutationResult.Updated(false), repository.setFavourite(idB, false))
        assertEquals(1, gateway.deletes)
        assertEquals(FavouriteMutationResult.Updated(false), repository.setFavourite(idA, false))
        assertEquals(2, gateway.deletes)
    }

    @Test fun visibilityFailureEmitsOnlyUnavailableAndCanClearIt() = runTest {
        val gateway = FakeGateway().apply { saved += idA; visible = false }
        val repository = repository(gateway)
        val result = repository.loadPage(1) as FavouriteLoadResult.Success
        assertTrue(result.page.cards.isEmpty())
        assertEquals(setOf(idA), result.page.unavailableIds)
        assertEquals(FavouriteClearResult.Success(setOf(idA)), repository.clearUnavailable(setOf(idA)))
        assertEquals(listOf("user-a" to listOf(idA)), gateway.deleted)
    }

    @Test fun pageOrderingHasMoreAndSharedStateIsUserScoped() = runTest {
        val gateway = FakeGateway().apply {
            rows = (1..20).map { FavouriteRow("00000000-0000-0000-0000-${it.toString().padStart(12, '0')}") }
            visible = true
        }
        val store = FavouriteStateStore()
        gateway.saved += gateway.rows.map { it.listingId }
        val result = FavouritesRepository(gateway, FakePublicSource(gateway.rows.map { it.listingId }.toSet()), store)
            .loadPage(1) as FavouriteLoadResult.Success
        assertTrue(result.page.hasMore)
        assertEquals(20, result.page.cards.size)
        assertEquals("user-a", store.state.value.userId)
        gateway.user = "user-b"
        store.applyIfCurrent("user-a", store.beginSession("user-a"), listOf(idA), setOf(idA))
        store.reconcile("user-b")
        assertTrue(store.state.value.values.isEmpty())
        assertEquals("user-b", store.state.value.userId)
    }

    @Test fun duplicateRowsAreDeduplicatedAndUnavailableDoNotBlockMorePages() = runTest {
        val gateway = FakeGateway().apply {
            rows = listOf(FavouriteRow(idA), FavouriteRow(idA), FavouriteRow(idB))
            saved += setOf(idA, idB)
        }
        val source = FakePublicSource(setOf(idB))
        val result = FavouritesRepository(gateway, source, FavouriteStateStore(), pageSize = 3).loadPage(1) as FavouriteLoadResult.Success
        assertEquals(1, result.page.cards.size)
        assertEquals(setOf(idA), result.page.unavailableIds)
        assertTrue(result.page.hasMore)
    }

    @Test fun duplicateDesiredAddIsPlainInsertConflictSuccessWithoutUpdate() = runTest {
        assertTrue(isDuplicateFavouriteConflict("23505"))
        assertFalse(isDuplicateFavouriteConflict("42501"))
        val gateway = FakeGateway().apply { saved += idA }
        val repository = repository(gateway)
        assertEquals(FavouriteMutationResult.Updated(true), repository.setFavourite(idA, true))
        assertEquals(FavouriteMutationResult.Updated(true), repository.setFavourite(idA, true))
        assertEquals(2, gateway.insertAttempts)
        assertEquals(0, gateway.updatedRows)
        assertEquals(setOf(idA), gateway.saved)
    }

    @Test fun staleLookupFromAIsDiscardedAfterSessionChangesToB() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply {
            saved += idA
            onExisting = { entered.complete(Unit); release.await() }
        }
        val store = FavouriteStateStore()
        val repository = FavouritesRepository(gateway, FakePublicSource(setOf(idA)), store)
        val request = async { repository.lookup(listOf(idA)) }
        runCurrent()
        entered.await()
        gateway.user = "user-b"
        store.beginSession("user-b")
        release.complete(Unit)
        assertEquals(emptySet<String>(), request.await().getOrThrow())
        assertEquals("user-b", store.state.value.userId)
        assertTrue(store.state.value.values.isEmpty())
    }

    @Test fun generationDiscardsStaleResponseAfterAtoBtoASessionChurn() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply {
            saved += idA
            onExisting = { entered.complete(Unit); release.await() }
        }
        val store = FavouriteStateStore()
        val repository = FavouritesRepository(gateway, FakePublicSource(setOf(idA)), store)
        val request = async { repository.lookup(listOf(idA)) }
        runCurrent()
        entered.await()
        gateway.user = "user-b"; store.beginSession("user-b")
        gateway.user = "user-a"; store.beginSession("user-a")
        release.complete(Unit)
        assertEquals(emptySet<String>(), request.await().getOrThrow())
        assertEquals("user-a", store.state.value.userId)
        assertTrue(store.state.value.values.isEmpty())
    }

    @Test fun staleMutationCompletionCannotChangeNewUserHearts() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { onInsert = { entered.complete(Unit); release.await() } }
        val store = FavouriteStateStore()
        val repository = FavouritesRepository(gateway, FakePublicSource(emptySet()), store)
        val request = async { repository.setFavourite(idA, true) }
        runCurrent()
        entered.await()
        gateway.user = "user-b"
        store.beginSession("user-b")
        release.complete(Unit)
        assertEquals(FavouriteMutationResult.AuthRequired, request.await())
        assertEquals("user-b", store.state.value.userId)
        assertTrue(store.state.value.values.isEmpty())
        assertTrue(store.state.value.mutating.isEmpty())
    }

    @Test fun signOutClearsAllHeartsAndCancellationReleasesMutatingState() = runTest {
        val gateway = FakeGateway()
        val store = FavouriteStateStore()
        val repository = FavouritesRepository(gateway, FakePublicSource(emptySet()), store)
        val userId = gateway.currentUserId()!!
        val generation = store.beginSession(userId)
        store.applyIfCurrent(userId, generation, listOf(idB), setOf(idB))
        store.reconcile(null)
        assertTrue(store.state.value.values.isEmpty())
        assertEquals(null, store.state.value.userId)

        store.beginSession(userId)
        gateway.onInsert = { throw kotlinx.coroutines.CancellationException("cancelled") }
        try { repository.setFavourite(idA, true) } catch (_: kotlinx.coroutines.CancellationException) { }
        assertTrue(store.state.value.mutating.isEmpty())
    }

    @Test fun repositoryNeverRequestsMoreThanTwentyRows() = runTest {
        val gateway = FakeGateway().apply {
            rows = (1..25).map { FavouriteRow("00000000-0000-0000-0000-${it.toString().padStart(12, '0')}") }
            saved += rows.map { it.listingId }
        }
        val result = FavouritesRepository(gateway, FakePublicSource(emptySet()), FavouriteStateStore(), pageSize = 200)
            .loadPage(1) as FavouriteLoadResult.Success
        assertEquals(20, gateway.lastPageSize)
        assertEquals(20, result.page.unavailableCount)
        assertTrue(result.page.hasMore)
    }

    @Test fun sameListingConcurrentMutationIsRejected() = runTest {
        val gateway = FakeGateway().apply { suspendInsert = true }
        val repository = repository(gateway)
        val first = async { repository.setFavourite(idA, true) }
        kotlinx.coroutines.yield()
        val second = repository.setFavourite(idA, true)
        assertEquals(FavouriteMutationResult.Busy, second)
        gateway.releaseInsert = true
        assertEquals(FavouriteMutationResult.Updated(true), first.await())
    }

    private fun repository(gateway: FakeGateway) = FavouritesRepository(gateway, FakePublicSource(emptySet()), FavouriteStateStore())

    private class FakeGateway : FavouritesGateway {
        var user: String? = "user-a"
        var accepted = true
        var policyError = false
        var visible = true
        var saved = mutableSetOf<String>()
        var rows = listOf(FavouriteRow("00000000-0000-0000-0000-000000000001"))
        var inserts = 0
        var insertAttempts = 0
        var updatedRows = 0
        var deletes = 0
        var lastPageSize = 0
        var onExisting: suspend () -> Unit = {}
        var onInsert: suspend () -> Unit = {}
        val inserted = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<Pair<String, List<String>>>()
        var suspendInsert = false
        var releaseInsert = false
        override suspend fun currentUserId() = user
        override suspend fun policyAccepted(): Boolean { if (policyError) error("backend raw detail"); return accepted }
        override suspend fun isVisible(listingId: String) = visible
        override suspend fun page(userId: String, offset: Int, size: Int): List<FavouriteRow> {
            lastPageSize = size
            return rows.drop(offset).take(size)
        }
        override suspend fun existing(userId: String, listingIds: List<String>): Set<String> {
            onExisting()
            return saved.intersect(listingIds.toSet())
        }
        override suspend fun insert(userId: String, listingId: String) {
            insertAttempts++
            if (!saved.add(listingId)) return // Plain INSERT duplicate-PK normalization; no UPDATE.
            inserts++; inserted += userId to listingId
            if (suspendInsert) while (!releaseInsert) kotlinx.coroutines.yield()
            onInsert()
        }
        override suspend fun delete(userId: String, listingIds: List<String>) {
            deletes++; deleted += userId to listingIds; saved.removeAll(listingIds.toSet())
        }
    }

    private class FakePublicSource(private val ids: Set<String>) : PublicMarketplaceDataSource {
        override suspend fun listing(id: String) = PublicDetailResult.Unavailable
        override suspend fun seller(id: String) = PublicSellerResult.Unavailable
        override suspend fun publicCards(ids: List<String>): Map<String, ListingCard> = ids.filter { it in this.ids }
            .associateWith { ListingCard(it, "Public title", "₹1.00", "Good", "Cycles", "Jaipur", null) }
    }
}
