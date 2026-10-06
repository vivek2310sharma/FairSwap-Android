package com.fairswap.core.sellerlistings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SellerMediaProcessingStatus(val wire: String) {
    UPLOADING("uploading"),
    PROCESSING("processing"),
    READY("ready"),
    FAILED("failed"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(wire: String?): SellerMediaProcessingStatus =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: UNKNOWN
    }
}

data class SellerMediaItem(
    val id: String,
    val listingId: String,
    val width: Int?,
    val height: Int?,
    val displayOrder: Int,
    val processingStatus: SellerMediaProcessingStatus,
    val createdAt: String?,
    val isCover: Boolean,
)

data class SellerMediaListingSnapshot(
    val id: String,
    val status: SellerListingStatus,
    val revision: Int,
    val coverPhotoId: String?,
)

data class SellerMediaSnapshot(
    val listing: SellerMediaListingSnapshot,
    val media: List<SellerMediaItem>,
)

data class SellerMediaSignedUrl(
    val url: String,
    val expiresInSeconds: Int,
)

enum class SellerMediaApiErrorCode(val wire: String) {
    UNAUTHENTICATED("UNAUTHENTICATED"),
    ACCOUNT_RESTRICTED("ACCOUNT_RESTRICTED"),
    POLICY_ACCEPTANCE_REQUIRED("POLICY_ACCEPTANCE_REQUIRED"),
    LISTING_NOT_FOUND("LISTING_NOT_FOUND"),
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND"),
    LISTING_MEDIA_NOT_EDITABLE("LISTING_MEDIA_NOT_EDITABLE"),
    PHOTO_LIMIT_REACHED("PHOTO_LIMIT_REACHED"),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE"),
    FILE_TOO_LARGE("FILE_TOO_LARGE"),
    INVALID_IMAGE_FORMAT("INVALID_IMAGE_FORMAT"),
    INVALID_MEDIA_REQUEST("INVALID_MEDIA_REQUEST"),
    MEDIA_UPLOAD_FAILED("MEDIA_UPLOAD_FAILED"),
    MEDIA_PROCESSING_FAILED("MEDIA_PROCESSING_FAILED"),
    MEDIA_DELETE_FAILED("MEDIA_DELETE_FAILED"),
    MEDIA_REORDER_FAILED("MEDIA_REORDER_FAILED"),
    COVER_UPDATE_FAILED("COVER_UPDATE_FAILED"),
    SELLER_WRITES_DISABLED("SELLER_WRITES_DISABLED"),
    MEDIA_SERVICE_UNAVAILABLE("MEDIA_SERVICE_UNAVAILABLE"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWire(wire: String?): SellerMediaApiErrorCode =
            entries.firstOrNull { it.wire.equals(wire, ignoreCase = true) } ?: UNKNOWN
    }
}

sealed interface SellerMediaFailure {
    data object AuthRequired : SellerMediaFailure
    data object AccountRestricted : SellerMediaFailure
    data object PolicyReviewRequired : SellerMediaFailure
    data object ListingNotFound : SellerMediaFailure
    data object MediaNotFound : SellerMediaFailure
    data object ListingNotEditable : SellerMediaFailure
    data object PhotoLimitReached : SellerMediaFailure
    data object InvalidInput : SellerMediaFailure
    data object UnsupportedFormat : SellerMediaFailure
    data object FileTooLarge : SellerMediaFailure
    data class ProcessingFailed(val mediaId: String? = null) : SellerMediaFailure
    data object SellerWritesDisabled : SellerMediaFailure
    data object MediaServiceUnavailable : SellerMediaFailure
    data object OperationInProgress : SellerMediaFailure
    data object TransportUncertain : SellerMediaFailure
}

sealed interface SellerMediaGetSnapshotResult {
    data class Success(val snapshot: SellerMediaSnapshot) : SellerMediaGetSnapshotResult
    data class Failure(val error: SellerMediaFailure) : SellerMediaGetSnapshotResult
}

sealed interface SellerMediaGetSignedUrlResult {
    data class Success(val signedUrl: SellerMediaSignedUrl) : SellerMediaGetSignedUrlResult
    data class Failure(val error: SellerMediaFailure) : SellerMediaGetSignedUrlResult
}

sealed interface SellerMediaMutationAcknowledgement {
    val committed: Boolean
    val refreshRequired: Boolean
    val snapshot: SellerMediaSnapshot?

    data class Upload(
        override val committed: Boolean,
        override val refreshRequired: Boolean,
        override val snapshot: SellerMediaSnapshot?,
        val uploadedMediaId: String?,
        val processingStatus: SellerMediaProcessingStatus,
    ) : SellerMediaMutationAcknowledgement

    data class Delete(
        override val committed: Boolean,
        override val refreshRequired: Boolean,
        override val snapshot: SellerMediaSnapshot?,
        val deleted: Boolean,
        val deletedMediaId: String?,
        val newCoverId: String?,
    ) : SellerMediaMutationAcknowledgement

    data class Reorder(
        override val committed: Boolean,
        override val refreshRequired: Boolean,
        override val snapshot: SellerMediaSnapshot?,
        val changed: Boolean,
    ) : SellerMediaMutationAcknowledgement

    data class Cover(
        override val committed: Boolean,
        override val refreshRequired: Boolean,
        override val snapshot: SellerMediaSnapshot?,
        val coverPhotoId: String?,
    ) : SellerMediaMutationAcknowledgement
}

sealed interface SellerMediaMutationResult<out T : SellerMediaMutationAcknowledgement> {
    data class Success<T : SellerMediaMutationAcknowledgement>(val value: T) : SellerMediaMutationResult<T>
    data class Failure(val error: SellerMediaFailure) : SellerMediaMutationResult<Nothing>
}

data class SellerMediaUploadFile(
    val fileName: String,
    val bytes: ByteArray,
    val detectedFormat: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SellerMediaUploadFile) return false
        return fileName == other.fileName && bytes.contentEquals(other.bytes) && detectedFormat == other.detectedFormat
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + detectedFormat.hashCode()
        return result
    }
}

// Transport DTOs
@Serializable
internal data class SafeMediaItemDto(
    val id: String,
    @SerialName("listingId") val listingId: String,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("displayOrder") val displayOrder: Int = 0,
    @SerialName("processingStatus") val processingStatus: String,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("isCover") val isCover: Boolean = false,
) {
    fun toDomain() = SellerMediaItem(
        id = id,
        listingId = listingId,
        width = width,
        height = height,
        displayOrder = displayOrder,
        processingStatus = SellerMediaProcessingStatus.fromWire(processingStatus),
        createdAt = createdAt,
        isCover = isCover,
    )
}

@Serializable
internal data class SafeMediaListingSnapshotDto(
    val id: String,
    val status: String,
    val revision: Int = 1,
    @SerialName("coverPhotoId") val coverPhotoId: String? = null,
) {
    fun toDomain() = SellerMediaListingSnapshot(
        id = id,
        status = SellerListingStatus.fromWire(status),
        revision = revision,
        coverPhotoId = coverPhotoId,
    )
}

@Serializable
internal data class SafeMediaSnapshotDto(
    val listing: SafeMediaListingSnapshotDto,
    val media: List<SafeMediaItemDto> = emptyList(),
) {
    fun toDomain() = SellerMediaSnapshot(
        listing = listing.toDomain(),
        media = media.map { it.toDomain() },
    )
}

@Serializable
internal data class MediaErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
    val media: FailedMediaIdentityDto? = null,
)

@Serializable
internal data class FailedMediaIdentityDto(
    val id: String? = null,
    @SerialName("processingStatus") val processingStatus: String? = null,
)

@Serializable
internal data class SignedUrlResponseDto(
    val url: String,
    @SerialName("expiresIn") val expiresIn: Int = 60,
) {
    fun toDomain() = SellerMediaSignedUrl(url = url, expiresInSeconds = expiresIn)
}

@Serializable
internal data class UploadResponseDto(
    val success: Boolean = true,
    val committed: Boolean = true,
    @SerialName("uploadedMediaId") val uploadedMediaId: String? = null,
    @SerialName("processingStatus") val processingStatus: String = "ready",
    @SerialName("refreshRequired") val refreshRequired: Boolean = false,
    val snapshot: SafeMediaSnapshotDto? = null,
)

@Serializable
internal data class DeleteResponseDto(
    val success: Boolean = true,
    val committed: Boolean = true,
    val deleted: Boolean = false,
    @SerialName("deleted_media_id") val deletedMediaId: String? = null,
    @SerialName("new_cover_id") val newCoverId: String? = null,
    @SerialName("refreshRequired") val refreshRequired: Boolean = false,
    val snapshot: SafeMediaSnapshotDto? = null,
)

@Serializable
internal data class ReorderResponseDto(
    val success: Boolean = true,
    val committed: Boolean = true,
    val changed: Boolean = false,
    @SerialName("refreshRequired") val refreshRequired: Boolean = false,
    val snapshot: SafeMediaSnapshotDto? = null,
)

@Serializable
internal data class CoverResponseDto(
    val success: Boolean = true,
    val committed: Boolean = true,
    @SerialName("coverPhotoId") val coverPhotoId: String? = null,
    @SerialName("refreshRequired") val refreshRequired: Boolean = false,
    val snapshot: SafeMediaSnapshotDto? = null,
)

@Serializable
internal data class ReorderRequestBodyDto(
    @SerialName("mediaIds") val mediaIds: List<String>,
)

@Serializable
internal data class SetCoverRequestBodyDto(
    @SerialName("mediaId") val mediaId: String,
)
