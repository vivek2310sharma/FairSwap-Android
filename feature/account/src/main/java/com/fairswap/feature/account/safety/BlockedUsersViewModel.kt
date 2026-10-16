package com.fairswap.feature.account.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.account.BlockResult
import com.fairswap.core.account.BlockedUsersResult
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

data class BlockedUsersUiState(
    val userId: String? = null,
    val sessionGeneration: Long = 0L,
    val users: List<String> = emptyList(),
    val loading: Boolean = false,
    val initialLoadDone: Boolean = false,
    val error: String? = null,
    val unblockingUserIds: Set<String> = emptySet()
)

sealed interface BlockedUsersEvent {
    data class AuthRequired(val session: SafetySession?) : BlockedUsersEvent
}

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val repository: UserSafetyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BlockedUsersUiState())
    val state: StateFlow<BlockedUsersUiState> = _state.asStateFlow()
    private val mutableEvents = Channel<BlockedUsersEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    
    private var loadJob: Job? = null
    private val unblockJobs = mutableMapOf<String, Job>()
    private var sessionGenCounter = 0L
    private var loadGenCounter = 0L
    private val unblockedInSession = mutableSetOf<String>()
    private var activeSession: SafetySession? = null

    fun onSessionChanged(session: SafetySession?) {
        val newUserId = session?.userId
        val newSessionGen = session?.generation ?: 0L
        val current = _state.value
        if (current.userId == newUserId && current.sessionGeneration == newSessionGen) return

        activeSession = session

        sessionGenCounter++
        loadGenCounter++
        loadJob?.cancel()
        unblockJobs.values.forEach { it.cancel() }
        unblockJobs.clear()
        unblockedInSession.clear()

        _state.update { 
            BlockedUsersUiState(
                userId = newUserId,
                sessionGeneration = newSessionGen
            ) 
        }
        if (newUserId != null) {
            loadUsersInternal()
        }
    }

    fun onUserChanged(userId: String?) {
        onSessionChanged(userId?.let { SafetySession(it, 0L) })
    }

    fun loadUsers() {
        val currentSession = activeSession
        if (_state.value.userId == null || currentSession == null) {
            viewModelScope.launch { mutableEvents.send(BlockedUsersEvent.AuthRequired(currentSession)) }
            return
        }
        if (_state.value.loading) return
        loadGenCounter++
        loadJob?.cancel()
        loadUsersInternal()
    }

    private fun loadUsersInternal() {
        val currentSession = activeSession
        val currentSessionGen = sessionGenCounter
        val currentLoadGen = loadGenCounter
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = repository.getBlockedUsers()
                if (currentSessionGen != sessionGenCounter || currentLoadGen != loadGenCounter) return@launch
                
                when (result) {
                    is BlockedUsersResult.Success -> {
                        _state.update { 
                            it.copy(
                                loading = false, 
                                initialLoadDone = true, 
                                users = (result.blockedIds - it.unblockingUserIds - unblockedInSession)
                            ) 
                        }
                    }
                    is BlockedUsersResult.AuthRequired -> {
                        _state.update { it.copy(loading = false, error = "Authentication required.") }
                        mutableEvents.send(BlockedUsersEvent.AuthRequired(currentSession))
                    }
                    is BlockedUsersResult.Failure -> {
                        _state.update { it.copy(loading = false, error = "Failed to load blocked users.") }
                    }
                }
            } finally {
                if (currentSessionGen == sessionGenCounter && currentLoadGen != loadGenCounter) {
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun unblockUser(blockedUserId: String) {
        val currentSession = activeSession
        if (_state.value.userId == null || currentSession == null) {
            viewModelScope.launch { mutableEvents.send(BlockedUsersEvent.AuthRequired(currentSession)) }
            return
        }
        if (blockedUserId in _state.value.unblockingUserIds) return

        val currentSessionGen = sessionGenCounter
        _state.update { it.copy(unblockingUserIds = it.unblockingUserIds + blockedUserId, error = null) }
        val job = viewModelScope.launch {
            try {
                val result = repository.unblockUser(blockedUserId)
                if (currentSessionGen != sessionGenCounter) return@launch
                
                when (result) {
                    is BlockResult.Success -> {
                        unblockedInSession.add(blockedUserId)
                        loadGenCounter++ // Invalidate in-flight load requests started before this unblock succeeded
                        _state.update { 
                            it.copy(
                                unblockingUserIds = it.unblockingUserIds - blockedUserId,
                                users = it.users - blockedUserId
                            ) 
                        }
                    }
                    is BlockResult.AuthRequired -> {
                        unblockedInSession.remove(blockedUserId)
                        _state.update { 
                            it.copy(
                                unblockingUserIds = it.unblockingUserIds - blockedUserId,
                                error = "Authentication required."
                            ) 
                        }
                        mutableEvents.send(BlockedUsersEvent.AuthRequired(currentSession))
                    }
                    is BlockResult.Failure -> {
                        unblockedInSession.remove(blockedUserId)
                        _state.update { 
                            it.copy(
                                unblockingUserIds = it.unblockingUserIds - blockedUserId,
                                error = "Failed to unblock user."
                            ) 
                        }
                    }
                    is BlockResult.Invalid -> {
                        unblockedInSession.remove(blockedUserId)
                        _state.update { 
                            it.copy(
                                unblockingUserIds = it.unblockingUserIds - blockedUserId,
                                error = result.message
                            ) 
                        }
                    }
                }
            } finally {
                unblockJobs.remove(blockedUserId)
            }
        }
        unblockJobs[blockedUserId] = job
    }
}
