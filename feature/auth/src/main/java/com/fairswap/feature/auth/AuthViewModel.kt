package com.fairswap.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.auth.AuthCallbackCoordinator
import com.fairswap.core.auth.AuthCallbackEvent
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.auth.AuthValidation
import com.fairswap.core.auth.EmailOtpRequestResult
import com.fairswap.core.auth.EmailOtpVerificationResult
import com.fairswap.core.auth.SignUpInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val callbacks: AuthCallbackCoordinator,
) : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    private var authJob: Job? = null
    private var handledCallbackSequence = 0L
    private var authGenerationId = 0L

    private val cooldowns = mutableMapOf<String, Long>()

    init {
        refresh()
        viewModelScope.launch {
            callbacks.events.collect { event ->
                if (event == null || event.sequence <= handledCallbackSequence) return@collect
                when (event) {
                    is AuthCallbackEvent.Processing -> {
                        authGenerationId++
                        authJob?.cancel()
                        uiState = uiState.copy(isLoading = true, error = null)
                    }
                    is AuthCallbackEvent.Succeeded -> {
                        authGenerationId++
                        handledCallbackSequence = event.sequence
                        callbacks.acknowledge(event.sequence)
                        authJob?.cancel()
                        uiState = uiState.copy(isLoading = false)
                        refresh()
                    }
                    is AuthCallbackEvent.Failed -> {
                        authGenerationId++
                        handledCallbackSequence = event.sequence
                        callbacks.acknowledge(event.sequence)
                        authJob?.cancel()
                        uiState = uiState.copy(
                            isLoading = false,
                            isInitializing = false,
                            error = "This sign-in link is invalid or expired. Request a new link and try again.",
                        )
                    }
                }
            }
        }
    }

    fun submitEmail(email: String) {
        val error = AuthValidation.validateEmail(email)
        if (error != null) {
            uiState = uiState.copy(error = error)
            return
        }
        authGenerationId++
        uiState = uiState.copy(
            signInStep = SignInStep.Password,
            normalizedEmail = email.trim().lowercase(),
            otpAcknowledged = false,
            error = null
        )
    }

    fun changeEmail() {
        authGenerationId++
        authJob?.cancel()
        uiState = uiState.copy(
            signInStep = SignInStep.Email,
            otpAcknowledged = false,
            error = null,
            isLoading = false
        )
    }

    fun switchToPassword() {
        authGenerationId++
        authJob?.cancel()
        uiState = uiState.copy(
            signInStep = SignInStep.Password,
            otpAcknowledged = false,
            error = null,
            isLoading = false
        )
    }

    fun switchToOtp() {
        if (uiState.isLoading || uiState.signInStep == SignInStep.Otp) return
        authGenerationId++
        authJob?.cancel()
        val currentCooldown = cooldowns[uiState.normalizedEmail] ?: 0L
        if (currentCooldown > System.currentTimeMillis()) {
            uiState = uiState.copy(
                signInStep = SignInStep.Otp,
                error = null,
                resendAvailableAt = currentCooldown,
                otpAcknowledged = true,
                isLoading = false
            )
        }
        requestOtp()
    }

    fun requestOtp() {
        val email = uiState.normalizedEmail
        if (email.isBlank()) return

        val currentGen = ++authGenerationId
        val now = System.currentTimeMillis()
        val currentCooldown = cooldowns[email] ?: 0L
        if (uiState.isLoading || currentCooldown > now) {
            return
        }

        authJob?.cancel()
        authJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val result = repository.requestEmailOtp(email)
            if (currentGen != authGenerationId) return@launch
            when (result) {
                EmailOtpRequestResult.Acknowledged -> {
                    val newCooldown = System.currentTimeMillis() + 60_000L
                    cooldowns[email] = newCooldown
                    uiState = uiState.copy(
                        signInStep = SignInStep.Otp,
                        isLoading = false,
                        otpAcknowledged = true,
                        resendAvailableAt = newCooldown,
                        error = null
                    )
                }
                EmailOtpRequestResult.RateLimited -> {
                    uiState = uiState.copy(isLoading = false, error = "Too many requests. Please wait a moment before trying again.")
                }
                EmailOtpRequestResult.ServiceUnavailable -> {
                    uiState = uiState.copy(isLoading = false, error = "Unable to send verification code at this time. Please try again later.")
                }
                EmailOtpRequestResult.ProviderUnavailable -> {
                    uiState = uiState.copy(isLoading = false, error = "Sign-in with email code is currently unavailable. Please sign in with your password or contact support.")
                }
                EmailOtpRequestResult.InvalidEmail -> {
                    uiState = uiState.copy(isLoading = false, error = "Please provide a valid email address.")
                }
            }
        }
    }

    fun verifyOtp(code: String) {
        val email = uiState.normalizedEmail
        if (email.isBlank()) return

        val currentGen = ++authGenerationId
        authJob?.cancel()
        authJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val result = repository.verifyEmailOtp(email, code)
            if (currentGen != authGenerationId) return@launch
            when (result) {
                is EmailOtpVerificationResult.Authenticated -> {
                    val gate = result.gate
                    if (gate is AuthGate.Failure) {
                        uiState = uiState.copy(isLoading = false, error = gate.message)
                    } else {
                        uiState = uiState.copy(isLoading = false, gate = gate, error = null)
                    }
                }
                EmailOtpVerificationResult.InvalidOrExpiredCode -> uiState = uiState.copy(isLoading = false, error = "The verification code is invalid or has expired. Please check the code and try again.")
                EmailOtpVerificationResult.RateLimited -> uiState = uiState.copy(isLoading = false, error = "Too many attempts. Please wait a moment before trying again.")
                EmailOtpVerificationResult.ServiceUnavailable -> uiState = uiState.copy(isLoading = false, error = "Unable to verify code at this time. Please try again later.")
                EmailOtpVerificationResult.IdentityVerificationFailed -> uiState = uiState.copy(isLoading = false, error = "Could not verify user identity. Please try again.")
                EmailOtpVerificationResult.InvalidInput -> uiState = uiState.copy(isLoading = false, error = "Please enter a valid 6-digit verification code.")
            }
        }
    }

    fun showSignIn() {
        uiState = uiState.copy(mode = AuthMode.SignIn, signInStep = SignInStep.Email, normalizedEmail = "", gate = AuthGate.SignedOut, error = null)
    }

    fun showSignUp() {
        uiState = uiState.copy(mode = AuthMode.SignUp, signInStep = SignInStep.Email, normalizedEmail = "", gate = AuthGate.SignedOut, error = null)
    }

    fun showForgotPassword() {
        uiState = uiState.copy(mode = AuthMode.ForgotPassword, signInStep = SignInStep.Email, normalizedEmail = "", gate = AuthGate.SignedOut, error = null)
    }

    fun requestPasswordReset(email: String) {
        launchAuth { repository.requestPasswordReset(email) }
    }

    fun setNewPassword(password: String, confirmation: String) {
        launchAuth { repository.setNewPassword(password, confirmation) }
    }

    fun signIn(email: String, password: String) {
        val normalizedEmail = email.trim().lowercase()
        val currentGen = ++authGenerationId
        launchAuth {
            if (currentGen == authGenerationId) repository.signIn(normalizedEmail, password) else AuthGate.Failure("Cancelled")
        }
    }

    fun signUp(input: SignUpInput) {
        launchAuth { repository.signUp(input) }
    }

    fun reacceptPolicies() {
        launchAuth(repository::reacceptPolicies)
    }

    fun signOut() {
        authGenerationId++
        authJob?.cancel()
        authJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            val currentGen = authGenerationId
            val result = repository.signOut()
            if (currentGen != authGenerationId) return@launch
            uiState = uiState.copy(
                isLoading = false,
                isInitializing = false,
                gate = if (result is AuthGate.Failure) uiState.gate else result,
                error = if (result is AuthGate.Failure) result.message else null
            )
        }
    }

    fun refresh() {
        val currentGen = ++authGenerationId
        launchAuth {
            if (currentGen == authGenerationId) repository.bootstrap() else AuthGate.Failure("Cancelled")
        }
    }

    private fun launchAuth(block: suspend () -> AuthGate) {
        if (uiState.isLoading) return
        val currentGen = authGenerationId

        authJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = block()) {
                is AuthGate.Failure -> uiState = uiState.copy(
                    isLoading = false,
                    isInitializing = false,
                    error = if (currentGen != authGenerationId || result.message == "Cancelled") null else result.message,
                )
                else -> uiState = uiState.copy(
                    isLoading = false,
                    isInitializing = false,
                    gate = if (currentGen != authGenerationId) uiState.gate else result,
                    error = null,
                )
            }
        }
    }
}

enum class AuthMode {
    SignIn,
    SignUp,
    ForgotPassword,
}

enum class SignInStep {
    Email,
    Password,
    Otp,
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = true,
    val mode: AuthMode = AuthMode.SignIn,
    val signInStep: SignInStep = SignInStep.Email,
    val normalizedEmail: String = "",
    val otpAcknowledged: Boolean = false,
    val resendAvailableAt: Long = 0L,
    val gate: AuthGate = AuthGate.SignedOut,
    val error: String? = null,
)
