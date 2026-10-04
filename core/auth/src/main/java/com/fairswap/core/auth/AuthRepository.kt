package com.fairswap.core.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface AuthRepository {
    suspend fun bootstrap(): AuthGate
    suspend fun signIn(email: String, password: String): AuthGate
    suspend fun signUp(input: SignUpInput): AuthGate
    suspend fun requestPasswordReset(email: String): AuthGate
    suspend fun setNewPassword(password: String, confirmation: String): AuthGate
    suspend fun reacceptPolicies(): AuthGate
    suspend fun signOut(): AuthGate
    suspend fun requestEmailOtp(email: String): EmailOtpRequestResult
    suspend fun verifyEmailOtp(email: String, token: String): EmailOtpVerificationResult
}

class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
    private val callbacks: AuthCallbackConfig,
    private val recoveryState: RecoveryStateStore,
) : AuthRepository {
    override suspend fun bootstrap(): AuthGate {
        supabase.auth.awaitInitialization()
        val session = supabase.auth.currentSessionOrNull() ?: run {
            recoveryState.isPending = false
            return AuthGate.SignedOut
        }
        if (recoveryState.isPending || recoveryState.expectedCallbackKind == AuthCallbackKind.Recovery) {
            recoveryState.isPending = true
            recoveryState.expectedCallbackKind = null
            return AuthGate.RecoveryRequired
        }
        val user = session.user ?: return AuthGate.Failure("Authenticated session did not contain a user.")

        return runCatching {
            val profile = supabase
                .from("profiles")
                .select(columns = Columns.list("id, display_name")) {
                    filter { eq("id", user.id) }
                }
                .decodeSingle<ProfileDto>()

            val account = supabase
                .from("user_accounts")
                .select(columns = Columns.list("user_id, account_status")) {
                    filter { eq("user_id", user.id) }
                }
                .decodeSingle<UserAccountDto>()

            if (account.accountStatus != "active") {
                return@runCatching AuthGate.Restricted(account.accountStatus)
            }

            val hasCurrentPolicy = supabase.postgrest
                .rpc("has_current_policy_acceptance")
                .decodeAs<Boolean>()

            val profileSummary = ProfileSummary(profile.id, profile.displayName)
            val accountSummary = AccountSummary(account.userId, account.accountStatus)

            if (hasCurrentPolicy) {
                AuthGate.SignedIn(profileSummary, accountSummary)
            } else {
                AuthGate.PolicyReviewRequired(profileSummary, accountSummary)
            }
        }.getOrElse {
            AuthGate.Failure("Unable to load your FairSwap account.")
        }
    }

    override suspend fun requestEmailOtp(email: String): EmailOtpRequestResult {
        AuthValidation.validateEmail(email)?.let {
            return EmailOtpRequestResult.InvalidEmail
        }
        val normalizedEmail = email.trim().lowercase()
        return try {
            supabase.auth.signInWith(
                OTP,
                redirectUrl = null,
            ) {
                this.email = normalizedEmail
                createUser = false
            }
            EmailOtpRequestResult.Acknowledged
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e

            val isNetwork = e is io.ktor.client.plugins.HttpRequestTimeoutException || e is java.net.UnknownHostException || e is java.net.ConnectException
            val restEx = e as? io.github.jan.supabase.exceptions.RestException
            val errorCode = (e as? io.github.jan.supabase.auth.exception.AuthRestException)?.errorCode?.value ?: restEx?.error ?: ""
            val status = restEx?.statusCode

            AuthErrorClassifier.classifyOtpRequestError(errorCode, status, isNetwork)
        }
    }

    override suspend fun verifyEmailOtp(email: String, token: String): EmailOtpVerificationResult {
        AuthValidation.validateOtp(token)?.let { return EmailOtpVerificationResult.InvalidInput }
        val normalizedEmail = email.trim().lowercase()
        return try {
            val verifyResult = supabase.auth.verifyEmailOtp(
                type = OtpType.Email.EMAIL,
                email = normalizedEmail,
                token = token,
            )
            if (verifyResult !is io.github.jan.supabase.auth.OtpVerifyResult.Authenticated) {
                return EmailOtpVerificationResult.IdentityVerificationFailed
            }

            // Verify current session user matches the requested email
            val currentUser = try {
                supabase.auth.retrieveUserForCurrentSession(updateSession = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }

            if (currentUser == null || currentUser.id != verifyResult.session.user?.id ||
                (currentUser.email != null && currentUser.email?.lowercase() != normalizedEmail)) {
                try {
                    supabase.auth.signOut() // local cleanup
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                }
                return EmailOtpVerificationResult.IdentityVerificationFailed
            }

            val gate = bootstrap()
            EmailOtpVerificationResult.Authenticated(gate)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val isNetwork = e is io.ktor.client.plugins.HttpRequestTimeoutException || e is java.net.UnknownHostException || e is java.net.ConnectException
            val restEx = e as? io.github.jan.supabase.exceptions.RestException
            val errorCode = (e as? io.github.jan.supabase.auth.exception.AuthRestException)?.errorCode?.value ?: restEx?.error ?: ""
            val status = restEx?.statusCode

            AuthErrorClassifier.classifyOtpVerificationError(errorCode, status, isNetwork)
        }
    }

    override suspend fun signIn(email: String, password: String): AuthGate {
        AuthValidation.validateSignIn(email, password)?.let {
            return AuthGate.Failure(it)
        }

        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email.trim().lowercase()
                this.password = password
            }
            recoveryState.expectedCallbackKind = null
        }.fold(
            onSuccess = { bootstrap() },
            onFailure = {
                AuthGate.Failure(
                    "We couldn't sign you in with those credentials. Check your email and password and try again."
                )
            },
        )
    }

    override suspend fun signUp(input: SignUpInput): AuthGate {
        AuthValidation.validateSignUp(input)?.let {
            return AuthGate.Failure(it)
        }

        return runCatching {
            supabase.auth.signUpWith(Email, redirectUrl = callbacks.confirmationUrl) {
                email = input.email.trim()
                password = input.password
                data = buildJsonObject {
                    put("display_name", input.displayName.trim())
                    put("terms_accepted", "true")
                    put("terms_version", LegalPolicy.TermsVersion)
                    put("privacy_policy_version", LegalPolicy.PrivacyVersion)
                    put("acceptable_use_version", LegalPolicy.AcceptableUseVersion)
                    put("age_confirmed", "true")
                    put("minimum_age_confirmed", LegalPolicy.MinimumAge)
                }
            }
            recoveryState.expectedCallbackKind = AuthCallbackKind.Confirmation
            AuthGate.ConfirmationRequired(input.email.trim())
        }.getOrElse {
            AuthGate.Failure("Unable to create your FairSwap account.")
        }
    }

    override suspend fun requestPasswordReset(email: String): AuthGate {
        AuthValidation.validateResetEmail(email)?.let { return AuthGate.Failure(it) }
        return runCatching {
            supabase.auth.resetPasswordForEmail(
                email = email.trim(),
                redirectUrl = callbacks.recoveryUrl,
            )
            recoveryState.expectedCallbackKind = AuthCallbackKind.Recovery
            AuthGate.ResetEmailSent
        }.getOrElse { AuthGate.ResetEmailSent }
    }

    override suspend fun setNewPassword(password: String, confirmation: String): AuthGate {
        AuthValidation.validateNewPassword(password, confirmation)?.let { return AuthGate.Failure(it) }
        if (!recoveryState.isPending || supabase.auth.currentSessionOrNull() == null) {
            return AuthGate.Failure("Recovery session expired. Request a new password reset email.")
        }
        return runCatching {
            supabase.auth.updateUser { this.password = password }
            recoveryState.isPending = false
        }.fold(
            onSuccess = { bootstrap() },
            onFailure = { AuthGate.Failure("Unable to update your password. Request a new reset link if it has expired.") },
        )
    }

    override suspend fun reacceptPolicies(): AuthGate {
        return runCatching {
            supabase.postgrest.rpc(
                function = "record_policy_reacceptance",
                parameters = PolicyReacceptanceParams(
                    termsAndPrivacyAccepted = true,
                    ageConfirmed = true,
                ),
            )
        }.fold(
            onSuccess = { bootstrap() },
            onFailure = {
                AuthGate.Failure(
                    "Failed to record policy re-acceptance. Please try again."
                )
            },
        )
    }

    override suspend fun signOut(): AuthGate {
        return runCatching {
            supabase.auth.signOut()
            recoveryState.isPending = false
            recoveryState.expectedCallbackKind = null
            AuthGate.SignedOut
        }.getOrElse {
            AuthGate.Failure("Unable to sign out. Please try again.")
        }
    }
}
