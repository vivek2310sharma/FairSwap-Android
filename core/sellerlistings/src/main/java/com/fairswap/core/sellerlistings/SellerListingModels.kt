package com.fairswap.core.sellerlistings

import com.fairswap.core.discovery.Category
import com.fairswap.core.discovery.City
import com.fairswap.core.discovery.Locality
import com.fairswap.core.discovery.Rupees
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface ReferenceDataResult<out T> {
    data class Success<T>(val value: T) : ReferenceDataResult<T>
    data class Failure(val message: String) : ReferenceDataResult<Nothing>
}

data class ListingValidationErrors(
    val titleError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val cityError: String? = null,
    val localityError: String? = null,
    val conditionError: String? = null,
    val priceError: String? = null,
    val pinCodeError: String? = null,
    val landmarkError: String? = null,
    val attributeErrors: Map<String, String> = emptyMap(),
) {
    val isValid: Boolean
        get() = titleError == null &&
            descriptionError == null &&
            categoryError == null &&
            cityError == null &&
            localityError == null &&
            conditionError == null &&
            priceError == null &&
            pinCodeError == null &&
            landmarkError == null &&
            attributeErrors.isEmpty()
}

enum class SellerListingStatus(val wire: String, val label: String) {
    DRAFT("draft", "Draft"),
    PENDING_REVIEW("pending_review", "Pending Review"),
    ACTIVE("active", "Active"),
    RESERVED("reserved", "Reserved"),
    SOLD("sold", "Sold"),
    REJECTED("rejected", "Rejected"),
    PAUSED("paused", "Paused"),
    EXPIRED("expired", "Expired"),
    REMOVED("removed", "Removed"),
    UNKNOWN("unknown", "Unsupported Status");

    val canEdit: Boolean get() = this in setOf(DRAFT, ACTIVE, RESERVED, PAUSED, REJECTED)
    val canEditMedia: Boolean get() = this in setOf(DRAFT, REJECTED, ACTIVE, RESERVED, PAUSED, PENDING_REVIEW)

    companion object {
        fun fromWire(wire: String?): SellerListingStatus =
            entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

fun canRenderSellerState(
    stateSession: SellerSession?,
    activeSession: SellerSession?,
    liveUserId: String?,
): Boolean =
    activeSession != null &&
    stateSession == activeSession &&
    activeSession.userId == liveUserId

fun formatUnavailableReferenceLabel(
    selectedId: String?,
    activeItemsNameMap: Map<String, String>,
    defaultPlaceholder: String,
    typeLabel: String,
): String {
    if (selectedId.isNullOrBlank()) return defaultPlaceholder
    val activeName = activeItemsNameMap[selectedId]
    if (activeName != null) return activeName
    return "Previously selected $typeLabel (unavailable)"
}

enum class SellerLifecycleAction(val wire: String, val label: String) {
    PAUSE("pause", "Pause Listing"),
    RESUME("resume", "Resume Listing"),
    RESERVE("reserve", "Mark Reserved"),
    UNRESERVE("unreserve", "Unreserve"),
    MARK_SOLD("mark_sold", "Mark Sold"),
    RENEW("renew", "Renew Listing");

    companion object {
        fun fromWire(wire: String?): SellerLifecycleAction? =
            entries.firstOrNull { it.wire == wire }
    }
}

@Serializable
data class CategoryAttributeDto(
    val id: String,
    @SerialName("category_id") val categoryId: String,
    val name: String,
    val label: String,
    @SerialName("value_type") val valueType: String,
    @SerialName("is_required") val isRequired: Boolean = false,
    val options: List<String>? = null,
) {
    fun toDomain(): CategoryAttributeMetadata = CategoryAttributeMetadata(
        id = id,
        categoryId = categoryId,
        name = name,
        label = label,
        valueType = valueType,
        isRequired = isRequired,
        options = options ?: emptyList(),
    )
}

data class CategoryAttributeMetadata(
    val id: String,
    val categoryId: String,
    val name: String,
    val label: String,
    val valueType: String,
    val isRequired: Boolean,
    val options: List<String>,
)

sealed interface CategoryAttributesLoadResult {
    data class Success(val attributes: List<CategoryAttributeMetadata>) : CategoryAttributesLoadResult
    data class Failure(val message: String) : CategoryAttributesLoadResult
}

@Serializable
data class SellerPrivateLocationDto(
    @SerialName("listing_id") val listingId: String,
    @SerialName("pin_code") val pinCode: String? = null,
    val landmark: String? = null,
)

@Serializable
data class SellerListingRowDto(
    val id: String,
    @SerialName("seller_id") val sellerId: String,
    val title: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("locality_id") val localityId: String? = null,
    val description: String? = null,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Long? = null,
    @SerialName("category_attributes") val categoryAttributes: JsonObject = JsonObject(emptyMap()),
    val status: String = "draft",
    val slug: String? = null,
    val revision: Int = 1,
    @SerialName("renewal_count") val renewalCount: Int = 0,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("removal_reason") val removalReason: String? = null,
    @SerialName("cover_photo_id") val coverPhotoId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val category: CategoryDto? = null,
    val city: CityDto? = null,
) {
    fun toSummary(readyMediaCount: Int): SellerListingSummary = SellerListingSummary(
        id = id,
        sellerId = sellerId,
        title = title,
        status = SellerListingStatus.fromWire(status),
        pricePaise = pricePaise,
        priceType = priceType,
        formattedPrice = Rupees.display(priceType, pricePaise?.toInt()),
        coverPhotoId = coverPhotoId,
        readyMediaCount = readyMediaCount,
        categoryName = category?.name ?: "",
        cityName = city?.name ?: "",
        rejectionReason = rejectionReason,
        removalReason = removalReason,
        revision = revision,
        renewalCount = renewalCount,
        createdAt = createdAt ?: "",
        updatedAt = updatedAt ?: "",
    )

    fun toDetail(
        readyMediaCount: Int,
        privatePinCode: String? = null,
        privateLandmark: String? = null,
    ): SellerListingDetail = SellerListingDetail(
        id = id,
        sellerId = sellerId,
        slug = slug ?: "",
        categoryId = categoryId,
        cityId = cityId,
        localityId = localityId,
        title = title,
        description = description,
        condition = condition,
        priceType = priceType,
        pricePaise = pricePaise,
        formattedPrice = Rupees.display(priceType, pricePaise?.toInt()),
        pinCode = privatePinCode,
        landmark = privateLandmark,
        categoryAttributes = categoryAttributes,
        status = SellerListingStatus.fromWire(status),
        revision = revision,
        renewalCount = renewalCount,
        rejectionReason = rejectionReason,
        removalReason = removalReason,
        coverPhotoId = coverPhotoId,
        readyMediaCount = readyMediaCount,
        createdAt = createdAt ?: "",
        updatedAt = updatedAt ?: "",
    )
}

@Serializable
data class CategoryDto(val id: String, val name: String, val slug: String? = null)

@Serializable
data class CityDto(val id: String, val name: String, val state: String? = null)

@Serializable
data class ListingMediaMetadataDto(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("processing_status") val processingStatus: String,
    @SerialName("display_order") val displayOrder: Int = 0,
)

data class SellerListingSummary(
    val id: String,
    val sellerId: String,
    val title: String,
    val status: SellerListingStatus,
    val pricePaise: Long?,
    val priceType: String?,
    val formattedPrice: String,
    val coverPhotoId: String?,
    val readyMediaCount: Int,
    val categoryName: String,
    val cityName: String,
    val rejectionReason: String?,
    val removalReason: String? = null,
    val revision: Int = 1,
    val renewalCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
) {
    val canEdit: Boolean get() = status.canEdit
    val canRenew: Boolean get() = status == SellerListingStatus.EXPIRED && renewalCount < 3
    val requiresPhotoForSubmit: Boolean get() = (status == SellerListingStatus.DRAFT || status == SellerListingStatus.REJECTED) && readyMediaCount == 0
}

data class SellerListingDetail(
    val id: String,
    val sellerId: String,
    val slug: String,
    val categoryId: String,
    val cityId: String,
    val localityId: String?,
    val title: String,
    val description: String?,
    val condition: String?,
    val priceType: String?,
    val pricePaise: Long?,
    val formattedPrice: String,
    val pinCode: String?,
    val landmark: String?,
    val categoryAttributes: JsonObject,
    val status: SellerListingStatus,
    val revision: Int,
    val renewalCount: Int,
    val rejectionReason: String?,
    val removalReason: String?,
    val coverPhotoId: String?,
    val readyMediaCount: Int,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toInput(): SellerListingInput = SellerListingInput(
        categoryId = categoryId,
        cityId = cityId,
        localityId = localityId?.ifBlank { null },
        title = title,
        description = description ?: "",
        condition = condition,
        priceType = priceType,
        priceRupees = if (priceType == "free") "" else (pricePaise?.let { Rupees.formatPaise(it.toInt()).removePrefix("₹").replace(",", "").trim() } ?: ""),
        pinCode = pinCode ?: "",
        landmark = landmark ?: "",
        categoryAttributes = categoryAttributes,
    )
}

data class SellerListingInput(
    val categoryId: String = "",
    val cityId: String? = null,
    val localityId: String? = null,
    val title: String = "",
    val description: String = "",
    val condition: String? = null,
    val priceType: String? = null,
    val priceRupees: String = "",
    val pinCode: String = "",
    val landmark: String = "",
    val categoryAttributes: JsonObject = JsonObject(emptyMap()),
)

sealed interface SellerDashboardResult {
    data class Success(val listings: List<SellerListingSummary>) : SellerDashboardResult
    data object AuthRequired : SellerDashboardResult
    data class Failure(val message: String) : SellerDashboardResult
}

sealed interface SellerDetailResult {
    data class Success(val listing: SellerListingDetail) : SellerDetailResult
    data object AuthRequired : SellerDetailResult
    data object NotFound : SellerDetailResult
    data class Failure(val message: String) : SellerDetailResult
}

sealed interface SellerMutationResult<out T> {
    data class Success<T>(val value: T) : SellerMutationResult<T>
    data object AuthRequired : SellerMutationResult<Nothing>
    data object PolicyReviewRequired : SellerMutationResult<Nothing>
    data class ValidationError(val errors: ListingValidationErrors) : SellerMutationResult<Nothing>
    data class Failure(val message: String) : SellerMutationResult<Nothing>
}

@Serializable
data class SellerListingInsertDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("locality_id") val localityId: String? = null,
    val title: String,
    val description: String? = null,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Long? = null,
    @SerialName("pin_code") val pinCode: String? = null,
    val landmark: String? = null,
    @SerialName("category_attributes") val categoryAttributes: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class SellerListingUpdateDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("locality_id") val localityId: String? = null,
    val title: String,
    val description: String? = null,
    val condition: String? = null,
    @SerialName("price_type") val priceType: String? = null,
    @SerialName("price_paise") val pricePaise: Long? = null,
    @SerialName("pin_code") val pinCode: String? = null,
    val landmark: String? = null,
    @SerialName("category_attributes") val categoryAttributes: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class PincodeLookupResponseDto(
    val success: Boolean,
    val pincode: String? = null,
    val state: String? = null,
    val district: String? = null,
    val city: String? = null,
    @SerialName("city_id") val cityId: String? = null,
    val districts: List<PincodeDistrictDto> = emptyList(),
    val localities: List<PincodeLocalityDto> = emptyList(),
    @SerialName("postal_offices") val postalOffices: JsonElement? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class PincodeDistrictDto(
    val name: String,
    @SerialName("city_id") val cityId: String? = null,
) {
    fun toDomain(state: String? = null) = PincodeDistrict(name = name, cityId = cityId, state = state)
}

@Serializable
data class PincodeLocalityDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    @SerialName("city_id") val cityId: String? = null,
    @SerialName("city_name") val cityName: String? = null,
    val district: String,
    val state: String? = null,
) {
    fun toDomain() = PincodeLocality(name = name, id = id, district = district, cityId = cityId)
}

data class PincodeDistrict(
    val name: String,
    val cityId: String? = null,
    val state: String? = null,
)

data class PincodeLocality(
    val name: String,
    val id: String,
    val district: String,
    val cityId: String? = null,
)

enum class PincodeLookupFailureReason {
    INVALID_PINCODE,
    PINCODE_NOT_FOUND,
    CONFLICTING_POSTAL_STATES,
    PINCODE_ADMIN_DATA_UNAVAILABLE,
    COMPATIBILITY_CITY_UNAVAILABLE,
    NETWORK_OR_FETCH_FAILURE,
}

sealed interface PincodeLookupResult {
    data class Success(
        val pincode: String,
        val state: String?,
        val district: String?,
        val city: String?,
        val cityId: String?,
        val districts: List<PincodeDistrict>,
        val localities: List<PincodeLocality>,
    ) : PincodeLookupResult
    data class Failure(val reason: PincodeLookupFailureReason) : PincodeLookupResult
}
