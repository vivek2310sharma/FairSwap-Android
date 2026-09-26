package com.fairswap.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fairswap.feature.auth.AuthScreen
import com.fairswap.feature.auth.AuthViewModel

@Composable
fun FairSwapApp() {
    val authViewModel: AuthViewModel = viewModel()

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
