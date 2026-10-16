package com.fairswap.core.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class SafetySession(
    val userId: String,
    val generation: Long = 0L
)

enum class ReportTargetType(val value: String) {
    LISTING("listing"),
    USER("user"),
    MESSAGE("message")
}

data class ReportInput(
    val targetType: ReportTargetType,
    val targetId: String,
    val reason: String,
    val details: String?
)

sealed interface ReportResult {
    data class Success(val reportId: String) : ReportResult
    data class Invalid(val message: String) : ReportResult
    data object AuthRequired : ReportResult
    data object Failure : ReportResult
}

sealed interface BlockResult {
    data object Success : BlockResult
    data class Invalid(val message: String) : BlockResult
    data object AuthRequired : BlockResult
    data object Failure : BlockResult
}

sealed interface BlockedUsersResult {
    data class Success(val blockedIds: List<String>) : BlockedUsersResult
    data object AuthRequired : BlockedUsersResult
    data object Failure : BlockedUsersResult
}

sealed interface PrivacyRequestResult {
    data object Success : PrivacyRequestResult
    data class Invalid(val message: String) : PrivacyRequestResult
    data object AuthRequired : PrivacyRequestResult
    data object Failure : PrivacyRequestResult
}

interface UserSafetyGateway {
    suspend fun currentIdentity(): AccountIdentity?
    suspend fun createReport(targetType: String, targetId: String, reason: String, details: String?): String?
    suspend fun blockUser(blockedUserId: String, reason: String?)
    suspend fun unblockUser(blockedUserId: String)
    suspend fun getBlockedUsers(): List<String>
    suspend fun submitPrivacyRequest(requestType: String)
}

@Serializable
internal data class PrivacyRequestDto(
    @SerialName("request_type") val requestType: String,
)

@Serializable
internal data class UserBlockDto(
    @SerialName("blocked_id") val blockedId: String,
)

@Serializable
internal data class CreateReportRpcRequest(
    @SerialName("target_type") val targetType: String,
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String?
)

@Serializable
internal data class BlockUserRpcRequest(
    @SerialName("blocked_user_id") val blockedUserId: String,
    val reason: String?
)

@Serializable
internal data class UnblockUserRpcRequest(
    @SerialName("blocked_user_id") val blockedUserId: String,
)
