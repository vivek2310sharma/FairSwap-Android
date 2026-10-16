package com.fairswap.core.discovery

import java.net.URI
import java.util.UUID

class PublicListingUrlBuilder(webBaseUrl: String) {
    private val origin: String? = runCatching {
        val uri = URI(webBaseUrl.trim())
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/")) {
            null
        } else {
            uri.toString().trimEnd('/')
        }
    }.getOrNull()

    fun buildListingUrl(listingId: String): String? {
        if (origin == null || !listingId.isUuid()) return null
        return "$origin/listings/$listingId"
    }

    private companion object {
        fun String.isUuid(): Boolean = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)
    }
}
