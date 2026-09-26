package com.fairswap.core.discovery

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class ListingCondition(val wire: String, val label: String) {
    NEW("new", "New"), LIKE_NEW("like_new", "Like new"), GOOD("good", "Good"),
    FAIR("fair", "Fair"), POOR("poor", "Poor");

    companion object {
        fun fromWire(value: String?): ListingCondition? = entries.firstOrNull { it.wire == value }
    }
}

enum class ListingSort(val wire: String, val label: String) {
    NEWEST("newest", "Newest"), PRICE_ASC("price_asc", "Price: low to high"),
    PRICE_DESC("price_desc", "Price: high to low"), RELEVANCE("relevance", "Relevance")
}

data class SearchFilters(
    val query: String = "",
    val categoryId: String? = null,
    val cityId: String? = null,
    val localityId: String? = null,
    val condition: ListingCondition? = null,
    val minRupees: String = "",
    val maxRupees: String = "",
    val sort: ListingSort = ListingSort.NEWEST,
) {
    fun withCity(id: String?) = copy(cityId = id, localityId = null)

    fun validate(): String? {
        if (query.trim().length > 100) return "Search text must be 100 characters or less."
        if (listOfNotNull(categoryId, cityId, localityId).any { !it.isUuid() }) {
            return "Invalid search filter. Clear filters and try again."
        }
        if (localityId != null && cityId == null) return "Select a city before a locality."
        val min = Rupees.toPaise(minRupees)
        val max = Rupees.toPaise(maxRupees)
        if (minRupees.isNotBlank() && min == null || maxRupees.isNotBlank() && max == null) {
            return "Enter a valid nonnegative price with at most two decimal places."
        }
        if (min != null && max != null && min > max) return "Minimum price cannot exceed maximum price."
        return null
    }

    fun toParams(page: Int, perPage: Int = 20): SearchPublicListingsParams {
        require(page >= 1 && perPage in 1..50)
        require(validate() == null)
        return SearchPublicListingsParams(
            query = query.trim().ifEmpty { null },
            categoryId = categoryId,
            categorySlug = null,
            cityId = cityId,
            localityId = localityId,
            condition = condition?.wire,
            priceMin = Rupees.toPaise(minRupees),
            priceMax = Rupees.toPaise(maxRupees),
            sort = sort.wire,
            page = page,
            perPage = perPage,
        )
    }
}

private fun String.isUuid() = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)

object Rupees {
    fun toPaise(input: String): Int? {
        if (input.isBlank()) return null
        val normalized = input.trim()
        if (normalized.length > 16) return null
        if (!normalized.matches(Regex("^[0-9]+(\\.[0-9]{1,2})?$"))) return null
        return runCatching {
            val amount = BigDecimal(normalized)
            amount.movePointRight(2).intValueExact()
        }.getOrNull()
    }

    fun formatPaise(paise: Int): String {
        val whole = paise / 100
        val fraction = kotlin.math.abs(paise % 100)
        val raw = whole.toString()
        val tail = raw.takeLast(3)
        val head = raw.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        val digits = if (head.isEmpty()) tail else "$head,$tail"
        return "₹$digits.${fraction.toString().padStart(2, '0')}"
    }

    fun display(priceType: String?, paise: Int?): String = when {
        priceType == "free" -> "Free"
        paise != null -> formatPaise(paise)
        else -> "Price on request"
    }
}

@Serializable
data class SearchPublicListingsParams(
    val query: String?,
    @SerialName("category_id") val categoryId: String?,
    @SerialName("category_slug") val categorySlug: String?,
    @SerialName("city_id") val cityId: String?,
    @SerialName("locality_id") val localityId: String?,
    val condition: String?,
    @SerialName("price_min") val priceMin: Int?,
    @SerialName("price_max") val priceMax: Int?,
    val sort: String,
    val page: Int,
    @SerialName("per_page") val perPage: Int,
)

@Serializable
data class SearchResponseDto(
    val items: List<PublicListingDto>,
    @SerialName("total_count") val totalCount: Int,
    val page: Int,
    @SerialName("per_page") val perPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
data class PublicListingDto(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val slug: String,
    val title: String,
    val description: String? = null,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Int? = null,
    @SerialName("pin_code") val pinCode: String? = null,
    @SerialName("category_attributes") val categoryAttributes: JsonElement? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val category: Category,
    val city: City,
    val locality: Locality? = null,
    @SerialName("cover_photo") val coverPhoto: CoverPhotoDto? = null,
)

@Serializable
data class Category(val id: String, val name: String, val slug: String, val icon: String? = null)

@Serializable
data class City(val id: String, val name: String, val state: String? = null)

@Serializable
data class Locality(val id: String, val name: String)

@Serializable
data class CoverPhotoDto(
    val id: String,
    @SerialName("thumbnail_storage_path") val thumbnailStoragePath: String? = null,
    @SerialName("medium_storage_path") val mediumStoragePath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

data class ListingCard(
    val id: String,
    val title: String,
    val price: String,
    val condition: String,
    val category: String,
    val location: String,
    val thumbnailUrl: String?,
)

data class SearchPage(
    val items: List<ListingCard>,
    val totalCount: Int,
    val page: Int,
    val totalPages: Int,
)
