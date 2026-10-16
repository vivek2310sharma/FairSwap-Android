package com.fairswap.core.account

import java.net.URI

class WebRoutes(webBaseUrl: String) {
    private val origin: String? = runCatching {
        val uri = URI(webBaseUrl.trim())
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/")) {
            null
        } else {
            uri.toString().trimEnd('/')
        }
    }.getOrNull()

    val privacyPolicy: String? get() = origin?.let { "$it/privacy" }
    val terms: String? get() = origin?.let { "$it/terms" }
    val acceptableUse: String? get() = origin?.let { "$it/acceptable-use" }
    val safety: String? get() = origin?.let { "$it/safety" }
    val support: String? get() = origin?.let { "$it/account/support" }
    val privacyRequests: String? get() = origin?.let { "$it/account/privacy" }
}
