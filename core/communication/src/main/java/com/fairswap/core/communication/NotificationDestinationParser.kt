package com.fairswap.core.communication

import java.util.UUID

sealed interface NotificationDestination {
    data class Conversation(val conversationId: String) : NotificationDestination
    data object MyListings : NotificationDestination
}

object NotificationDestinationParser {
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun parseDestination(type: String, link: String?): NotificationDestination? {
        if (link.isNullOrBlank()) return null

        return when (type) {
            "new_message" -> {
                val prefix = "/account/messages/"
                if (!link.startsWith(prefix)) return null
                val rawUuid = link.removePrefix(prefix)
                if (!uuidRegex.matches(rawUuid)) return null
                try {
                    UUID.fromString(rawUuid)
                    NotificationDestination.Conversation(rawUuid)
                } catch (_: Exception) {
                    null
                }
            }
            "listing_rejected" -> {
                if (link == "/account/listings") NotificationDestination.MyListings else null
            }
            else -> null
        }
    }
}
