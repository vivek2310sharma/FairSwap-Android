package com.fairswap.core.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc

class SupabaseUserSafetyGateway(private val supabase: SupabaseClient) : UserSafetyGateway {
    override suspend fun currentIdentity(): AccountIdentity? {
        supabase.auth.awaitInitialization()
        val session = supabase.auth.currentSessionOrNull() ?: return null
        val user = session.user ?: return null
        return AccountIdentity(user.id, user.email)
    }

    override suspend fun createReport(targetType: String, targetId: String, reason: String, details: String?): String? {
        return supabase.postgrest.rpc(
            "create_report",
            CreateReportRpcRequest(targetType, targetId, reason, details)
        ).decodeAsOrNull()
    }

    override suspend fun blockUser(blockedUserId: String, reason: String?) {
        supabase.postgrest.rpc(
            "block_user",
            BlockUserRpcRequest(blockedUserId, reason)
        )
    }

    override suspend fun unblockUser(blockedUserId: String) {
        supabase.postgrest.rpc(
            "unblock_user",
            UnblockUserRpcRequest(blockedUserId)
        )
    }

    override suspend fun getBlockedUsers(): List<String> {
        val dtos = supabase.from("user_blocks")
            .select(columns = Columns.list("blocked_id"))
            .decodeList<UserBlockDto>()
        return dtos.map { it.blockedId }
    }

    override suspend fun submitPrivacyRequest(requestType: String) {
        supabase.from("privacy_requests")
            .insert(PrivacyRequestDto(requestType))
    }
}
