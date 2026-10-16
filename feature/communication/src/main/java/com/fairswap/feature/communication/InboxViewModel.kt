package com.fairswap.feature.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.ConversationSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val boundSession: CommunicationSession? = null,
    val conversations: List<ConversationSummary> = emptyList(),
    val activeFilter: InboxFilter = InboxFilter.ALL,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val filteredConversations: List<ConversationSummary>
        get() {
            val session = boundSession ?: return emptyList()
            return when (activeFilter) {
                InboxFilter.ALL -> conversations
                InboxFilter.BUYING -> conversations.filter { it.buyerId == session.userId }
                InboxFilter.SELLING -> conversations.filter { it.sellerId == session.userId }
            }
        }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: CommunicationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CommunicationNavigationEvent>()
    val events: SharedFlow<CommunicationNavigationEvent> = _events.asSharedFlow()

    private var job: kotlinx.coroutines.Job? = null

    fun bindSession(session: CommunicationSession?) {
        val current = _uiState.value.boundSession
        if (current == session) return

        job?.cancel()
        job = null

        _uiState.update {
            InboxUiState(
                boundSession = session,
                conversations = emptyList(),
                activeFilter = InboxFilter.ALL,
                isLoading = false,
                errorMessage = null,
            )
        }

        if (session != null) {
            refresh()
        }
    }

    fun setFilter(filter: InboxFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    internal suspend fun performFetchInboxInternal(session: CommunicationSession): CommunicationResult<List<ConversationSummary>> {
        return repository.fetchInbox(session)
    }

    fun refresh() {
        val session = _uiState.value.boundSession ?: return

        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val res = performFetchInboxInternal(session)

            // Re-verify session after await
            if (_uiState.value.boundSession != session) return@launch

            when (res) {
                is CommunicationResult.Success -> {
                    _uiState.update {
                        it.copy(
                            conversations = res.value,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is CommunicationResult.Failure -> {
                    val error = res.error
                    if (error == CommunicationFailure.AuthRequired ||
                        error == CommunicationFailure.PolicyReviewRequired ||
                        error == CommunicationFailure.AccountRestricted
                    ) {
                        _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                        _uiState.update { it.copy(isLoading = false) }
                    } else {
                        val safeMessage = CommunicationUiText.toUserFacingMessage(error)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = safeMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}
