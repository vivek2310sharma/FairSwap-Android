package com.fairswap.feature.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.StartConversationResult
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

data class NewInquiryUiState(
    val boundSession: CommunicationSession? = null,
    val listingId: String = "",
    val composerText: String = "",
    val isSending: Boolean = false,
    val pendingRequestId: String? = null,
    val sendError: String? = null,
)

@HiltViewModel
class NewInquiryViewModel @Inject constructor(
    private val repository: CommunicationRepository,
    private val requestIdProvider: CommunicationRequestIdProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewInquiryUiState())
    val uiState: StateFlow<NewInquiryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CommunicationNavigationEvent>()
    val events: SharedFlow<CommunicationNavigationEvent> = _events.asSharedFlow()

    fun bind(session: CommunicationSession?, listingId: String) {
        val current = _uiState.value
        if (current.boundSession == session && current.listingId == listingId) return

        _uiState.update {
            NewInquiryUiState(
                boundSession = session,
                listingId = listingId,
            )
        }
    }

    fun onComposerTextChanged(text: String) {
        _uiState.update {
            it.copy(
                composerText = text,
                pendingRequestId = if (text != it.composerText) null else it.pendingRequestId,
                sendError = if (text != it.composerText) null else it.sendError,
            )
        }
    }

    internal suspend fun performSendInquiryInternal(
        session: CommunicationSession,
        listingId: String,
        textToSend: String,
        reqId: String,
    ): CommunicationResult<StartConversationResult> {
        return repository.startConversation(
            session = session,
            listingId = listingId,
            content = textToSend,
            clientRequestId = reqId,
        )
    }

    fun sendInquiry() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        val listingId = state.listingId
        if (state.isSending) return

        val textToSend = state.composerText.trim()
        if (textToSend.isEmpty()) {
            _uiState.update { it.copy(sendError = "Enter a message between 1 and 2000 characters.") }
            return
        }
        val reqId = state.pendingRequestId ?: requestIdProvider.generateRequestId()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    pendingRequestId = reqId,
                    sendError = null,
                )
            }

            val res = performSendInquiryInternal(
                session = session,
                listingId = listingId,
                textToSend = textToSend,
                reqId = reqId,
            )

            if (_uiState.value.boundSession != session) return@launch

            when (res) {
                is CommunicationResult.Success -> {
                    val result = res.value
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            composerText = "",
                            pendingRequestId = null,
                            sendError = null,
                        )
                    }
                    _events.emit(
                        CommunicationNavigationEvent.NavigateToConversation(
                            session = session,
                            conversationId = result.conversationId,
                            listingId = listingId,
                        )
                    )
                }
                is CommunicationResult.Failure -> {
                    val error = res.error
                    if (error == CommunicationFailure.AuthRequired ||
                        error == CommunicationFailure.PolicyReviewRequired ||
                        error == CommunicationFailure.AccountRestricted
                    ) {
                        _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                        _uiState.update { it.copy(isSending = false) }
                    } else {
                        val safeMessage = CommunicationUiText.toUserFacingMessage(error)
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                sendError = safeMessage,
                            )
                        }
                    }
                }
            }
        }
    }
}
