package com.fairswap.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.DiscoveryDataSource
import com.fairswap.core.discovery.Locality
import com.fairswap.core.discovery.ResultsState
import com.fairswap.core.discovery.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class DiscoveryUiState(
    val draft: SearchFilters = SearchFilters(),
    val applied: SearchFilters = SearchFilters(),
    val categories: List<Category> = emptyList(),
    val cities: List<City> = emptyList(),
    val localities: List<Locality> = emptyList(),
    val results: ResultsState = ResultsState(),
    val loadingFirstPage: Boolean = false,
    val loadingMore: Boolean = false,
    val loadingMetadata: Boolean = false,
    val metadataError: String? = null,
    val inputError: String? = null,
) {
    fun apply(filters: SearchFilters) = copy(
        applied = filters,
        results = ResultsState(),
        loadingMore = false,
        inputError = null,
    )
}

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val repository: DiscoveryDataSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiscoveryUiState())
    val state: StateFlow<DiscoveryUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var localitiesJob: Job? = null
    private var searchGeneration = 0L
    private var metadataGeneration = 0L

    init {
        loadMetadata()
        refresh()
    }

    fun editFilters(transform: (SearchFilters) -> SearchFilters) {
        mutableState.update { it.copy(draft = transform(it.draft), inputError = null) }
    }

    fun selectCity(cityId: String?) {
        localitiesJob?.cancel()
        mutableState.update { it.copy(draft = it.draft.withCity(cityId), localities = emptyList(), inputError = null, metadataError = null) }
        if (cityId == null) return
        localitiesJob = viewModelScope.launch {
            attempt { repository.localities(cityId) }.fold(
                onSuccess = { localities ->
                    mutableState.update { state ->
                        if (state.draft.cityId == cityId) state.copy(localities = localities, metadataError = null) else state
                    }
                },
                onFailure = {
                    mutableState.update { state ->
                        if (state.draft.cityId == cityId) state.copy(metadataError = "Unable to load localities. Retry filters.") else state
                    }
                },
            )
        }
    }

    fun loadMetadata() {
        if (mutableState.value.loadingMetadata) return
        val generation = ++metadataGeneration
        mutableState.update { it.copy(loadingMetadata = true, metadataError = null) }
        viewModelScope.launch {
            try {
                attempt { repository.categories() to repository.cities() }.fold(
                    onSuccess = { (categories, cities) ->
                        if (generation == metadataGeneration) mutableState.update {
                            it.copy(categories = categories, cities = cities, metadataError = null)
                        }
                    },
                    onFailure = {
                        if (generation == metadataGeneration) mutableState.update {
                            it.copy(metadataError = "Unable to load filters. Retry.")
                        }
                    },
                )
            } finally {
                if (generation == metadataGeneration) mutableState.update { it.copy(loadingMetadata = false) }
            }
        }
    }

    fun retryMetadata() {
        loadMetadata()
        mutableState.value.draft.cityId?.let(::selectCity)
    }

    fun applyFilters() {
        val filters = mutableState.value.draft
        filters.validate()?.let { message ->
            mutableState.update { it.copy(inputError = message) }
            return
        }
        searchJob?.cancel()
        mutableState.update { it.apply(filters) }
        requestFirstPage(filters)
    }

    fun clearFilters() {
        localitiesJob?.cancel()
        mutableState.update { it.copy(draft = SearchFilters(), localities = emptyList(), inputError = null) }
        applyFilters()
    }

    fun refresh() {
        if (mutableState.value.loadingFirstPage) return
        requestFirstPage(mutableState.value.applied)
    }

    private fun requestFirstPage(filters: SearchFilters) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        mutableState.update {
            it.copy(loadingFirstPage = true, loadingMore = false, results = it.results.copy(error = null, errorKind = null))
        }
        searchJob = viewModelScope.launch {
            try {
                attempt { repository.search(filters, 1) }.fold(
                    onSuccess = { page ->
                        if (generation == searchGeneration) mutableState.update {
                            it.copy(results = it.results.firstPage(page))
                        }
                    },
                    onFailure = {
                        if (generation == searchGeneration) mutableState.update {
                            it.copy(results = it.results.firstPageFailure())
                        }
                    },
                )
            } finally {
                if (generation == searchGeneration) mutableState.update { it.copy(loadingFirstPage = false) }
            }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value
        if (snapshot.loadingFirstPage || snapshot.loadingMore || !snapshot.results.canLoadMore) return
        if (!mutableState.compareAndSet(
                snapshot,
                snapshot.copy(loadingMore = true, results = snapshot.results.copy(error = null, errorKind = null)),
            )) return
        val page = snapshot.results.nextPage
        val filters = snapshot.applied
        val generation = ++searchGeneration
        searchJob = viewModelScope.launch {
            try {
                attempt { repository.search(filters, page) }.fold(
                    onSuccess = { next ->
                        if (generation == searchGeneration) mutableState.update {
                            it.copy(results = it.results.more(next))
                        }
                    },
                    onFailure = {
                        if (generation == searchGeneration) mutableState.update {
                            it.copy(results = it.results.nextPageFailure())
                        }
                    },
                )
            } finally {
                if (generation == searchGeneration) mutableState.update { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(REQUEST_TIMEOUT_MS) { block() })
    } catch (timeout: TimeoutCancellationException) {
        Result.failure(timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
    }
}
