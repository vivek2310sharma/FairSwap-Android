package com.fairswap.feature.listingdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.communication.CommunicationFailure
import com.fairswap.core.communication.CommunicationRepository
import com.fairswap.core.communication.CommunicationResult
import com.fairswap.core.communication.CommunicationSession
import com.fairswap.core.discovery.PublicDetailResult
import com.fairswap.core.discovery.PublicListingDetail
import com.fairswap.core.discovery.PublicMarketplaceDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ListingDetailUiState(
    val detail: PublicListingDetail? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val error: String? = null,
    val isMessagingActionLoading: Boolean = false,
    val messagingActionError: String? = null,
)

sealed interface ListingDetailMessagingEvent {
    data class NavigateToConversation(val session: CommunicationSession, val listingId: String, val conversationId: String) : ListingDetailMessagingEvent
    data class NavigateToNewInquiry(val session: CommunicationSession, val listingId: String) : ListingDetailMessagingEvent
    data class NavigateToAuth(val session: CommunicationSession?) : ListingDetailMessagingEvent
}

@HiltViewModel
class ListingDetailViewModel @Inject constructor(
    private val repository: PublicMarketplaceDataSource,
    private val favourites: com.fairswap.core.favourites.FavouritesRepository,
    private val communicationRepository: CommunicationRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ListingDetailUiState())
    val state: StateFlow<ListingDetailUiState> = mutableState.asStateFlow()

    private val _messagingEvents = MutableSharedFlow<ListingDetailMessagingEvent>()
    val messagingEvents: SharedFlow<ListingDetailMessagingEvent> = _messagingEvents.asSharedFlow()

    private var currentId: String? = null
    private var job: Job? = null
    private var generation = 0L

    fun syncFavourite(id: String) { viewModelScope.launch { favourites.lookup(listOf(id)) } }

    fun setFavourite(id: String, desired: Boolean, onResult: (com.fairswap.core.favourites.FavouriteMutationResult) -> Unit) {
        viewModelScope.launch { onResult(favourites.setFavourite(id, desired)) }
    }

    fun load(id: String) {
        if (id == currentId && (mutableState.value.loading || mutableState.value.detail != null || mutableState.value.unavailable)) return
        request(id)
    }

    fun retry() { currentId?.let(::request) }

    fun onMessageSellerClicked(session: CommunicationSession?) {
        val detail = mutableState.value.detail
        val listingId = currentId ?: detail?.id ?: return

        if (session == null) {
            viewModelScope.launch {
                _messagingEvents.emit(ListingDetailMessagingEvent.NavigateToAuth(null))
            }
            return
        }

        if (detail != null && session.userId == detail.sellerId) {
            // Owner cannot message self
            return
        }

        if (mutableState.value.isMessagingActionLoading) return // Duplicate tap lock

        viewModelScope.launch {
            mutableState.update { it.copy(isMessagingActionLoading = true, messagingActionError = null) }

            val res = communicationRepository.findExistingConversation(session, listingId)

            mutableState.update { it.copy(isMessagingActionLoading = false) }

            when (res) {
                is CommunicationResult.Success -> {
                    val existing = res.value
                    if (existing != null) {
                        _messagingEvents.emit(
                            ListingDetailMessagingEvent.NavigateToConversation(session, listingId, existing.id)
                        )
                    } else {
                        _messagingEvents.emit(
                            ListingDetailMessagingEvent.NavigateToNewInquiry(session, listingId)
                        )
                    }
                }
                is CommunicationResult.Failure -> {
                    val error = res.error
                    if (error == CommunicationFailure.AuthRequired ||
                        error == CommunicationFailure.PolicyReviewRequired ||
                        error == CommunicationFailure.AccountRestricted
                    ) {
                        _messagingEvents.emit(ListingDetailMessagingEvent.NavigateToAuth(session))
                    } else {
                        mutableState.update {
                            it.copy(messagingActionError = "Unable to start conversation. Try again.")
                        }
                    }
                }
            }
        }
    }

    private fun request(id: String) {
        job?.cancel()
        val sameId = id == currentId
        currentId = id
        val requestGeneration = ++generation
        mutableState.update { it.copy(detail = if (sameId) it.detail else null, loading = true, unavailable = false, error = null) }
        job = viewModelScope.launch {
            try {
                val result = withTimeout(15_000) { repository.listing(id) }
                if (requestGeneration == generation) mutableState.update { state ->
                    when (result) {
                        is PublicDetailResult.Found -> state.copy(detail = result.detail, unavailable = false)
                        PublicDetailResult.Unavailable -> state.copy(detail = null, unavailable = true)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this listing. Retry.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation) mutableState.update { it.copy(error = "Unable to load this listing. Retry.") }
            } finally {
                if (requestGeneration == generation) mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
