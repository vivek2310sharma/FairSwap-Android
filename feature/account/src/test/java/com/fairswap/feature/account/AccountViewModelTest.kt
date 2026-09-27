package com.fairswap.feature.account

import com.fairswap.core.account.AccountGateway
import com.fairswap.core.account.AccountIdentity
import com.fairswap.core.account.AccountRepository
import com.fairswap.core.account.AccountRow
import com.fairswap.core.account.ProfileInput
import com.fairswap.core.account.PublicProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.async
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    @Test fun initialLoadAndSuccessfulSaveUpdateDisplayedAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            assertEquals("Ada", viewModel.state.value.account?.profile?.displayName)
            viewModel.beginEditing()
            viewModel.updateForm(ProfileForm("Grace Hopper", "Compiler pioneer", "Arlington", "Virginia"))
            viewModel.save()
            runCurrent()
            assertEquals("Grace Hopper", viewModel.state.value.account?.profile?.displayName)
            assertFalse(viewModel.state.value.editing)
            assertFalse(viewModel.state.value.saving)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun saveFailureRetainsFormAndSuccessfulSummary() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply { failUpdate = true }
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            val edited = ProfileForm("New Name", "New bio", "New city", "New state")
            viewModel.beginEditing()
            viewModel.updateForm(edited)
            viewModel.save()
            runCurrent()
            assertEquals(edited, viewModel.state.value.form)
            assertEquals("Ada", viewModel.state.value.account?.profile?.displayName)
            assertTrue(viewModel.state.value.editing)
            assertFalse(viewModel.state.value.saving)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.error!!.contains("backend"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedRefreshRetainsLastSuccessfulAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway()
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            gateway.failProfile = true
            viewModel.refresh()
            runCurrent()
            assertEquals("Ada", viewModel.state.value.account?.profile?.displayName)
            assertNotNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.loading)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun identityChangeClearsPreviousProfileBeforeLoadingNewUser() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply { holdPolicy = CompletableDeferred() }
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            viewModel.beginEditing()
            viewModel.updateForm(ProfileForm("A private draft", "A bio", "A city", "A state"))
            viewModel.save()
            runCurrent()
            assertTrue(viewModel.state.value.saving)
            gateway.identity = AccountIdentity("user-b", "b@example.test")
            gateway.profileName = "Bea"
            viewModel.onUserChanged("user-b")
            assertNull(viewModel.state.value.account)
            assertEquals("user-b", viewModel.state.value.userId)
            assertEquals(ProfileForm(), viewModel.state.value.form)
            assertFalse(viewModel.state.value.editing)
            assertFalse(viewModel.state.value.saving)
            assertNull(viewModel.state.value.error)
            runCurrent()
            assertEquals("Bea", viewModel.state.value.account?.profile?.displayName)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun repeatedSaveIsBlockedAndTimeoutReleasesSavingState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply { holdPolicy = CompletableDeferred() }
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            viewModel.beginEditing()
            viewModel.updateForm(ProfileForm("A private draft", "A bio", "A city", "A state"))
            viewModel.save()
            viewModel.save()
            runCurrent()
            assertTrue(viewModel.state.value.saving)
            advanceTimeBy(15_001)
            runCurrent()
            assertFalse(viewModel.state.value.saving)
            assertNotNull(viewModel.state.value.error)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun sessionChangeCancelsSaveAndClearsPreviousAccountState() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply { holdPolicy = CompletableDeferred() }
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            viewModel.beginEditing()
            viewModel.updateForm(ProfileForm("A private draft", "A bio", "A city", "A state"))
            viewModel.save()
            runCurrent()
            assertTrue(viewModel.state.value.saving)
            gateway.identity = AccountIdentity("user-b", "b@example.test")
            viewModel.onUserChanged("user-b")
            assertNull(viewModel.state.value.account)
            assertEquals(ProfileForm(), viewModel.state.value.form)
            assertFalse(viewModel.state.value.saving)
            assertFalse(viewModel.state.value.editing)
            assertNull(viewModel.state.value.error)
            gateway.holdPolicy = null
            runCurrent()
            assertEquals("user-b", viewModel.state.value.account?.userId)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun stalePolicyProducesHandoffWithoutUpdatingProfile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gateway = FakeGateway().apply { accepted = false }
            val viewModel = AccountViewModel(AccountRepository(gateway))
            viewModel.onUserChanged("user-a")
            runCurrent()
            viewModel.beginEditing()
            val event = async { viewModel.events.first() }
            viewModel.save()
            runCurrent()
            assertEquals(AccountEvent.PolicyReviewRequired, event.await())
            assertEquals(0, gateway.updateCount)
            assertFalse(viewModel.state.value.saving)
            assertTrue(viewModel.state.value.editing)
        } finally { Dispatchers.resetMain() }
    }

    private class FakeGateway : AccountGateway {
        var identity: AccountIdentity? = AccountIdentity("user-a", "a@example.test")
        var profileName = "Ada"
        var accepted = true
        var updateCount = 0
        var failProfile = false
        var failUpdate = false
        var holdPolicy: CompletableDeferred<Unit>? = null

        override suspend fun currentIdentity() = identity
        override suspend fun profile(userId: String): PublicProfile? {
            if (failProfile) error("private backend exception")
            return PublicProfile(profileName, null, null, null, "now")
        }
        override suspend fun account(userId: String) = AccountRow(userId, "active", "terms-v1")
        override suspend fun policyAccepted(): Boolean {
            holdPolicy?.await()
            return accepted
        }
        override suspend fun updateProfile(userId: String, input: ProfileInput): PublicProfile {
            if (failUpdate) error("private backend exception")
            updateCount++
            profileName = input.displayName
            return PublicProfile(input.displayName, input.bio, input.city, input.state, "now")
        }
    }
}
