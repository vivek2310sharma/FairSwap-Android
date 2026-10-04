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
    fun signUpEnforcesDisplayNameBoundsAfterTrimming() {
        assertEquals(
            "Display name must be between 2 and 50 characters.",
            AuthValidation.validateSignUp(validSignUp().copy(displayName = " a ")),
        )
        assertNull(AuthValidation.validateSignUp(validSignUp().copy(displayName = "x".repeat(50))))
        assertEquals(
            "Display name must be between 2 and 50 characters.",
            AuthValidation.validateSignUp(validSignUp().copy(displayName = "x".repeat(51))),
        )
    }

    @Test
    fun signUpEnforcesEightCharacterPassword() {
        assertEquals(
            "Password must be at least 8 characters long.",
            AuthValidation.validateSignUp(validSignUp().copy(password = "1234567")),
        )
        assertNull(AuthValidation.validateSignUp(validSignUp().copy(password = "12345678")))
    }

    @Test
    fun validateEmailRejectsInvalidEmail() {
        assertEquals("Please enter a valid email address.", AuthValidation.validateEmail("invalid"))
        assertNull(AuthValidation.validateEmail(" user@example.com "))
    }

    @Test
    fun validateOtpEnforcesExactlySixDigits() {
        assertEquals("Please enter a valid 6-digit verification code.", AuthValidation.validateOtp("12345"))
        assertEquals("Please enter a valid 6-digit verification code.", AuthValidation.validateOtp("1234567"))
        assertEquals("Please enter a valid 6-digit verification code.", AuthValidation.validateOtp("abcdef"))
        assertNull(AuthValidation.validateOtp("123456"))
    }

    @Test
    fun resetEmailMustBeValid() {
        assertEquals("Please enter a valid email address.", AuthValidation.validateResetEmail("invalid"))
        assertNull(AuthValidation.validateResetEmail(" user@example.com "))
    }

    @Test
    fun newPasswordRequiresEightCharactersAndMatchingConfirmation() {
        assertEquals(
            "Password must be at least 8 characters long.",
            AuthValidation.validateNewPassword("1234567", "1234567"),
        )
        assertEquals(
            "Passwords do not match.",
            AuthValidation.validateNewPassword("12345678", "different"),
        )
        assertNull(AuthValidation.validateNewPassword("12345678", "12345678"))
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
