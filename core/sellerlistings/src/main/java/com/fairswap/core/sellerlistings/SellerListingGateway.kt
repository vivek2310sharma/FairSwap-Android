package com.fairswap.core.sellerlistings

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SubmitListingRpcParams(
    @SerialName("listing_id") val listingId: String,
)

@Serializable
private data class TransitionListingRpcParams(
    @SerialName("listing_id") val listingId: String,
    val action: String,
)

@Serializable
private data class PrivateLocationRpcParams(
    @SerialName("p_listing_id") val listingId: String,
)

interface SellerListingGateway {
    suspend fun currentUserId(): String?
    suspend fun policyAccepted(): Boolean
    suspend fun fetchCategories(): List<Category>
    suspend fun fetchCities(): List<City>
    suspend fun fetchLocalities(cityId: String): List<Locality>
    suspend fun fetchSellerListings(sellerId: String, limit: Int = 50): List<SellerListingRowDto>
    suspend fun fetchReadyMediaCounts(listingIds: List<String>): Map<String, Int>
    suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto?
    suspend fun fetchSellerPrivateLocation(listingId: String): SellerPrivateLocationDto?
    suspend fun fetchCategoryAttributes(categoryId: String): List<CategoryAttributeMetadata>
    suspend fun insertListing(insertDto: SellerListingInsertDto): SellerListingRowDto
    suspend fun updateListing(listingId: String, sellerId: String, updateDto: SellerListingUpdateDto): SellerListingRowDto
    suspend fun submitListing(listingId: String)
    suspend fun transitionListing(listingId: String, action: String)
}

class SupabaseSellerListingGateway(
    private val supabase: SupabaseClient,
) : SellerListingGateway {

    val sellerListingSafeColumns = Columns.raw(
        "id, seller_id, slug, category_id, city_id, locality_id, title, description, condition, " +
            "price_type, price_paise, category_attributes, status, revision, renewal_count, " +
            "rejection_reason, removal_reason, cover_photo_id, created_at, updated_at, " +
            "category:categories(id, name, slug), city:cities(id, name, state)",
    )

    private val categoryColumns = Columns.raw("id, slug, name, description, icon, sort_order, is_active")
    private val cityColumns = Columns.raw("id, name, state, slug, is_active")
    private val localityColumns = Columns.raw("id, city_id, name, slug, is_active")

    override suspend fun currentUserId(): String? {
        supabase.auth.awaitInitialization()
        return supabase.auth.currentSessionOrNull()?.user?.id
    }

    override suspend fun policyAccepted(): Boolean =
        supabase.postgrest.rpc("has_current_policy_acceptance").decodeAs()

    override suspend fun fetchCategories(): List<Category> =
        supabase.from("categories")
            .select(columns = categoryColumns) {
                filter { eq("is_active", true) }
                order("sort_order", Order.ASCENDING)
                order("name", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun fetchCities(): List<City> =
        supabase.from("cities")
            .select(columns = cityColumns) {
                filter { eq("is_active", true) }
                order("name", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun fetchLocalities(cityId: String): List<Locality> =
        supabase.from("localities")
            .select(columns = localityColumns) {
                filter {
                    eq("city_id", cityId)
                    eq("is_active", true)
                }
                order("name", Order.ASCENDING)
            }
            .decodeList()

    override suspend fun fetchSellerListings(sellerId: String, limit: Int): List<SellerListingRowDto> =
        supabase.from("listings")
            .select(columns = sellerListingSafeColumns) {
                filter { eq("seller_id", sellerId) }
                order("updated_at", Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList()

    override suspend fun fetchReadyMediaCounts(listingIds: List<String>): Map<String, Int> {
        if (listingIds.isEmpty()) return emptyMap()
        val rows = supabase.from("listing_media")
            .select(columns = Columns.list("id, listing_id, processing_status, display_order")) {
                filter {
                    isIn("listing_id", listingIds)
                    eq("processing_status", "ready")
                }
            }.decodeList<ListingMediaMetadataDto>()
        return rows.groupBy { it.listingId }.mapValues { it.value.size }
    }

    override suspend fun fetchSellerListingDetail(listingId: String, sellerId: String): SellerListingRowDto? =
        supabase.from("listings")
            .select(columns = sellerListingSafeColumns) {
                filter {
                    eq("id", listingId)
                    eq("seller_id", sellerId)
                }
            }.decodeSingleOrNull()

    override suspend fun fetchSellerPrivateLocation(listingId: String): SellerPrivateLocationDto? =
        supabase.postgrest.rpc(
            function = "get_seller_listing_location_private",
            parameters = PrivateLocationRpcParams(listingId),
        ).decodeList<SellerPrivateLocationDto>().firstOrNull()

    override suspend fun fetchCategoryAttributes(categoryId: String): List<CategoryAttributeMetadata> =
        supabase.from("category_attributes")
            .select(columns = Columns.list("id, category_id, name, label, value_type, is_required, options, validation_rules, sort_order")) {
                filter { eq("category_id", categoryId) }
                order("sort_order", Order.ASCENDING)
            }.decodeList<CategoryAttributeDto>()
            .map { it.toDomain() }

    override suspend fun insertListing(insertDto: SellerListingInsertDto): SellerListingRowDto =
        supabase.from("listings")
            .insert(insertDto) { select(columns = sellerListingSafeColumns) }
            .decodeSingle()

    override suspend fun updateListing(
        listingId: String,
        sellerId: String,
        updateDto: SellerListingUpdateDto,
    ): SellerListingRowDto =
        supabase.from("listings")
            .update(updateDto) {
                filter {
                    eq("id", listingId)
                    eq("seller_id", sellerId)
                }
                select(columns = sellerListingSafeColumns)
            }.decodeSingle()

    override suspend fun submitListing(listingId: String) {
        supabase.postgrest.rpc(
            function = "seller_submit_listing",
            parameters = SubmitListingRpcParams(listingId),
        )
    }

    override suspend fun transitionListing(listingId: String, action: String) {
        supabase.postgrest.rpc(
            function = "seller_transition_listing",
            parameters = TransitionListingRpcParams(listingId = listingId, action = action),
        )
    }
}
