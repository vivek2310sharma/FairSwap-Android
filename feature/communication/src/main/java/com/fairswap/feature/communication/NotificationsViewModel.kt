package com.fairswap.feature.communication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.communication.InAppNotification
import com.fairswap.core.communication.NotificationDestination
import com.fairswap.core.communication.NotificationDestinationParser
import com.fairswap.core.communication.NotificationRealtimeEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class NotificationsUiState(
    val boundSession: CommunicationSession? = null,
    val notifications: List<InAppNotification> = emptyList(),
    val isLoading: Boolean = false,
    val mutatingIds: Set<String> = emptySet(),
    val isMarkingAllRead: Boolean = false,
    val errorMessage: String? = null,
) {
    val unreadCount: Int get() = notifications.count { it.readAt == null }
    val hasUnread: Boolean get() = unreadCount > 0
}

sealed class FetchOutcome {
    data class AppliedSuccess(val notifications: List<InAppNotification>) : FetchOutcome()
    object SupersededSuccess : FetchOutcome()
    data class RetryableFailure(val message: String) : FetchOutcome()
    data class OtherFailure(val message: String) : FetchOutcome()
    data class AuthFailure(val failure: CommunicationFailure) : FetchOutcome()
    object StaleOrInvalidSession : FetchOutcome()
}

private data class AcceptedResultRecord(
    val session: CommunicationSession,
    val revision: Long,
    val outcomeKind: AcceptedOutcomeKind
)

private enum class AcceptedOutcomeKind {
    SUCCESS,
    RETRYABLE_FAILURE,
    OTHER_FAILURE,
    AUTH_FAILURE
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: CommunicationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CommunicationNavigationEvent>()
    val events: SharedFlow<CommunicationNavigationEvent> = _events.asSharedFlow()

    private var isVisible = false
    private var requestRevision = 0L
    private var lastAcceptedRecord: AcceptedResultRecord? = null

    private var loadJob: Job? = null
    private val markReadJobs = mutableMapOf<String, Job>()
    private var markAllJob: Job? = null
    private var visibleLifecycleJob: Job? = null
    private val visibleLifecycleMutex = Mutex()
    private var visibleLifecycleOwner: Any? = null
    private var loadingOwnerToken: Any? = null

    fun bind(session: CommunicationSession?) {
        val current = _uiState.value.boundSession
        if (current == session) return

        cancelAllWork()

        _uiState.update {
            NotificationsUiState(
                boundSession = session,
                notifications = emptyList(),
                isLoading = false,
                mutatingIds = emptySet(),
                isMarkingAllRead = false,
                errorMessage = null,
            )
        }

        if (session != null) {
            if (isVisible) {
                startVisibleLifecycle()
            } else {
                refresh()
            }
        }
    }

    fun onVisible() {
        isVisible = true
        startVisibleLifecycle()
    }

    fun onNotVisible() {
        isVisible = false
        visibleLifecycleOwner = null
        if (visibleLifecycleJob != null) {
            loadingOwnerToken = null
            visibleLifecycleJob?.cancel()
            visibleLifecycleJob = null
        }
    }

    fun refresh() {
        val session = _uiState.value.boundSession ?: return

        if (isVisible) {
            startVisibleLifecycle()
            return
        }

        val oldJob = loadJob
        val token = Any()
        loadingOwnerToken = token
        val revision = ++requestRevision

        val currentJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                val res = performFetchNotificationsInternal(session)
                processFetchResult(session, revision, res)
            } finally {
                if ((loadingOwnerToken === token || loadingOwnerToken == null) && _uiState.value.boundSession == session) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
        loadJob = currentJob
        oldJob?.cancel()
        currentJob.start()
    }

    fun onNotificationTapped(notificationId: String) {
        val state = _uiState.value
        val session = state.boundSession ?: return
        val notification = state.notifications.find { it.id == notificationId } ?: return

        val destination = NotificationDestinationParser.parseDestination(notification.type, notification.link)

        if (notification.readAt != null) {
            emitDestinationEvent(session, destination)
            return
        }

        if (notificationId in state.mutatingIds || markReadJobs.containsKey(notificationId)) return

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val currentJob = coroutineContext[Job]
            try {
                _uiState.update { it.copy(mutatingIds = it.mutatingIds + notificationId, errorMessage = null) }

                val res = performMarkNotificationReadInternal(session, notificationId)

                if (_uiState.value.boundSession != session || !isActive) return@launch

                when (res) {
                    is CommunicationResult.Success -> {
                        val reloadOutcome = reconcileAuthoritatively(session)
                        if (isActive && _uiState.value.boundSession == session && (reloadOutcome is FetchOutcome.AppliedSuccess || reloadOutcome is FetchOutcome.SupersededSuccess)) {
                            emitDestinationEvent(session, destination)
                        }
                    }
                    is CommunicationResult.Failure -> {
                        val error = res.error
                        if (isAuthFailure(error)) {
                            _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                        } else {
                            val safeMessage = CommunicationUiText.toNotificationUserFacingMessage(error)
                            _uiState.update { it.copy(errorMessage = safeMessage) }
                        }
                    }
                }
            } finally {
                if (markReadJobs[notificationId] === currentJob) {
                    markReadJobs.remove(notificationId)
                }
                if (_uiState.value.boundSession == session) {
                    _uiState.update { it.copy(mutatingIds = it.mutatingIds - notificationId) }
                }
            }
        }

        markReadJobs[notificationId] = job
        job.start()
    }

    fun markAllRead() {
        val state = _uiState.value
        val session = state.boundSession ?: return
        if (!state.hasUnread || state.isMarkingAllRead) return

        markAllJob?.cancel()
        markAllJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isMarkingAllRead = true, errorMessage = null) }

                val res = performMarkAllNotificationsReadInternal(session)

                if (_uiState.value.boundSession != session || !isActive) return@launch

                when (res) {
                    is CommunicationResult.Success -> {
                        reconcileAuthoritatively(session)
                    }
                    is CommunicationResult.Failure -> {
                        val error = res.error
                        if (isAuthFailure(error)) {
                            _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                        } else {
                            val safeMessage = CommunicationUiText.toNotificationUserFacingMessage(error)
                            _uiState.update { it.copy(errorMessage = safeMessage) }
                        }
                    }
                }
            } finally {
                if (_uiState.value.boundSession == session) {
                    _uiState.update { it.copy(isMarkingAllRead = false) }
                }
            }
        }
    }

    internal suspend fun performFetchNotificationsInternal(session: CommunicationSession): CommunicationResult<List<InAppNotification>> {
        return repository.fetchNotifications(session)
    }

    internal suspend fun performMarkNotificationReadInternal(session: CommunicationSession, notificationId: String): CommunicationResult<Unit> {
        return repository.markNotificationRead(session, notificationId)
    }

    internal suspend fun performMarkAllNotificationsReadInternal(session: CommunicationSession): CommunicationResult<Unit> {
        return repository.markAllNotificationsRead(session)
    }

    private fun startVisibleLifecycle() {
        val session = _uiState.value.boundSession ?: return

        val ownerToken = Any()
        visibleLifecycleOwner = ownerToken
        val revision = ++requestRevision

        val currentJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            visibleLifecycleMutex.withLock {
                if (!isVisible || visibleLifecycleOwner !== ownerToken || _uiState.value.boundSession != session) return@launch

                val fetchToken = Any()
                loadingOwnerToken = fetchToken

                val outcome = try {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                    val res = performFetchNotificationsInternal(session)

                    if (!isVisible || visibleLifecycleOwner !== ownerToken || _uiState.value.boundSession != session || !currentCoroutineContext().job.isActive) {
                        FetchOutcome.StaleOrInvalidSession
                    } else {
                        processFetchResult(session, revision, res)
                    }
                } finally {
                    if ((loadingOwnerToken === fetchToken || loadingOwnerToken == null) && _uiState.value.boundSession == session) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }

                if (!isVisible || visibleLifecycleOwner !== ownerToken || _uiState.value.boundSession != session) return@launch

                when (outcome) {
                    is FetchOutcome.AppliedSuccess, FetchOutcome.SupersededSuccess -> {
                        repository.observeNotificationEvents(session).collect { event ->
                            if (!isVisible || visibleLifecycleOwner !== ownerToken || _uiState.value.boundSession != session) return@collect

                            when (event) {
                                is NotificationRealtimeEvent.Inserted -> {
                                    if (event.notification.userId == session.userId) {
                                        reconcileAuthoritatively(session)
                                    }
                                }
                                is NotificationRealtimeEvent.ReadyForBackfill -> {
                                    reconcileAuthoritatively(session)
                                }
                            }
                        }
                    }
                    is FetchOutcome.RetryableFailure, is FetchOutcome.OtherFailure, is FetchOutcome.AuthFailure, FetchOutcome.StaleOrInvalidSession -> {
                        return@launch
                    }
                }
            }
        }

        val oldJob = visibleLifecycleJob
        visibleLifecycleJob = currentJob
        oldJob?.cancel()
        currentJob.start()
    }

    private suspend fun reconcileAuthoritatively(session: CommunicationSession): FetchOutcome {
        val token = Any()
        loadingOwnerToken = token
        val revision = ++requestRevision
        return try {
            val res = performFetchNotificationsInternal(session)
            if (_uiState.value.boundSession != session || !currentCoroutineContext().job.isActive) {
                FetchOutcome.StaleOrInvalidSession
            } else {
                processFetchResult(session, revision, res)
            }
        } finally {
            if ((loadingOwnerToken === token || loadingOwnerToken == null) && _uiState.value.boundSession == session) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun processFetchResult(
        session: CommunicationSession,
        revision: Long,
        res: CommunicationResult<List<InAppNotification>>,
    ): FetchOutcome {
        if (_uiState.value.boundSession != session) return FetchOutcome.StaleOrInvalidSession

        val lastRecord = lastAcceptedRecord

        if (lastRecord != null && lastRecord.session == session && revision < lastRecord.revision) {
            return when (res) {
                is CommunicationResult.Success -> {
                    if (lastRecord.outcomeKind == AcceptedOutcomeKind.SUCCESS) {
                        FetchOutcome.SupersededSuccess
                    } else {
                        FetchOutcome.StaleOrInvalidSession
                    }
                }
                is CommunicationResult.Failure -> {
                    FetchOutcome.StaleOrInvalidSession
                }
            }
        }

        return when (res) {
            is CommunicationResult.Success -> {
                lastAcceptedRecord = AcceptedResultRecord(session, revision, AcceptedOutcomeKind.SUCCESS)
                _uiState.update {
                    it.copy(
                        notifications = res.value,
                        errorMessage = null,
                    )
                }
                FetchOutcome.AppliedSuccess(res.value)
            }
            is CommunicationResult.Failure -> {
                val error = res.error
                if (isAuthFailure(error)) {
                    lastAcceptedRecord = AcceptedResultRecord(session, revision, AcceptedOutcomeKind.AUTH_FAILURE)
                    _events.emit(CommunicationNavigationEvent.NavigateToAuth(session))
                    FetchOutcome.AuthFailure(error)
                } else {
                    val safeMessage = CommunicationUiText.toNotificationUserFacingMessage(error)
                    _uiState.update {
                        it.copy(
                            errorMessage = safeMessage,
                        )
                    }
                    if (error == CommunicationFailure.NetworkUnavailable || error == CommunicationFailure.ServiceUnavailable) {
                        lastAcceptedRecord = AcceptedResultRecord(session, revision, AcceptedOutcomeKind.RETRYABLE_FAILURE)
                        FetchOutcome.RetryableFailure(safeMessage)
                    } else {
                        lastAcceptedRecord = AcceptedResultRecord(session, revision, AcceptedOutcomeKind.OTHER_FAILURE)
                        FetchOutcome.OtherFailure(safeMessage)
                    }
                }
            }
        }
    }

    private fun emitDestinationEvent(session: CommunicationSession, destination: NotificationDestination?) {
        viewModelScope.launch {
            if (_uiState.value.boundSession != session) return@launch
            when (destination) {
                is NotificationDestination.Conversation -> {
                    _events.emit(
                        CommunicationNavigationEvent.NavigateToConversation(
                            session = session,
                            conversationId = destination.conversationId,
                        )
                    )
                }
                is NotificationDestination.MyListings -> {
                    _events.emit(CommunicationNavigationEvent.NavigateToMyListings(session))
                }
                null -> Unit
            }
        }
    }

    private fun cancelAllWork() {
        loadJob?.cancel()
        loadJob = null
        val oldJobs = markReadJobs.values.toList()
        markReadJobs.clear()
        oldJobs.forEach { it.cancel() }
        markAllJob?.cancel()
        markAllJob = null
        visibleLifecycleOwner = null
        loadingOwnerToken = null
        visibleLifecycleJob?.cancel()
        visibleLifecycleJob = null
        lastAcceptedRecord = null
    }

    private fun isAuthFailure(error: CommunicationFailure): Boolean {
        return error == CommunicationFailure.AuthRequired ||
                error == CommunicationFailure.PolicyReviewRequired ||
                error == CommunicationFailure.AccountRestricted
    }
}
