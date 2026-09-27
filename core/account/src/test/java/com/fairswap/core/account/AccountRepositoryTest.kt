package com.fairswap.core.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {
    @Test fun signedOutLoadRequiresAuth() = runTest {
        val gateway = FakeAccountGateway().apply { identity = null }
        assertEquals(AccountLoadResult.AuthRequired, AccountRepository(gateway).load())
    }

    @Test fun signedOutUpdateRequiresAuthWithoutPolicyOrMutation() = runTest {
        val gateway = FakeAccountGateway().apply { identity = null }
        assertEquals(ProfileUpdateResult.AuthRequired, AccountRepository(gateway).update(validInput))
        assertEquals(0, gateway.policyChecks)
        assertEquals(0, gateway.updateCount)
    }

    @Test fun loadUsesCurrentIdentityAndMapsNarrowAccountAndProfileModels() = runTest {
        val gateway = FakeAccountGateway()
        val result = AccountRepository(gateway).load() as AccountLoadResult.Success
        assertEquals("user-a", gateway.profileUserId)
        assertEquals("user-a", gateway.accountUserId)
        assertEquals(MyAccount("user-a", "a@example.test", "active", "terms-v1", PublicProfile("Ada", "Bio", "Copenhagen", "H", "updated")), result.account)
    }

    @Test fun staleLoadForAIsDiscardedAfterSwitchToB() = runTest {
        val gateway = FakeAccountGateway()
        val profileStarted = CompletableDeferred<Unit>()
        val releaseProfile = CompletableDeferred<Unit>()
        gateway.beforeProfile = {
            if (gateway.identity?.userId == "user-a") {
                profileStarted.complete(Unit)
                releaseProfile.await()
            }
        }
        val operation = async { AccountRepository(gateway).load() }
        profileStarted.await()
        gateway.identity = AccountIdentity("user-b", "b@example.test")
        releaseProfile.complete(Unit)
        assertEquals(AccountLoadResult.AuthRequired, operation.await())
    }

    @Test fun signOutDuringLoadDiscardsAResult() = runTest {
        val gateway = FakeAccountGateway()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gateway.beforeProfile = { started.complete(Unit); release.await() }
        val operation = async { AccountRepository(gateway).load() }
        started.await()
        gateway.identity = null
        release.complete(Unit)
        assertEquals(AccountLoadResult.AuthRequired, operation.await())
    }

    @Test fun stalePolicyAndPolicyRpcFailureDoNotUpdate() = runTest {
        val gateway = FakeAccountGateway().apply { accepted = false }
        assertEquals(ProfileUpdateResult.PolicyReviewRequired, AccountRepository(gateway).update(validInput))
        assertEquals(0, gateway.updateCount)
        gateway.policyThrows = true
        assertEquals(ProfileUpdateResult.Failure, AccountRepository(gateway).update(validInput))
        assertEquals(0, gateway.updateCount)
    }

    @Test fun invalidInputDoesNotCallPolicyOrUpdate() = runTest {
        val gateway = FakeAccountGateway()
        val invalid = validInput.copy(displayName = " ")
        assertTrue(AccountRepository(gateway).update(invalid) is ProfileUpdateResult.Invalid)
        assertEquals(0, gateway.policyChecks)
        assertEquals(0, gateway.updateCount)
    }

    @Test fun updateUsesCapturedUserAndOnlyNormalizedEditableFields() = runTest {
        val gateway = FakeAccountGateway()
        val result = AccountRepository(gateway).update(ProfileInput("  Ada Lovelace  ", "  bio  ", " ", " H ")) as ProfileUpdateResult.Success
        assertEquals("user-a", gateway.updatedUserId)
        assertEquals(ProfileInput("Ada Lovelace", "bio", null, "H"), gateway.updatedInput)
        assertEquals("Ada Lovelace", result.account.profile.displayName)
        assertEquals("a@example.test", result.account.email)
    }

    @Test fun userSwitchDuringUpdateDiscardsResponse() = runTest {
        val gateway = FakeAccountGateway()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        gateway.beforeUpdate = { started.complete(Unit); release.await() }
        val operation = async { AccountRepository(gateway).update(validInput) }
        started.await()
        gateway.identity = AccountIdentity("user-b", "b@example.test")
        release.complete(Unit)
        assertEquals(ProfileUpdateResult.AuthRequired, operation.await())
    }

    @Test fun suspendedAccountIsRestrictedAndUpdateRejectsIt() = runTest {
        val gateway = FakeAccountGateway().apply { status = "suspended" }
        assertEquals(AccountLoadResult.Restricted, AccountRepository(gateway).load())
        assertEquals(ProfileUpdateResult.Restricted, AccountRepository(gateway).update(validInput))
        assertEquals(0, gateway.updateCount)
    }

    @Test fun backendExceptionsBecomeGenericTypedFailures() = runTest {
        val gateway = FakeAccountGateway().apply { profileThrows = true }
        assertEquals(AccountLoadResult.Failure, AccountRepository(gateway).load())
        gateway.profileThrows = false
        gateway.updateThrows = true
        assertEquals(ProfileUpdateResult.Failure, AccountRepository(gateway).update(validInput))
    }

    private class FakeAccountGateway : AccountGateway {
        var identity: AccountIdentity? = AccountIdentity("user-a", "a@example.test")
        var status = "active"
        var accepted = true
        var policyThrows = false
        var profileThrows = false
        var updateThrows = false
        var updateCount = 0
        var policyChecks = 0
        var profileUserId: String? = null
        var accountUserId: String? = null
        var updatedUserId: String? = null
        var updatedInput: ProfileInput? = null
        var beforeProfile: suspend () -> Unit = {}
        var beforeUpdate: suspend () -> Unit = {}

        override suspend fun currentIdentity() = identity
        override suspend fun profile(userId: String): PublicProfile? {
            profileUserId = userId
            if (profileThrows) error("private profile backend response")
            beforeProfile()
            return PublicProfile("Ada", "Bio", "Copenhagen", "H", "updated")
        }
        override suspend fun account(userId: String): AccountRow? {
            accountUserId = userId
            return AccountRow(userId, status, "terms-v1")
        }
        override suspend fun policyAccepted(): Boolean {
            policyChecks++
            if (policyThrows) error("private backend details")
            return accepted
        }
        override suspend fun updateProfile(userId: String, input: ProfileInput): PublicProfile {
            if (updateThrows) error("private update backend response")
            updateCount++
            updatedUserId = userId
            updatedInput = input
            beforeUpdate()
            return PublicProfile(input.displayName, input.bio, input.city, input.state, "updated")
        }
    }

    private companion object {
        val validInput = ProfileInput("Ada Lovelace", "Bio", "London", "England")
    }
}

class ProfileValidatorTest {
    @Test fun normalizesWhitespaceAndEmptyOptionalFields() {
        assertEquals(
            ProfileValidation.Valid(ProfileInput("AB", null, null, "H")),
            ProfileValidator.validate(ProfileInput(" AB ", "  ", " ", " H ")),
        )
    }

    @Test fun displayNameBoundariesAreInclusive() {
        assertTrue(ProfileValidator.validate(ProfileInput("A", null, null, null)) is ProfileValidation.Invalid)
        assertEquals(ProfileValidation.Valid(ProfileInput("AB", null, null, null)), ProfileValidator.validate(ProfileInput("AB", null, null, null)))
        assertTrue(ProfileValidator.validate(ProfileInput("x".repeat(51), null, null, null)) is ProfileValidation.Invalid)
        assertTrue(ProfileValidator.validate(ProfileInput("x".repeat(50), null, null, null)) is ProfileValidation.Valid)
        assertTrue(ProfileValidator.validate(ProfileInput(" ", null, null, null)) is ProfileValidation.Invalid)
    }

    @Test fun optionalFieldMaximumLengthsAreEnforced() {
        assertTrue(ProfileValidator.validate(ProfileInput("AB", "x".repeat(500), "x".repeat(100), "x".repeat(100))) is ProfileValidation.Valid)
        assertEquals(ProfileField.BIO, (ProfileValidator.validate(ProfileInput("AB", "x".repeat(501), null, null)) as ProfileValidation.Invalid).error.field)
        assertEquals(ProfileField.CITY, (ProfileValidator.validate(ProfileInput("AB", null, "x".repeat(101), null)) as ProfileValidation.Invalid).error.field)
        assertEquals(ProfileField.STATE, (ProfileValidator.validate(ProfileInput("AB", null, null, "x".repeat(101))) as ProfileValidation.Invalid).error.field)
    }
}
