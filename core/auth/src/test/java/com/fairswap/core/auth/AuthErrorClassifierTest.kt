package com.fairswap.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorClassifierTest {
    @Test
    fun classifyOtpRequestError_networkError_isServiceUnavailable() {
        assertEquals(EmailOtpRequestResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpRequestError(null, null, true))
    }

    @Test
    fun classifyOtpRequestError_accountSensitive_isAcknowledged() {
        assertEquals(EmailOtpRequestResult.Acknowledged, AuthErrorClassifier.classifyOtpRequestError("user_not_found", null, false))
        assertEquals(EmailOtpRequestResult.Acknowledged, AuthErrorClassifier.classifyOtpRequestError("otp_disabled", null, false))
        assertEquals(EmailOtpRequestResult.Acknowledged, AuthErrorClassifier.classifyOtpRequestError("email_not_confirmed", null, false))
        assertEquals(EmailOtpRequestResult.Acknowledged, AuthErrorClassifier.classifyOtpRequestError("user_banned", null, false))
        assertEquals(EmailOtpRequestResult.Acknowledged, AuthErrorClassifier.classifyOtpRequestError("identity_not_found", null, false))
    }

    @Test
    fun classifyOtpRequestError_rateLimit_isRateLimited() {
        assertEquals(EmailOtpRequestResult.RateLimited, AuthErrorClassifier.classifyOtpRequestError("over_email_send_rate_limit", null, false))
        assertEquals(EmailOtpRequestResult.RateLimited, AuthErrorClassifier.classifyOtpRequestError("over_request_rate_limit", null, false))
        assertEquals(EmailOtpRequestResult.RateLimited, AuthErrorClassifier.classifyOtpRequestError(null, 429, false))
    }

    @Test
    fun classifyOtpRequestError_providerUnavailable_isProviderUnavailable() {
        assertEquals(EmailOtpRequestResult.ProviderUnavailable, AuthErrorClassifier.classifyOtpRequestError("provider_disabled", null, false))
        assertEquals(EmailOtpRequestResult.ProviderUnavailable, AuthErrorClassifier.classifyOtpRequestError("captcha_failed", null, false))
    }

    @Test
    fun classifyOtpRequestError_serviceUnavailable_isServiceUnavailable() {
        assertEquals(EmailOtpRequestResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpRequestError("unexpected_failure", null, false))
        assertEquals(EmailOtpRequestResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpRequestError(null, 500, false))
        assertEquals(EmailOtpRequestResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpRequestError("unknown_code", null, false))
    }

    @Test
    fun classifyOtpVerificationError_rateLimit_isRateLimited() {
        assertEquals(EmailOtpVerificationResult.RateLimited, AuthErrorClassifier.classifyOtpVerificationError("over_email_send_rate_limit", null, false))
        assertEquals(EmailOtpVerificationResult.RateLimited, AuthErrorClassifier.classifyOtpVerificationError(null, 429, false))
    }

    @Test
    fun classifyOtpVerificationError_serviceUnavailable_isServiceUnavailable() {
        assertEquals(EmailOtpVerificationResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpVerificationError("unexpected_failure", null, false))
        assertEquals(EmailOtpVerificationResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpVerificationError(null, 500, false))
        assertEquals(EmailOtpVerificationResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpVerificationError(null, null, true))
    }

    @Test
    fun classifyOtpVerificationError_invalidCode_isInvalidOrExpiredCode() {
        assertEquals(EmailOtpVerificationResult.InvalidOrExpiredCode, AuthErrorClassifier.classifyOtpVerificationError("otp_expired", null, false))
        assertEquals(EmailOtpVerificationResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpVerificationError("some_other_error", null, false))
        assertEquals(EmailOtpVerificationResult.ServiceUnavailable, AuthErrorClassifier.classifyOtpVerificationError(null, null, false))
    }
}
