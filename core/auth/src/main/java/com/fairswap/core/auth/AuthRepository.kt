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
) {
    suspend fun bootstrap(): AuthGate {
        supabase.auth.awaitInitialization()
        val session = supabase.auth.currentSessionOrNull() ?: return AuthGate.SignedOut
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
            supabase.auth.signUpWith(Email) {
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
            AuthGate.ConfirmationRequired(input.email.trim())
        }.getOrElse {
            AuthGate.Failure("Unable to create your FairSwap account.")
        }
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
            AuthGate.SignedOut
        }.getOrElse {
            AuthGate.Failure("Unable to sign out. Please try again.")
        }
    }
}
