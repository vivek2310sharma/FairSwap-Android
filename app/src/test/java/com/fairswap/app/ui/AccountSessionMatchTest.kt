package com.fairswap.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSessionMatchTest {
    @Test fun accountUiRequiresExactGateAndLiveSessionIdentity() {
        assertTrue(accountGateSessionMatches("user-a", "user-a"))
        assertFalse(accountGateSessionMatches("user-a", "user-b"))
        assertFalse(accountGateSessionMatches("user-a", null))
        assertFalse(accountGateSessionMatches(null, "user-b"))
    }
}
