package com.fairswap.core.sellerlistings

import java.net.URI

data class SellerMediaConfig(
    val webBaseUrl: String,
) {
    val normalizedWebBaseUrl: String

    init {
        val trimmed = webBaseUrl.trim()
        require(trimmed.isNotBlank()) { "Web base URL cannot be blank." }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("Web base URL must be a valid absolute URI.", e)
        }
        require(uri.isAbsolute) { "Web base URL must be an absolute URI." }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "https") {
            "Web base URL scheme must be HTTPS."
        }
        require(uri.userInfo == null) { "Web base URL must not contain embedded user credentials." }
        require(uri.fragment == null) { "Web base URL must not contain a fragment." }

        normalizedWebBaseUrl = trimmed.removeSuffix("/")
    }
}
