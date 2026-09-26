package com.fairswap.app

import com.fairswap.app.ui.ListingDetailRoute
import com.fairswap.app.ui.SellerProfileRoute
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PublicRoutesTest {
    @Test
    fun typedRouteIdsSurviveSerialization() {
        val listing = ListingDetailRoute("00000000-0000-0000-0000-000000000001")
        val seller = SellerProfileRoute("00000000-0000-0000-0000-000000000002")
        assertEquals(listing, Json.decodeFromString<ListingDetailRoute>(Json.encodeToString(listing)))
        assertEquals(seller, Json.decodeFromString<SellerProfileRoute>(Json.encodeToString(seller)))
    }
}
