package com.fairswap.feature.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicListingDetail
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ListingDetailUiState(
    val detail: PublicListingDetail? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: PublicMarketplaceDataSource,
    private val favourites: com.fairswap.core.favourites.FavouritesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = mutableState.asStateFlow()
    private var currentId: String? = null
    private var job: Job? = null
    private var generation = 0L

    fun syncFavourite(id: String) { viewModelScope.launch { favourites.lookup(listOf(id)) } }

    fun setFavourite(id: String, desired: Boolean, onResult: (com.fairswap.core.favourites.FavouriteMutationResult) -> Unit) {
        viewModelScope.launch { onResult(favourites.setFavourite(id, desired)) }
    }

    fun load(id: String) {
        if (id == currentId && (mutableState.value.loading || mutableState.value.detail != null || mutableState.value.unavailable)) return
        request(id)
    }

    fun retry() { currentId?.let(::request) }

    private fun request(id: String) {
        job?.cancel()
        val sameId = id == currentId
        currentId = id
        val requestGeneration = ++generation
        mutableState.update { it.copy(detail = if (sameId) it.detail else null, loading = true, unavailable = false, error = null) }
        job = viewModelScope.launch {
            try {
                val result = withTimeout(15_000) { repository.listing(id) }
                if (requestGeneration == generation) mutableState.update { state ->
                    when (result) {
                        is PublicDetailResult.Found -> state.copy(detail = result.detail, unavailable = false)
                        PublicDetailResult.Unavailable -> state.copy(detail = null, unavailable = true)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this listing. Retry.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this listing. Retry.") }
            } finally {
                if (requestGeneration == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
