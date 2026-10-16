package com.fairswap.core.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebRoutesTest {

    @Test
    fun validHttpsBaseUrlResolvesRoutesCorrectly() {
        val routes = WebRoutes("https://staging.fairswap.in/")
        assertEquals("https://staging.fairswap.in/privacy", routes.privacyPolicy)
        assertEquals("https://staging.fairswap.in/terms", routes.terms)
        assertEquals("https://staging.fairswap.in/acceptable-use", routes.acceptableUse)
        assertEquals("https://staging.fairswap.in/safety", routes.safety)
        assertEquals("https://staging.fairswap.in/account/support", routes.support)
        assertEquals("https://staging.fairswap.in/account/privacy", routes.privacyRequests)
    }

    @Test
    fun validBaseUrlWithoutTrailingSlashResolvesRoutesCorrectly() {
        val routes = WebRoutes("https://staging.fairswap.in")
        assertEquals("https://staging.fairswap.in/privacy", routes.privacyPolicy)
        assertEquals("https://staging.fairswap.in/terms", routes.terms)
    }

    @Test
    fun invalidBaseUrlsReturnNullRoutes() {
        assertNull(WebRoutes("http://staging.fairswap.in").privacyPolicy) // HTTP rejected
        assertNull(WebRoutes("https://user:pass@staging.fairswap.in").privacyPolicy) // UserInfo rejected
        assertNull(WebRoutes("https://staging.fairswap.in?query=1").privacyPolicy) // Query rejected
        assertNull(WebRoutes("https://staging.fairswap.in#fragment").privacyPolicy) // Fragment rejected
        assertNull(WebRoutes("https://staging.fairswap.in/extra/path").privacyPolicy) // Non-root path rejected
        assertNull(WebRoutes("invalid-url").privacyPolicy)
    }
}
