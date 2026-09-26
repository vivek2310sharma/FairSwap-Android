package com.fairswap.core.discovery

import java.util.UUID
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

fun validPublicId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id.lowercase() }.getOrDefault(false)

fun publicDate(raw: String?): String? = raw?.let {
    runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())) }.getOrNull()
}

data class PublicListingMedia(
    val id: String,
    val width: Int?,
    val height: Int?,
    val displayOrder: Int,
    val mediumUrl: String?,
    val largeUrl: String?,
)

data class PublicSpecification(val label: String, val value: String)

data class PublicSellerProfile(
    val id: String,
    val displayName: String,
    val bio: String?,
    val city: String?,
    val state: String?,
    val createdAt: String,
    val listings: List<ListingCard> = emptyList(),
) {
    val displayedListingCount: Int get() = listings.size
}

data class PublicListingDetail(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String?,
    val condition: String,
    val priceType: String?,
    val pricePaise: Int?,
    val formattedPrice: String,
    val category: String,
    val city: String,
    val locality: String?,
    val specifications: List<PublicSpecification>,
    val publishedAt: String?,
    val createdAt: String,
    val seller: PublicSellerProfile,
    val media: List<PublicListingMedia>,
)

sealed interface PublicDetailResult {
    data class Found(val detail: PublicListingDetail) : PublicDetailResult
    data object Unavailable : PublicDetailResult
}

sealed interface PublicSellerResult {
    data class Found(val profile: PublicSellerProfile) : PublicSellerResult
    data object Unavailable : PublicSellerResult
}

@Serializable
data class PublicListingDetailDto(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    val description: String? = null,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Int? = null,
    @SerialName("category_attributes") val categoryAttributes: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val category: Category,
    val city: City,
    val locality: Locality? = null,
)

@Serializable
data class PublicSellerProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val bio: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PublicListingMediaDto(
    val id: String,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("display_order") val displayOrder: Int,
)

fun JsonObject.publicSpecifications(): List<PublicSpecification> = entries.mapNotNull { (key, value) ->
    val scalar = value as? JsonPrimitive ?: return@mapNotNull null
    val content = when (scalar.booleanOrNull) {
        true -> "Yes"
        false -> "No"
        null -> scalar.contentOrNull?.trim()
    }
    if (content.isNullOrBlank() || content.length > 120 || key.length > 60) return@mapNotNull null
    val label = key.replace('_', ' ').replace('-', ' ').trim()
        .split(Regex("\\s+")).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    PublicSpecification(label, content)
}.take(20)

fun PublicSellerProfileDto.toDomain(listings: List<ListingCard> = emptyList()) = PublicSellerProfile(
    id, displayName, bio?.trim()?.ifEmpty { null }, city?.trim()?.ifEmpty { null },
    state?.trim()?.ifEmpty { null }, createdAt, listings,
)

fun PublicListingDetailDto.toDomain(
    seller: PublicSellerProfile,
    media: List<PublicListingMedia>,
) = PublicListingDetail(
    id = id,
    sellerId = sellerId,
    title = title,
    description = description?.trim()?.ifEmpty { null },
    condition = ListingCondition.fromWire(condition)?.label ?: "Condition not specified",
    priceType = priceType,
    pricePaise = pricePaise,
    formattedPrice = Rupees.display(priceType, pricePaise),
    category = category.name,
    city = city.name,
    locality = locality?.name,
    specifications = categoryAttributes.publicSpecifications(),
    publishedAt = publishedAt,
    createdAt = createdAt,
    seller = seller,
    media = media.sortedWith(compareBy(PublicListingMedia::displayOrder, PublicListingMedia::id)),
)
