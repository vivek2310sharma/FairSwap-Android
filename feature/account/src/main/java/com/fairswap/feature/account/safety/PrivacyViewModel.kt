package com.fairswap.feature.account.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.account.PrivacyRequestResult
import com.fairswap.core.account.SafetySession
import com.fairswap.core.account.UserSafetyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val userId: String? = null,
    val sessionGeneration: Long = 0L,
    val submitting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

sealed interface PrivacyEvent {
    data class AuthRequired(val session: SafetySession?) : PrivacyEvent
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val repository: UserSafetyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyUiState())
    val state: StateFlow<PrivacyUiState> = _state.asStateFlow()
    private val mutableEvents = Channel<PrivacyEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    
    private var submitJob: Job? = null
    private var generation = 0L
    private var activeSession: SafetySession? = null

    fun onSessionChanged(session: SafetySession?) {
        val newUserId = session?.userId
        val newGen = session?.generation ?: 0L
        if (_state.value.userId == newUserId && _state.value.sessionGeneration == newGen) return
        activeSession = session
        generation++
        submitJob?.cancel()
        _state.update { PrivacyUiState(userId = newUserId, sessionGeneration = newGen) }
    }

    fun onUserChanged(userId: String?) {
        onSessionChanged(userId?.let { SafetySession(it, 0L) })
    }

    fun submitExportData() {
        submitRequest("export_data")
    }

    fun submitDeleteAccount() {
        submitRequest("delete_account")
    }

    private fun submitRequest(requestType: String) {
        val currentSession = activeSession
        if (_state.value.userId == null || currentSession == null) {
            viewModelScope.launch { mutableEvents.send(PrivacyEvent.AuthRequired(currentSession)) }
            return
        }
        if (_state.value.submitting) return

        generation++
        val requestGen = generation
        _state.update { it.copy(submitting = true, error = null, successMessage = null) }
        submitJob = viewModelScope.launch {
            val result = repository.submitPrivacyRequest(requestType)
            if (requestGen != generation) return@launch
            
            when (result) {
                is PrivacyRequestResult.Success -> {
                    _state.update { 
                        it.copy(
                            submitting = false, 
                            successMessage = if (requestType == "delete_account") "Account deletion requested." else "Data export requested."
                        ) 
                    }
                }
                is PrivacyRequestResult.Invalid -> {
                    _state.update { it.copy(submitting = false, error = result.message) }
                }
                is PrivacyRequestResult.AuthRequired -> {
                    _state.update { it.copy(submitting = false, error = "Authentication required.") }
                    mutableEvents.send(PrivacyEvent.AuthRequired(currentSession))
                }
                is PrivacyRequestResult.Failure -> {
                    _state.update { it.copy(submitting = false, error = "Failed to submit request. Please try again.") }
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(error = null, successMessage = null) }
    }
}
