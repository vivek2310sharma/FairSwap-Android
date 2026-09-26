package com.fairswap.feature.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.favourites.FavouriteClearResult
import com.fairswap.core.favourites.FavouriteLoadResult
import com.fairswap.core.favourites.FavouritePage
import com.fairswap.core.favourites.FavouritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavouritesUiState(
    val cards: List<com.fairswap.core.discovery.ListingCard> = emptyList(),
    val unavailableIds: Set<String> = emptySet(),
    val unavailableCount: Int = 0,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val stale: Boolean = false,
    val error: String? = null,
    val authRequired: Boolean = false,
    val policyRequired: Boolean = false,
    val clearBusy: Boolean = false,
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(private val repository: FavouritesRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(FavouritesUiState())
    val state: StateFlow<FavouritesUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun load() = refresh()

    fun sessionChanged() {
        job?.cancel()
        mutableState.value = FavouritesUiState()
        refresh()
    }

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.loadingMore) return
        val hadData = mutableState.value.page > 0
        mutableState.update { it.copy(loading = true, error = null, authRequired = false, policyRequired = false) }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                when (val result = repository.loadPage(1)) {
                    is FavouriteLoadResult.Success -> mutableState.value = result.page.toUi(loading = false)
                    FavouriteLoadResult.AuthRequired -> mutableState.value = FavouritesUiState(authRequired = true)
                    FavouriteLoadResult.Failure -> mutableState.update {
                        it.copy(loading = false, stale = hadData, error = "Unable to refresh saved favourites. Retry.")
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(loading = false, stale = hadData, error = "Unable to refresh saved favourites. Retry.") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(loading = false) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, stale = hadData, error = "Unable to refresh saved favourites. Retry.") }
            } finally {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value
        if (!snapshot.hasMore || snapshot.loading || snapshot.loadingMore) return
        mutableState.update { it.copy(loadingMore = true, error = null) }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                when (val result = repository.loadPage(snapshot.page + 1)) {
                    is FavouriteLoadResult.Success -> mutableState.update { current ->
                        current.copy(
                            cards = (current.cards + result.page.cards).distinctBy { it.id },
                            unavailableIds = current.unavailableIds + result.page.unavailableIds,
                            unavailableCount = (current.unavailableIds + result.page.unavailableIds).size,
                            page = result.page.page,
                            hasMore = result.page.hasMore,
                            loadingMore = false,
                            stale = false,
                        )
                    }
                    FavouriteLoadResult.AuthRequired -> mutableState.value = FavouritesUiState(authRequired = true)
                    FavouriteLoadResult.Failure -> mutableState.update { it.copy(loadingMore = false, stale = true, error = "Unable to load more saved favourites. Retry.") }
                }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(loadingMore = false, stale = true, error = "Unable to load more saved favourites. Retry.") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(loadingMore = false) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loadingMore = false, stale = true, error = "Unable to load more saved favourites. Retry.") }
            } finally { mutableState.update { it.copy(loadingMore = false) } }
        }
    }

    fun clearUnavailable() {
        val ids = mutableState.value.unavailableIds
        if (ids.isEmpty() || mutableState.value.clearBusy) return
        mutableState.update { it.copy(clearBusy = true, error = null) }
        viewModelScope.launch {
            try { when (val result = repository.clearUnavailable(ids)) {
                is FavouriteClearResult.Success -> mutableState.update { it.copy(
                    unavailableIds = it.unavailableIds - result.deletedIds,
                    unavailableCount = (it.unavailableIds - result.deletedIds).size,
                    clearBusy = false,
                ) }
                FavouriteClearResult.AuthRequired -> mutableState.update { it.copy(clearBusy = false, authRequired = true) }
                FavouriteClearResult.PolicyReviewRequired -> mutableState.update { it.copy(clearBusy = false, policyRequired = true) }
                FavouriteClearResult.Failure -> mutableState.update { it.copy(clearBusy = false, error = "Unable to clear unavailable saved items. Retry.") }
            } } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(clearBusy = false, error = "Unable to clear unavailable saved items. Retry.") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(clearBusy = false) }
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(clearBusy = false, error = "Unable to clear unavailable saved items. Retry.") }
            } finally { mutableState.update { it.copy(clearBusy = false) } }
        }
    }

    private fun FavouritePage.toUi(loading: Boolean) = FavouritesUiState(
        cards = cards,
        unavailableIds = unavailableIds,
        unavailableCount = unavailableCount,
        page = page,
        hasMore = hasMore,
        loading = loading,
    )
}
