package com.fairswap.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SignUpInput(
    val displayName: String,
    val email: String,
    val password: String,
    val termsAccepted: Boolean,
    val ageConfirmed: Boolean,
)

sealed interface AuthGate {
    data object SignedOut : AuthGate
    data class ConfirmationRequired(val email: String) : AuthGate
    data class PolicyReviewRequired(
        val profile: ProfileSummary,
        val account: AccountSummary,
    ) : AuthGate
    data class SignedIn(
        val profile: ProfileSummary,
        val account: AccountSummary,
    ) : AuthGate
    data class Restricted(val accountStatus: String) : AuthGate
    data class Failure(val message: String) : AuthGate
}

data class ProfileSummary(
    val id: String,
    val displayName: String,
)

data class AccountSummary(
    val userId: String,
    val accountStatus: String,
)

@Serializable
internal data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
internal data class UserAccountDto(
    @SerialName("user_id") val userId: String,
    @SerialName("account_status") val accountStatus: String,
)

@Serializable
internal data class PolicyReacceptanceParams(
    @SerialName("terms_and_privacy_accepted")
    val termsAndPrivacyAccepted: Boolean,
    @SerialName("age_confirmed")
    val ageConfirmed: Boolean,
)
