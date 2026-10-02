package com.fairswap.feature.sellerlistings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.sellerlistings.SellerDashboardResult
import com.fairswap.core.sellerlistings.SellerLifecycleAction
import com.fairswap.core.sellerlistings.SellerListingRepository
import com.fairswap.core.sellerlistings.SellerListingSummary
import com.fairswap.core.sellerlistings.SellerMutationResult
import com.fairswap.core.sellerlistings.SellerSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MyListingsEvent {
    val session: SellerSession

    data class AuthRequired(override val session: SellerSession) : MyListingsEvent
    data class PolicyReviewRequired(override val session: SellerSession) : MyListingsEvent
}

data class MyListingsUiState(
    val session: SellerSession? = null,
    val isLoading: Boolean = true,
    val userId: String? = null,
    val listings: List<SellerListingSummary> = emptyList(),
    val isRefreshing: Boolean = false,
    val mutatingListingId: String? = null,
    val lastLoadedVersion: Long = -1L,
    val errorMessage: String? = null,
    val actionNotice: String? = null,
)

@HiltViewModel
class MyListingsViewModel @Inject constructor(
    private val repository: SellerListingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyListingsUiState())
    val state: StateFlow<MyListingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<MyListingsEvent>()
    val events: SharedFlow<MyListingsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null
    private var actionJob: Job? = null
    private var currentActionOpId = 0L

    init {
        viewModelScope.launch {
            repository.dashboardVersion.collect { currentVersion ->
                val currentSession = _state.value.session
                if (currentSession != null &&
                    repository.isSessionActive(currentSession) &&
                    _state.value.lastLoadedVersion < currentVersion
                ) {
                    refresh(currentVersion)
                }
            }
        }
    }

    fun bind(session: SellerSession?) {
        val previousSession = _state.value.session
        val currentVersion = repository.dashboardVersion.value

        if (session == previousSession) {
            if (session != null && _state.value.lastLoadedVersion < currentVersion) {
                refresh(currentVersion)
            }
            return
        }

        loadJob?.cancel()
        actionJob?.cancel()
        loadJob = null
        actionJob = null

        if (session == null) {
            _state.value = MyListingsUiState(session = null, isLoading = false, userId = null, errorMessage = "Signed out.")
        } else {
            _state.value = MyListingsUiState(
                session = session,
                isLoading = true,
                userId = session.userId,
                lastLoadedVersion = currentVersion,
            )
            load(session, currentVersion)
        }
    }

    fun load(session: SellerSession? = _state.value.session, version: Long = repository.dashboardVersion.value) {
        val currentSession = session ?: _state.value.session ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val userId = currentSession.userId
            _state.update { it.copy(session = currentSession, isLoading = true, userId = userId, errorMessage = null) }

            when (val result = repository.loadDashboard(currentSession)) {
                is SellerDashboardResult.Success -> {
                    if (_state.value.session == currentSession) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                userId = userId,
                                listings = result.listings,
                                lastLoadedVersion = version,
                                errorMessage = null,
                            )
                        }
                    }
                }
                SellerDashboardResult.AuthRequired -> {
                    if (_state.value.session == currentSession) {
                        _state.update { it.copy(isLoading = false, isRefreshing = false) }
                        _events.emit(MyListingsEvent.AuthRequired(currentSession))
                    }
                }
                is SellerDashboardResult.Failure -> {
                    if (_state.value.session == currentSession) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun refresh(version: Long = repository.dashboardVersion.value) {
        val currentSession = _state.value.session ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val userId = currentSession.userId
            _state.update { it.copy(session = currentSession, isRefreshing = true, errorMessage = null) }
            when (val result = repository.loadDashboard(currentSession)) {
                is SellerDashboardResult.Success -> {
                    if (_state.value.session == currentSession) {
                        _state.update {
                            it.copy(
                                isRefreshing = false,
                                userId = userId,
                                listings = result.listings,
                                lastLoadedVersion = version,
                                errorMessage = null,
                            )
                        }
                    }
                }
                SellerDashboardResult.AuthRequired -> {
                    if (_state.value.session == currentSession) {
                        _state.update { it.copy(isRefreshing = false) }
                        _events.emit(MyListingsEvent.AuthRequired(currentSession))
                    }
                }
                is SellerDashboardResult.Failure -> {
                    if (_state.value.session == currentSession) {
                        _state.update {
                            it.copy(
                                isRefreshing = false,
                                errorMessage = result.message,
                            )
                        }
                    }
                }
            }
        }
    }

    fun executeLifecycleAction(listingId: String, action: SellerLifecycleAction) {
        val currentSession = _state.value.session ?: return
        if (_state.value.mutatingListingId != null) return
        val opId = ++currentActionOpId

        _state.update { it.copy(mutatingListingId = listingId, errorMessage = null, actionNotice = null) }

        actionJob = viewModelScope.launch {
            try {
                when (val result = repository.executeLifecycleAction(listingId, action, currentSession)) {
                    is SellerMutationResult.Success -> {
                        if (currentActionOpId == opId && _state.value.session == currentSession) {
                            _state.update { it.copy(actionNotice = "Action '${action.label}' completed.") }
                        }
                    }
                    SellerMutationResult.AuthRequired -> {
                        if (currentActionOpId == opId && _state.value.session == currentSession) {
                            _events.emit(MyListingsEvent.AuthRequired(currentSession))
                        }
                    }
                    SellerMutationResult.PolicyReviewRequired -> {
                        if (currentActionOpId == opId && _state.value.session == currentSession) {
                            _events.emit(MyListingsEvent.PolicyReviewRequired(currentSession))
                        }
                    }
                    is SellerMutationResult.ValidationError -> {
                        if (currentActionOpId == opId && _state.value.session == currentSession) {
                            _state.update { it.copy(errorMessage = "Validation failed.") }
                        }
                    }
                    is SellerMutationResult.Failure -> {
                        if (currentActionOpId == opId && _state.value.session == currentSession) {
                            _state.update { it.copy(errorMessage = result.message) }
                        }
                    }
                }
            } finally {
                if (currentActionOpId == opId &&
                    _state.value.session == currentSession &&
                    _state.value.mutatingListingId == listingId
                ) {
                    _state.update { it.copy(mutatingListingId = null) }
                }
            }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, actionNotice = null) }
    }
}
