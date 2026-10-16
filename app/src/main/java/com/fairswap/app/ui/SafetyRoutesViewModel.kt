package com.fairswap.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.account.BlockResult
import com.fairswap.core.account.UserSafetyRepository
import com.fairswap.core.account.WebRoutes
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.discovery.PublicListingUrlBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockActionUiState(
    val pendingCounterpartyId: String? = null,
    val error: String? = null,
    val successMessage: String? = null,
)

@HiltViewModel
class SafetyRoutesViewModel @Inject constructor(
    val webRoutes: WebRoutes,
    val userSafetyRepository: UserSafetyRepository,
    val publicListingUrlBuilder: PublicListingUrlBuilder
) : ViewModel() {

    private val _blockState = MutableStateFlow(BlockActionUiState())
    val blockState: StateFlow<BlockActionUiState> = _blockState.asStateFlow()

    fun dispatchBlockUser(
        originatingSession: CommunicationSession?,
        originatingRoute: Any?,
        getLiveSession: () -> CommunicationSession?,
        getLiveTopRoute: () -> Any?,
        getLiveUserId: () -> String?,
        counterpartyId: String,
        onSuccess: (route: Any) -> Unit
    ) {
        val liveUserId = getLiveUserId()
        val currentSession = getLiveSession()
        val liveTopRoute = getLiveTopRoute()

        if (originatingSession == null || currentSession == null || liveUserId == null || originatingRoute == null) {
            _blockState.update { it.copy(error = "Authentication session or route state invalid") }
            return
        }
        if (originatingSession != currentSession || currentSession.userId != liveUserId) {
            _blockState.update { it.copy(error = "Authentication session mismatch") }
            return
        }
        if (originatingRoute != liveTopRoute) {
            _blockState.update { it.copy(error = "Route changed before block dispatch") }
            return
        }
        if (counterpartyId.isBlank() || counterpartyId == liveUserId) {
            _blockState.update { it.copy(error = "Cannot block yourself or invalid user ID") }
            return
        }
        // Reject overlapping or duplicate dispatches while an action is pending
        if (_blockState.value.pendingCounterpartyId != null) return

        _blockState.update { it.copy(pendingCounterpartyId = counterpartyId, error = null, successMessage = null) }

        viewModelScope.launch {
            try {
                val liveSessionNow = getLiveSession()
                val liveUserIdNow = getLiveUserId()
                
                if (liveSessionNow != originatingSession || liveUserIdNow != liveUserId) {
                    _blockState.update { 
                        if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = "Session changed before block execution")
                        else it
                    }
                    return@launch
                }

                val currentIdentity = userSafetyRepository.currentIdentity()
                if (currentIdentity == null || currentIdentity.userId != liveUserIdNow) {
                    _blockState.update { 
                        if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = "Authentication session changed")
                        else it
                    }
                    return@launch
                }

                when (val result = userSafetyRepository.blockUser(counterpartyId)) {
                    is BlockResult.Success -> {
                        val finalSessionNow = getLiveSession()
                        val finalUserIdNow = getLiveUserId()
                        val finalRouteNow = getLiveTopRoute()

                        if (finalSessionNow == originatingSession && finalUserIdNow == liveUserId) {
                            _blockState.update { 
                                if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, successMessage = "User blocked successfully")
                                else it
                            }
                            if (finalRouteNow == originatingRoute) {
                                onSuccess(originatingRoute)
                            }
                        }
                    }
                    is BlockResult.Invalid -> {
                        _blockState.update { 
                            if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = result.message)
                            else it
                        }
                    }
                    is BlockResult.AuthRequired -> {
                        _blockState.update { 
                            if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = "Authentication required")
                            else it
                        }
                    }
                    is BlockResult.Failure -> {
                        _blockState.update { 
                            if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = "Failed to block user. Please try again.")
                            else it
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _blockState.update { 
                    if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null, error = "Uncertain outcome occurred")
                    else it
                }
            } finally {
                _blockState.update { 
                    if (it.pendingCounterpartyId == counterpartyId) it.copy(pendingCounterpartyId = null)
                    else it
                }
            }
        }
    }

    fun clearMessages() {
        _blockState.update { it.copy(error = null, successMessage = null) }
    }
}
