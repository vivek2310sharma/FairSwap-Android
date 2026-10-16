package com.fairswap.feature.account.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.account.ReportInput
import com.fairswap.core.account.ReportResult
import com.fairswap.core.account.ReportTargetType
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
import java.util.UUID
import javax.inject.Inject

data class ReportUiState(
    val userId: String? = null,
    val sessionGeneration: Long = 0L,
    val targetType: ReportTargetType? = null,
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

sealed interface ReportEvent {
    data class AuthRequired(val session: SafetySession?) : ReportEvent
}

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: UserSafetyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()
    private val mutableEvents = Channel<ReportEvent>(Channel.BUFFERED)
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
        _state.update { ReportUiState(userId = newUserId, sessionGeneration = newGen) }
    }

    fun onUserChanged(userId: String?) {
        onSessionChanged(userId?.let { SafetySession(it, 0L) })
    }

    fun bindTarget(targetTypeRaw: String, targetId: String) {
        val parsedTargetType = ReportTargetType.entries.firstOrNull { it.name.equals(targetTypeRaw, ignoreCase = true) || it.value.equals(targetTypeRaw, ignoreCase = true) }
        val isSameTarget = _state.value.targetType == parsedTargetType && _state.value.targetId == targetId
        if (isSameTarget) return
        
        generation++
        submitJob?.cancel()
        
        val isValidTarget = parsedTargetType != null && targetId.isNotBlank() && targetId.isUuid()
        _state.update { 
            it.copy(
                targetType = parsedTargetType,
                targetId = targetId,
                reason = "",
                details = "",
                submitting = false,
                success = false,
                error = if (!isValidTarget) "Invalid or unsupported report target." else null
            ) 
        }
    }

    fun updateReason(reason: String) {
        _state.update { it.copy(reason = reason.take(100), error = if (it.targetType == null) it.error else null) }
    }

    fun updateDetails(details: String) {
        _state.update { it.copy(details = details.take(2000)) }
    }

    fun submitReport() {
        val current = _state.value
        val currentSession = activeSession
        if (current.userId == null || currentSession == null) {
            viewModelScope.launch { mutableEvents.send(ReportEvent.AuthRequired(currentSession)) }
            return
        }
        val targetType = current.targetType
        if (targetType == null || current.targetId.isBlank() || !current.targetId.isUuid()) {
            _state.update { it.copy(error = "Invalid or unsupported report target.") }
            return
        }
        if (current.submitting) return
        
        val cleanReason = current.reason.trim()
        if (cleanReason.length < 3) {
            _state.update { it.copy(error = "Reason must be between 3 and 100 characters") }
            return
        }

        generation++
        val requestGen = generation
        _state.update { it.copy(submitting = true, error = null) }
        submitJob = viewModelScope.launch {
            val result = repository.createReport(
                ReportInput(
                    targetType = targetType,
                    targetId = current.targetId,
                    reason = cleanReason,
                    details = current.details.takeIf { it.isNotBlank() }
                )
            )
            if (requestGen != generation) return@launch
            
            when (result) {
                is ReportResult.Success -> {
                    _state.update { it.copy(submitting = false, success = true) }
                }
                is ReportResult.Invalid -> {
                    _state.update { it.copy(submitting = false, error = result.message) }
                }
                is ReportResult.AuthRequired -> {
                    _state.update { it.copy(submitting = false, error = "Authentication required.") }
                    mutableEvents.send(ReportEvent.AuthRequired(currentSession))
                }
                is ReportResult.Failure -> {
                    _state.update { it.copy(submitting = false, error = "Failed to submit report. Please try again.") }
                }
            }
        }
    }

    private companion object {
        fun String.isUuid(): Boolean = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)
    }
}
