package com.fairswap.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicMediaUrlBuilderTest {
    private val listing = "00000000-0000-0000-0000-000000000001"
    private val photo = "00000000-0000-0000-0000-000000000002"

    @Test
    fun onlyPublicThumbnailEndpointIsBuilt() {
        val url = PublicMediaUrlBuilder("https://staging.example.test/").thumbnail(listing, photo)!!
        assertEquals("https://staging.example.test/api/listings/$listing/media/$photo/thumb", url)
        assertFalse(url.contains("/original"))
        assertFalse(url.contains("storage/v1"))
    }

    @Test
    fun missingOrUnsafeBaseAndIdsYieldPlaceholder() {
        assertNull(PublicMediaUrlBuilder("").thumbnail(listing, photo))
        assertNull(PublicMediaUrlBuilder("http://example.test").thumbnail(listing, photo))
        assertNull(PublicMediaUrlBuilder("https://example.test/other").thumbnail(listing, photo))
        assertNull(PublicMediaUrlBuilder("https://example.test").thumbnail("../other", photo))
        assertNull(PublicMediaUrlBuilder("https://example.test").thumbnail(listing, null))
    }
}
