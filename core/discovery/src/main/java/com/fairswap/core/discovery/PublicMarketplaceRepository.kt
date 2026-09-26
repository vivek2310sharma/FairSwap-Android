package com.fairswap.core.discovery

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface PublicMarketplaceDataSource {
    suspend fun listing(id: String): PublicDetailResult
    suspend fun seller(id: String): PublicSellerResult
    suspend fun publicCards(ids: List<String>): Map<String, ListingCard>
}

interface PublicMarketplaceGateway {
    suspend fun isVisible(listingId: String): Boolean
    suspend fun listing(listingId: String): PublicListingDetailDto?
    suspend fun profile(sellerId: String): PublicSellerProfileDto?
    suspend fun readyMedia(listingId: String): List<PublicListingMediaDto>
    suspend fun sellerCandidates(sellerId: String): List<PublicListingDetailDto>
    suspend fun publicCards(ids: List<String>): List<PublicListingCardDto>
    suspend fun publicCardMedia(ids: List<String>): List<PublicListingCardMediaDto>
}

@Serializable
private data class ListingVisibilityParams(@SerialName("listing_id") val listingId: String)

class SupabasePublicMarketplaceGateway(private val supabase: SupabaseClient) : PublicMarketplaceGateway {
    override suspend fun isVisible(listingId: String): Boolean = supabase.postgrest
        .rpc("is_listing_publicly_visible", ListingVisibilityParams(listingId)).decodeAs<Boolean>()

    override suspend fun listing(listingId: String): PublicListingDetailDto? = supabase.from("listings")
        .select(columns = LISTING_COLUMNS) { filter { eq("id", listingId) }; limit(1) }
        .decodeList<PublicListingDetailDto>().firstOrNull()

    override suspend fun profile(sellerId: String): PublicSellerProfileDto? = supabase.from("profiles")
        .select(columns = Columns.list("id,display_name,bio,city,state,created_at")) {
            filter { eq("id", sellerId) }; limit(1)
        }.decodeList<PublicSellerProfileDto>().firstOrNull()

    override suspend fun readyMedia(listingId: String): List<PublicListingMediaDto> = supabase.from("listing_media")
        .select(columns = Columns.list("id,width,height,display_order")) {
            filter { eq("listing_id", listingId); eq("processing_status", "ready") }
            order("display_order", Order.ASCENDING)
            limit(20)
        }.decodeList()

    override suspend fun sellerCandidates(sellerId: String): List<PublicListingDetailDto> = supabase.from("listings")
        .select(columns = LISTING_COLUMNS) {
            filter { eq("seller_id", sellerId); eq("status", "active") }
            order("created_at", Order.DESCENDING)
            limit(20)
        }.decodeList()

    override suspend fun publicCards(ids: List<String>): List<PublicListingCardDto> = supabase.from("listings")
        .select(columns = Columns.list(
            "id,title,condition,price_type,price_paise,category:categories(name),city:cities(name),locality:localities(name)"
        )) {
            filter { isIn("id", ids) }
            limit(ids.size.toLong())
        }.decodeList()

    override suspend fun publicCardMedia(ids: List<String>): List<PublicListingCardMediaDto> = if (ids.isEmpty()) emptyList() else
        supabase.from("listing_media").select(columns = Columns.list("listing_id,id,display_order")) {
            filter { isIn("listing_id", ids); eq("processing_status", "ready") }
            order("display_order", Order.ASCENDING)
            limit(ids.size.toLong() * 20L)
        }.decodeList()

    private companion object {
        val LISTING_COLUMNS = Columns.list(
            "id,seller_id,title,description,condition,price_type,price_paise,category_attributes,created_at,published_at," +
                "category:categories(id,name,slug,icon),city:cities(id,name,state),locality:localities(id,name)"
        )
    }
}

class PublicMarketplaceRepository(
    private val gateway: PublicMarketplaceGateway,
    private val mediaUrls: PublicMediaUrlBuilder,
) : PublicMarketplaceDataSource {
    override suspend fun listing(id: String): PublicDetailResult {
        if (!validPublicId(id)) return PublicDetailResult.Unavailable
        if (!gateway.isVisible(id)) return PublicDetailResult.Unavailable
        val row = gateway.listing(id) ?: return PublicDetailResult.Unavailable
        if (row.id != id) return PublicDetailResult.Unavailable
        val (profile, mediaRows) = coroutineScope {
            val profile = async { gateway.profile(row.sellerId) }
            val media = async { gateway.readyMedia(id) }
            profile.await() to media.await()
        }
        val seller = profile ?: return PublicDetailResult.Unavailable
        val media = mediaRows.map { row ->
            PublicListingMedia(
                row.id, row.width, row.height, row.displayOrder,
                mediaUrls.medium(id, row.id), mediaUrls.large(id, row.id),
            )
        }
        val detail = row.toDomain(seller.toDomain(), media)
        if (!gateway.isVisible(id)) return PublicDetailResult.Unavailable
        return PublicDetailResult.Found(detail)
    }

    override suspend fun seller(id: String): PublicSellerResult {
        if (!validPublicId(id)) return PublicSellerResult.Unavailable
        val profile = gateway.profile(id) ?: return PublicSellerResult.Unavailable
        val candidates = gateway.sellerCandidates(id).filter { it.sellerId == id }.take(20)
        // A public listing proves the seller's account is currently active. Owner-expanded profile RLS alone cannot.
        val visible = candidates.chunked(4).flatMap { batch ->
            coroutineScope { batch.map { row -> async { row.takeIf { gateway.isVisible(row.id) } } }.awaitAll() }
        }.filterNotNull()
        if (visible.isEmpty()) return PublicSellerResult.Unavailable
        val cards = visible.map { row ->
            ListingCard(
                id = row.id,
                title = row.title,
                price = Rupees.display(row.priceType, row.pricePaise),
                condition = ListingCondition.fromWire(row.condition)?.label ?: "Condition not specified",
                category = row.category.name,
                location = listOfNotNull(row.locality?.name, row.city.name).joinToString(", "),
                thumbnailUrl = null,
            )
        }
        return PublicSellerResult.Found(profile.toDomain(cards))
    }

    override suspend fun publicCards(ids: List<String>): Map<String, ListingCard> {
        val boundedIds = ids.distinct().filter(::validPublicId).take(20)
        if (boundedIds.isEmpty()) return emptyMap()
        val initiallyVisible = boundedIds.chunked(4).flatMap { batch ->
            coroutineScope { batch.map { id -> async { id.takeIf { gateway.isVisible(it) } } }.awaitAll() }
        }.filterNotNull().toSet()
        if (initiallyVisible.isEmpty()) return emptyMap()
        val rows = gateway.publicCards(initiallyVisible.toList()).filter { it.id in initiallyVisible }
        val media = gateway.publicCardMedia(rows.map { it.id }).groupBy { it.listingId }
        val finalVisible = rows.map { it.id }.chunked(4).flatMap { batch ->
            coroutineScope { batch.map { id -> async { id.takeIf { gateway.isVisible(it) } } }.awaitAll() }
        }.filterNotNull().toSet()
        return rows.asSequence().filter { it.id in finalVisible }.associate { row ->
            row.id to ListingCard(
                id = row.id,
                title = row.title,
                price = Rupees.display(row.priceType, row.pricePaise),
                condition = ListingCondition.fromWire(row.condition)?.label ?: "Condition not specified",
                category = row.category.name,
                location = listOfNotNull(row.locality?.name, row.city.name).joinToString(", "),
                thumbnailUrl = mediaUrls.thumbnail(row.id, media[row.id]?.firstOrNull()?.id),
            )
        }
    }
}

@Serializable
data class PublicListingCardDto(
    val id: String,
    val title: String,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Int? = null,
    val category: CardCategoryDto,
    val city: CardCityDto,
    val locality: CardLocalityDto? = null,
)

@Serializable
data class PublicListingCardMediaDto(
    @SerialName("listing_id") val listingId: String,
    val id: String,
    @SerialName("display_order") val displayOrder: Int,
)

@Serializable
data class CardCategoryDto(val name: String)

@Serializable
data class CardCityDto(val name: String)

@Serializable
data class CardLocalityDto(val name: String)
