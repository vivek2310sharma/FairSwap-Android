package com.fairswap.core.favourites

import com.fairswap.core.discovery.ListingCard
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.validPublicId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException

sealed interface FavouriteMutationResult {
    data class Updated(val saved: Boolean) : FavouriteMutationResult
    data object AuthRequired : FavouriteMutationResult
    data object PolicyReviewRequired : FavouriteMutationResult
    data object ListingUnavailable : FavouriteMutationResult
    data object InvalidListing : FavouriteMutationResult
    data object Busy : FavouriteMutationResult
    data object Failure : FavouriteMutationResult
}

data class FavouritePage(
    val cards: List<ListingCard>,
    val unavailableIds: Set<String>,
    val unavailableCount: Int,
    val hasMore: Boolean,
    val page: Int,
)

sealed interface FavouriteLoadResult {
    data class Success(val page: FavouritePage) : FavouriteLoadResult
    data object AuthRequired : FavouriteLoadResult
    data object Failure : FavouriteLoadResult
}

sealed interface FavouriteClearResult {
    data class Success(val deletedIds: Set<String>) : FavouriteClearResult
    data object AuthRequired : FavouriteClearResult
    data object PolicyReviewRequired : FavouriteClearResult
    data object Failure : FavouriteClearResult
}

@Serializable
data class FavouriteRow(@SerialName("listing_id") val listingId: String, @SerialName("created_at") val createdAt: String = "")

@Serializable
private data class FavouriteInsert(@SerialName("user_id") val userId: String, @SerialName("listing_id") val listingId: String)

@Serializable
private data class ListingVisibilityParams(@SerialName("listing_id") val listingId: String)

interface FavouritesGateway {
    suspend fun currentUserId(): String?
    suspend fun policyAccepted(): Boolean
    suspend fun isVisible(listingId: String): Boolean
    suspend fun page(userId: String, offset: Int, size: Int): List<FavouriteRow>
    suspend fun existing(userId: String, listingIds: List<String>): Set<String>
    suspend fun insert(userId: String, listingId: String)
    suspend fun delete(userId: String, listingIds: List<String>)
}

class SupabaseFavouritesGateway(private val supabase: SupabaseClient) : FavouritesGateway {
    override suspend fun currentUserId(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull()?.user?.id
    }

    override suspend fun policyAccepted(): Boolean = supabase.postgrest.rpc("has_current_policy_acceptance").decodeAs()

    override suspend fun isVisible(listingId: String): Boolean = supabase.postgrest
        .rpc("is_listing_publicly_visible", ListingVisibilityParams(listingId)).decodeAs()

    override suspend fun page(userId: String, offset: Int, size: Int): List<FavouriteRow> = supabase.from("favourites")
        .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("listing_id,created_at")) {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            range(offset.toLong(), (offset + size - 1).toLong())
        }.decodeList()

    override suspend fun existing(userId: String, listingIds: List<String>): Set<String> = if (listingIds.isEmpty()) emptySet() else
        supabase.from("favourites").select(columns = io.github.jan.supabase.postgrest.query.Columns.list("listing_id")) {
            filter { eq("user_id", userId); isIn("listing_id", listingIds) }
        }.decodeList<FavouriteRow>().mapTo(mutableSetOf()) { it.listingId }

    override suspend fun insert(userId: String, listingId: String) {
        try {
            // INSERT only: do not use upsert because authenticated lacks UPDATE on favourites.
            supabase.from("favourites").insert(FavouriteInsert(userId, listingId))
        } catch (duplicate: PostgrestRestException) {
            // The composite primary key makes an already-saved desired=true operation successful.
            if (!isDuplicateFavouriteConflict(duplicate.code)) throw duplicate
        }
    }

    override suspend fun delete(userId: String, listingIds: List<String>) {
        if (listingIds.isNotEmpty()) supabase.from("favourites").delete {
            filter { eq("user_id", userId); isIn("listing_id", listingIds) }
        }
    }
}

internal fun isDuplicateFavouriteConflict(postgresCode: String?) = postgresCode == "23505"

class FavouritesRepository(
    private val gateway: FavouritesGateway,
    private val publicListings: PublicMarketplaceDataSource,
    private val stateStore: FavouriteStateStore,
    pageSize: Int = 20,
) {
    private val pageSize = pageSize.coerceIn(1, 20)
    private val mutationLocks = mutableMapOf<String, Mutex>()
    private val unavailableBySession = mutableMapOf<FavouriteSession, MutableSet<String>>()

    private data class FavouriteSession(val userId: String, val generation: Long)

    suspend fun setFavourite(listingId: String, desired: Boolean): FavouriteMutationResult {
        if (!validPublicId(listingId)) return FavouriteMutationResult.InvalidListing
        val session = resolveSession() ?: return FavouriteMutationResult.AuthRequired
        val userId = session.userId
        val lock = synchronized(mutationLocks) { mutationLocks.getOrPut(listingId) { Mutex() } }
        if (!lock.tryLock()) return FavouriteMutationResult.Busy
        stateStore.markMutating(userId, session.generation, listingId, true)
        return try {
            withTimeout(15_000) {
                if (!gateway.policyAccepted()) return@withTimeout FavouriteMutationResult.PolicyReviewRequired
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteMutationResult.AuthRequired
                if (desired && !gateway.isVisible(listingId)) return@withTimeout FavouriteMutationResult.ListingUnavailable
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteMutationResult.AuthRequired
                if (desired) gateway.insert(userId, listingId)
                else gateway.delete(userId, listOf(listingId))
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteMutationResult.AuthRequired
                stateStore.set(userId, session.generation, listingId, desired)
                FavouriteMutationResult.Updated(desired)
            }
        } catch (_: TimeoutCancellationException) {
            FavouriteMutationResult.Failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FavouriteMutationResult.Failure
        } finally {
            stateStore.markMutating(userId, session.generation, listingId, false)
            lock.unlock()
        }
    }

    suspend fun loadPage(page: Int): FavouriteLoadResult {
        val session = resolveSession() ?: return FavouriteLoadResult.AuthRequired
        val userId = session.userId
        if (page < 1) return FavouriteLoadResult.Failure
        return try {
            withTimeout(15_000) {
                val rows = gateway.page(userId, (page - 1) * pageSize, pageSize)
                val ids = rows.map { it.listingId }.filter(::validPublicId).distinct()
                val cards = publicListings.publicCards(ids)
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteLoadResult.AuthRequired
                val existing = gateway.existing(userId, ids)
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteLoadResult.AuthRequired
                val stillSaved = ids.filter { it in existing }
                if (!stateStore.applyIfCurrent(userId, session.generation, ids, existing)) {
                    return@withTimeout FavouriteLoadResult.AuthRequired
                }
                synchronized(unavailableBySession) {
                    unavailableBySession.getOrPut(session) { mutableSetOf() }.addAll(stillSaved.filterNot(cards::containsKey))
                    unavailableBySession.keys.removeAll { it != session }
                }
                FavouriteLoadResult.Success(FavouritePage(
                    cards = stillSaved.mapNotNull(cards::get),
                    unavailableIds = stillSaved.filterNot(cards::containsKey).toSet(),
                    unavailableCount = stillSaved.count { it !in cards },
                    hasMore = rows.size == pageSize,
                    page = page,
                ))
            }
        } catch (_: TimeoutCancellationException) {
            FavouriteLoadResult.Failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FavouriteLoadResult.Failure
        }
    }

    suspend fun lookup(ids: List<String>): Result<Set<String>> {
        val session = resolveSession() ?: return Result.success(emptySet())
        val userId = session.userId
        val validIds = ids.distinct().filter(::validPublicId).take(50)
        return try {
            withTimeout(15_000) {
                val saved = gateway.existing(userId, validIds)
                if (!matchesCurrentSession(session)) {
                    return@withTimeout Result.success(emptySet())
                }
                if (!stateStore.applyIfCurrent(userId, session.generation, validIds, saved)) {
                    return@withTimeout Result.success(emptySet())
                }
                Result.success(saved)
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Unable to sync favourites."))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to sync favourites."))
        }
    }

    suspend fun clearUnavailable(ids: Set<String>): FavouriteClearResult {
        val session = resolveSession() ?: return FavouriteClearResult.AuthRequired
        val userId = session.userId
        val owned = synchronized(unavailableBySession) {
            unavailableBySession[session].orEmpty().intersect(ids).take(20).toSet()
        }
        if (owned.isEmpty()) return FavouriteClearResult.Success(emptySet())
        return try {
            withTimeout(15_000) {
                if (!gateway.policyAccepted()) return@withTimeout FavouriteClearResult.PolicyReviewRequired
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteClearResult.AuthRequired
                gateway.delete(userId, owned.toList())
                if (!matchesCurrentSession(session)) return@withTimeout FavouriteClearResult.AuthRequired
                synchronized(unavailableBySession) { unavailableBySession[session]?.removeAll(owned) }
                owned.forEach { stateStore.set(userId, session.generation, it, false) }
                FavouriteClearResult.Success(owned)
            }
        } catch (_: TimeoutCancellationException) {
            FavouriteClearResult.Failure
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FavouriteClearResult.Failure
        }
    }

    private suspend fun resolveSession(): FavouriteSession? {
        val userId = gateway.currentUserId()
        val generation = stateStore.beginSession(userId)
        val session = userId?.let { FavouriteSession(it, generation) }
        synchronized(unavailableBySession) { unavailableBySession.keys.removeAll { it != session } }
        return session
    }

    private suspend fun matchesCurrentSession(expected: FavouriteSession): Boolean {
        val actualUserId = gateway.currentUserId()
        val actualGeneration = stateStore.beginSession(actualUserId)
        return actualUserId == expected.userId && actualGeneration == expected.generation
    }
}
