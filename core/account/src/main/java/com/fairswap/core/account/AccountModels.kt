package com.fairswap.core.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class AccountIdentity(val userId: String, val email: String?)

data class PublicProfile(
    val displayName: String,
    val bio: String?,
    val city: String?,
    val state: String?,
    val updatedAt: String?,
)

data class MyAccount(
    val userId: String,
    val email: String?,
    val accountStatus: String,
    val termsVersion: String?,
    val profile: PublicProfile,
)

data class ProfileInput(
    val displayName: String,
    val bio: String?,
    val city: String?,
    val state: String?,
)

enum class ProfileField { DISPLAY_NAME, BIO, CITY, STATE }

data class ProfileValidationError(val field: ProfileField, val message: String)

sealed interface ProfileValidation {
    data class Valid(val normalized: ProfileInput) : ProfileValidation
    data class Invalid(val error: ProfileValidationError) : ProfileValidation
}

object ProfileValidator {
    fun validate(input: ProfileInput): ProfileValidation {
        val displayName = input.displayName.trim()
        if (displayName.length < 2) return ProfileValidation.Invalid(ProfileValidationError(ProfileField.DISPLAY_NAME, "Enter at least 2 characters."))
        if (displayName.length > 50) return ProfileValidation.Invalid(ProfileValidationError(ProfileField.DISPLAY_NAME, "Use 50 characters or fewer."))
        val bio = input.bio.cleanOptional()
        if (bio != null && bio.length > 500) return ProfileValidation.Invalid(ProfileValidationError(ProfileField.BIO, "Use 500 characters or fewer."))
        val city = input.city.cleanOptional()
        if (city != null && city.length > 100) return ProfileValidation.Invalid(ProfileValidationError(ProfileField.CITY, "Use 100 characters or fewer."))
        val state = input.state.cleanOptional()
        if (state != null && state.length > 100) return ProfileValidation.Invalid(ProfileValidationError(ProfileField.STATE, "Use 100 characters or fewer."))
        return ProfileValidation.Valid(ProfileInput(displayName, bio, city, state))
    }

    private fun String?.cleanOptional(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}

sealed interface AccountLoadResult {
    data class Success(val account: MyAccount) : AccountLoadResult
    data object AuthRequired : AccountLoadResult
    data object Restricted : AccountLoadResult
    data object Failure : AccountLoadResult
}

sealed interface ProfileUpdateResult {
    data class Success(val account: MyAccount) : ProfileUpdateResult
    data class Invalid(val error: ProfileValidationError) : ProfileUpdateResult
    data object AuthRequired : ProfileUpdateResult
    data object PolicyReviewRequired : ProfileUpdateResult
    data object Restricted : ProfileUpdateResult
    data object Failure : ProfileUpdateResult
}

interface AccountGateway {
    suspend fun currentIdentity(): AccountIdentity?
    suspend fun profile(userId: String): PublicProfile?
    suspend fun account(userId: String): AccountRow?
    suspend fun policyAccepted(): Boolean
    suspend fun updateProfile(userId: String, input: ProfileInput): PublicProfile
}

data class AccountRow(val userId: String, val status: String, val termsVersion: String?)

@Serializable
internal data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val bio: String? = null,
    val city: String? = null,
    @SerialName("state") val region: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain() = PublicProfile(displayName, bio, city, region, updatedAt)
}

@Serializable
internal data class AccountDto(
    @SerialName("user_id") val userId: String,
    @SerialName("account_status") val status: String,
    @SerialName("terms_version") val termsVersion: String? = null,
) {
    fun toDomain() = AccountRow(userId, status, termsVersion)
}
