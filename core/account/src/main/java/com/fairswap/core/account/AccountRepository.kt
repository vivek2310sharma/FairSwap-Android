package com.fairswap.core.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class AccountRepository(private val gateway: AccountGateway) {
    suspend fun load(): AccountLoadResult = try {
        withTimeout(OPERATION_TIMEOUT_MS) {
            val identity = gateway.currentIdentity() ?: return@withTimeout AccountLoadResult.AuthRequired
            val account = gateway.account(identity.userId) ?: return@withTimeout AccountLoadResult.Failure
            if (account.status != ACTIVE_STATUS) return@withTimeout AccountLoadResult.Restricted
            val profile = gateway.profile(identity.userId) ?: return@withTimeout AccountLoadResult.Failure
            if (!sameCurrentUser(identity.userId)) return@withTimeout AccountLoadResult.AuthRequired
            AccountLoadResult.Success(MyAccount(identity.userId, identity.email, account.status, account.termsVersion, profile))
        }
    } catch (cancelled: CancellationException) {
        if (cancelled is TimeoutCancellationException) AccountLoadResult.Failure else throw cancelled
    } catch (_: Exception) {
        AccountLoadResult.Failure
    }

    suspend fun update(input: ProfileInput): ProfileUpdateResult {
        val identity = try {
            gateway.currentIdentity()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ProfileUpdateResult.Failure
        }
            ?: return ProfileUpdateResult.AuthRequired
        val valid = when (val validation = ProfileValidator.validate(input)) {
            is ProfileValidation.Invalid -> return ProfileUpdateResult.Invalid(validation.error)
            is ProfileValidation.Valid -> validation.normalized
        }
        return try {
            withTimeout(OPERATION_TIMEOUT_MS) {
                if (!gateway.policyAccepted()) return@withTimeout ProfileUpdateResult.PolicyReviewRequired
                if (!sameCurrentUser(identity.userId)) return@withTimeout ProfileUpdateResult.AuthRequired
                val account = gateway.account(identity.userId) ?: return@withTimeout ProfileUpdateResult.Failure
                if (account.status != ACTIVE_STATUS) return@withTimeout ProfileUpdateResult.Restricted
                val profile = gateway.updateProfile(identity.userId, valid)
                if (!sameCurrentUser(identity.userId)) return@withTimeout ProfileUpdateResult.AuthRequired
                ProfileUpdateResult.Success(MyAccount(identity.userId, identity.email, account.status, account.termsVersion, profile))
            }
        } catch (cancelled: CancellationException) {
            if (cancelled is TimeoutCancellationException) ProfileUpdateResult.Failure else throw cancelled
        } catch (_: Exception) {
            ProfileUpdateResult.Failure
        }
    }

    private suspend fun sameCurrentUser(userId: String): Boolean = try {
        gateway.currentIdentity()?.userId == userId
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val OPERATION_TIMEOUT_MS = 15_000L
        const val ACTIVE_STATUS = "active"
    }
}
