package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel
import kotlinx.serialization.Serializable

@Serializable
private data object AuthRoute : NavKey

@Composable
fun FairSwapApp() {
    val backStack = rememberNavBackStack(AuthRoute)
    val authViewModel: AuthViewModel = hiltViewModel()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                AuthRoute -> NavEntry(key) {
                    AuthScreen(
                        uiState = authViewModel.uiState,
                        onShowSignIn = authViewModel::showSignIn,
                        onShowSignUp = authViewModel::showSignUp,
                        onSignIn = authViewModel::signIn,
                        onSignUp = authViewModel::signUp,
                        onReacceptPolicies = authViewModel::reacceptPolicies,
                        onSignOut = authViewModel::signOut,
                    )
                }
                else -> error("Unknown navigation key: $key")
            }
        },
    )
}
