package com.fairswap.core.account

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class UserSafetyRepository(private val gateway: UserSafetyGateway) {

    suspend fun currentIdentity(): AccountIdentity? = gateway.currentIdentity()

    suspend fun createReport(input: ReportInput): ReportResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout ReportResult.AuthRequired

            val cleanReason = input.reason.trim()
            if (cleanReason.length < 3 || cleanReason.length > 100) {
                return@withTimeout ReportResult.Invalid("Reason must be between 3 and 100 characters")
            }
            if (input.targetId.isBlank() || !input.targetId.isUuid()) {
                return@withTimeout ReportResult.Invalid("Invalid report target ID format")
            }
            if (input.details != null && input.details.length > 2000) {
                return@withTimeout ReportResult.Invalid("Details cannot exceed 2000 characters")
            }

            val id = gateway.createReport(input.targetType.value, input.targetId, cleanReason, input.details?.takeIf { it.isNotBlank() })
            if (id != null && id.isNotBlank()) ReportResult.Success(id) else ReportResult.Failure
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) ReportResult.Failure else throw cancelled
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("provide a reason") || msg.contains("between 3 and 100")) {
            ReportResult.Invalid("Reason must be between 3 and 100 characters")
        } else if (msg.contains("Cannot report yourself") || msg.contains("own listing")) {
            ReportResult.Invalid("You cannot report your own account or listing")
        } else {
            ReportResult.Failure
        }
    }

    suspend fun blockUser(blockedUserId: String, reason: String? = null): BlockResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout BlockResult.AuthRequired
            if (blockedUserId.isBlank() || !blockedUserId.isUuid() || blockedUserId == identity.userId) {
                return@withTimeout BlockResult.Invalid("Cannot block yourself or invalid user ID")
            }
            gateway.blockUser(blockedUserId, reason)
            BlockResult.Success
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) BlockResult.Failure else throw cancelled
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("Cannot block yourself") || msg.contains("chk_user_blocks_not_self")) {
            BlockResult.Invalid("Cannot block yourself")
        } else {
            BlockResult.Failure
        }
    }

    suspend fun unblockUser(blockedUserId: String): BlockResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout BlockResult.AuthRequired
            if (blockedUserId.isBlank() || !blockedUserId.isUuid()) {
                return@withTimeout BlockResult.Invalid("Invalid user ID")
            }
            gateway.unblockUser(blockedUserId)
            BlockResult.Success
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) BlockResult.Failure else throw cancelled
    } catch (e: Exception) {
        BlockResult.Failure
    }

    suspend fun getBlockedUsers(): BlockedUsersResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout BlockedUsersResult.AuthRequired
            val users = gateway.getBlockedUsers()
            BlockedUsersResult.Success(users)
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) BlockedUsersResult.Failure else throw cancelled
    } catch (e: Exception) {
        BlockedUsersResult.Failure
    }

    suspend fun submitPrivacyRequest(requestType: String): PrivacyRequestResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout PrivacyRequestResult.AuthRequired
            if (requestType !in SUPPORTED_PRIVACY_REQUESTS) {
                return@withTimeout PrivacyRequestResult.Invalid("Unsupported request type")
            }
            gateway.submitPrivacyRequest(requestType)
            PrivacyRequestResult.Success
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) PrivacyRequestResult.Failure else throw cancelled
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("23505") || msg.contains("duplicate key") || msg.contains("idx_unique_pending_privacy_request")) {
            PrivacyRequestResult.Invalid("You already have a pending privacy request of this type.")
        } else {
            PrivacyRequestResult.Failure
        }
    }

    private companion object {
        const val OPERATION_TIMEOUT_MS = 15_000L
        val SUPPORTED_PRIVACY_REQUESTS = setOf("export_data", "delete_account")

        fun String.isUuid(): Boolean = runCatching { UUID.fromString(this).toString() == lowercase() }.getOrDefault(false)
    }
}
