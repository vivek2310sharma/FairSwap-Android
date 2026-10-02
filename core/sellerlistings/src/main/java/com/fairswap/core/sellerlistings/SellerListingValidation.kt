package com.fairswap.core.sellerlistings

import com.fairswap.core.discovery.ListingCondition
import com.fairswap.core.discovery.Rupees
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

object SellerListingValidation {

    fun isUuid(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
    }

    fun validateDraft(input: SellerListingInput): ListingValidationErrors {
        var titleErr: String? = null
        var descErr: String? = null
        var catErr: String? = null
        var cityErr: String? = null
        var localityErr: String? = null
        var condErr: String? = null
        var priceErr: String? = null
        var pinErr: String? = null

        val trimmedTitle = input.title.trim()
        if (trimmedTitle.length < 3) {
            titleErr = "Title must be at least 3 characters."
        } else if (trimmedTitle.length > 150) {
            titleErr = "Title cannot exceed 150 characters."
        }

        val trimmedDesc = input.description.trim()
        if (trimmedDesc.length > 5000) {
            descErr = "Description cannot exceed 5000 characters."
        }

        if (input.categoryId.isBlank() || !isUuid(input.categoryId)) {
            catErr = "Select a valid category."
        }

        if (input.cityId.isNullOrBlank() || !isUuid(input.cityId)) {
            cityErr = "Select a valid city."
        }

        if (!input.localityId.isNullOrBlank() && !isUuid(input.localityId)) {
            localityErr = "Select a valid locality."
        }

        if (!input.condition.isNullOrBlank() && ListingCondition.fromWire(input.condition) == null) {
            condErr = "Select a valid condition."
        }

        if (!input.priceType.isNullOrBlank() && input.priceType !in setOf("fixed", "negotiable", "free")) {
            priceErr = "Select a valid price type."
        }

        if (input.priceRupees.isNotBlank()) {
            val paise = Rupees.toPaise(input.priceRupees)
            if (paise == null || paise < 0 || paise > 100_000_000) {
                priceErr = "Enter a valid price up to ₹10,00,000."
            }
        }

        val trimmedPin = input.pinCode.trim()
        if (trimmedPin.isNotBlank() && !trimmedPin.matches(Regex("^[0-9]{6}$"))) {
            pinErr = "PIN code must be exactly 6 digits."
        }

        return ListingValidationErrors(
            titleError = titleErr,
            descriptionError = descErr,
            categoryError = catErr,
            cityError = cityErr,
            localityError = localityErr,
            conditionError = condErr,
            priceError = priceErr,
            pinCodeError = pinErr,
        )
    }

    fun validateSubmit(
        input: SellerListingInput,
        attributeMetadata: List<CategoryAttributeMetadata> = emptyList(),
    ): ListingValidationErrors {
        val draftErrors = validateDraft(input)

        var descErr = draftErrors.descriptionError
        var condErr = draftErrors.conditionError
        var priceErr = draftErrors.priceError
        val attrErrors = mutableMapOf<String, String>()

        val trimmedDesc = input.description.trim()
        if (trimmedDesc.length < 10) {
            descErr = "Description must be at least 10 characters."
        } else if (trimmedDesc.length > 5000) {
            descErr = "Description cannot exceed 5000 characters."
        }

        if (input.condition.isNullOrBlank() || ListingCondition.fromWire(input.condition) == null) {
            condErr = "Select a condition before submitting."
        }

        if (input.priceType.isNullOrBlank() || input.priceType !in setOf("fixed", "negotiable", "free")) {
            priceErr = "Select a price type before submitting."
        } else if (input.priceType != "free") {
            val paise = Rupees.toPaise(input.priceRupees)
            if (paise == null || paise <= 0) {
                priceErr = "Enter a price greater than zero for paid listings."
            }
        }

        for (attr in attributeMetadata) {
            if (attr.isRequired) {
                val element = input.categoryAttributes[attr.name]
                val isPresent = when (element) {
                    is JsonPrimitive -> {
                        element.booleanOrNull != null || !element.contentOrNull.isNullOrBlank()
                    }
                    null -> false
                    else -> true
                }
                if (!isPresent) {
                    attrErrors[attr.name] = "${attr.label} is required."
                }
            }
        }

        return ListingValidationErrors(
            titleError = draftErrors.titleError,
            descriptionError = descErr,
            categoryError = draftErrors.categoryError,
            cityError = draftErrors.cityError,
            localityError = draftErrors.localityError,
            conditionError = condErr,
            priceError = priceErr,
            pinCodeError = draftErrors.pinCodeError,
            attributeErrors = attrErrors,
        )
    }
}
