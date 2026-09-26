package com.fairswap.core.discovery

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicMarketplaceRepositoryTest {
    private val listingId = "00000000-0000-0000-0000-000000000001"
    private val otherId = "00000000-0000-0000-0000-000000000003"
    private val sellerId = "00000000-0000-0000-0000-000000000002"
    private val urls = PublicMediaUrlBuilder("https://staging.example.test")

    @Test
    fun invalidIdsNeverReachNetwork() = runTest {
        val gateway = FakeGateway()
        val repository = PublicMarketplaceRepository(gateway, urls)
        assertEquals(PublicDetailResult.Unavailable, repository.listing("not-a-uuid"))
        assertEquals(PublicSellerResult.Unavailable, repository.seller("not-a-uuid"))
        assertEquals(0, gateway.calls)
    }

    @Test
    fun ownerReadableListingCannotBypassPublicVisibility() = runTest {
        val gateway = FakeGateway().apply { visible = { false } }
        val result = PublicMarketplaceRepository(gateway, urls).listing(listingId)
        assertEquals(PublicDetailResult.Unavailable, result)
        assertEquals(0, gateway.listingCalls)
    }

    @Test
    fun finalVisibilityCheckSuppressesDetailIfListingChangesState() = runTest {
        var checks = 0
        val gateway = FakeGateway().apply { visible = { ++checks == 1 } }
        assertEquals(PublicDetailResult.Unavailable, PublicMarketplaceRepository(gateway, urls).listing(listingId))
        assertEquals(2, checks)
    }

    @Test
    fun detailMapsPriceConditionDescriptionAttributesAndOrderedMedia() = runTest {
        val gateway = FakeGateway().apply {
            row = row(listingId).copy(
                description = "  Bike in good condition  ",
                condition = "like_new",
                priceType = "negotiable",
                pricePaise = 1250,
                categoryAttributes = buildJsonObject {
                    put("frame_size", JsonPrimitive("Medium"))
                    put("warranty", JsonPrimitive(false))
                    put("nested", buildJsonObject { put("secret", JsonPrimitive("hidden")) })
                },
            )
            media = listOf(media("00000000-0000-0000-0000-000000000005", 2), media("00000000-0000-0000-0000-000000000004", 1))
        }
        val detail = (PublicMarketplaceRepository(gateway, urls).listing(listingId) as PublicDetailResult.Found).detail
        assertEquals("Bike in good condition", detail.description)
        assertEquals("Like new", detail.condition)
        assertEquals("₹12.50", detail.formattedPrice)
        assertEquals(listOf("Frame Size", "Warranty"), detail.specifications.map { it.label })
        assertEquals(listOf("Medium", "No"), detail.specifications.map { it.value })
        assertEquals(listOf(1, 2), detail.media.map { it.displayOrder })
        assertTrue(detail.media.first().mediumUrl!!.endsWith("/medium"))
        assertTrue(detail.media.first().largeUrl!!.endsWith("/large"))
    }

    @Test
    fun sellerEmitsOnlyVerifiedPublicListings() = runTest {
        val gateway = FakeGateway().apply {
            candidates = listOf(row(listingId), row(otherId))
            visible = { it == listingId }
        }
        val profile = (PublicMarketplaceRepository(gateway, urls).seller(sellerId) as PublicSellerResult.Found).profile
        assertEquals(listOf(listingId), profile.listings.map { it.id })
        assertEquals(1, profile.displayedListingCount)
        assertEquals("₹12.50", profile.listings.single().price)
    }

    @Test
    fun profileRowReadableButZeroListingsPassPublicVisibilityMeansSellerUnavailable() = runTest {
        val gateway = FakeGateway().apply { visible = { false } }
        assertEquals(PublicSellerResult.Unavailable, PublicMarketplaceRepository(gateway, urls).seller(sellerId))
        assertEquals(1, gateway.profileCalls)
        assertEquals(1, gateway.visibilityCalls)
    }

    @Test
    fun publicCardsVisibilityFlipBeforeFinalEmissionSuppressesCard() = runTest {
        var checks = 0
        val gateway = FakeGateway().apply {
            visible = { ++checks == 1 }
            cardRows = listOf(PublicListingCardDto(
                listingId, "Private title", "good", "fixed", 1250,
                CardCategoryDto("Cycles"), CardCityDto("Jaipur"), null,
            ))
        }
        assertTrue(PublicMarketplaceRepository(gateway, urls).publicCards(listOf(listingId)).isEmpty())
        assertEquals(2, checks)
        assertEquals(1, gateway.cardQueryCalls)
    }

    @Test
    fun ownerReadablePrivateCardIsRejectedBeforeCardQuery() = runTest {
        val gateway = FakeGateway().apply {
            visible = { false }
            cardRows = listOf(PublicListingCardDto(
                listingId, "Private title", "good", "fixed", 1250,
                CardCategoryDto("Private category"), CardCityDto("Private city"), null,
            ))
        }
        assertTrue(PublicMarketplaceRepository(gateway, urls).publicCards(listOf(listingId)).isEmpty())
        assertEquals(0, gateway.cardQueryCalls)
        assertEquals(0, gateway.mediaQueryCalls)
    }

    @Test
    fun publicCardVisibilityConcurrencyNeverExceedsFour() = runTest {
        val ids = (1..20).map { "00000000-0000-0000-0000-${it.toString().padStart(12, '0')}" }
        var active = 0
        var maximum = 0
        val gateway = FakeGateway().apply {
            cardRows = ids.map { id -> PublicListingCardDto(
                id, "Public $id", "good", "fixed", 100,
                CardCategoryDto("Cycles"), CardCityDto("Jaipur"), null,
            ) }
            visibilityProbe = {
                active++
                maximum = maxOf(maximum, active)
                yield()
                active--
                true
            }
        }
        val cards = PublicMarketplaceRepository(gateway, urls).publicCards(ids)
        assertEquals(20, cards.size)
        assertEquals(20, gateway.cardQueryIds.size)
        assertTrue(maximum <= 4)
    }

    @Test
    fun dtoDecodeAndMediaBuilderNeverExposeOriginals() {
        val dto = Json.decodeFromString<PublicListingDetailDto>(
            """{"id":"$listingId","seller_id":"$sellerId","title":"Bike","description":null,"created_at":"2026-09-01T00:00:00Z","category":{"id":"$listingId","name":"Cycles","slug":"cycles"},"city":{"id":"$listingId","name":"Jaipur"},"locality":null} """
        )
        assertNull(dto.description)
        assertTrue(dto.categoryAttributes.isEmpty())
        for (url in listOfNotNull(urls.thumbnail(listingId, otherId), urls.medium(listingId, otherId), urls.large(listingId, otherId))) {
            assertFalse(url.contains("/original"))
            assertFalse(url.contains("storage/v1"))
        }
        assertNull(PublicMediaUrlBuilder("").medium(listingId, otherId))
        assertNull(PublicMediaUrlBuilder("http://example.test").large(listingId, otherId))
    }

    private fun row(id: String) = PublicListingDetailDto(
        id = id, sellerId = sellerId, title = "Bike", priceType = "fixed", pricePaise = 1250,
        createdAt = "2026-09-01T00:00:00Z", category = Category(listingId, "Cycles", "cycles"),
        city = City(listingId, "Jaipur"), categoryAttributes = JsonObject(emptyMap()),
    )

    private fun media(id: String, order: Int) = PublicListingMediaDto(id, 800, 600, order)

    private inner class FakeGateway : PublicMarketplaceGateway {
        var calls = 0
        var listingCalls = 0
        var profileCalls = 0
        var visibilityCalls = 0
        var cardQueryCalls = 0
        var mediaQueryCalls = 0
        var cardQueryIds = emptyList<String>()
        var row = row(listingId)
        var media = emptyList<PublicListingMediaDto>()
        var candidates = listOf(row)
        var cardRows = emptyList<PublicListingCardDto>()
        var visible: (String) -> Boolean = { true }
        var visibilityProbe: (suspend (String) -> Boolean)? = null
        override suspend fun isVisible(listingId: String): Boolean {
            calls++; visibilityCalls++
            return visibilityProbe?.invoke(listingId) ?: visible(listingId)
        }
        override suspend fun listing(listingId: String): PublicListingDetailDto? { calls++; listingCalls++; return row }
        override suspend fun profile(sellerId: String): PublicSellerProfileDto? {
            calls++; profileCalls++
            return PublicSellerProfileDto(sellerId, "Seller", null, "Jaipur", "Rajasthan", "2026-09-01T00:00:00Z")
        }
        override suspend fun readyMedia(listingId: String): List<PublicListingMediaDto> { calls++; return media }
        override suspend fun sellerCandidates(sellerId: String): List<PublicListingDetailDto> { calls++; return candidates }
        override suspend fun publicCards(ids: List<String>): List<PublicListingCardDto> {
            cardQueryCalls++; cardQueryIds = ids
            return cardRows
        }
        override suspend fun publicCardMedia(ids: List<String>): List<PublicListingCardMediaDto> {
            mediaQueryCalls++
            return emptyList()
        }
    }
}
