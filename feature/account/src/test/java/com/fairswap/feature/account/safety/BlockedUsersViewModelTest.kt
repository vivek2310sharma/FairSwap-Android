package com.fairswap.feature.account.safety

import com.fairswap.core.account.AccountIdentity
import com.fairswap.core.account.UserSafetyGateway
import com.fairswap.core.account.UserSafetyRepository
import com.fairswap.core.account.SafetySession
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedUsersViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: FakeUserSafetyGatewayFeature
    private lateinit var repository: UserSafetyRepository
    private lateinit var viewModel: BlockedUsersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = FakeUserSafetyGatewayFeature()
        repository = UserSafetyRepository(gateway)
        viewModel = BlockedUsersViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadUsers_loadsBlockedUsersSuccessfully() = runTest {
        gateway.blockedUsersList.add("00000000-0000-0000-0000-000000000002")
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(listOf("00000000-0000-0000-0000-000000000002"), viewModel.state.value.users)
    }

    @Test
    fun unblockUser_removesUserFromState() = runTest {
        gateway.blockedUsersList.add("00000000-0000-0000-0000-000000000002")
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.unblockUser("00000000-0000-0000-0000-000000000002")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.state.value.users)
    }

    @Test
    fun staleRefresh_afterUnblock_doesNotResurrectUnblockedUser() = runTest {
        gateway.blockedUsersList.add("00000000-0000-0000-0000-000000000002")
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Slow refresh starts
        gateway.getBlockedUsersDelayMs = 1000L
        viewModel.loadUsers()

        // 2. Unblock succeeds while refresh is pending
        viewModel.unblockUser("00000000-0000-0000-0000-000000000002")
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify unblocked user is NOT resurrected by stale refresh
        assertEquals(emptyList<String>(), viewModel.state.value.users)
    }

    @Test
    fun sessionReplacement_resetsState() = runTest {
        gateway.blockedUsersList.add("00000000-0000-0000-0000-000000000002")
        viewModel.onSessionChanged(SafetySession("user_123", 1L))
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch to session 2 (different user or new generation)
        gateway.blockedUsersList.clear()
        viewModel.onSessionChanged(SafetySession("user_456", 2L))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user_456", viewModel.state.value.userId)
        assertEquals(2L, viewModel.state.value.sessionGeneration)
        assertEquals(emptyList<String>(), viewModel.state.value.users)
    }
}

class FakeUserSafetyGatewayFeature : UserSafetyGateway {
    var loggedIn: Boolean = true
    var createReportError: String? = null
    var privacyRequestError: String? = null
    var getBlockedUsersDelayMs: Long = 0L
    val blockedUsersList = mutableListOf<String>()

    override suspend fun currentIdentity(): AccountIdentity? {
        return if (loggedIn) AccountIdentity("user_123", "user@example.com") else null
    }

    override suspend fun createReport(targetType: String, targetId: String, reason: String, details: String?): String {
        createReportError?.let { throw Exception(it) }
        return "report_123"
    }

    override suspend fun blockUser(blockedUserId: String, reason: String?) {
        blockedUsersList.add(blockedUserId)
    }

    override suspend fun unblockUser(blockedUserId: String) {
        blockedUsersList.remove(blockedUserId)
    }

    override suspend fun getBlockedUsers(): List<String> {
        if (getBlockedUsersDelayMs > 0) delay(getBlockedUsersDelayMs)
        return blockedUsersList.toList()
    }

    override suspend fun submitPrivacyRequest(requestType: String) {
        privacyRequestError?.let { throw Exception(it) }
    }
}
