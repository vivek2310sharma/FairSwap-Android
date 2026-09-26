package com.fairswap.core.favourites

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FavouriteValue { UNKNOWN, NOT_FAVOURITED, FAVOURITED }

data class FavouriteSnapshot(
    val userId: String?,
    val values: Map<String, FavouriteValue>,
    val mutating: Set<String>,
    val generation: Long,
)

class FavouriteStateStore {
    private val mutableState = MutableStateFlow(FavouriteSnapshot(null, emptyMap(), emptySet(), 0L))
    val state: StateFlow<FavouriteSnapshot> = mutableState.asStateFlow()

    @Synchronized
    fun reconcile(userId: String?) {
        if (mutableState.value.userId != userId) {
            mutableState.value = FavouriteSnapshot(userId, emptyMap(), emptySet(), mutableState.value.generation + 1)
        }
    }

    @Synchronized
    fun beginSession(userId: String?): Long {
        reconcile(userId)
        return mutableState.value.generation
    }

    @Synchronized
    fun applyIfCurrent(userId: String, generation: Long, listingIds: Collection<String>, savedIds: Set<String>): Boolean {
        val current = mutableState.value
        if (current.userId != userId || current.generation != generation) return false
        mutableState.value = current.copy(values = current.values + listingIds.associateWith {
            if (it in savedIds) FavouriteValue.FAVOURITED else FavouriteValue.NOT_FAVOURITED
        })
        return true
    }

    @Synchronized
    fun markMutating(userId: String, generation: Long, listingId: String, mutating: Boolean) {
        val state = mutableState.value
        if (state.userId != userId || state.generation != generation) return
        mutableState.value = state.copy(mutating = if (mutating) state.mutating + listingId else state.mutating - listingId)
    }

    @Synchronized
    fun set(userId: String?, generation: Long, listingId: String, saved: Boolean) {
        val state = mutableState.value
        if (state.userId != userId || state.generation != generation) return
        mutableState.value = state.copy(
            values = state.values + (listingId to if (saved) FavouriteValue.FAVOURITED else FavouriteValue.NOT_FAVOURITED),
            mutating = state.mutating - listingId,
        )
    }
}
