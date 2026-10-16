package com.fairswap.feature.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.ANDROID_CLIENT_PAGE_SIZE
import com.fairswap.core.communication.ChatMessage
import com.fairswap.core.communication.ChatMessageMerger
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.ConversationSummary
import com.fairswap.core.communication.MessageHistoryCursor
import com.fairswap.core.communication.MessageRealtimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreadUiState(
    val boundSession: CommunicationSession? = null,
    val conversationId: String = "",
    val summary: ConversationSummary? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoadingInitial: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val canLoadOlder: Boolean = true,
    val composerText: String = "",
    val isSending: Boolean = false,
    val pendingRequestId: String? = null,
    val sendError: String? = null,
    val messagingUnavailableError: String? = null,
)

@HiltViewModel
class ThreadViewModel @Inject constructor(
    private val repository: CommunicationRepository,
    private val requestIdProvider: CommunicationRequestIdProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThreadUiState())
    val uiState: StateFlow<ThreadUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CommunicationNavigationEvent>()
    val events: SharedFlow<CommunicationNavigationEvent> = _events.asSharedFlow()

    private var realtimeJob: Job? = null

    fun bind(session: CommunicationSession?, conversationId: String) {
        val current = _uiState.value
        if (current.boundSession == session && current.conversationId == conversationId) return

        realtimeJob?.cancel()
        realtimeJob = null

        _uiState.update {
            ThreadUiState(
                boundSession = session,
                conversationId = conversationId,
            )
        }

        if (session != null && conversationId.isNotBlank()) {
            loadInitialData()
        }
    }

    fun onVisible() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        if (state.conversationId.isBlank()) return

        reconcileNewestMessages()
        if (realtimeJob == null || realtimeJob?.isActive == false) {
            startRealtimeObserver()
        }
    }

    fun onNotVisible() {
        realtimeJob?.cancel()
        realtimeJob = null
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

    fun loadOlderMessages() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        if (state.isLoadingOlder || !state.canLoadOlder || state.messages.isEmpty()) return

        val oldestMsg = state.messages.first()
        val cursor = MessageHistoryCursor(oldestMsg.createdAt, oldestMsg.id)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }

            val res = repository.fetchOlderMessages(session, state.conversationId, cursor)
            if (_uiState.value.boundSession != session || _uiState.value.conversationId != state.conversationId) return@launch

            when (res) {
                is CommunicationResult.Success -> {
                    val page = res.value
                    _uiState.update { current ->
                        val merged = ChatMessageMerger.mergeMessages(current.messages, page)
                        current.copy(
                            messages = merged,
                            isLoadingOlder = false,
                            canLoadOlder = page.size >= ANDROID_CLIENT_PAGE_SIZE,
                        )
                    }
                }
                is CommunicationResult.Failure -> {
                    _uiState.update { it.copy(isLoadingOlder = false) }
                }
            }
        }
    }

    internal suspend fun performSendMessageInternal(
        session: CommunicationSession,
        conversationId: String,
        content: String,
        reqId: String,
    ): CommunicationResult<ChatMessage> {
        return repository.sendMessage(
            session = session,
            conversationId = conversationId,
            content = content,
            clientRequestId = reqId,
        )
    }

    fun sendMessage() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        if (state.isSending) return

        val textToSend = state.composerText.trim()
        val reqId = state.pendingRequestId ?: requestIdProvider.generateRequestId()

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    pendingRequestId = reqId,
                    sendError = null,
                )
            }

            val res = performSendMessageInternal(
                session = session,
                conversationId = state.conversationId,
                content = textToSend,
                reqId = reqId,
            )

            if (_uiState.value.boundSession != session || _uiState.value.conversationId != state.conversationId) return@launch

            when (res) {
                is CommunicationResult.Success -> {
                    val msg = res.value
                    _uiState.update { current ->
                        val merged = ChatMessageMerger.mergeMessages(current.messages, listOf(msg))
                        val isSameSend = current.pendingRequestId == reqId || current.pendingRequestId == null
                        current.copy(
                            messages = merged,
                            isSending = if (isSameSend) false else current.isSending,
                            composerText = if (current.pendingRequestId == reqId) "" else current.composerText,
                            pendingRequestId = if (current.pendingRequestId == reqId) null else current.pendingRequestId,
                            sendError = if (isSameSend) null else current.sendError,
                        )
                    }
                }
                is CommunicationResult.Failure -> {
                    val error = res.error
                    // If Realtime already delivered the message with matching clientRequestId while RPC was in flight, do not resurrect error
                    val currentMessages = _uiState.value.messages
                    val alreadyDeliveredByRealtime = currentMessages.any { it.clientRequestId == reqId }

                    if (alreadyDeliveredByRealtime) {
                        _uiState.update { current ->
                            val isSameSend = current.pendingRequestId == reqId || current.pendingRequestId == null
                            current.copy(
                                isSending = if (isSameSend) false else current.isSending,
                                composerText = if (current.pendingRequestId == reqId) "" else current.composerText,
                                pendingRequestId = if (current.pendingRequestId == reqId) null else current.pendingRequestId,
                                sendError = if (isSameSend) null else current.sendError,
                            )
                        }
                    } else if (error == CommunicationFailure.AuthRequired ||
                        error == CommunicationFailure.PolicyReviewRequired ||
                        error == CommunicationFailure.AccountRestricted
                    ) {
                        _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                        _uiState.update { current ->
                            val isSameSend = current.pendingRequestId == reqId || current.pendingRequestId == null
                            current.copy(isSending = if (isSameSend) false else current.isSending)
                        }
                    } else {
                        val safeMessage = CommunicationUiText.toUserFacingMessage(error)
                        _uiState.update { current ->
                            val isSameSend = current.pendingRequestId == reqId || current.pendingRequestId == null
                            current.copy(
                                isSending = if (isSameSend) false else current.isSending,
                                sendError = if (isSameSend) safeMessage else current.sendError,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadInitialData() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        val convId = state.conversationId

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInitial = true, messagingUnavailableError = null) }

            val summaryRes = repository.fetchConversation(session, convId)
            val newestRes = repository.fetchNewestMessages(session, convId)

            if (_uiState.value.boundSession != session || _uiState.value.conversationId != convId) return@launch

            var unavailableErr: String? = null
            var summary: ConversationSummary? = null
            var messages = emptyList<ChatMessage>()

            if (summaryRes is CommunicationResult.Success) {
                summary = summaryRes.value
            } else if (summaryRes is CommunicationResult.Failure) {
                val err = summaryRes.error
                if (err == CommunicationFailure.AuthRequired ||
                    err == CommunicationFailure.PolicyReviewRequired ||
                    err == CommunicationFailure.AccountRestricted
                ) {
                    _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                    return@launch
                }
                unavailableErr = CommunicationUiText.toUserFacingMessage(err)
            }

            if (newestRes is CommunicationResult.Success) {
                messages = newestRes.value
            }

            _uiState.update {
                it.copy(
                    summary = summary,
                    messages = messages,
                    isLoadingInitial = false,
                    canLoadOlder = messages.size >= ANDROID_CLIENT_PAGE_SIZE,
                    messagingUnavailableError = unavailableErr,
                )
            }

            checkAndMarkRead(session, messages)
        }
    }

    private fun reconcileNewestMessages() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        val convId = state.conversationId

        viewModelScope.launch {
            val newestRes = repository.fetchNewestMessages(session, convId)
            if (_uiState.value.boundSession != session || _uiState.value.conversationId != convId) return@launch

            if (newestRes is CommunicationResult.Success) {
                val newest = newestRes.value
                _uiState.update { current ->
                    val merged = ChatMessageMerger.mergeMessages(current.messages, newest)
                    current.copy(messages = merged)
                }
                checkAndMarkRead(session, newest)
            }
        }
    }

    private fun startRealtimeObserver() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        val convId = state.conversationId

        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            repository.observeMessageEvents(session, convId).collect { event ->
                if (_uiState.value.boundSession != session || _uiState.value.conversationId != convId) return@collect

                when (event) {
                    is MessageRealtimeEvent.ReadyForBackfill -> {
                        reconcileNewestMessages()
                    }
                    is MessageRealtimeEvent.Inserted -> {
                        val msg = event.message
                        _uiState.update { current ->
                            val isMyPendingMsg = msg.senderId == session.userId &&
                                    msg.clientRequestId == current.pendingRequestId
                            val merged = ChatMessageMerger.mergeMessages(current.messages, listOf(msg))

                            current.copy(
                                messages = merged,
                                isSending = if (isMyPendingMsg) false else current.isSending,
                                composerText = if (isMyPendingMsg) "" else current.composerText,
                                pendingRequestId = if (isMyPendingMsg) null else current.pendingRequestId,
                                sendError = if (isMyPendingMsg) null else current.sendError,
                            )
                        }
                        if (msg.senderId != session.userId) {
                            checkAndMarkRead(session, listOf(msg))
                        }
                    }
                }
            }
        }
    }

    private fun checkAndMarkRead(session: CommunicationSession, messages: List<ChatMessage>) {
        val convId = _uiState.value.conversationId
        val latestMessage = messages.maxByOrNull { it.createdAt } ?: return
        if (latestMessage.senderId == null || latestMessage.senderId == session.userId) return

        viewModelScope.launch {
            repository.markConversationRead(session, convId, latestMessage.id)
        }
    }
}
