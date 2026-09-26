package com.fairswap.core.discovery

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import java.net.URI
import java.util.UUID

interface DiscoveryDataSource {
    suspend fun search(filters: SearchFilters, page: Int): SearchPage
    suspend fun categories(): List<Category>
    suspend fun cities(): List<City>
    suspend fun localities(cityId: String): List<Locality>
}

class PublicMediaUrlBuilder(webBaseUrl: String) {
    private val origin: String? = runCatching {
        val uri = URI(webBaseUrl.trim())
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || uri.rawPath !in listOf("", "/")) {
            null
        } else uri.toString().trimEnd('/')
    }.getOrNull()

    fun thumbnail(listingId: String, mediaId: String?): String? {
        if (origin == null || mediaId == null || !listingId.isUuid() || !mediaId.isUuid()) return null
        return "$origin/api/listings/$listingId/media/$mediaId/thumb"
    }
}

private fun String.isUuid() = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)

class DiscoveryRepository(
    private val supabase: SupabaseClient,
    private val mediaUrls: PublicMediaUrlBuilder,
) : DiscoveryDataSource {
    override suspend fun search(filters: SearchFilters, page: Int): SearchPage {
        val response = supabase.postgrest
            .rpc("search_public_listings", filters.toParams(page))
            .decodeAs<SearchResponseDto>()
        return SearchPage(
            items = response.items.map { item ->
                ListingCard(
                    id = item.id,
                    title = item.title,
                    price = Rupees.display(item.priceType, item.pricePaise),
                    condition = ListingCondition.fromWire(item.condition)?.label ?: "Condition not specified",
                    category = item.category.name,
                    location = listOfNotNull(item.locality?.name, item.city.name).joinToString(", "),
                    thumbnailUrl = mediaUrls.thumbnail(item.id, item.coverPhoto?.id),
                )
            },
            totalCount = response.totalCount,
            page = response.page,
            totalPages = response.totalPages,
        )
    }

    override suspend fun categories(): List<Category> = supabase.from("categories")
        .select(columns = Columns.list("id, name, slug, icon")) {
            filter { eq("is_active", true) }
            order("sort_order", Order.ASCENDING)
            order("name", Order.ASCENDING)
        }.decodeList()

    override suspend fun cities(): List<City> = supabase.from("cities")
        .select(columns = Columns.list("id, name, state")) {
            filter { eq("is_active", true) }
            order("name", Order.ASCENDING)
        }.decodeList()

    override suspend fun localities(cityId: String): List<Locality> {
        require(cityId.isUuid())
        return supabase.from("localities")
            .select(columns = Columns.list("id, name")) {
                filter {
                    eq("is_active", true)
                    eq("city_id", cityId)
                }
                order("name", Order.ASCENDING)
            }.decodeList()
    }
}
