package com.fairswap.core.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUserSafetyGateway : UserSafetyGateway {
    var loggedIn: Boolean = true
    var createReportError: String? = null
    var privacyRequestError: String? = null
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
        return blockedUsersList.toList()
    }

    override suspend fun submitPrivacyRequest(requestType: String) {
        privacyRequestError?.let { throw Exception(it) }
    }
}

class UserSafetyRepositoryTest {

    @Test
    fun createReport_success() = runBlocking {
        val gateway = FakeUserSafetyGateway()
        val repository = UserSafetyRepository(gateway)
        val result = repository.createReport(ReportInput(ReportTargetType.LISTING, "00000000-0000-0000-0000-000000000001", "Spam listing", "Some details"))
        assertTrue(result is ReportResult.Success)
        assertEquals("report_123", (result as ReportResult.Success).reportId)
    }

    @Test
    fun createReport_invalidUuid_returnsInvalidWithoutGatewayCall() = runBlocking {
        val gateway = FakeUserSafetyGateway()
        val repository = UserSafetyRepository(gateway)
        val result = repository.createReport(ReportInput(ReportTargetType.LISTING, "not_a_valid_uuid", "Spam listing", "Some details"))
        assertTrue(result is ReportResult.Invalid)
        assertEquals("Invalid report target ID format", (result as ReportResult.Invalid).message)
    }

    @Test
    fun createReport_shortReason_returnsInvalid() = runBlocking {
        val gateway = FakeUserSafetyGateway()
        val repository = UserSafetyRepository(gateway)
        val result = repository.createReport(ReportInput(ReportTargetType.LISTING, "00000000-0000-0000-0000-000000000001", "ab", "Some details"))
        assertTrue(result is ReportResult.Invalid)
        assertEquals("Reason must be between 3 and 100 characters", (result as ReportResult.Invalid).message)
    }

    @Test
    fun createReport_authRequired() = runBlocking {
        val gateway = FakeUserSafetyGateway().apply { loggedIn = false }
        val repository = UserSafetyRepository(gateway)
        val result = repository.createReport(ReportInput(ReportTargetType.LISTING, "00000000-0000-0000-0000-000000000001", "Spam listing", "Some details"))
        assertEquals(ReportResult.AuthRequired, result)
    }

    @Test
    fun blockAndUnblockUser_success() = runBlocking {
        val gateway = FakeUserSafetyGateway()
        val repository = UserSafetyRepository(gateway)
        
        val blockResult = repository.blockUser("00000000-0000-0000-0000-000000000002")
        assertEquals(BlockResult.Success, blockResult)
        
        val listResult = repository.getBlockedUsers()
        assertTrue(listResult is BlockedUsersResult.Success)
        assertEquals(listOf("00000000-0000-0000-0000-000000000002"), (listResult as BlockedUsersResult.Success).blockedIds)

        val unblockResult = repository.unblockUser("00000000-0000-0000-0000-000000000002")
        assertEquals(BlockResult.Success, unblockResult)
        
        val listResult2 = repository.getBlockedUsers()
        assertEquals(emptyList<String>(), (listResult2 as BlockedUsersResult.Success).blockedIds)
    }

    @Test
    fun blockUser_selfBlock_returnsInvalid() = runBlocking {
        val gateway = FakeUserSafetyGateway() // identity = "user_123"
        val repository = UserSafetyRepository(gateway)
        
        val blockResult = repository.blockUser("user_123")
        assertTrue(blockResult is BlockResult.Invalid)
        assertEquals("Cannot block yourself or invalid user ID", (blockResult as BlockResult.Invalid).message)
    }

    @Test
    fun submitPrivacyRequest_duplicateKey_returnsInvalid() = runBlocking {
        val gateway = FakeUserSafetyGateway().apply { privacyRequestError = "23505 duplicate key value violates unique constraint" }
        val repository = UserSafetyRepository(gateway)
        val result = repository.submitPrivacyRequest("delete_account")
        assertTrue(result is PrivacyRequestResult.Invalid)
        assertEquals("You already have a pending privacy request of this type.", (result as PrivacyRequestResult.Invalid).message)
    }

    @Test
    fun submitPrivacyRequest_unsupportedType_returnsInvalid() = runBlocking {
        val gateway = FakeUserSafetyGateway()
        val repository = UserSafetyRepository(gateway)
        val result = repository.submitPrivacyRequest("invalid_request_type")
        assertTrue(result is PrivacyRequestResult.Invalid)
        assertEquals("Unsupported request type", (result as PrivacyRequestResult.Invalid).message)
    }
}
