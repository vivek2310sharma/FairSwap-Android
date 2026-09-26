package com.fairswap.feature.sellerprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import com.fairswap.core.discovery.PublicSellerProfile
import com.fairswap.core.discovery.PublicSellerResult
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

data class SellerProfileUiState(
    val profile: PublicSellerProfile? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SellerProfileViewModel @Inject constructor(
    private val repository: PublicMarketplaceDataSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SellerProfileUiState())
    val state: StateFlow<SellerProfileUiState> = mutableState.asStateFlow()
    private var currentId: String? = null
    private var job: Job? = null
    private var generation = 0L

    fun load(id: String) {
        if (id == currentId && (mutableState.value.loading || mutableState.value.profile != null || mutableState.value.unavailable)) return
        request(id)
    }

    fun retry() { currentId?.let(::request) }

    private fun request(id: String) {
        job?.cancel()
        val sameId = id == currentId
        currentId = id
        val requestGeneration = ++generation
        mutableState.update { it.copy(profile = if (sameId) it.profile else null, loading = true, unavailable = false, error = null) }
        job = viewModelScope.launch {
            try {
                val result = withTimeout(15_000) { repository.seller(id) }
                if (requestGeneration == generation) mutableState.update { state ->
                    when (result) {
                        is PublicSellerResult.Found -> state.copy(profile = result.profile, unavailable = false)
                        PublicSellerResult.Unavailable -> state.copy(profile = null, unavailable = true)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this seller. Retry.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this seller. Retry.") }
            } finally {
                if (requestGeneration == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
