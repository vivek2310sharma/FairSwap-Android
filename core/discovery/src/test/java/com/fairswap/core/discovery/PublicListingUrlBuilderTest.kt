package com.fairswap.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicListingUrlBuilderTest {

    private val validListingId = "00000000-0000-0000-0000-000000000001"

    @Test
    fun validBaseUrlAndListingId_buildsCanonicalUrl() {
        val builder = PublicListingUrlBuilder("https://fairswap.in/")
        assertEquals("https://fairswap.in/listings/$validListingId", builder.buildListingUrl(validListingId))
    }

    @Test
    fun validBaseUrlWithoutTrailingSlash_buildsCanonicalUrl() {
        val builder = PublicListingUrlBuilder("https://fairswap.in")
        assertEquals("https://fairswap.in/listings/$validListingId", builder.buildListingUrl(validListingId))
    }

    @Test
    fun invalidListingId_returnsNull() {
        val builder = PublicListingUrlBuilder("https://fairswap.in")
        assertNull(builder.buildListingUrl("not-a-valid-uuid"))
    }

    @Test
    fun invalidOrigin_returnsNull() {
        val builder = PublicListingUrlBuilder("http://fairswap.in") // HTTP rejected
        assertNull(builder.buildListingUrl(validListingId))
    }
}
