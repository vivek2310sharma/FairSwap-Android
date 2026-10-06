package com.fairswap.core.sellerlistings

object SellerMediaErrorMapper {
    fun map(
        errorCode: SellerMediaApiErrorCode,
        failedMediaId: String? = null,
        httpStatus: Int? = null,
    ): SellerMediaFailure {
        return when (errorCode) {
            SellerMediaApiErrorCode.UNAUTHENTICATED -> SellerMediaFailure.AuthRequired
            SellerMediaApiErrorCode.ACCOUNT_RESTRICTED -> SellerMediaFailure.AccountRestricted
            SellerMediaApiErrorCode.POLICY_ACCEPTANCE_REQUIRED -> SellerMediaFailure.PolicyReviewRequired
            SellerMediaApiErrorCode.LISTING_NOT_FOUND -> SellerMediaFailure.ListingNotFound
            SellerMediaApiErrorCode.MEDIA_NOT_FOUND -> SellerMediaFailure.MediaNotFound
            SellerMediaApiErrorCode.LISTING_MEDIA_NOT_EDITABLE -> SellerMediaFailure.ListingNotEditable
            SellerMediaApiErrorCode.PHOTO_LIMIT_REACHED -> SellerMediaFailure.PhotoLimitReached
            SellerMediaApiErrorCode.PAYLOAD_TOO_LARGE,
            SellerMediaApiErrorCode.FILE_TOO_LARGE -> SellerMediaFailure.FileTooLarge
            SellerMediaApiErrorCode.INVALID_IMAGE_FORMAT -> SellerMediaFailure.UnsupportedFormat
            SellerMediaApiErrorCode.INVALID_MEDIA_REQUEST -> SellerMediaFailure.InvalidInput
            SellerMediaApiErrorCode.MEDIA_PROCESSING_FAILED -> SellerMediaFailure.ProcessingFailed(failedMediaId)
            SellerMediaApiErrorCode.SELLER_WRITES_DISABLED -> SellerMediaFailure.SellerWritesDisabled
            SellerMediaApiErrorCode.MEDIA_UPLOAD_FAILED,
            SellerMediaApiErrorCode.MEDIA_DELETE_FAILED,
            SellerMediaApiErrorCode.MEDIA_REORDER_FAILED,
            SellerMediaApiErrorCode.COVER_UPDATE_FAILED,
            SellerMediaApiErrorCode.MEDIA_SERVICE_UNAVAILABLE,
            SellerMediaApiErrorCode.UNKNOWN -> {
                if (httpStatus == 401) {
                    SellerMediaFailure.AuthRequired
                } else {
                    SellerMediaFailure.MediaServiceUnavailable
                }
            }
        }
    }
}
