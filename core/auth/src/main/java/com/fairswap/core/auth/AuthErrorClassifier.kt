package com.fairswap.core.auth

object AuthErrorClassifier {
    fun classifyOtpRequestError(
        errorCode: String?,
        httpStatus: Int?,
        isNetworkError: Boolean
    ): EmailOtpRequestResult {
        if (isNetworkError) return EmailOtpRequestResult.ServiceUnavailable

        when (errorCode) {
            "user_not_found", "otp_disabled", "email_not_confirmed", "user_banned", "identity_not_found" ->
                return EmailOtpRequestResult.Acknowledged

            "over_email_send_rate_limit", "over_request_rate_limit", "over_sms_send_rate_limit", "rate_limit_exceeded" ->
                return EmailOtpRequestResult.RateLimited

            "email_provider_disabled", "provider_disabled", "signup_disabled", "email_address_not_authorized", "email_address_invalid", "bad_json", "validation_failed", "captcha_failed" ->
                return EmailOtpRequestResult.ProviderUnavailable

            "unexpected_failure", "request_timeout", "hook_timeout", "hook_timeout_after_retry" ->
                return EmailOtpRequestResult.ServiceUnavailable
        }

        if (httpStatus == 429) return EmailOtpRequestResult.RateLimited
        if (httpStatus != null && httpStatus >= 500) return EmailOtpRequestResult.ServiceUnavailable

        return EmailOtpRequestResult.ServiceUnavailable
    }

    fun classifyOtpVerificationError(
        errorCode: String?,
        httpStatus: Int?,
        isNetworkError: Boolean
    ): EmailOtpVerificationResult {
        if (isNetworkError) return EmailOtpVerificationResult.ServiceUnavailable

        when (errorCode) {
            "over_email_send_rate_limit", "over_request_rate_limit", "over_sms_send_rate_limit", "rate_limit_exceeded" ->
                return EmailOtpVerificationResult.RateLimited

            "unexpected_failure", "request_timeout", "hook_timeout", "hook_timeout_after_retry" ->
                return EmailOtpVerificationResult.ServiceUnavailable
        }

        if (httpStatus == 429) return EmailOtpVerificationResult.RateLimited
        if (httpStatus != null && httpStatus >= 500) return EmailOtpVerificationResult.ServiceUnavailable

        if (errorCode == "otp_expired") return EmailOtpVerificationResult.InvalidOrExpiredCode

        return EmailOtpVerificationResult.ServiceUnavailable
    }
}
