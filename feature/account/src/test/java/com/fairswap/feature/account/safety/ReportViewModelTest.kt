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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeUserSafetyGatewayFeature
    private lateinit var repository: UserSafetyRepository
    private lateinit var viewModel: ReportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = FakeUserSafetyGatewayFeature()
        repository = UserSafetyRepository(gateway)
        viewModel = ReportViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bindTarget_invalidTarget_setsError() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.bindTarget("invalid_target_type", "00000000-0000-0000-0000-000000000001")

        assertEquals("Invalid or unsupported report target.", viewModel.state.value.error)
        assertEquals(null, viewModel.state.value.targetType)
    }

    @Test
    fun bindTarget_differentTarget_resetsDraftsAndSuccess() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.bindTarget("listing", "00000000-0000-0000-0000-000000000001")
        viewModel.updateReason("Valid Reason")
        viewModel.updateDetails("Draft details")

        // Switch target
        viewModel.bindTarget("listing", "00000000-0000-0000-0000-000000000002")

        assertEquals("", viewModel.state.value.reason)
        assertEquals("", viewModel.state.value.details)
        assertFalse(viewModel.state.value.success)
    }

    @Test
    fun submitReport_shortReason_showsErrorWithoutCallingGateway() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.bindTarget("listing", "00000000-0000-0000-0000-000000000001")
        viewModel.updateReason("ab") // < 3 chars

        viewModel.submitReport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Reason must be between 3 and 100 characters", viewModel.state.value.error)
        assertFalse(viewModel.state.value.success)
    }

    @Test
    fun submitReport_validReason_submitsSuccessfully() = runTest {
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        viewModel.bindTarget("listing", "00000000-0000-0000-0000-000000000001")
        viewModel.updateReason("Valid Reason")
        viewModel.updateDetails("Some details")

        viewModel.submitReport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun submitReport_unauthenticated_emitsAuthRequired() = runTest {
        viewModel.onSessionChanged(null) // Unauthenticated
        viewModel.bindTarget("listing", "00000000-0000-0000-0000-000000000001")
        viewModel.updateReason("Valid Reason")

        viewModel.submitReport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.success)
    }
}
