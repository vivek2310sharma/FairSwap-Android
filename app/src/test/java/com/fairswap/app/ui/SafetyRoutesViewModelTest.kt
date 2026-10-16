package com.fairswap.app.ui

import com.fairswap.core.discovery.PublicListingUrlBuilder
import com.fairswap.core.account.AccountIdentity
import com.fairswap.core.account.UserSafetyGateway
import com.fairswap.core.account.UserSafetyRepository
import com.fairswap.core.account.WebRoutes
import com.fairswap.core.communication.CommunicationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
class SafetyRoutesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeUserSafetyGatewayApp
    private lateinit var repository: UserSafetyRepository
    private lateinit var viewModel: SafetyRoutesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = FakeUserSafetyGatewayApp()
        repository = UserSafetyRepository(gateway)
        viewModel = SafetyRoutesViewModel(
            webRoutes = WebRoutes("https://staging.fairswap.in"),
            userSafetyRepository = repository,
            publicListingUrlBuilder = PublicListingUrlBuilder("https://staging.fairswap.in")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dispatchBlockUser_sessionMismatch_doesNotCallGateway() = runTest {
        val userASession = CommunicationSession("user_A", 1L)
        val userBSession = CommunicationSession("user_B", 2L)

        var callbackCalled = false
        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userBSession }, // Mismatch!
            getLiveTopRoute = { "route_A" },
            getLiveUserId = { "user_B" },
            counterpartyId = "00000000-0000-0000-0000-000000000002",
            onSuccess = { callbackCalled = true }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, gateway.blockedUsersList.size)
        assertFalse(callbackCalled)
    }

    @Test
    fun dispatchBlockUser_routeMismatch_doesNotCallOnSuccess() = runTest {
        val userASession = CommunicationSession("user_123", 1L)
        gateway.loggedInIdentity = AccountIdentity("user_123", "user@example.com")

        var callbackCalled = false
        var liveRoute: Any = "route_A"

        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userASession },
            getLiveTopRoute = { liveRoute },
            getLiveUserId = { "user_123" },
            counterpartyId = "00000000-0000-0000-0000-000000000002",
            onSuccess = { callbackCalled = true }
        )

        // Route changed while pending!
        liveRoute = "route_B"
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("00000000-0000-0000-0000-000000000002"), gateway.blockedUsersList)
        assertFalse(callbackCalled) // Success callback ignored because top route changed!
    }

    @Test
    fun dispatchBlockUser_overlappingOperations_rejectsSecondOperation() = runTest {
        val userASession = CommunicationSession("user_123", 1L)
        gateway.loggedInIdentity = AccountIdentity("user_123", "user@example.com")
        gateway.blockDelayMs = 1000L // Delayed RPC

        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userASession },
            getLiveTopRoute = { "route_A" },
            getLiveUserId = { "user_123" },
            counterpartyId = "00000000-0000-0000-0000-000000000002",
            onSuccess = {}
        )

        // Attempt second overlapping block dispatch while first is pending
        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userASession },
            getLiveTopRoute = { "route_A" },
            getLiveUserId = { "user_123" },
            counterpartyId = "00000000-0000-0000-0000-000000000003",
            onSuccess = {}
        )

        testDispatcher.scheduler.advanceUntilIdle()

        // Only first block operation dispatched!
        assertEquals(listOf("00000000-0000-0000-0000-000000000002"), gateway.blockedUsersList)
    }

    @Test
    fun dispatchBlockUser_validSessionAndRoute_callsGatewayAndTriggersSuccess() = runTest {
        val userASession = CommunicationSession("user_123", 1L)
        gateway.loggedInIdentity = AccountIdentity("user_123", "user@example.com")

        var callbackCalled = false
        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userASession },
            getLiveTopRoute = { "route_A" },
            getLiveUserId = { "user_123" },
            counterpartyId = "00000000-0000-0000-0000-000000000002",
            onSuccess = { callbackCalled = true }
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("00000000-0000-0000-0000-000000000002"), gateway.blockedUsersList)
        assertTrue(callbackCalled)
    }

    @Test
    fun dispatchBlockUser_selfBlock_doesNotCallGatewayAndSetsError() = runTest {
        val userASession = CommunicationSession("user_123", 1L)
        gateway.loggedInIdentity = AccountIdentity("user_123", "user@example.com")

        viewModel.dispatchBlockUser(
            originatingSession = userASession,
            originatingRoute = "route_A",
            getLiveSession = { userASession },
            getLiveTopRoute = { "route_A" },
            getLiveUserId = { "user_123" },
            counterpartyId = "user_123", // Self-block!
            onSuccess = {}
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, gateway.blockedUsersList.size)
    }
}

class FakeUserSafetyGatewayApp : UserSafetyGateway {
    var loggedInIdentity: AccountIdentity? = AccountIdentity("user_123", "user@example.com")
    var createReportError: String? = null
    var privacyRequestError: String? = null
    var blockDelayMs: Long = 0L
    val blockedUsersList = mutableListOf<String>()

    override suspend fun currentIdentity(): AccountIdentity? = loggedInIdentity

    override suspend fun createReport(targetType: String, targetId: String, reason: String, details: String?): String? {
        createReportError?.let { throw Exception(it) }
        return "report_123"
    }

    override suspend fun blockUser(blockedUserId: String, reason: String?) {
        if (blockDelayMs > 0) delay(blockDelayMs)
        blockedUsersList.add(blockedUserId)
    }

    override suspend fun unblockUser(blockedUserId: String) {
        blockedUsersList.remove(blockedUserId)
    }

    override suspend fun getBlockedUsers(): List<String> {
        return blockedUsersList.toList()
    }

    override suspend fun submitPrivacyRequest(requestType: String) {
        privacyRequestError?.let { throw Exception(it) }
    }
}
