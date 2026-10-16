package com.fairswap.feature.account.safety

import com.fairswap.core.account.SafetySession
import com.fairswap.core.account.UserSafetyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeUserSafetyGatewayFeature
    private lateinit var repository: UserSafetyRepository
    private lateinit var viewModel: PrivacyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = FakeUserSafetyGatewayFeature()
        repository = UserSafetyRepository(gateway)
        viewModel = PrivacyViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun submitExportData_submitsSuccessfully() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.submitExportData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.submitting)
        assertNotNull(viewModel.state.value.successMessage)
        assertEquals("Data export requested.", viewModel.state.value.successMessage)
    }

    @Test
    fun submitDeleteAccount_submitsSuccessfully() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.submitDeleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.submitting)
        assertNotNull(viewModel.state.value.successMessage)
        assertEquals("Account deletion requested.", viewModel.state.value.successMessage)
    }

    @Test
    fun sessionReplacement_clearsState() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.submitDeleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        // Session changes!
        viewModel.onSessionChanged(SafetySession("user_456", 2L))

        assertEquals("user_456", viewModel.state.value.userId)
        assertEquals(2L, viewModel.state.value.sessionGeneration)
        assertNull(viewModel.state.value.successMessage)
    }
}
