package com.fairswap.core.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAccountGateway(private val supabase: SupabaseClient) : AccountGateway {
    override suspend fun currentIdentity(): AccountIdentity? {
        supabase.auth.awaitInitialization()
        val session = supabase.auth.currentSessionOrNull() ?: return null
        val user = session.user ?: return null
        return AccountIdentity(user.id, user.email)
    }

    override suspend fun profile(userId: String): PublicProfile? = supabase.from("profiles")
        .select(columns = Columns.list("id,display_name,bio,city,state,updated_at")) {
            filter { eq("id", userId) }
        }.decodeList<ProfileDto>().singleOrNull()?.toDomain()

    override suspend fun account(userId: String): AccountRow? = supabase.from("user_accounts")
        .select(columns = Columns.list("user_id,account_status,terms_version")) {
            filter { eq("user_id", userId) }
        }.decodeList<AccountDto>().singleOrNull()?.toDomain()

    override suspend fun policyAccepted(): Boolean = supabase.postgrest
        .rpc("has_current_policy_acceptance")
        .decodeAs()

    override suspend fun updateProfile(userId: String, input: ProfileInput): PublicProfile {
        val returned = supabase.from("profiles").update(
            buildJsonObject {
                put("display_name", input.displayName)
                put("bio", input.bio)
                put("city", input.city)
                put("state", input.state)
            },
        ) {
            filter { eq("id", userId) }
            select(Columns.list("id,display_name,bio,city,state,updated_at"))
        }.decodeSingle<ProfileDto>()
        return returned.toDomain()
    }
}
