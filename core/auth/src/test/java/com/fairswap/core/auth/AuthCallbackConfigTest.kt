package com.fairswap.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthCallbackConfigTest {
    private val staging = AuthCallbackConfig("fairswap-staging", "auth")

    @Test
    fun pathsDistinguishConfirmationAndRecovery() {
        assertEquals(
            CallbackClassification.Valid(AuthCallbackKind.Confirmation),
            staging.classify("fairswap-staging://auth/confirm?code=abc"),
        )
        assertEquals(
            CallbackClassification.Valid(AuthCallbackKind.Recovery),
            staging.classify("fairswap-staging://auth/recovery?code=abc"),
        )
        val production = AuthCallbackConfig("fairswap", "auth")
        assertEquals(
            CallbackClassification.Valid(AuthCallbackKind.Confirmation),
            production.classify("fairswap://auth/confirm?code=abc"),
        )
        assertEquals(
            CallbackClassification.Invalid,
            production.classify("fairswap-staging://auth/confirm?code=abc"),
        )
    }

    @Test
    fun rejectsWrongFlavorHostPathAndMissingCode() {
        listOf(
            "fairswap://auth/confirm?code=abc",
            "fairswap-staging://other/confirm?code=abc",
            "fairswap-staging://auth/unknown?code=abc",
            "fairswap-staging://auth/con%66irm?code=abc",
            "fairswap-staging://user@auth/confirm?code=abc",
            "fairswap-staging://auth/recovery",
            "fairswap-staging://auth/recovery?code=",
            "fairswap-staging://auth/recovery?code=abc&code=def",
            "fairswap-staging://auth/recovery?error=access_denied",
            "not a uri",
        ).forEach { assertEquals(CallbackClassification.Invalid, staging.classify(it)) }
    }
}
