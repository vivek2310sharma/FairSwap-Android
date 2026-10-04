package com.fairswap.core.auth

object AuthValidation {
    private val EmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateEmail(email: String): String? {
        if (!EmailPattern.matches(email.trim())) return "Please enter a valid email address."
        return null
    }

    fun validateOtp(otp: String): String? {
        if (!otp.matches(Regex("^[0-9]{6}$"))) return "Please enter a valid 6-digit verification code."
        return null
    }

    fun validateSignIn(email: String, password: String): String? {
        if (!EmailPattern.matches(email.trim())) return "Please enter a valid email address."
        if (password.isBlank()) return "Password is required."
        return null
    }

    fun validateSignUp(input: SignUpInput): String? {
        val displayName = input.displayName.trim()
        if (displayName.length !in 2..50) {
            return "Display name must be between 2 and 50 characters."
        }
        if (!EmailPattern.matches(input.email.trim())) {
            return "Please enter a valid email address."
        }
        if (input.password.length < 8) {
            return "Password must be at least 8 characters long."
        }
        if (!input.termsAccepted) {
            return "You must accept the Terms of Service and Acceptable Use Policy, and acknowledge the Privacy Policy."
        }
        if (!input.ageConfirmed) {
            return "You must confirm that you are at least 18 years old."
        }
        return null
    }

    fun validateResetEmail(email: String): String? =
        if (EmailPattern.matches(email.trim())) null else "Please enter a valid email address."

    fun validateNewPassword(password: String, confirmation: String): String? {
        if (password.length < 8) return "Password must be at least 8 characters long."
        if (password != confirmation) return "Passwords do not match."
        return null
    }
}
