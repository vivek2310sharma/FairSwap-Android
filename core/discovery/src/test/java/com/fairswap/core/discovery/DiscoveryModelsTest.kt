package com.fairswap.core.discovery

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryModelsTest {
    private val json = Json { encodeDefaults = true }
    private val id = "00000000-0000-0000-0000-000000000001"

    @Test
    fun rpcParametersSerializeCanonicalNamesAndEnums() {
        val filters = SearchFilters(
            query = "  bicycle  ", categoryId = id, cityId = id,
            condition = ListingCondition.LIKE_NEW, minRupees = "12.50",
            sort = ListingSort.PRICE_ASC,
        )
        val encoded = json.encodeToString(filters.toParams(2))
        assertTrue(encoded.contains("\"category_id\":\"$id\""))
        assertTrue(encoded.contains("\"category_slug\":null"))
        assertTrue(encoded.contains("\"condition\":\"like_new\""))
        assertTrue(encoded.contains("\"price_min\":1250"))
        assertTrue(encoded.contains("\"sort\":\"price_asc\""))
        assertTrue(encoded.contains("\"per_page\":20"))
        assertTrue(encoded.contains("\"query\":\"bicycle\""))
    }

    @Test
    fun validationBoundsAndDependentLocality() {
        assertEquals("Search text must be 100 characters or less.", SearchFilters(query = "a".repeat(101)).validate())
        assertNull(SearchFilters(query = "a".repeat(100)).validate())
        assertEquals("Select a city before a locality.", SearchFilters(localityId = id).validate())
        assertEquals("Minimum price cannot exceed maximum price.", SearchFilters(minRupees = "5", maxRupees = "4").validate())
        assertNull(SearchFilters(minRupees = "4", maxRupees = "5").validate())
        assertEquals("new", ListingCondition.NEW.wire)
        assertEquals("relevance", ListingSort.RELEVANCE.wire)
        assertNull(ListingCondition.fromWire("arbitrary"))
        assertEquals(null, SearchFilters(cityId = id, localityId = id).withCity(null).localityId)
    }

    @Test
    fun moneyConversionIsExactAndBounded() {
        assertEquals(1250, Rupees.toPaise("12.50"))
        assertEquals(0, Rupees.toPaise("0"))
        assertNull(Rupees.toPaise("-1"))
        assertNull(Rupees.toPaise("1.234"))
        assertNull(Rupees.toPaise("1e3"))
        assertNull(Rupees.toPaise("21474836.48"))
        assertNull(Rupees.toPaise("1".repeat(1000)))
        assertEquals("₹1,00,000.00", Rupees.formatPaise(10_000_000))
        assertEquals("Free", Rupees.display("free", null))
        assertEquals("Price on request", Rupees.display("fixed", null))
    }

    @Test
    fun responseDecodesItemsAndEmptyPage() {
        val empty = json.decodeFromString<SearchResponseDto>(
            """{"items":[],"total_count":0,"page":1,"per_page":20,"total_pages":0}"""
        )
        assertTrue(empty.items.isEmpty())
        assertEquals(0, empty.totalPages)
        val response = json.decodeFromString<SearchResponseDto>(
            """{"items":[{"id":"$id","seller_id":"$id","slug":"bike","title":"Bike","description":null,"condition":"good","price_type":"fixed","price_paise":12345,"pin_code":null,"category_attributes":{},"status":"active","created_at":"2026-09-01T00:00:00Z","published_at":null,"category":{"id":"$id","name":"Cycles","slug":"cycles","icon":null},"city":{"id":"$id","name":"Jaipur","state":"Rajasthan"},"locality":null,"cover_photo":null}],"total_count":1,"page":1,"per_page":20,"total_pages":1}"""
        )
        assertEquals("Bike", response.items.single().title)
        assertEquals("Jaipur", response.items.single().city.name)
    }

    @Test
    fun paginationPreservesOrderAndDataOnFailures() {
        val first = ResultsState().firstPage(SearchPage(listOf(card("a"), card("b")), 4, 1, 2))
        assertTrue(first.canLoadMore)
        assertEquals(2, first.nextPage)
        val failed = first.nextPageFailure()
        assertEquals(listOf("a", "b"), failed.items.map { it.id })
        val next = failed.more(SearchPage(listOf(card("b"), card("c")), 4, 2, 2))
        assertEquals(listOf("a", "b", "c"), next.items.map { it.id })
        assertFalse(next.canLoadMore)
        assertEquals(null, next.error)
        val stale = next.firstPageFailure()
        assertTrue(stale.isStale)
        assertEquals(3, stale.items.size)
        assertEquals(emptyList<ListingCard>(), ResultsState().items)
    }

    private fun card(id: String) = ListingCard(id, id, "Free", "Good", "Cycles", "Jaipur", null)
}
