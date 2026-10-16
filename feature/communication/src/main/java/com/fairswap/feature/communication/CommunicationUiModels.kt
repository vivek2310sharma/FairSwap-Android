package com.fairswap.feature.communication

import com.fairswap.core.communication.CommunicationSession

enum class InboxFilter {
    ALL,
    BUYING,
    SELLING,
}

sealed interface CommunicationNavigationEvent {
    data class NavigateToAuth(val session: CommunicationSession) : CommunicationNavigationEvent
    data class NavigateToConversation(val session: CommunicationSession, val conversationId: String, val listingId: String? = null) : CommunicationNavigationEvent
    data class NavigateToNewInquiry(val session: CommunicationSession, val listingId: String) : CommunicationNavigationEvent
    data class NavigateToMyListings(val session: CommunicationSession) : CommunicationNavigationEvent
}
