package com.fairswap.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fairswap.core.auth.AuthGate
import com.fairswap.core.auth.AuthRepository
import com.fairswap.core.auth.SignUpInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {
    var uiState by mutableStateOf(AuthUiState())
        private set

    init {
        refresh()
    }

    fun showSignIn() {
        uiState = uiState.copy(mode = AuthMode.SignIn, gate = AuthGate.SignedOut, error = null)
    }

    fun showSignUp() {
        uiState = uiState.copy(mode = AuthMode.SignUp, gate = AuthGate.SignedOut, error = null)
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

        viewModelScope.launch {
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
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isInitializing: Boolean = true,
    val mode: AuthMode = AuthMode.SignIn,
    val gate: AuthGate = AuthGate.SignedOut,
    val error: String? = null,
)
