package com.fairswap.core.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository(
    private val supabase: SupabaseClient,
    private val callbacks: AuthCallbackConfig,
    private val recoveryState: RecoveryStateStore,
) {
    suspend fun bootstrap(): AuthGate {
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

    suspend fun signIn(email: String, password: String): AuthGate {
        AuthValidation.validateSignIn(email, password)?.let {
            return AuthGate.Failure(it)
        }

        return runCatching {
            supabase.auth.signInWith(Email) {
                this.email = email.trim()
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

    suspend fun signUp(input: SignUpInput): AuthGate {
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

    suspend fun requestPasswordReset(email: String): AuthGate {
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

    suspend fun setNewPassword(password: String, confirmation: String): AuthGate {
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

    suspend fun reacceptPolicies(): AuthGate {
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

    suspend fun signOut(): AuthGate {
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
