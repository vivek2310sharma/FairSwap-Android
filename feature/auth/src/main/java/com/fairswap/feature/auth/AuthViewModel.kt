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

    init {
        refresh()
        viewModelScope.launch {
            callbacks.events.collect { event ->
                if (event == null || event.sequence <= handledCallbackSequence) return@collect
                when (event) {
                    is AuthCallbackEvent.Processing -> {
                        authJob?.cancel()
                        uiState = uiState.copy(isLoading = true, error = null)
                    }
                    is AuthCallbackEvent.Succeeded -> {
                        handledCallbackSequence = event.sequence
                        callbacks.acknowledge(event.sequence)
                        authJob?.cancel()
                        uiState = uiState.copy(isLoading = false)
                        refresh()
                    }
                    is AuthCallbackEvent.Failed -> {
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

    fun showSignIn() {
        uiState = uiState.copy(mode = AuthMode.SignIn, gate = AuthGate.SignedOut, error = null)
    }

    fun showSignUp() {
        uiState = uiState.copy(mode = AuthMode.SignUp, gate = AuthGate.SignedOut, error = null)
    }

    fun showForgotPassword() {
        uiState = uiState.copy(mode = AuthMode.ForgotPassword, gate = AuthGate.SignedOut, error = null)
    }

    fun requestPasswordReset(email: String) {
        launchAuth { repository.requestPasswordReset(email) }
    }

    fun setNewPassword(password: String, confirmation: String) {
        launchAuth { repository.setNewPassword(password, confirmation) }
    }

    fun signIn(email: String, password: String) {
        launchAuth { repository.signIn(email, password) }
    }

    fun signUp(input: SignUpInput) {
        launchAuth { repository.signUp(input) }
    }

    fun reacceptPolicies() {
        launchAuth(repository::reacceptPolicies)
    }

    fun signOut() {
        launchAuth(repository::signOut)
    }

    fun refresh() {
        launchAuth(repository::bootstrap)
    }

    private fun launchAuth(block: suspend () -> AuthGate) {
        if (uiState.isLoading) return

        authJob = viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            when (val result = block()) {
                is AuthGate.Failure -> uiState = uiState.copy(
                    isLoading = false,
                    isInitializing = false,
                    error = result.message,
                )
                else -> uiState = uiState.copy(
                    isLoading = false,
                    isInitializing = false,
                    gate = result,
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

data class AuthUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = true,
    val mode: AuthMode = AuthMode.SignIn,
    val gate: AuthGate = AuthGate.SignedOut,
    val error: String? = null,
)
