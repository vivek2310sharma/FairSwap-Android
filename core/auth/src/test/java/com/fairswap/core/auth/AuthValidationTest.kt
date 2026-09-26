package com.fairswap.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun signUpRequiresLegalAcceptance() {
        val error = AuthValidation.validateSignUp(validSignUp().copy(termsAccepted = false))
        assertEquals(
            "You must accept the Terms of Service and Acceptable Use Policy, and acknowledge the Privacy Policy.",
            error,
        )
    }

    @Test
    fun signUpRequiresAgeConfirmation() {
        val error = AuthValidation.validateSignUp(validSignUp().copy(ageConfirmed = false))
        assertEquals("You must confirm that you are at least 18 years old.", error)
    }

    @Test
    fun validSignUpMatchesWebValidationBaseline() {
        assertNull(AuthValidation.validateSignUp(validSignUp()))
    }

    @Test
    fun legalVersionsMatchPublicBetaBaseline() {
        assertEquals("2026-09-18", LegalPolicy.TermsVersion)
        assertEquals("2026-09-18", LegalPolicy.PrivacyVersion)
        assertEquals("2026-09-18", LegalPolicy.AcceptableUseVersion)
        assertEquals(18, LegalPolicy.MinimumAge)
    }

    private fun validSignUp() = SignUpInput(
        displayName = "Test User",
        email = "test@example.com",
        password = "SecurePassword123!",
        termsAccepted = true,
        ageConfirmed = true,
    )
}
